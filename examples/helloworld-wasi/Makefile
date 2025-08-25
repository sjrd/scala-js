fetch:
	wkg wit fetch

embed:
	wasm-tools component embed wit .2.12/target/scala-2.12/helloworld-wasi-fastopt/main.wasm -o main.wasm --encoding utf16

opt: embed
	wasm-opt main.wasm --enable-tail-call --enable-multivalue --enable-gc --enable-reference-types --enable-exception-handling --enable-bulk-memory --enable-nontrapping-float-to-int --closed-world --inline-functions-with-loops --traps-never-happen --fast-math --type-ssa -O3 -O3 --gufa -O3 --type-merging -O3 -Oz -o main.wasm

component: opt
	wasm-tools component new main.wasm -o main.wasm

run:
	wasmtime run -W function-references,gc main.wasm
