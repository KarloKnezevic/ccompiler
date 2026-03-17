# Appendix C. Book Generation Workflow

This appendix documents the automated pipeline that transforms the Markdown
documentation in `docs/` into a typeset LaTeX/PDF book. The pipeline is
implemented in `generate_book.py`, a single Python script at the project root.

## C.1 Prerequisites

The following tools must be available on the build system:

| Tool | Purpose | Required |
|---|---|---|
| Python 3 | Script runtime | Yes |
| Pandoc | Markdown-to-LaTeX conversion | Yes |
| pdflatex | LaTeX-to-PDF compilation | Yes (unless `--tex` mode) |
| makeindex | Index generation | Yes (unless `--tex` mode) |
| Java 21 | Running the FRISCcc compiler for example programs | No (skipped with `--quick`) |
| mmdc (Mermaid CLI) | Rendering Mermaid diagrams to SVG/PDF | No (diagrams omitted if absent) |
| Inkscape | Converting SVG diagrams to PDF for pdflatex | No (SVG used directly if absent) |

The compiler JAR is expected at `cli/target/ccompiler.jar`. If absent, example
compilations and benchmarks are skipped with a warning.

## C.2 Build Invocation

Three invocation modes are supported:

```bash
python3 generate_book.py            # Full build: examples + LaTeX + PDF
python3 generate_book.py --tex      # Generate .tex files only; skip PDF
python3 generate_book.py --quick    # Skip compiler examples; use cached results
```

A content-addressed cache in `book/.cache/` stores intermediate results so that
unchanged files are not reprocessed on subsequent builds.

## C.3 Build Pipeline

The script executes six sequential phases:

1. **Clean and initialize.** Remove the `book/` directory (preserving the
   cache), then create `book/chapters/` and `book/res/`.

2. **Generate LaTeX class.** Write `frisc-compiler-book.cls`, a custom document
   class defining page geometry, code listing styles (C, Java, FRISC assembly,
   IR, pseudocode), color palette, Unicode support, and tcolorbox environments
   for definitions, theorems, notes, warnings, and examples.

3. **Process chapters.** Iterate over documentation directories in the defined
   chapter order. For each chapter directory:
   - Collect and sort `.md` files, excluding supplementary files (prefixes
     96--101 and explicitly named exclusions).
   - Extract Mermaid diagram blocks, render them via `mmdc`, and replace the
     blocks with image references.
   - Preprocess Markdown: normalize code fences, add LaTeX labels to headings,
     convert `.md` cross-references to LaTeX label references, and ensure
     correct spacing before lists.
   - Merge all `.md` files into a single chapter Markdown file.
   - Convert the merged Markdown to LaTeX via Pandoc with `--listings` mode.
   - Post-process the generated LaTeX: fix multi-line `lstinline` issues, wrap
     standalone images in figure environments, normalize listing language and
     style options, and convert `\paragraph` to `\subsubsection*`.
   - Append compiler-generated example artifacts (token streams, AST, IR,
     FRISC assembly) for chapters with associated example programs.

4. **Generate appendices and benchmarks.** Run benchmark compilations (O0 vs.
   O1) on predefined programs to produce a comparison table. Generate a
   configuration-reference appendix from the raw configuration files. Generate
   a glossary from glossary Markdown files.

5. **Assemble main.tex.** Generate the master LaTeX file with document class,
   front matter, table of contents, chapter includes, appendix files, index,
   and list of listings.

6. **Compile to PDF.** Run pdflatex three times (for cross-references and
   index) with makeindex, producing `book/main.pdf`.

## C.4 Chapter Ordering

Chapters are processed in the order defined by the `CHAPTER_ORDER` list:

| Directory | Book Chapter |
|---|---|
| `00_frontmatter` | Front matter (no chapter heading) |
| `01_introduction` | Introduction |
| `02_compiler_theory` | Compiler Architecture and Theory |
| `03_lexer` | Lexical Analysis |
| `04_parser` | Syntax Analysis |
| `05_semantic_analysis` | Semantic Analysis |
| `06_ir` | Intermediate Representation |
| `07_optimizations` | Optimization Passes |
| `08_codegen_frisc` | Code Generation for FRISC |
| `09_runtime` | Runtime Support and ABI |
| `10_simulator` | FRISC Simulator Integration |
| `11_real_world_programs` | Real-World Programs and Case Studies |
| `12_performance` | Performance Engineering |
| `13_future_work` | Future Work and Research Directions |
| `appendix` | Appendices |

Within each directory, Markdown files are sorted lexicographically by filename.
Numeric prefixes (e.g., `01_`, `02_`) control section ordering within a chapter.

## C.5 Compiler Example Embedding

For selected chapters, the script compiles example C programs through FRISCcc
and embeds the resulting artifacts:

- **Token stream** (`tokens.txt`) from the lexer.
- **Abstract syntax tree** (`ast.txt`) from the parser.
- **Intermediate representation** (`intermediate.ir`) from the IR generator.
- **FRISC assembly** (`a.out`) from the code generator.

Results are cached by hashing the source file and compiler flags. The `--quick`
flag skips compilation entirely and relies on cached results. Benchmark
compilations record IR line counts and FRISC instruction counts at O0 and O1 to
produce reduction-percentage tables.

## C.6 Mermaid Diagram Rendering

Mermaid code blocks in Markdown sources are processed as follows:

1. The Mermaid source is sanitized (HTML entities converted, `<br>` tags
   removed).
2. If `mmdc` is available, the diagram is rendered to SVG.
3. If Inkscape is available, the SVG is converted to PDF for pdflatex.
4. The original code block is replaced with an image reference.
5. If `mmdc` is unavailable, a placeholder note is inserted.

Rendered diagrams are stored in `book/res/` with names derived from the chapter
directory and a sequential counter.

## C.7 Cross-Reference System

Every heading receives a unique LaTeX label of the form `sec:<chapter>-<slug>`.
Internal Markdown links are converted to LaTeX `\ref` commands using these
labels, producing clickable hyperlinks in the PDF output.

## C.8 Output Structure

```
book/
  main.tex                    Master LaTeX document
  main.pdf                    Final PDF output
  frisc-compiler-book.cls     Custom document class
  chapters/
    00_frontmatter.tex
    01_introduction.tex
    ...
    appendix.tex
    _benchmarks.tex           Auto-generated benchmark table
    _config_appendix.tex      Auto-generated config reference
    _glossary.tex             Auto-generated glossary
  res/
    *.svg, *.pdf              Rendered Mermaid diagrams
  .cache/
    <hash>/result.json        Cached compiler artifacts
```
