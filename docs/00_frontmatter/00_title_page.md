# Building a C-Subset Compiler for the FRISC Architecture

## From Formal Languages to Executable Code: A Theory-to-Implementation Monograph

**Author:** Karlo Knezevic

**Repository:** `ccompiler`
**Primary language:** Java 21
**Target ISA:** FRISC (FER Instruction Set Computer)
**Document version:** 2.0

---

### Abstract

This monograph presents a complete, implementation-backed treatment of compiler construction for a constrained but expressive subset of C, targeting the FRISC processor architecture. The compiler pipeline spans seven distinct phases --- lexical analysis, syntax analysis, semantic analysis, typed intermediate representation generation, IR optimization, FRISC code generation, and simulator-based execution --- each realized as an independent module within a Maven multi-module Java project.

Unlike purely theoretical compiler textbooks, this work is anchored in a concrete, executable codebase. Every algorithm, invariant, and design decision described herein has a direct counterpart in source code, configuration files, and generated artifacts. The pedagogical method is intentionally bidirectional: formal models are derived first and then mapped to implementation constraints; conversely, implementation choices are examined and the underlying theory is recovered.

The source language supports integer, character, and floating-point arithmetic; pointers and arrays; user-defined structures with field access; nested control flow including `if`/`else`, `while`, and `for` loops; and function definitions with full calling-convention discipline. The typed intermediate representation is designed as a strict contract between front-end semantics and back-end code generation, with explicit control-flow graphs, typed temporaries, and a formal grammar specified in BNF. An optimization pipeline of seventeen passes operates at the IR level, and the back end emits FRISC assembly with software helper routines for operations absent from the target instruction set, including integer multiplication, division, modulo, and Q16.16 fixed-point floating-point arithmetic.

The text is designed for three audiences simultaneously: advanced students learning compiler engineering, practitioners who need a deterministic and debuggable educational compiler, and instructors who require a complete course reference with end-to-end artifacts. The exposition is rigorous but implementation-oriented. Formal definitions, invariants, algorithms, and complexity analyses are always paired with generated IR, FRISC assembly listings, and simulator behavior.

### Scope Statement

The compiler pipeline covered in this monograph is:

```
Source text --> Lexer --> Parser --> Semantic Analysis --> Typed IR --> IR Optimization --> FRISC Codegen --> Simulator Execution
```

The source language is a controlled C subset with deterministic semantics. The target execution model is FRISCjs, with project-specific runner integration and strict behavior contracts for return values, memory usage, and runtime diagnostics.
