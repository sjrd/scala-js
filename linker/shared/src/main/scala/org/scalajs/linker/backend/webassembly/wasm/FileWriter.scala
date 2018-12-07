/* Copyright 2009-2018 EPFL, Lausanne */

package org.scalajs.linker.backend.webassembly.wasm

import java.io.{IOException, File, FileWriter => JFW}
import scala.sys.process._

import org.scalajs.linker.LinkingException

class FileWriter(module: Module, toExecute: Set[String]) {
  object Env {
    trait OS
    object Linux extends OS
    object Windows extends OS
    object Mac extends OS

    lazy val os = {
      // If all fails returns Linux
      val optOsName = Option(System.getProperty("os.name"))
      optOsName.map(_.toLowerCase()).map { osName =>
        if (osName contains "linux") Linux
        else if (osName contains "win") Windows
        else if (osName contains "mac") Mac
        else Linux
      } getOrElse Linux
    }
  }

  def writeWasmText(fileName: String): Unit = {
    val fw = new JFW(new File(fileName))
    fw.write(Printer(module))
    fw.flush()
  }

  def writeNodejsWrapper(fileName: String, moduleFile: String): Unit = {
    val wrapperString =
      s"""|// `Wasm` does **not** understand node buffers, but thankfully a node buffer
          |// is easy to convert to a native Uint8Array.
          |function toUint8Array(buf) {
          |  var u = new Uint8Array(buf.length);
          |  for (var i = 0; i < buf.length; ++i) {
          |    u[i] = buf[i];
          |  }
          |  return u;
          |}
          |// Loads a WebAssembly dynamic library, returns a promise.
          |// imports is an optional imports object
          |function loadWebAssembly(filename, imports) {
          |  // Fetch the file and compile it
          |  const buffer = toUint8Array(require('fs').readFileSync(filename))
          |  return WebAssembly.compile(buffer).then(module => {
          |    return new WebAssembly.Instance(module, imports)
          |  })
          |}
          |
          |var memory = new WebAssembly.Memory({initial:100});
          |var importObject = {
          |  system: {
          |    mem: memory,
          |
          |    printInt: function(arg) {
          |      console.log(arg);
          |      0;
          |    },
          |
          |    printString: function(arg) {
          |      var bufView = new Uint32Array(memory.buffer);
          |      var len = bufView[arg];
          |      var i = 0;
          |      var result = "";
          |      while(i < len) {
          |        result += String.fromCharCode(bufView[arg+i+1]);
          |        i = i + 1
          |      }
          |      console.log(result);
          |      0;
          |    }
          |
          |  }
          |};
          |
          |loadWebAssembly('$moduleFile', importObject).then(function(instance) {
          |""".stripMargin ++
          module.functions.filter(f => toExecute(f.name)).map { f =>
      s"""|  console.log("${f.name} = " + instance.exports.${f.name}());\n""".stripMargin // FIXME: Add printing for all types to the wasm side
          }.mkString ++
       """|}).catch( function(error) {
          |  console.log("Error in wasm application")
          |  process.exit(1)
          |})
          |""".stripMargin
    val fw = new JFW(new File(fileName))
    fw.write(wrapperString)
    fw.flush()
  }

  def writeFiles() = {
    val outDirName = "wasmout"

    def withExt(ext: String) = s"$outDirName/${module.name}.$ext"

    val (local, inPath) = {
      import Env._
      os match {
        case Linux   => ("./unmanaged/wasm/wat2wasm",     "wat2wasm")
        case Windows => ("./unmanaged/wasm/wat2wasm.exe", "wat2wasm.exe")
        case Mac     => ("./unmanaged/wasm/mac/wat2wasm", "wat2wasm")
      }
    }

    val w2wOptions = s"${withExt("wat")} -o ${withExt("wasm")}"

    val outDir = new File(outDirName)
    if (!outDir.exists()) {
      outDir.mkdir()
    }

    writeWasmText(withExt("wat"))

    try {
      try {
        s"$local $w2wOptions".!!
      } catch {
        case _: IOException =>
          s"$inPath $w2wOptions".!!
      }
    } catch {
      case e: IOException =>
        throw new IOException(
          "wat2wasm utility was not found under expected directory or in system path, " +
          "or did not have permission to execute",
          e
        )
      case e: RuntimeException =>
        throw new LinkingException(
          s"wat2wasm failed to translate WebAssembly text file ${withExt("wat")} to binary",
          e
        )
    }

    writeNodejsWrapper(
      withExt("js"), withExt("wasm") //, CheckFilter(
    )

  }

}
