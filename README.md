# PPJ Compiler

A complete C compiler implementation for a subset of the C programming language, including lexical analysis, syntax analysis, semantic analysis, and code generation. The compiler generates FRISC assembly code from C source files.

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Building the Project](#building-the-project)
- [Running the Compiler](#running-the-compiler)
- [Output Files](#output-files)
- [Configuration](#configuration)
- [Documentation](#documentation)
- [Development](#development)
- [Status](#status)

## Overview

The PPJ Compiler is a multi-phase compiler that translates a subset of the C programming language into FRISC assembly code. The compiler consists of four main phases:

1. **Lexical Analysis**: Tokenizes source code using deterministic finite automata (DFAs)
2. **Syntax Analysis**: Parses tokens into an abstract syntax tree (AST) using canonical LR(1) parsing
3. **Semantic Analysis**: Performs type checking and semantic validation
4. **Code Generation**: Generates FRISC assembly code

The project is implemented in Java 21 using modern language features (records, sealed classes, pattern matching) and follows strict code quality standards.

## Features

### Lexical Analysis
- Manual regex parsing (no regex libraries) following formal specifications
- ε-NFA → DFA conversion with subset construction
- Multi-state support (comments, string literals)
- Maximal munch with rule priority
- Comprehensive error recovery
- Symbol table generation

### Syntax Analysis
- Canonical LR(1) parser generator
- Grammar augmentation and FIRST set computation
- CLOSURE and GOTO algorithms
- ACTION and GOTO table generation (~823 states for PPJ grammar)
- Conflict resolution (SHIFT/REDUCE, REDUCE/REDUCE)
- Generative and syntax tree generation
- Error recovery with synchronization tokens

### Language Subset

The compiler supports a subset of C with:

- **Keywords**: `break`, `char`, `const`, `continue`, `else`, `float`, `for`, `if`, `int`, `return`, `struct`, `void`, `while`
- **Operators**: Arithmetic (`+`, `-`, `*`, `/`, `%`), comparison (`<`, `>`, `<=`, `>=`, `==`, `!=`), logical (`&&`, `||`, `!`), bitwise (`&`, `|`, `^`, `~`), increment/decrement (`++`, `--`), assignment (`=`)
- **Identifiers**: Start with letter or underscore, followed by letters, digits, or underscores
- **Literals**: Integers, floating-point numbers, character literals, string literals
- **Comments**: Single-line (`//`) and multi-line (`/* */`)

## Project Structure

This is a Maven multi-module project:

```
ppj-compiler/
├── compiler-lexer/      # Lexer generator and runtime
├── compiler-parser/     # LR(1) parser generator and runtime
├── compiler-semantics/  # Type system and semantic analysis
├── compiler-codegen/   # FRISC assembly code generation
├── cli/                # Command-line interface
├── test-fixtures/      # Test resources
├── config/             # Configuration files
│   ├── lexer_definition.txt
│   └── parser_definition.txt
└── docs/               # Documentation
```

### Module Descriptions

- **compiler-lexer**: Implements a lexer generator that reads specification files and produces DFAs for tokenization
- **compiler-parser**: Implements a canonical LR(1) parser generator that builds parsing tables from grammar definitions
- **compiler-semantics**: (In development) Type system, symbol tables, and semantic analysis
- **compiler-codegen**: (In development) FRISC instruction emitter and code generation
- **cli**: Provides a unified command-line interface for all compiler phases

## Prerequisites

- **Java 21** or higher
- **Maven 3.8+**
- **Bash** (for build scripts)

## Building the Project

### Quick Build

The simplest way to build the entire project into a single executable JAR:

```bash
./build.sh
```

This will:
- Clean and compile all modules
- Run tests and static analysis
- Create a single executable JAR file at `cli/target/ccompiler.jar`
- Include all dependencies (fat JAR with all modules bundled)

### Manual Build

Alternatively, you can use Maven directly:

```bash
# Full build with tests
mvn clean verify

# Quick build without tests (for faster iteration)
mvn clean package -DskipTests -Dspotbugs.skip=true -Dcheckstyle.skip=true -Dspotless.check.skip=true
```

The JAR file will be created at: `cli/target/ccompiler.jar`

### Building Individual Modules

```bash
# Build only the lexer module
mvn clean install -pl compiler-lexer -am

# Build only the parser module
mvn clean install -pl compiler-parser -am

# Build CLI with all dependencies
mvn clean package -pl cli -am
```

## Running the Compiler

### Using the Run Script (Recommended)

```bash
# Lexical analysis only (output to stdout)
./run.sh lexer <file.c>

# Lexical and syntax analysis (output to compiler-bin/)
./run.sh syntax <file.c>

# Full compilation (lexical + syntax + semantic + codegen)
./run.sh semantic <file.c>
# or simply:
./run.sh <file.c>
```

### Using Java Directly

```bash
# Lexical analysis only
java -jar cli/target/ccompiler.jar lexer <file.c>

# Lexical and syntax analysis
java -jar cli/target/ccompiler.jar syntax <file.c>

# Full compilation
java -jar cli/target/ccompiler.jar <file.c>
```

### Using Maven (for Development)

```bash
# Lexer only
mvn -q -pl cli -am exec:java -Dexec.mainClass=hr.fer.ppj.cli.Main -Dexec.args="lexer <file.c>"

# Syntax analysis
mvn -q -pl cli -am exec:java -Dexec.mainClass=hr.fer.ppj.cli.Main -Dexec.args="syntax <file.c>"
```

### Examples

```bash
# Analyze a C source file lexically
./run.sh lexer compiler-parser/src/test/resources/ppjc_case_01/program.c

# Perform full syntax analysis
./run.sh syntax compiler-parser/src/test/resources/ppjc_case_01/program.c
# Output files will be in compiler-bin/:
#   - leksicke_jedinke.txt
#   - generativno_stablo.txt
#   - sintaksno_stablo.txt

# Full compilation
./run.sh compiler-parser/src/test/resources/ppjc_case_01/program.c
```

## Output Files

When running syntax analysis or full compilation, the following files are generated in the `compiler-bin/` directory:

- **leksicke_jedinke.txt**: Lexical tokens output (symbol table and token stream)
- **generativno_stablo.txt**: Complete generative (parse) tree showing the derivation process
- **sintaksno_stablo.txt**: Abstract syntax tree (AST) optimized for semantic analysis and code generation

### Output Format

**Lexical Tokens:**
```
tablica znakova:
indeks   uniformni znak   izvorni tekst
    0   KR_INT           int
    1   IDN              main
    ...

niz uniformnih znakova:
uniformni znak    redak    indeks u tablicu znakova
KR_INT            1        0
IDN               1        1
...
```

**Generative Tree:**
```
0:<prijevodna_jedinica>
    1:<vanjska_deklaracija>
        2:<definicija_funkcije>
        ...
```

**Syntax Tree:**
```
0:<prijevodna_jedinica>
    1:<vanjska_deklaracija>
        2:<definicija_funkcije>
        ...
```

## Configuration

### Lexer Definition

The lexer definition file is located at `config/lexer_definition.txt`. This file contains:
- Macro definitions for reusable patterns
- State declarations
- Token type declarations
- Lexer rules with patterns and actions

The path can be customized via the `LEXER_DEFINITION_PATH` environment variable.

### Parser Definition

The parser definition file is located at `config/parser_definition.txt`. This file contains:
- `%V`: Set of non-terminals (first one is the start symbol)
- `%T`: Set of terminals
- `%Syn`: Synchronization tokens for error recovery
- Production rules in the format `A -> α` or `A -> β | γ | ...`
- Epsilon is denoted by `$`

The path can be customized via the `PARSER_DEFINITION_PATH` environment variable.

## Test Programs

The `examples/` directory contains a comprehensive collection of test programs:

- **`examples/valid/`**: 80 valid C programs covering all language features
- **`examples/invalid/`**: 70 invalid programs with various error types (lexical, syntax, semantic)

Each directory includes a `README` file describing the programs.

### Generating HTML Reports

To generate HTML reports for all test programs:

```bash
java -cp "cli/target/classes:compiler-lexer/target/classes:compiler-parser/target/classes" \
  hr.fer.ppj.examples.ExamplesReportGenerator
```

This generates:
- `examples/report_valid.html` - Report for all valid programs
- `examples/report_invalid.html` - Report for all invalid programs

Each report shows:
- Source code for each program
- Lexical tokens output
- Generative tree
- Syntax tree
- Error messages (if any)

## Documentation

Comprehensive documentation is available in the `docs/` directory:

### Lexer Documentation
- **[LEXER_IMPLEMENTATION.md](docs/LEXER_IMPLEMENTATION.md)**: Detailed technical documentation of the lexer implementation, including regex parsing, ε-NFA to DFA conversion, and lexer generator algorithms
- **[LEXER_USER_GUIDE.md](docs/LEXER_USER_GUIDE.md)**: User guide for writing lexer specifications and using the lexer
- **[LEXER_DOCUMENTATION.md](docs/LEXER_DOCUMENTATION.md)**: General lexer documentation and overview

### Parser Documentation
- **[PARSER_DOCUMENTATION.md](docs/PARSER_DOCUMENTATION.md)**: Overview of the parser module, architecture, usage, and output files
- **[LR_PARSER_TECHNICAL.md](docs/LR_PARSER_TECHNICAL.md)**: Detailed technical documentation of the canonical LR(1) parser implementation, including:
  - Input grammar definition format
  - Grammar augmentation
  - FIRST set computation
  - CLOSURE and GOTO algorithms
  - Canonical collection construction
  - ACTION and GOTO table generation
  - Conflict resolution
  - Runtime parser implementation

### Additional Documentation
- **[IMPLEMENTATION_NOTES.md](docs/IMPLEMENTATION_NOTES.md)**: Implementation notes and design decisions
- **[TESTING_STATUS.md](docs/TESTING_STATUS.md)**: Testing status and coverage information
- **[LEXER_CONSISTENCY_CHECK.md](docs/LEXER_CONSISTENCY_CHECK.md)**: Lexer consistency checks and validation

## Development

### Key Constraints

1. **NO REGEX libraries** - All regex parsing is manual, following formal specifications
2. **LR(1) parser** - Generated from `parser_definition.txt` grammar file using canonical LR(1) construction
3. **Java 21** - Uses modern features: records, sealed classes, pattern matching
4. **Code quality** - Error Prone, Checkstyle, SpotBugs, Spotless

### Running Tests

```bash
# Run all tests
mvn test

# Run tests for a specific module
mvn test -pl compiler-lexer
mvn test -pl compiler-parser

# Run a specific test class
mvn test -Dtest=LexerGoldenTest
mvn test -Dtest=ParserGoldenTest
```

### Code Quality Checks

```bash
# Run all static analysis tools
mvn verify

# Run individual tools
mvn checkstyle:check
mvn spotbugs:check
mvn spotless:check
```

### Project Dependencies

The project uses a strict dependency hierarchy:
- `compiler-lexer` has no dependencies on other compiler modules
- `compiler-parser` depends on `compiler-lexer`
- `compiler-semantics` depends on `compiler-parser`
- `compiler-codegen` depends on `compiler-semantics`
- `cli` depends on all compiler modules

## Status

### ✅ Completed

- **Parent POM**: All required plugins (Error Prone, Checkstyle, SpotBugs, Spotless, Enforcer)
- **Lexer Module**: 
  - Manual regex parser (no regex libraries) following formal specifications
  - ε-NFA → DFA conversion with subset construction
  - Lexer spec parser (handles macros, states, rules, actions)
  - Action handling: `UDJI_U_STANJE`, `VRATI_SE`, `NOVI_REDAK`
  - Maximal munch with rule priority (earlier rules win)
  - Symbol table with correct output format
  - Recursive macro expansion
  - Comprehensive error recovery
  - Multi-state support (comments, string literals)
- **Parser Module**:
  - Grammar parser for `parser_definition.txt` format
  - Grammar augmentation (S' → S)
  - FIRST set computation
  - CLOSURE and GOTO algorithms
  - Canonical LR(1) collection construction (~823 states)
  - ACTION and GOTO table generation
  - Conflict resolution (SHIFT/REDUCE, REDUCE/REDUCE)
  - Runtime parser with SHIFT, REDUCE, ACCEPT operations
  - Generative and syntax tree generation
  - LR table caching for performance
  - Error recovery with synchronization tokens
- **CLI**: Command-line interface for all compiler phases

### 🚧 In Progress

- Parser: Enhanced error recovery
- Semantics: Type system implementation
- Semantics: Symbol tables with scopes
- Codegen: FRISC instruction emitter

### 📋 TODO

- Semantics: Semantic analysis visitors
- Codegen: Register allocation and function ABI
- Codegen: Short-circuit evaluation
- Tests: Integration tests for full compilation pipeline

## License

This project is part of the PPJ (Prevođenje Programskih Jezika / Compiler Construction) course at the University of Zagreb, Faculty of Electrical Engineering and Computing.

## References

1. Srbljić, Siniša (2007). *Prevođenje programskih jezika*. Element, Zagreb. ISBN 978-953-197-625-1.

2. Aho, A. V., Lam, M. S., Sethi, R., & Ullman, J. D. (2006). *Compilers: Principles, Techniques, and Tools* (2nd ed.). Pearson Education.

## Contributing

This is an academic project. For questions or issues, please contact the course instructors.
