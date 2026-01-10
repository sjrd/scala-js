fetch:
	wkg wit fetch

opt:
	wasm-opt .2.12/target/scala-2.12/helloworld-wasi-fastopt/main.wasm --enable-tail-call --enable-multivalue --enable-gc --enable-reference-types --enable-exception-handling --enable-bulk-memory --enable-nontrapping-float-to-int --closed-world --inline-functions-with-loops --traps-never-happen --fast-math --type-ssa -O3 -O3 --gufa -O3 --type-merging -O3 -Oz -o main.wasm

run:
	wasmtime run -W function-references,gc,exceptions .2.12/target/scala-2.12/helloworld-wasi-fastopt/main.wasm

run-opt: opt
	wasmtime run -W function-references,gc,exceptions main.wasm

clean:
	rm -f main.wasm

.PHONY: fetch opt run run-opt clean
