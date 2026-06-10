# Appendix C. The book and how it was produced

The full-length, authoritative treatment of this compiler is the book:

> **Building a C-Subset Compiler for the FRISC Architecture: From Formal
> Languages to Executable Code**
> Dr. Karlo Knežević · Self-published, Zagreb, 2026
> **ISBN** 978-953-47198-0-0
> **DOI** [10.5281/zenodo.20511073](https://doi.org/10.5281/zenodo.20511073) (concept; always the latest version)
> Version 1 DOI: [10.5281/zenodo.20511074](https://doi.org/10.5281/zenodo.20511074)

A copy of the book ships with this repository:
[`docs/book/Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf`](../book/Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf).

## C.1 What the book is

The book is a narrative monograph — written in the accessible, first-person
voice of Robert Nystrom's *Crafting Interpreters*, but preserving full formal
rigour. It builds *this exact compiler* from first principles: automata and
formal languages, LR(1) parsing, type systems, a typed intermediate
representation, an optimizer (with semantic-preservation proofs collected in an
appendix), a FRISC back end, and two alternative execution engines — a
tree-walking IR interpreter and a bytecode virtual machine. Every listing in the
book is reproduced from real FRISCcc output; nothing is hand-waved.

## C.2 How it was produced

The book is authored directly in LaTeX and typeset with **LuaLaTeX** (Latin
Modern for text and mathematics, Inconsolata for code) on a Crown Quarto
(189 × 246 mm) trim. Figures are rendered with TikZ, D2, Graphviz, matplotlib,
and Asymptote; syntax-highlighted listings are produced with Pygments using
custom lexers for the IR and FRISC assembly. The manuscript is maintained in its
own repository, separate from this compiler's source tree, and published on
Zenodo under the DOI above.

## C.3 How this documentation relates to the book

The Markdown documentation under [`docs/`](../) is the in-repo companion: a
chapter-by-chapter map of the compiler that tracks the book's structure (see the
mapping table in [`docs/README.md`](../README.md)). For the complete development
of any topic — including the formal definitions, proofs, and figures — read the
corresponding chapter of the book.
