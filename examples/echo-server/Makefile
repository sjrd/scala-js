run:
	wasmtime serve -W function-references,gc,exceptions .2.12/target/scala-2.12/echo-fastopt/main.wasm

clean:
	rm -f main.wasm main.wasm

.PHONY: run clean
