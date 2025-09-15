SCALA_MODULE := .2.12/target/scala-2.12/helloworld-component-model-fastopt/main.wasm
RUST_MODULE := rust-greeter/target/wasm32-wasip1/release/rust_greeter.wasm
MAIN_MODULE := main.wasm
OUT := out.wasm

fetch:
	wkg wit fetch

build-rust:
	cd rust-greeter; cargo component build -r

embed:
	wasm-tools component embed wit $(SCALA_MODULE) -o $(MAIN_MODULE) -w scala --encoding utf16

new: embed
	wasm-tools component new $(MAIN_MODULE) -o $(MAIN_MODULE)

component: new
	wac plug --plug $(RUST_MODULE) $(MAIN_MODULE) -o $(OUT)

run: component
	wasmtime -W function-references,gc $(OUT)

