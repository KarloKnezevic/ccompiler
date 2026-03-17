# FRISCcc Compiler Book -- Documentation Map

This directory contains the canonical Markdown source for the FRISCcc technical
monograph. Each subdirectory corresponds to a chapter or appendix. Files within
each directory are ordered numerically and processed by `generate_book.py` to
produce the final LaTeX/PDF book.

## Chapter Structure

| # | Directory | Title | Files |
|---|-----------|-------|-------|
| 0 | `00_frontmatter/` | Title, Copyright, Dedication, Preface, Acknowledgments, How to Read | 6 |
| 1 | `01_introduction/` | Introduction and System Overview | 1 |
| 2 | `02_compiler_theory/` | System Architecture and Module Topology | 1 |
| 3 | `03_lexer/` | Lexical Analysis | 1 |
| 4 | `04_parser/` | Syntax Analysis | 1 |
| 5 | `05_semantic_analysis/` | Semantic Analysis | 1 |
| 6 | `06_ir/` | Intermediate Representation | 2 |
| 7 | `07_optimizations/` | Optimization Theory and Practice | 1 |
| 8 | `08_codegen_frisc/` | FRISC Code Generation | 1 |
| 9 | `09_runtime/` | Runtime Model and Helper Algorithms | 1 |
| 10 | `10_simulator/` | Simulator Architecture and Integration | 1 |
| 11 | `11_real_world_programs/` | Example Suites and Validation Workflows | 1 |
| 12 | `12_performance/` | Performance Analysis | 1 |
| 13 | `13_future_work/` | Future Work and Research Directions | 1 |
| A | `appendix/` | Glossary, Notation, Build Workflow, Config References, FRISC/Simulator References | 9 |

## Appendix Files

| File | Title |
|------|-------|
| `01_glossary.md` | Appendix A. Glossary |
| `02_notation_and_conventions.md` | Appendix B. Notation and Conventions |
| `03_book_generation_workflow.md` | Appendix C. Book Generation Workflow |
| `04_config_lexer_definition.md` | Appendix D. Lexer Definition Reference |
| `05_config_parser_definition.md` | Appendix E. Parser Definition Reference |
| `06_config_semantics_definition.md` | Appendix F. Semantics Definition Reference |
| `07_config_ir_definition.md` | Appendix G. IR Definition Reference |
| `08_frisc_theory_reference.md` | Appendix H. FRISC Architecture Reference |
| `09_simulator_theory_reference.md` | Appendix I. FRISC Simulator Reference |

## Authoring Rules

- Keep chapter-local files ordered numerically.
- Put formal definitions before implementation excerpts.
- For any major concept, include: theory, pseudocode, one diagram, and code mapping.
- Keep terminology consistent across chapters (`typed IR`, `slot`, `frame`, `FRISC helper`, `semantic equivalence`).
- All IR and FRISC listings must be reproduced from actual compiler output unless explicitly marked as simplified.
- Croatian nonterminal names from the grammar are always accompanied by English translations.

## Build

Generate the LaTeX/PDF book with:

```bash
python3 generate_book.py          # Full build: examples + LaTeX + PDF
python3 generate_book.py --tex    # Generate .tex only; skip PDF
python3 generate_book.py --quick  # Skip compiler examples; faster iteration
```

Generated artifacts are placed in:

- `book/chapters/` -- per-chapter LaTeX files
- `book/res/` -- rendered diagrams and figures
- `book/main.tex` -- assembled master document
- `book/main.pdf` -- final PDF (if LaTeX toolchain is present)
