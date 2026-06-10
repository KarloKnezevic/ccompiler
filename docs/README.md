# FRISCcc — Technical Documentation

Engineering documentation for the **FRISCcc** compiler: a Maven/Java 21 compiler
that translates a deterministic subset of C into FRISC assembly through a typed
intermediate representation, and also executes that IR on a tree-walking
interpreter and a bytecode virtual machine.

This is reference documentation aimed at engineers reading, building, or
extending the codebase — each page describes a real component and is grounded in
the source under the corresponding module. For the project overview and a
one-minute quickstart, see the repository [`README.md`](../README.md).

## Map

### Getting oriented
- [Overview](overview.md) — what the compiler is and how the phases connect.
- [Architecture](architecture.md) — module topology, dependencies, and data flow.
- [Build & run](build-and-run.md) — prerequisites, building, and running.

### Pipeline (phase by phase)
- [Lexer](pipeline/lexer.md) — ε-NFA → DFA, maximal-munch tokenization.
- [Parser](pipeline/parser.md) — LR(1) table construction and parsing, AST.
- [Semantic analysis](pipeline/semantics.md) — symbol tables, type system, legality.
- [Intermediate representation](pipeline/ir.md) — the typed IR and lowering.
- [Optimization](pipeline/optimization.md) — the IR-to-IR fixpoint pass pipeline.
- [Code generation](pipeline/codegen.md) — typed IR → FRISC assembly.
- [Runtime & ABI](pipeline/runtime-abi.md) — calling convention, frames, helpers.
- [Interpreter & bytecode VM](pipeline/interpreter-vm.md) — the two IR execution back ends.

### Reference
- [IR grammar](reference/ir-grammar.md) — the canonical IR grammar.
- [FRISC ISA](reference/frisc-isa.md) — the instruction subset the compiler targets.
- [Simulator](reference/simulator.md) — how generated assembly is executed.
- [CLI](reference/cli.md) — complete command-line reference.
- [Glossary](reference/glossary.md) — terms used across the docs.
- [Notation & conventions](reference/notation.md) — symbols and conventions.

## The accompanying book

These docs are a practical companion to the full-length book, which develops the
same compiler from first principles with complete formal treatment, proofs, and
figures:

> **Building a C-Subset Compiler for the FRISC Architecture: From Formal Languages to Executable Code**
> Dr. Karlo Knežević · Zenodo, 2026 · ISBN 978-953-47198-0-0
> DOI [10.5281/zenodo.20511074](https://doi.org/10.5281/zenodo.20511074) · 📄 [Local PDF](book/Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf)

The documentation here is deliberately technical and self-contained; reach for
the book when you want the underlying theory and the long-form narrative.
