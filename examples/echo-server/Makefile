fetch:
	wkg wit fetch

component:
	wasm-tools component embed wit .2.12/target/scala-2.12/echo-fastopt/main.wasm -o main.wasm --encoding utf16
	wasm-tools component new main.wasm -o main.wasm

run: component
	wasmtime serve -W function-references,gc -C collector=null main.wasm

clean:
	rm -f main.wasm main.wasm

.PHONY: fetch embed new run
