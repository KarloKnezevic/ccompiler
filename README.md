# FRISCcc: A C-Subset Compiler for FRISC

FRISCcc is a modular compiler project that translates a deterministic C subset into FRISC assembly through a fully typed intermediate representation. The project is designed for rigorous education, reproducible experiments, and backend engineering work.

## Why This Project

Most educational compilers stop at parsing or semantic checks. FRISCcc delivers the full chain:

- source analysis,
- typed IR generation,
- IR optimization,
- deterministic FRISC backend lowering,
- simulation and runtime diagnostics.

The result is a complete environment for studying how correctness and performance constraints propagate across compiler phases.

## Compiler Pipeline

```mermaid
flowchart LR
  C[Source C] --> L[Lexer]
  L --> P[Parser]
  P --> S[Semantic Analysis]
  S --> IR[IR Generation]
  IR --> OPT[Optimization Passes]
  OPT --> CG[FRISC Codegen]
  CG --> ASM[FRISC Assembly]
```

## Module Architecture

- `compiler-lexer`: lexical analysis and token artifact generation.
- `compiler-parser`: grammar-based syntax analysis.
- `compiler-semantics`: symbol table, typing, semantic legality.
- `compiler-ir`: strict typed IR generation.
- `compiler-opt`: IR-to-IR optimization pipeline.
- `compiler-codegen-frisc`: typed IR to FRISC lowering.
- `cli`: orchestration, IR interpreter, FRISC runner integration.

## Supported C Subset

The supported subset is intentionally constrained and deterministic:

- scalar types: `int`, `char`, `float`
- arrays and selected struct usage
- control flow: `if`, `if/else`, `while`, `for`, `return`
- functions, globals, locals, and recursion where semantically valid
- deterministic, self-contained programs without standard-library dependence

## IR Design Philosophy

The typed IR is the semantic backbone of the project.

- explicit types on every value and operation,
- explicit control flow (`br`, `jmp`, `ret`) through basic blocks,
- explicit frame and slot metadata for backend ABI stability,
- deterministic textual form suitable for golden tests.

Canonical grammar definition: `config/ir_definition.txt`.

## Optimization Pipeline

The optimizer (`compiler-opt`) runs IR-to-IR passes under semantic-preservation rules. Current pass families include:

- constant folding and algebraic simplification,
- control-flow simplification and unreachable block removal,
- copy/value propagation and dead temp elimination,
- memory-level simplifications (dead stores, load forwarding),
- loop-oriented simplifications and strength reduction.

## Code Generation Target: FRISC

Backend lowering follows fixed conventions:

- `R7` = stack pointer
- `R5` = frame pointer
- `R6` = function return register
- `R0` = primary expression result register

Entry sequence:

```asm
MOVE 40000, R7
CALL F_MAIN
HALT
```

## Build

```bash
./build.sh
```

This builds all Maven modules and produces the CLI artifact in `cli/target/`.

## Run

### Scripted run

```bash
./run.sh --all --run examples/valid/basics/0001_basics_main_int/program.c
```

### Direct JAR run

```bash
java -jar cli/target/ccompiler.jar --all --run path/to/program.c
```

## CLI Usage Contract

Representative commands:

- `--lex`
- `--parse`
- `--sem`
- `--ir`
- `--frisc`
- `--run`
- `--all`
- `--dump-ir`

Later-stage flags imply prerequisite stages automatically.

## Optimization Levels

- `--O0`: bypass optimizer (IR passthrough to backend)
- `--O1`: enabled baseline optimization pipeline
- `--O2`: advanced profile target documented in `docs/07_optimizations/` (enablement depends on current implementation state)

## Example Workflow

```bash
# 1) Full compile with optimization
./run.sh --O1 --all examples/real_world/real_bfs_shortest_path/program.c

# 2) Compile and execute generated FRISC
./run.sh --O1 --all --run examples/real_world/real_bfs_shortest_path/program.c

# 3) Validate IR semantics directly
./run.sh run-ir examples/real_world/real_bfs_shortest_path/program.ir
```

## Output Artifacts

Each run produces one coherent artifact set in `compiler-bin/` for exactly one program at a time. Outputs are overwritten on each run to prevent artifact contamination.

## Performance Comparison (Representative)

| Configuration | Expected behavior | Typical use |
|---|---|---|
| `O0` | Maximum transparency, minimal transformation | debugging IR/codegen correctness |
| `O1` | Lower instruction count on most loops | default compile/run path |
| `O2` | Aggressive optimization profile (project roadmap) | benchmark and research experiments |

## Book and Documentation

The project documentation is organized as a technical book in `docs/`.

Generate the book:

```bash
python3 generate_book.py
```

Primary outputs:

- `book/main.tex`
- `book/main.pdf` (if LaTeX toolchain is installed)
- `book/res/` (rendered diagram assets)

## Contribution Guidelines

1. Keep changes phase-local whenever possible.
2. Preserve deterministic outputs and golden-test behavior.
3. Document every non-trivial transformation in the relevant chapter under `docs/`.
4. Add tests for correctness and regressions (IR and FRISC paths when applicable).
5. Validate end-to-end equivalence on representative examples before submitting.

Recommended pre-PR checks:

```bash
mvn test
./run.sh --O1 --all --run examples/real_world/real_prime_sieve/program.c
./run.sh run-ir examples/real_world/real_prime_sieve/program.ir
```
