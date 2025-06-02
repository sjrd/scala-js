# WASI echo-server example

`wasmtime serve` and low-level container runtime for Wasm like [runwasi](https://github.com/containerd/runwasi) uses the [wasi:http/proxy](https://github.com/WebAssembly/wasi-http/blob/main/wit/proxy.wit) world, which is a world just for accepting requests and sending back the responses.

This is a simple example to run WASI HTTP server using `wasmtime serve` command.

```sh
sbt:Scala.js> set Global/enableWasmEverywhere := true
sbt:Scala.js> echoserver2_12/fastLinkJS
[info] Fast optimizing .../scala-wasm/examples/echo-server/.2.12/target/scala-2.12/echo-fastopt
```

This produces the [Wasm Component Model compliant Wasm module binary](https://github.com/WebAssembly/component-model/pull/378). We can translate the Wasm module to Wasm component binary using `wasm-tools`.

```sh
$ cd examples/echo-server
$ wasm-tools component embed wit .2.12/target/scala-2.12/echo-fastopt/main.wasm -o main.wasm --encoding utf16
$ wasm-tools component new main.wasm -o main.wasm
```

Run the server using `wasmtime serve` command. Adding `-C collector=null` to disable garbage collection in wasmtime, since it causes some issues with component model.

```sh
$ wasmtime serve -W function-references,gc -C collector=null main.wasm
Serving HTTP on http://0.0.0.0:8080/
```

```sh
$ curl http://localhost:8080 -d 'hello world!'
hello world!
```