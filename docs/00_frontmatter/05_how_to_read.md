# How To Read This Book

This monograph supports three reading modes, each optimized for a different learning objective.

## Mode A: Curriculum Path (First-Time Compiler Study)

Read chapters in numeric order. The conceptual dependencies are intentionally layered:

1. **Chapters 1--2:** Introduction, system architecture, and module topology.
2. **Chapter 3:** Lexical analysis --- regular expressions, DFA construction, tokenization.
3. **Chapter 4:** Syntax analysis --- LR(1) parsing, grammar specification, parse tree construction.
4. **Chapter 5:** Semantic analysis --- type system, symbol tables, rule-based checking.
5. **Chapter 6:** Typed intermediate representation --- IR grammar, control-flow graphs, frame model.
6. **Chapter 7:** Optimization --- dataflow analysis, pass pipeline, algebraic rewrites.
7. **Chapter 8:** FRISC code generation --- instruction selection, calling convention, helper routines.
8. **Chapters 9--10:** Runtime model and simulator configuration.
9. **Chapters 11--12:** Example programs, validation workflows, and performance analysis.
10. **Chapter 13:** Future work and research directions.

This path is best for readers who want a coherent, theorem-to-code progression.

## Mode B: Systems Debugging Path (Implementation and Tooling Focus)

Start with runtime and simulator chapters, then walk backward:

1. Simulator behavior and timeout diagnostics (Chapters 10, 12).
2. Backend lowering and helper routines (Chapters 8, 9).
3. Optimization legality and pass effects (Chapter 7).
4. IR invariants and control-flow structure (Chapter 6).
5. Semantic contracts and type system (Chapter 5).
6. Parser and lexer guarantees (Chapters 3, 4).

This path is practical when investigating non-termination, wrong outputs, or performance regressions.

## Mode C: Module-Owner Path (Contributors)

If you own one module, read:

1. The chapter corresponding to your module.
2. The chapters immediately before and after your module in the pipeline.
3. Performance and simulator chapters (Chapters 10, 12).
4. Appendix with configuration definitions.

This path minimizes local optimizations that violate upstream or downstream contracts.

## Reading Conventions

- **Theory and implementation are paired.** Formal statements reference concrete classes and methods.
- **Pseudocode is executable in spirit.** Each algorithm section maps to concrete code patterns in the repository.
- **Mermaid diagrams are normative** where they encode control-flow, memory layout, or module topology.
- **IR and FRISC listings are reproduced from actual compiler output** unless explicitly marked as simplified.
- **When two representations differ** (C source, IR, FRISC), semantic equivalence is always the primary correctness criterion.

## Notation Summary

| Notation | Meaning |
|---|---|
| `tN` | IR temporary variable (e.g., `t0`, `t7`) |
| `#42:int32` | Typed IR constant |
| `FP+k` / `FP-k` | Frame-pointer-relative byte offset |
| `R0`--`R7` | FRISC general-purpose registers |
| `F_MUL`, `F_DIV`, ... | Runtime helper routine labels |
| `L0`, `L1`, ... | Basic block labels in IR or FRISC |

## Reproducibility Workflow

Each major chapter can be validated by running project scripts and inspecting generated artifacts in `compiler-bin/`. The recommended baseline is to run both the IR interpreter and FRISC execution for representative programs and compare outcomes. Discrepancies between the two execution paths localize bugs to either the code generation or runtime layer.
