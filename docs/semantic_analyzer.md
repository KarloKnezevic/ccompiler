# Semantic Analyzer Overview

> **Modules covered:** `compiler-semantics/`, `cli/`  
> **Author:** [Karlo Knežević](https://karloknezevic.github.io/)  
> **Last update:** 2025‑11‑25

This document gives a high-level narrative of the semantic analyzer: where it lives in the pipeline, which language rules it enforces, how it surfaces diagnostics, and how developers interact with it. For a line-by-line walkthrough of the implementation, data structures, and algorithms, see [`docs/semantic_analyzer_implementation.md`](semantic_analyzer_implementation.md).

---

## 1. Role in the Compilation Pipeline

```
    lexer ──► parser ──► semantic analyzer ──► (planned) code generator
```

* **Input:** a fully built `ParseTree` produced by the LR(1) parser (grammar in `config/parser_definition.txt`).
* **Output:** either (a) silence if the program is semantically correct, or (b) one canonical diagnostic describing the first semantic error.
* **Guarantees to later phases:** all identifiers are declared and scoped properly, expressions have concrete PPJ types, control-flow constructs obey the language rules, and a conforming `int main(void)` definition exists.

Invocation happens exclusively through the CLI (`hr.fer.ppj.cli.Main#runSemantic`). The CLI already performed lexical and syntactic analysis and then calls `SemanticAnalyzer.analyze(parseTree, System.out)` to validate semantics in-memory.

---

## 2. Grammar Surface & Responsibilities

*The specification of record is `config/semantics_definition.txt`.* Every production in that file has a dedicated handler, so the analyzer never invents ad-hoc checks. The responsibilities split into three categories:

- **Declarations & Definitions** (`<deklaracija>`, `<definicija_funkcije>`, `<izravni_deklarator>`, `<deklaracija_parametra>`):  
  - constants require initializers,  
  - arrays validate bounds (including parameter forms such as `int arr[]`),  
  - nested scopes own their identifiers,  
  - prototypes and definitions must agree exactly,  
  - `int main(void)` must be present once.
- **Expressions** (all nodes between `<primarni_izraz>` and `<izraz>`):  
  - l-value / r-value propagation,  
  - implicit conversions for arithmetic / logical expressions,  
  - postfix rules for indexing & calls (fixed bug: empty loop bodies and `arr[i]` in both local and parameter contexts are now legal per PPJ spec),  
  - assignment compatibility.
- **Statements & Control Flow** (`<slozena_naredba>`, `<naredba_petlje>`, `<naredba_skoka>`...):  
  - block scopes are RAII-managed,  
  - `continue`/`break` require an active loop,  
  - `return;` inside `void` functions is explicitly allowed (and any expression in a `void` function is rejected),  
  - verification of loop bodies covers empty `{ }` blocks per spec.

Attribute data (type, identifier, l-value flag, parameter lists, initializer metadata) lives inside `SemanticAttributes` attached to each `NonTerminalNode`. Scopes are modeled via a tree of `SymbolTable` instances that exactly mirrors lexical nesting.

---

## 3. Architecture & Control Flow

```
ParseTree ─► ParseTreeConverter ─► NonTerminalNode / TerminalNode (+ SemanticAttributes)
                                               │
                                               ▼
                                       SemanticAnalyzer
                                               │
                                          SemanticChecker
                                     ┌─────────┼─────────┐
                                     │                   │
                             DeclarationRules     ExpressionRules
                                     │                   │
                              StatementRules (blocks, jumps)
```

- **`SemanticAnalyzer`** is the CLI-facing façade. It converts the parser tree, seeds a global `SymbolTable`, and delegates to `SemanticChecker`.
- **`SemanticChecker`** owns the mutable state (scope chain, loop depth, current function) and a dispatch table. Instead of one monolithic visitor, the production handlers live inside three focused helpers:
  - `DeclarationRules` – all productions that introduce or initialize symbols.
  - `ExpressionRules` – typing logic for `<primarni_izraz>` up to `<izraz>`.
  - `StatementRules` – scopes, loops, and `return` semantics.
- **Error reporting** is centralized in `SemanticChecker.fail(...)` which uses `ProductionFormatter` to print the offending production, a blank line, and the canonical `semantic error` message before throwing `SemanticException`.

This split keeps each file under ~550 LOC and makes future updates (new grammar rule, optional diagnostics, configuration hooks) surgical.

---

## 4. Error Reporting & CLI Behavior

* The analyzer reports **only the first** semantic violation.
* Output format (stdout):

  ```
  <primarni_izraz> ::= IDN(8,y)

  semantic error
  ```

* The CLI prints `Error: semantic error` to `stderr` and exits with status code `1`.
* No recovery or warning infrastructure is implemented yet—determinism is more important for automated grading.

---

## 5. Configurability Snapshot

There is currently **no semantic configuration surface**:

* No `SemanticConfig` class, CLI flag, environment variable, or YAML/JSON file controls the analyzer.
* All checks are always enabled and use PPJ’s strict semantics (stop after the first error).
* Adding configurability would require introducing a configuration object, threading it through `SemanticAnalyzer` / `SemanticChecker`, and guarding rule blocks manually.

---

## 4. Configurability Snapshot

The analyzer is intentionally rigid today:

- No `SemanticConfig` class, CLI flag, or environment variable toggles checks.
- Every rule in `semantics_definition.txt` is always enforced in “fail-fast” mode.
- Extending it would require defining an options object, passing it through `SemanticAnalyzer`/`SemanticChecker`, and guarding rule blocks manually. The current architecture (modular rule groups) already provides natural seams for such toggles.

## 5. Usage Examples

### CLI Invocation

```bash
# Validate a single translation unit
./run.sh semantic compiler-semantics/src/test/resources/ppjc_case_14/program.c

# Direct invocation (if you packaged the CLI module)
java -jar cli/target/ccompiler.jar semantic examples/invalid/program33.c
```

Failure mode (undeclared identifier):

```
<primarni_izraz> ::= IDN(8,y)

semantic error
Error: semantic error
```

Success mode (empty loop body, array parameter, and `void` return are all valid):

```
./run.sh semantic compiler-semantics/src/test/resources/ppjc_case_15/program.c
# (no stdout)
```

### Batch HTML Reports

`hr.fer.ppj.examples.ExamplesReportGenerator` runs lexer → parser → semantic analyzer for every file in `examples/valid` and `examples/invalid`, then produces `examples/report_valid.html` / `examples/report_invalid.html`. Each section embeds:

- source snippet,
- lexical tokens,
- generative & syntax trees,
- semantic verdict (either “No semantic errors found.” or the exact production that failed).

This utility is the quickest way to eyeball regressions across the entire corpus.

---

## 6. Implementation Notes & Testing

- Only the first semantic violation is reported. This guarantees deterministic results that match PPJ grading scripts.
- Tree conversion happens up-front so rule handlers can mutate attributes freely without worrying about parser immutability.
- Golden tests live under `compiler-semantics/src/test/resources/ppjc_case_*`. Recent additions (`case_12`–`case_15`) cover the previously failing situations:
  - empty `for`/`while` bodies (`{ }`),
  - function parameters (`int arr[]`, `int n`),
  - array indexing for both locals and parameters,
  - `void` functions that legally use `return;`.

## 7. Where to Go Next

- Need algorithmic details, attribute layouts, or extension tips? → **[`docs/semantic_analyzer_implementation.md`](semantic_analyzer_implementation.md)** (deep dive).
- Need to trace the CLI orchestration? → `cli/src/main/java/hr/fer/ppj/cli/Main.java`, methods `compileToParseTree` and `runSemantic`.
- Need to inspect grammar-driven behavior? → `config/semantics_definition.txt` (semantic intent) and `config/parser_definition.txt` (LR grammar).

Use this overview to onboard contributors quickly; switch to the implementation document for low-level mechanics.


