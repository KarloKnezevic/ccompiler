# FRISCcc — Documentation

> **📖 Companion to the book.** This documentation accompanies
> **_Building a C-Subset Compiler for the FRISC Architecture: From Formal
> Languages to Executable Code_** by Dr. Karlo Knežević (Zenodo, 2026) —
> **ISBN** 978-953-47198-0-0 · **DOI**
> [10.5281/zenodo.20511073](https://doi.org/10.5281/zenodo.20511073).
> The book is the complete, authoritative narrative; the full PDF is here:
> [📄 Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf](book/Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf).

This directory holds the in-repo technical documentation for the FRISCcc
compiler, organized to mirror the book. Each subdirectory corresponds to a
chapter or appendix; for the full-length treatment of any topic — with the
formal development, proofs, and figures — read the corresponding chapter of the
book.

## How the documentation maps to the book

| Directory | Topic | Read in the book |
|-----------|-------|------------------|
| `01_introduction/` | Introduction and system overview | Ch. 1 — *The shape of a compiler* |
| `02_compiler_theory/` | System architecture and module topology | Ch. 2 — *A tour of the machine* |
| `03_lexer/` | Lexical analysis (ε-NFA → DFA, maximal munch) | Ch. 3 — *Words* |
| `04_parser/` | Syntax analysis (LR(1)) | Ch. 4 — *Grammar* |
| `05_semantic_analysis/` | Symbol tables, typing, semantic legality | Ch. 5 — *Meaning* |
| `06_ir/` | The typed intermediate representation | Ch. 6 — *A language in the middle* |
| `07_optimizations/` | Optimization theory and practice | Ch. 7 — *Making it smaller* |
| `08_codegen_frisc/` | FRISC code generation | Ch. 8–10 — *Down to the metal* |
| `09_runtime/` | Runtime model and helper algorithms | Ch. 9 — *The runtime* |
| `10_simulator/` | Simulator architecture and integration | Ch. 10 — *Running it* |
| `11_real_world_programs/` | Example suites and validation workflows | Ch. 13 — *Case studies* |
| `12_performance/` | Performance analysis | Ch. 14 — *Performance* |
| `13_future_work/` | Future work and research directions | Ch. 15 — *Where to take it next* |
| `appendix/` | Glossary, notation, config and ISA references | Appendices A–L |

(The book also adds Part IV — an IR tree-walking interpreter (Ch. 11) and a
bytecode virtual machine (Ch. 12) — both implemented in this compiler under
`cli/`.)

## Appendix files

| File | Title |
|------|-------|
| `01_glossary.md` | Glossary |
| `02_notation_and_conventions.md` | Notation and conventions |
| `03_book_generation_workflow.md` | The book and how it was produced |
| `04_config_lexer_definition.md` | Lexer definition reference |
| `05_config_parser_definition.md` | Parser definition reference |
| `06_config_semantics_definition.md` | Semantics definition reference |
| `07_config_ir_definition.md` | IR definition reference |
| `08_frisc_theory_reference.md` | FRISC architecture reference |
| `09_simulator_theory_reference.md` | FRISC simulator reference |

## Conventions used throughout

- Terminology is kept consistent across chapters (`typed IR`, `slot`, `frame`,
  `FRISC helper`, `semantic equivalence`).
- Formal definitions come before implementation excerpts.
- All IR and FRISC listings are reproduced from actual compiler output unless
  explicitly marked as simplified.
- Croatian nonterminal names from the grammar are always paired with English
  translations.
