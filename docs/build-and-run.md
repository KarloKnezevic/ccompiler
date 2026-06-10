# Build & run

How to build FRISCcc from source and run it. For the complete flag reference,
see [CLI](reference/cli.md); for the quickstart, see the repository
[`README.md`](../README.md).

## Prerequisites

| Tool | Version | Used for |
|------|---------|----------|
| JDK | 21 or newer | building and running the compiler |
| Maven | 3.9 or newer | building from source |
| Node.js | 18 or newer | the bundled FRISC simulator (only for `--run`) |

The FRISC simulator (`friscjs`) is vendored under `node_modules/`, so no
`npm install` is required. Always invoke the compiler **from the repository
root** so it can locate `node_modules/friscjs` and the `examples/` tree.
(`friscjs` is also an independent package, installable on its own with
`npm install friscjs` — see [Using friscjs directly](reference/simulator.md#using-friscjs-directly-standalone).)

## Build

```bash
./build.sh           # build all modules → cli/target/ccompiler.jar (tests skipped)
./build.sh -t        # build and run the full test suite
./build.sh -c        # clean build
```

Equivalently, with Maven directly:

```bash
mvn clean package    # build the fat JAR at cli/target/ccompiler.jar
mvn test             # run the test suite
```

The build uses the Maven Shade plugin to produce a single self-contained
executable JAR (`cli/target/ccompiler.jar`, main class
`hr.fer.ppj.cli.CCompilerMain`). A prebuilt copy also ships in
[`dist/ccompiler.jar`](../dist), so running the compiler does not require a
build at all.

## Run

Two equivalent front doors:

```bash
# via the wrapper script (prints a per-phase report):
./run.sh --all --run examples/real_world/math_fibonacci_iter/program.c

# via the JAR directly:
java -jar dist/ccompiler.jar --all --run examples/real_world/math_fibonacci_iter/program.c
# → Program output: 6765
```

### Common invocations

```bash
./run.sh --lex   program.c          # tokenize only
./run.sh --parse program.c          # + parse (AST)
./run.sh --sem   program.c          # + semantic analysis
./run.sh --ir    program.c          # + IR generation
./run.sh --frisc program.c          # + FRISC code generation
./run.sh --all --run program.c      # full pipeline + simulation
./run.sh --O1 --all --run program.c # with optimization
./run.sh --dump-ir program.c        # dump pre/post-optimization IR

# Execute the IR directly, skipping code generation:
./run.sh run-ir program.ir          # tree-walking interpreter
./run.sh run-vm program.ir          # bytecode VM
./run.sh run-vm --dump-bytecode program.ir
```

Later-stage flags imply the earlier phases (`--frisc` runs lexing through code
generation). The default optimization level is `--O0`; `--O1` enables the
[optimization pipeline](pipeline/optimization.md). See the
[CLI reference](reference/cli.md) for every flag and option.

## Output artifacts

A run writes its artifacts under `compiler-bin/` (overwritten each run, one
program at a time):

| File | Produced by |
|------|-------------|
| `compiler-bin/tokens.txt` | lexer |
| `compiler-bin/ast.txt` | parser |
| `compiler-bin/intermediate.ir` | IR generation (and optimization) |
| `compiler-bin/a.out` | FRISC code generation |
| `compiler-bin/ir-dumps/` | `--dump-ir` |

## Tests

```bash
mvn test                 # whole suite
mvn test -pl cli         # one module
```

The suite includes a differential check that runs every example `.ir` file on
both the interpreter and the bytecode VM and asserts they agree (see
[interpreter & VM](pipeline/interpreter-vm.md)).

## Examples

523 programs live under [`examples/`](../examples): `valid/` (feature-focused),
`real_world/` (larger programs with `expected.txt` golden output), `fer/`
(course-style), and `invalid/` (must be rejected). The `real_world` programs
ship their generated IR and FRISC assembly alongside the source, which makes
them convenient end-to-end references.
