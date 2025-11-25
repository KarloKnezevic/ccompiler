# Semantic Analyzer Architecture and Implementation

> **Author:** [Karlo Knežević](https://karloknezevic.github.io/)
>
> **Target module:** `compiler-semantics`

This document serves as the canonical reference for the semantic-analysis phase of the PPJ compiler. It is written for engineers who want to understand, extend, or reimplement the semantic analyzer. The goal is to describe _what_ the analyzer validates, _how_ it derives semantic attributes, and _why_ the present architecture was chosen.  
If you only need the high-level picture, start with [`docs/semantic_analyzer.md`](semantic_analyzer.md); this file intentionally dives into implementation minutiae.

---

## 1. Purpose of the Semantic Phase

The semantic analyzer consumes the generative parse tree produced by the LR parser and guarantees that the program respects the PPJ-C language rules that cannot be captured by context-free grammars. Typical responsibilities include:

* enforcing typing rules (type compatibility, implicit conversions, const propagation)
* tracking declaration/definition consistency, including function signatures
* ensuring proper control-flow constructs (`break`, `continue`, `return`)
* validating aggregate constraints (array bounds, `main` signature, reachability of declarations)
* producing the first semantic error exactly once, in canonical format, then terminating

The analyzer operates purely in-memory. Each compilation unit is processed top-down, attaches synthesized attributes to non-terminals, and updates a hierarchical symbol table that mirrors lexical scopes.

---

## 2. Input Contract: `semantics_definition.txt`

The file `config/semantics_definition.txt` enumerates every grammar production that requires semantic handling. Each line follows the canonical `<non_terminal> ::= RHS` format, where the RHS consists of terminals and/or non-terminals separated by whitespace or newlines. The analyzer uses this file as an authoritative reference when mapping generative-tree patterns to semantic handlers.

Key properties:

1. **Deterministic ordering** – productions appear in parser order. The semantic checker mirrors this order in its dispatch table (`visitNonTerminal`) for predictable behavior.
2. **Terminal annotations** – uppercase symbols correspond to lexer tokens (e.g., `IDN`, `KR_INT`), and give hints about attribute sources (line numbers, lexemes).
3. **Implicit attributes** – while the file is purely syntactic, the analyzer associates each production with:
   * input attributes (e.g., expected types for child nodes)
   * synthesized attributes (e.g., resulting `Type`, `isLValue`, function metadata)
4. **Extensibility** – to add a new rule, extend this definition file, then implement a matching handler inside `SemanticChecker`. Because we do not interpret the file at runtime (no DSL), the file acts as documentation + a reference for unit tests.

---

## 3. High-Level Architecture

```
┌───────────────────────────────┐
│ ParseTree (parser module)     │
└──────────────┬────────────────┘
               │
         ParseTreeConverter
               │
┌──────────────▼────────────────┐       ┌──────────────────────┐
│ NonTerminalNode / TerminalNode│◄──────┤ SemanticAttributes   │
└──────────────┬────────────────┘       └──────────────────────┘
               │
          SemanticAnalyzer
               │
          SemanticChecker
               │
        ┌──────┴─────────┐
        │ SymbolTable    │
        │ Type hierarchy │
        └────────────────┘
```

### 3.1 Parse Tree Conversion

`ParseTreeConverter` bridges the parser's immutable tree with the semantic module's mutable tree (`NonTerminalNode`, `TerminalNode`). The semantic tree stores:

* child links (preserving grammar order)
* optional `SemanticAttributes` object for synthesized data (type, l-value flag, function metadata)
* convenience helpers (`NodeUtils`) for safe casting and node inspection

### 3.2 Symbol Table

The `SymbolTable` implements a classic hierarchical map:

* `declare(Symbol symbol)` – inserts into the current scope (throws on duplicates)
* `lookup(String identifier)` – searches current scope first, then walks parents
* `enterChildScope()` / `exit()` – builds a tree that matches block nesting

Symbols are represented by dedicated classes:

* `VariableSymbol` – includes declared type, `isConst`, and optional array size
* `FunctionSymbol` – retains `FunctionType`, `isDefined` flag, and declaration location

The table enforces invariants (e.g., function declaration vs. definition counts, global `main` contract) during final verification.

### 3.3 Type System

The type hierarchy is implemented via a Java 17 sealed interface (`Type`) with the following implementors:

* `PrimitiveType` – enumerates `VOID`, `CHAR`, `INT`
* `ArrayType` – wraps an element type and optional length
* `FunctionType` – retains return type, parameter types, and parameter const flags
* `ConstType` – decorator for const-qualified types

`TypeSystem` offers helpers for:

* const stripping and application (`stripConst`, `applyConst`)
* implicit-conversion checks (`canAssign`, `canConvert`)
* type equality semantics (primitive widening, array compatibility)

### 3.4 Rule dispatcher (`DeclarationRules`, `ExpressionRules`, `StatementRules`)

`SemanticChecker` registers lightweight rule modules instead of hosting every production inline. The constructor looks like:

```java
// compiler-semantics/src/main/java/hr/fer/ppj/semantics/analysis/SemanticChecker.java
registerRule("$", node -> {});
new DeclarationRules(this);
statementRules = new StatementRules(this);
new ExpressionRules(this);
```

- **`DeclarationRules`** mutates scopes (enter/exit), inserts symbols, and enforces everything around `<deklaracija>`, `<definicija_funkcije>`, `<deklaracija_parametra>`, etc. The new helpers `isSizedArrayDeclarator`, `isUnsizedArrayDeclarator`, and `extractArrayLengthLiteral` make sure that both `int arr[5];` and `int arr[]` follow the spec verbatim.
- **`ExpressionRules`** covers the entire `<primarni_izraz>`…`<izraz>` hierarchy. It owns all typing logic (l-values, implicit conversions, string literal metadata, postfix checking for arrays/functions). No scope mutation happens here.
- **`StatementRules`** is responsible for `<slozena_naredba>`, loops, and jump statements. It encapsulates the fixed behavior for empty `{ }` blocks and `void` functions with `return;`, while sharing scope-tracking utilities with the checker core.

The separation keeps each file below ~550 LOC, mirrors the structure of `semantics_definition.txt`, and makes future toggles (e.g., a warning configuration) straightforward.

---

## 4. SemanticChecker Workflow

`SemanticChecker` is the core visitor that traverses the generative tree. Its workflow can be summarized as:

1. **Pre-pass setup** – instantiate the root scope, prime built-in symbols if needed.
2. **Recursive descent** – dispatch each non-terminal to a dedicated `visitXxx` method. Each method:
   * reads child attributes (types, l-values, literal values)
   * validates rule-specific constraints
   * updates `SemanticAttributes` for the parent node
   * mutates scope/type state (entering/exiting blocks, tracking the current function, loop depth, etc.)
3. **Error handling** – on the first violation, call `fail(node)`:
   * format the exact production using `ProductionFormatter`
   * print the production followed by a blank line and `semantic error`
   * throw `SemanticException` to unwind the analyzer
4. **Post-pass verification** – after traversal, run `verifyGlobalConstraints()` to ensure:
   * a `main` function exists with signature `int main(void)`
   * every declared function is also defined (unless explicitly allowed)

### 4.1 Attribute Propagation

Each `NonTerminalNode` carries a mutable `SemanticAttributes` object containing:

| Attribute          | Meaning                                                  |
|--------------------|----------------------------------------------------------|
| `Type type`        | The computed type of the non-terminal                    |
| `boolean lValue`   | Whether the expression is an assignable l-value          |
| `boolean isConst`  | Propagated constness (useful for arrays/functions)       |
| `String identifier`| Identifier captured at declaration nodes                 |
| `List<Type> parameterTypes` | For function signatures and argument lists       |
| `List<String> parameterNames` | Captures names for scope insertion            |
| `FunctionType functionType` | Resolved function signature metadata            |

Attributes are lazily created to avoid allocating structures for terminals that don't need them. Helper methods inside `SemanticAttributes` use fluent setters to keep method bodies readable.

### 4.2 Control-Flow Tracking

* `loopDepth` – incremented on `while`/`for`, decremented afterwards. Ensures `break`/`continue` exist inside loops.
* `currentFunction` – tracks the active function signature for `return` validation and implicit parameter scope.

### 4.3 Error Reporting Format

The analyzer prints exactly one production using the format:

```
<production> ::= SYMBOL( line , lexeme ) ...

semantic error
```

`ProductionFormatter` renders terminals as `TERMINAL(line,lexeme)` to satisfy PPJ specification. No stack traces are emitted; the CLI catches `SemanticException` and exits immediately.

---

## 5. Algorithms & Checks

Below is a non-exhaustive list of the rule categories handled by `SemanticChecker`. Each item references the underlying algorithmic idea.

### 5.1 Expression Typing

* **Primary expressions** – `IDN`, literals, parenthesized expressions. Identifiers are resolved via `SymbolTable.lookup`, verifying presence and type.
* **Postfix expressions** – arrays and functions:
  * Array indexing ensures the base expression is an array type and the index is `int`.
  * Function calls cross-check argument count/types with the callee's `FunctionType`.
  * Post-increment/decrement require l-value `int`.
* **Unary/cast expressions** – adhere to C-like promotion rules. Casts validate convertibility.
* **Binary expressions** – grouped via `visitBinaryExpression` to minimize duplication. Each operator category (multiplicative, additive, relational, logical) validates operand compatibility and synthesizes resulting types.
* **Assignment** – the LHS must be an l-value, RHS must be assignable per `TypeSystem.canAssign`.

### 5.2 Declarations & Definitions

* Handles constants (`KR_CONST`) by wrapping types with `ConstType`.
* Arrays validate positive length and upper bound (`MAX_ARRAY_LENGTH`). `DeclarationRules` explicitly covers both grammar variants:

  ```java
  if (isUnsizedArrayDeclarator(children)) { ... }        // e.g. int arr[]
  if (isSizedArrayDeclarator(children)) { ... }          // e.g. int arr[5]
  String literal = extractArrayLengthLiteral(children.get(2), node);
  int length = checker.parseArrayLength(literal, node);
  ```

  `extractArrayLengthLiteral` walks through wrapper non-terminals until it reaches the `BROJ` terminal mandated by `semantics_definition.txt`, so even if the parser expands the literal through `<log_ili_izraz>` the semantic rule still enforces “compile-time integer only”.
* Functions are inserted into the global scope upon declaration; redefinitions check type equality and prevent double definitions.
* `init_deklarator` ensures initializers exist for const variables and arrays are initialized with matching element counts.

### 5.3 Statements

* Block scopes create child `SymbolTable` instances through `checker.withNewScope(...)`, ensuring RAII-like semantics (try/finally to restore the parent scope).
* `<slozena_naredba>` explicitly recognises all legal shapes from the grammar: `{ }`, `{ <lista_deklaracija> }`, and `{ <lista_deklaracija> <lista_naredbi> }`. This is what makes empty `for`/`while` bodies (and standalone `{ }` blocks) legal again.
* Conditional statements require expressions that are `~ int` (int-convertible via `TypeSystem.isIntConvertible`).
* Loop constructs increment/decrement `loopDepth`; `continue`/`break` fail immediately when `loopDepth == 0`.
* `StatementRules.handleReturn` mirrors the spec’s split between `return;` (only permitted inside `void` functions) and `return <expr>;` (where `<expr>` must be assignable to the current function’s return type).

### 5.4 Global Constraints

Executed after the full traversal:

1. `main` existence and signature (exact match to `int main(void)`).
2. All declared functions must be defined once; duplicates or missing definitions trigger `fail`.
3. Optional cross-checks can be added here (e.g., unused symbol diagnostics).

---

## 6. Building and Extending the Analyzer

### 6.1 Adding a New Semantic Rule

1. Extend `config/semantics_definition.txt` to document the grammar production.
2. Implement a corresponding `visitXxx` method (or extend an existing one).
3. Update `SemanticChecker.visitNonTerminal` dispatch table.
4. Add unit tests under `compiler-semantics/src/test/java`.
5. Update this document if the change alters architecture or introduces new invariants.

### 6.2 Debugging Tips

* Use `GenerativeTreeParser` to reconstruct the semantic tree from `generativno_stablo.txt` during manual debugging.
* The `SemanticAnalyzerTest` class contains golden tests that load sample programs and compare semantic outputs against `semantic.txt`.
* Enable verbose logging around symbol lookups by temporarily instrumenting `SymbolTable`.

---

## 7. Implementation Conventions

* **Java 17 features** – sealed interfaces, pattern matching, and records are used to express intent.
* **Null-safety** – extensive `Objects.requireNonNull` usage to fail fast.
* **Immutability** – `Type` instances are immutable; symbol tables encapsulate mutability within well-defined APIs.
* **Error locality** – the first breach stops the analysis, ensuring deterministic outputs for automated graders.

---

## 8. Future Work

* **Flow-sensitive const and definite-assignment analysis** for more precise diagnostics.
* **Interprocedural checks** (e.g., verifying that recursive definitions meet specific constraints).
* **Warning system** – optional diagnostics for unused variables, unreachable code, etc.
* **Serialization hooks** – emit symbol tables for downstream optimization passes.

---

## 9. Appendix: Quick Reference

| Component | Location | Responsibility |
|-----------|----------|----------------|
| `SemanticAnalyzer` | `compiler-semantics/.../analysis` | API façade used by CLI |
| `SemanticChecker`  | same | Implements all semantic rules |
| `SymbolTable` + symbols | `.../symbols` | Hierarchical scope storage |
| `Type`, `PrimitiveType`, `ArrayType`, `FunctionType`, `ConstType`, `TypeSystem` | `.../types` | Type modeling and conversion helpers |
| `ParseTreeConverter`, `GenerativeTreeParser` | `.../analysis` & `.../io` | Tree reconstruction utilities |
| `SemanticAttributes`, `NonTerminalNode`, `TerminalNode` | `.../tree` | Attribute carriers |

By following this document and the source code, engineers should be able to implement new semantic checks confidently, ensure they adhere to PPJ specifications, and keep the analyzer maintainable.

---

_Document version: 2025-11-25_


