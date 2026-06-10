<div align="center">

# FRISCcc

### A C-Subset Compiler for the FRISC Architecture

**From formal languages to executable code — a complete, runnable compiler in Java.**

[![License: MIT](https://img.shields.io/badge/License-MIT-1B3A6B.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-21%2B-C7253E.svg)](#prerequisites)
[![Book DOI](https://img.shields.io/badge/Book-10.5281%2Fzenodo.20511074-2E8B57.svg)](https://doi.org/10.5281/zenodo.20511074)
[![Release](https://img.shields.io/badge/release-v1.0.0-1B3A6B.svg)](https://github.com/KarloKnezevic/ccompiler/releases)

</div>

FRISCcc compiles a deterministic subset of C all the way down to **FRISC**
assembly (the educational instruction-set architecture used at the University of
Zagreb), runs it on a bundled simulator, and can also execute its typed
intermediate representation on a **tree-walking interpreter** and a **bytecode
virtual machine**. It is a *complete* teaching compiler: nothing is hand-waved,
every phase is real, and every example in this repository compiles and runs.

```
C source → Lexer → Parser → Semantic analysis → Typed IR
         → Optimization (IR→IR passes) → FRISC codegen → Simulation
                                       ↘ IR interpreter
                                       ↘ Bytecode VM
```

---

## 📖 The book

This compiler is the running example dissected, line by line, throughout the book:

> **Building a C-Subset Compiler for the FRISC Architecture: From Formal Languages to Executable Code**
> Dr. Karlo Knežević · Self-published, Zagreb, 2026
> **ISBN** 978-953-47198-0-0 · **DOI** [10.5281/zenodo.20511074](https://doi.org/10.5281/zenodo.20511074)
> 📕 Read it here: [doi.org/10.5281/zenodo.20511074](https://doi.org/10.5281/zenodo.20511074) · 📄 [Local PDF](docs/book/Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf)

The book is a narrative monograph: it builds this exact compiler from first
principles — automata and formal languages, LR(1) parsing, type systems, a
typed IR, an optimizer with semantic-preservation proofs, a FRISC back end, and
two alternative execution engines. The in-repo documentation under
[`docs/`](docs/) mirrors the book chapter by chapter; the book is the
authoritative, full-length treatment. See [Citing this work](#citing-this-work).

---

## Quickstart (under a minute)

You don't even need to build — a ready-to-run JAR ships in [`dist/`](dist/).

```bash
git clone https://github.com/KarloKnezevic/ccompiler.git
cd ccompiler

# Compile a C program to FRISC and run it on the simulator:
java -jar dist/ccompiler.jar --all --run examples/real_world/math_fibonacci_iter/program.c
# → Program output: 6765
```

> Run commands **from the repository root** so the compiler can find the bundled
> FRISC simulator (`node_modules/friscjs`) and the `examples/` tree.

The convenience wrapper [`run.sh`](run.sh) does the same and prints a tidy
per-phase report:

```bash
./run.sh --all --run examples/real_world/math_fibonacci_iter/program.c
```

---

## Prerequisites

| Tool | Version | Used for |
|------|---------|----------|
| **JDK** | 21 or newer | building and running the compiler |
| **Maven** | 3.9 or newer | building from source (not needed to run `dist/ccompiler.jar`) |
| **Node.js** | 18 or newer | the bundled FRISC simulator (only for `--run`) |

Check your toolchain:

```bash
java -version    # 21+
mvn -version     # 3.9+
node --version   # 18+
```

The FRISC simulator (`friscjs`) is vendored under `node_modules/`, so no `npm
install` is required. It is also an independent package — you can install it on
its own with `npm install friscjs` and use its assembler/simulator API directly;
see [Using friscjs directly](docs/reference/simulator.md#using-friscjs-directly-standalone).

---

## Build from source

```bash
./build.sh           # build all modules, produce cli/target/ccompiler.jar (tests skipped)
./build.sh -t        # build and run the full test suite
./build.sh -c        # clean build
```

Equivalently, with Maven directly:

```bash
mvn clean package            # builds the fat JAR at cli/target/ccompiler.jar
mvn test                     # runs the test suite (89 tests across the modules)
```

The build produces a single self-contained executable JAR via the Maven Shade
plugin. After building, copy it over the shipped one if you like:
`cp cli/target/ccompiler.jar dist/ccompiler.jar`.

---

## Using the compiler

Invoke either through `./run.sh <flags> <file>` or `java -jar dist/ccompiler.jar
<flags> <file>`. Later-stage flags automatically imply the earlier phases.

### Compilation stages

| Flag | Stops after | Output (in `compiler-bin/`) |
|------|-------------|------------------------------|
| `--lex` | lexical analysis | `tokens.txt` |
| `--parse` | parsing | `ast.txt` |
| `--sem` | semantic analysis | typed tree |
| `--ir` | IR generation | `intermediate.ir` |
| `--frisc` | code generation | `a.out` (FRISC assembly) |
| `--all` | full pipeline | all of the above |
| `--run` | + simulation | program output on stdout |

### Optimization

| Flag | Meaning |
|------|---------|
| `--O0` | no optimization — IR passes straight to the back end (default) |
| `--O1` | baseline optimizing pipeline (constant folding, copy/value propagation, dead-code elimination, CSE, LICM, strength reduction, …) |
| `--dump-ir` | also dump pre- and post-optimization IR into `compiler-bin/ir-dumps` |

### Running the IR directly

Skip code generation entirely and execute the typed IR:

```bash
# Tree-walking interpreter:
java -jar dist/ccompiler.jar run-ir examples/real_world/math_fibonacci_iter/program.ir
#   → Return value: 6765   (Executed steps: 478)

# Bytecode virtual machine (lowers IR → bytecode, runs on a stack VM):
java -jar dist/ccompiler.jar run-vm examples/real_world/math_fibonacci_iter/program.ir
#   → Return value: 6765   (Dispatched instructions: 1264)

# Inspect the lowered bytecode:
java -jar dist/ccompiler.jar run-vm --dump-bytecode examples/real_world/math_fibonacci_iter/program.ir
```

All three back ends — native FRISC, interpreter, and VM — agree on every result;
this equivalence is enforced by the test suite across all example IR files.

Run `./run.sh --help` for the complete flag reference (tracing, step/dispatch
watchdogs, custom output directories, and batch IR execution).

---

## What the compiler accepts

A deliberately constrained, deterministic subset of C:

- scalar types `int`, `char`, `float`; arrays; selected `struct` usage;
- control flow: `if` / `else`, `while`, `for`, `return`;
- functions, globals, locals, and recursion;
- self-contained programs with no standard-library dependence.

Floating-point and 32-bit integer arithmetic that the FRISC ISA lacks in
hardware is lowered to software helper routines (`F_MUL`, `F_DIV`, `F_MOD`,
`F_FMUL`, `F_FDIV`, `F_I2F`, `F_F2I`).

---

## Architecture

FRISCcc is a Maven multi-module project. Each phase is its own module with a
clean boundary:

| Module | Responsibility |
|--------|----------------|
| `compiler-common` | shared diagnostics, source locations |
| `compiler-lexer` | tokenization via ε-NFA → DFA with maximal munch |
| `compiler-parser` | LR(1) table construction and parsing |
| `compiler-semantics` | symbol tables, typing, semantic legality |
| `compiler-ir` | lowering the typed tree to a typed three-address IR |
| `compiler-opt` | the IR-to-IR optimization passes |
| `compiler-codegen-frisc` | typed IR → FRISC assembly |
| `cli` | orchestration, IR interpreter, bytecode VM, simulator driver |

The **typed IR** is the backbone: every value and operation carries a type,
control flow is explicit through basic blocks (`br`, `jmp`, `ret`), and frame
and slot metadata make the back-end ABI stable. Its canonical grammar lives in
[`config/ir_definition.txt`](config/ir_definition.txt).

### FRISC ABI at a glance

`R0` result · `R1`–`R4` scratch/arguments · `R5` frame pointer · `R6` return
value · `R7` stack pointer (grows down from `40000`). Entry sequence:

```asm
MOVE 40000, R7
CALL F_MAIN
HALT
```

---

## Examples

523 ready-to-compile programs live under [`examples/`](examples/):

| Category | Count | What it holds |
|----------|------:|---------------|
| `valid/` | 218 | small, focused programs grouped by feature |
| `real_world/` | 31 | larger end-to-end programs with `expected.txt` golden output |
| `fer/` | 186 | course-style test programs |
| `invalid/` | 88 | programs that must be *rejected* (diagnostics tests) |

Every `real_world` example carries its expected output, its generated IR, and
its FRISC assembly, so you can diff against a known-good result.

---

## Documentation

- [`docs/`](docs/) — the in-repo technical documentation, organized to mirror the
  book chapter by chapter (lexer, parser, semantics, IR, optimization, codegen,
  runtime, simulator, performance).
- [`docs/book/`](docs/book/) — the full book PDF.
- The book itself, [doi.org/10.5281/zenodo.20511074](https://doi.org/10.5281/zenodo.20511074),
  is the complete, authoritative narrative.

---

## Citing this work

If FRISCcc or its book helped your work, please cite the book (a
[`CITATION.cff`](CITATION.cff) is included for automatic citation):

> Knežević, K. (2026). *Building a C-Subset Compiler for the FRISC Architecture:
> From Formal Languages to Executable Code.* Zenodo.
> https://doi.org/10.5281/zenodo.20511074 — ISBN 978-953-47198-0-0.

The work is archived on Zenodo under DOI `10.5281/zenodo.20511074`
(ISBN 978-953-47198-0-0).

---

## License

The FRISCcc compiler source and its in-repo documentation are released under the
[MIT License](LICENSE), © 2026 Karlo Knežević. The accompanying **book** is a
separate work under CC BY-NC-ND 4.0. The bundled FRISC simulator
(`node_modules/friscjs`) is distributed under its own license.

---

<div align="center">
<sub>Built by Dr. Karlo Knežević · Zagreb, 2026</sub>
</div>
