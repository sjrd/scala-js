SCALA_MODULE := .2.12/target/scala-2.12/helloworld-component-model-fastopt/main.wasm
RUST_MODULE := rust-greeter/target/wasm32-wasip1/release/rust_greeter.wasm
OUT := out.wasm

fetch:
	wkg wit fetch

build-rust:
	cd rust-greeter; cargo component build -r

component:
	wac plug --plug $(RUST_MODULE) $(SCALA_MODULE) -o $(OUT)

run: component
	wasmtime -W function-references,gc,exceptions $(OUT)

clean:
	rm -f $(OUT)

.PHONY: fetch build-rust component run clean

