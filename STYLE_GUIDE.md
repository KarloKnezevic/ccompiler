# Compiler Style Guide

This document defines the coding standards, naming conventions, and architectural patterns for the PPJ Compiler project.

## 1. Naming Conventions

### 1.1. Tokens and AST
- **Token**: Use `Token` for the lexical unit.
- **TokenKind**: Use `TokenKind` (enum) instead of `TokenType` string constants where possible.
- **Nodes**:
  - All AST nodes must implement `ASTNode`.
  - Concrete nodes should be named `[Concept]Node` or just `[Concept]` (e.g., `BinaryExpression`, `IfStatement`).
  - Do not use `GenerativeTree` terminology in new code; prefer `AST`.

### 1.2. Types and Symbols
- **Type**: Use `Type` interface. Implementations: `IntType`, `VoidType`, `ArrayType`, `FunctionType`.
- **Symbol**: Use `Symbol` record/class.
- **Scope**: Use `Scope` interface/class.

## 2. Diagnostics (Error Reporting)

All modules must use the shared diagnostics framework in `hr.fer.ppj.common.diagnostic`.

### 2.1. The Diagnostic Model
Errors are not strings; they are structured objects:
- **Severity**: `ERROR`, `WARNING`, `INFO`.
- **Stage**: `LEXER`, `PARSER`, `SEMANTICS`, `IR`.
- **SourceLocation**: `line`, `column`.
- **Message**: Human-readable description.

### 2.2. Error Flows
- **Lexer/Parser**: May throw `CompilationException` (wrapping a Diagnostic) for fatal syntax errors, or log to `DiagnosticReporter` for recovery.
- **Semantics**: Must use `DiagnosticReporter` to collect errors.
  - If a fatal error occurs (blocking further analysis), throw `CompilationException`.
- **CLI**: Catches `CompilationException` or checks `DiagnosticReporter`, formats errors, and exits with non-zero status.
- **No `System.exit()`** in library modules.
- **No `System.out/err.println`** for errors in library modules.

## 3. Common Utilities
- Use `hr.fer.ppj.common.source.SourceLocation` for all line/column data.
- Use `hr.fer.ppj.common.util.Preconditions` for argument validation.

## 4. Coding Style
- **Immutability**: Prefer `record` for data carriers.
- **Visibility**: Minimize `public` visibility. Use `package-private` where possible.
- **Javadoc**: Required for all public APIs.
