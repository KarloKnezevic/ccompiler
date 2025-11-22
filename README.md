# PPJ Compiler

A complete C compiler implementation for a subset of the C programming language, including lexical analysis, syntax analysis, semantic analysis, and code generation.

## Quick Start

```bash
# Build the compiler
./build.sh

# Run lexical analysis
./run.sh lexer <file.c>

# Run syntax analysis
./run.sh syntax <file.c>

# Full compilation
./run.sh <file.c>
```

## Overview

This project implements a complete compiler for a subset of the C programming language. The compiler consists of multiple phases:

- **Lexical Analysis**: Tokenizes source code using deterministic finite automata (DFAs)
- **Syntax Analysis**: Parses tokens into an abstract syntax tree (AST) using LR(1) parsing
- **Semantic Analysis**: Performs type checking and semantic validation
- **Code Generation**: Generates FRISC assembly code

The lexer generator reads a specification file and produces deterministic finite automata (DFAs) that are used at runtime to tokenize source code.

## Language Subset

This lexer focuses on a subset of the C programming language with the following features:

### Keywords

The lexer recognizes the following C keywords:

- **Control Flow**: `break`, `else`, `return`, `if`, `while`, `for`, `continue`
- **Types**: `char`, `int`, `void`, `const`, `struct`, `float`

### Special Characters and Operators

#### Brackets and Delimiters
- `[` `]` - Array brackets (`L_UGL_ZAGRADA`, `D_UGL_ZAGRADA`)
- `(` `)` - Parentheses (`L_ZAGRADA`, `D_ZAGRADA`)
- `{` `}` - Braces (`L_VIT_ZAGRADA`, `D_VIT_ZAGRADA`)
- `;` - Semicolon (`TOCKAZAREZ`)
- `,` - Comma (`ZAREZ`)
- `.` - Dot (`TOCKA`)

#### Arithmetic Operators
- `+` - Plus (`PLUS`)
- `-` - Minus (`MINUS`)
- `*` - Asterisk (`ASTERISK`)
- `/` - Division (`OP_DIJELI`)
- `%` - Modulo (`OP_MOD`)

#### Increment/Decrement
- `++` - Increment operator (`OP_INC`)
- `--` - Decrement operator (`OP_DEC`)

#### Comparison Operators
- `<` - Less than (`OP_LT`)
- `>` - Greater than (`OP_GT`)
- `<=` - Less than or equal (`OP_LTE`)
- `>=` - Greater than or equal (`OP_GTE`)
- `==` - Equality (`OP_EQ`)
- `!=` - Inequality (`OP_NEQ`)

#### Logical Operators
- `&&` - Logical AND (`OP_I`)
- `||` - Logical OR (`OP_ILI`)
- `!` - Logical NOT (`OP_NEG`)

#### Bitwise Operators
- `&` - Bitwise AND (`AMPERSAND`)
- `|` - Bitwise OR (`OP_BIN_ILI`)
- `^` - Bitwise XOR (`OP_BIN_XILI`)
- `~` - Bitwise NOT (`OP_TILDA`)

#### Assignment
- `=` - Assignment operator (`OP_PRIDRUZI`)

### Identifiers and Literals

- **Identifiers** (`IDN`): Start with letter or underscore, followed by letters, digits, or underscores
- **Numbers** (`BROJ`): Integer and floating-point literals
- **Character Literals** (`ZNAK`): Single characters enclosed in single quotes
- **String Literals** (`NIZ_ZNAKOVA`): Sequences of characters enclosed in double quotes
- **Comments**: Single-line (`//`) and multi-line (`/* */`) comments (not tokens)

## Project Structure

Maven multi-module project:

- **compiler-lexer**: Lexer generator and runtime (manual regex parsing, ε-NFA → DFA)
- **compiler-parser**: LR(1) parser generator and runtime, AST construction
- **compiler-semantics**: Type system, symbol tables, semantic analysis
- **compiler-codegen**: FRISC assembly code generation
- **cli**: Command-line interface
- **test-fixtures**: Test resources

## Key Constraints

1. **NO REGEX libraries** - All regex parsing is manual, following `regex_pseudokod.txt`
2. **LR(1) parser** - Generated from `ppjLang_sintaksa.txt` grammar file
3. **Java 21** - Uses modern features: records, sealed classes, pattern matching
4. **Code quality** - Error Prone, Checkstyle, SpotBugs, Spotless

## Lexer Definition File

The lexer definition file is located at `config/lexer_definition.txt`. This file contains:

- Macro definitions for reusable patterns
- State declarations
- Token type declarations
- Lexer rules with patterns and actions

All components use `LexerConfig.getLexerDefinitionPath()` to locate this file, ensuring consistency and easy maintenance.

## Build

```bash
mvn clean verify
```

## Building

### Quick Build

The simplest way to build the entire project into a single executable JAR:

```bash
./build.sh
```

This will:
- Clean and compile all modules
- Create a single executable JAR file at `cli/target/ccompiler.jar`
- Include all dependencies (fat JAR with all modules bundled)

### Manual Build

Alternatively, you can use Maven directly:

```bash
mvn clean package -DskipTests -Dspotbugs.skip=true
```

The JAR file will be created at: `cli/target/ccompiler.jar`

**Note:** The build process skips tests and static analysis for faster compilation. To run tests, use:
```bash
mvn clean package
```

## Usage

### Running the Compiler

After building, you can run the compiler in several ways:

#### Option 1: Using the run script (recommended)

```bash
# Lexical analysis only (output to stdout)
./run.sh lexer <file>

# Lexical and syntax analysis (output to compiler-bin/)
./run.sh syntax <file>

# Full compilation
./run.sh semantic <file>
# or simply:
./run.sh <file>
```

#### Option 2: Using Java directly

```bash
# Lexical analysis only
java -jar cli/target/ccompiler.jar lexer <file>

# Lexical and syntax analysis
java -jar cli/target/ccompiler.jar syntax <file>

# Full compilation
java -jar cli/target/ccompiler.jar <file>
```

**Note:** Make sure you have built the project first using `./build.sh` or `mvn clean package`.

#### Option 3: Using Maven (for development)

```bash
# Lexer only
mvn -q -pl cli -am exec:java -Dexec.mainClass=hr.fer.ppj.cli.Main -Dexec.args="lexer <file>"

# Syntax analysis
mvn -q -pl cli -am exec:java -Dexec.mainClass=hr.fer.ppj.cli.Main -Dexec.args="syntax <file>"
```

### Examples

```bash
# Analyze a C source file lexically
./run.sh lexer data/test/program0/program.c

# Perform full syntax analysis
./run.sh syntax data/test/program0/program.c
# Output files will be in compiler-bin/:
#   - leksicke_jedinke.txt
#   - generativno_stablo.txt
#   - sintaksno_stablo.txt

# Full compilation
./run.sh data/test/program0/program.c
```

## Documentation

- **[LEXER_IMPLEMENTATION.md](LEXER_IMPLEMENTATION.md)**: Detailed technical documentation of the lexer implementation
- **[LEXER_USER_GUIDE.md](LEXER_USER_GUIDE.md)**: User guide for writing lexer specifications and using the lexer

## Status

### ✅ Completed
- **Parent POM**: All required plugins (Error Prone, Checkstyle, SpotBugs, Spotless, Enforcer)
- **Lexer Module**: 
  - Manual regex parser (no regex libraries) following `regex_pseudokod.txt`
  - ε-NFA → DFA conversion with subset construction
  - Lexer spec parser (handles macros, states, rules, actions)
  - Action handling: `UDJI_U_STANJE`, `VRATI_SE`, `NOVI_REDAK`
  - Maximal munch with rule priority (earlier rules win)
  - Symbol table with correct output format
  - Recursive macro expansion
  - Comprehensive error recovery
  - Multi-state support (comments, string literals)

### 🚧 In Progress
- Parser: AST node construction from parse tree
- Parser: Error recovery with `%Syn` tokens
- Parser: Generativno stablo output

### 📋 TODO
- Semantics: Type system implementation
- Semantics: Symbol tables with scopes
- Semantics: Semantic analysis visitors
- Codegen: FRISC instruction emitter
- Codegen: Register allocation and function ABI
- Codegen: Short-circuit evaluation
- Tests: Golden output comparison (minusLang, ppjC)
- Tests: Integration tests

## Configuration

The lexer definition file path can be customized via the `LEXER_DEFINITION_PATH` environment variable. If not set, it defaults to `config/lexer_definition.txt` relative to the project root.
