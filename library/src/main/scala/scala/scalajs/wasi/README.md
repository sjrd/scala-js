This is a repository for bindings to https://github.com/WebAssembly/WASI/tree/main/wasip2.

Originally, these code were located under `javalib-internal`. However, it is being moved under `library` because there are cases where we want to use the WASI API directly from outside of `WasmSystem` (really?).

`JavalibIRCleaner` currently allows access to `scala.scalajs.wasi` and `scala.scalajs.component`. However, ideally, we would like to apply `JavalibIRCleaner` to these WASI and component model-related APIs as well, to ensure that there is no access to Scala.