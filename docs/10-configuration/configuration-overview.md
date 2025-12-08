# Configuration Overview

## Introduction

The PPJ compiler uses three configuration files located in the `config/` directory to define the language syntax and semantics. These files serve as the authoritative specification for lexical analysis, syntax analysis, and semantic analysis phases. Understanding these configuration files is essential for extending the compiler or modifying the supported language features.

## Configuration Files

The compiler uses the following configuration files:

1. **`config/lexer_definition.txt`**: Defines token patterns and lexical rules
2. **`config/parser_definition.txt`**: Defines context-free grammar productions
3. **`config/semantics_definition.txt`**: Defines semantic rules and type system constraints

## Configuration Loading

### Path Resolution

Configuration files are loaded using a hierarchical path resolution strategy:

1. **Environment Variable Override**: Check for custom path via environment variable
   - `LEXER_DEFINITION_PATH`: Override lexer definition path
   - `PARSER_DEFINITION_PATH`: Override parser definition path
   - `SEMANTICS_DEFINITION_PATH`: Override semantics definition path

2. **Project Root Detection**: Automatically detect project root by searching for:
   - `pom.xml` file (Maven project marker)
   - `config/` directory

3. **Default Paths**: If project root is found, use:
   - `config/lexer_definition.txt`
   - `config/parser_definition.txt`
   - `config/semantics_definition.txt`

4. **Fallback**: If project root not found, use current directory

### Implementation

Configuration loading is implemented in:

- **Lexer**: `hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath()`
- **Parser**: `hr.fer.ppj.parser.config.ParserConfig.getParserDefinitionPath()`
- **Semantics**: Similar pattern (check semantic analyzer code)

**Example**:
```java
// Load lexer definition
Path lexerDef = LexerConfig.getLexerDefinitionPath();

// Load parser definition
Path parserDef = ParserConfig.getParserDefinitionPath();
```

## Configuration File Format

### Lexer Definition Format

The lexer definition file (`config/lexer_definition.txt`) uses a custom format with four sections:

1. **Macro Definitions**: `{name} pattern`
2. **State Declarations**: `%X state1 state2 ...`
3. **Token Declarations**: `%L TOKEN1 TOKEN2 ...`
4. **Lexer Rules**: `<state>pattern { actions }`

**See Also**: [Lexer Configuration Reference](config-file-reference.md#lexer-definition)

### Parser Definition Format

The parser definition file (`config/parser_definition.txt`) uses BNF-like notation:

1. **Non-terminal Declarations**: `%V <nonterm1> <nonterm2> ...`
2. **Terminal Declarations**: `%T TOKEN1 TOKEN2 ...`
3. **Synchronization Tokens**: `%Syn TOKEN1 TOKEN2 ...`
4. **Productions**: `<nonterm> ::= alternative1 | alternative2 ...`

**See Also**: [Parser Configuration Reference](config-file-reference.md#parser-definition)

### Semantics Definition Format

The semantics definition file (`config/semantics_definition.txt`) defines semantic rules:

- Type compatibility rules
- Scope resolution rules
- Semantic constraint specifications

**See Also**: [Semantics Configuration Reference](config-file-reference.md#semantics-definition)

## Configuration Validation

Configuration files are validated during loading:

- **Lexer**: Validates macro definitions, state declarations, token declarations, rule syntax
- **Parser**: Validates grammar syntax, non-terminal/terminal declarations, production format
- **Semantics**: Validates semantic rule syntax and consistency

**Error Handling**: Invalid configuration files cause compilation to fail with descriptive error messages.

## Configuration Precedence

When multiple configuration sources are available:

1. **Environment Variables** (highest priority)
2. **Project Root Configuration Files**
3. **Current Directory Configuration Files** (fallback)

## Extending Configuration

### Adding New Tokens

1. Add token name to `%L` section in `lexer_definition.txt`
2. Add pattern rule in lexer rules section
3. Add token to `%T` section in `parser_definition.txt` (if used in grammar)

### Adding Grammar Productions

1. Add non-terminal to `%V` section in `parser_definition.txt`
2. Add productions for the non-terminal
3. Update semantic rules in `semantics_definition.txt` if needed

### Adding Semantic Rules

1. Add rule specification to `semantics_definition.txt`
2. Implement rule checking in semantic analyzer code
3. Add tests for new semantic constraints

## Configuration Best Practices

1. **Version Control**: Always commit configuration files to version control
2. **Documentation**: Document non-obvious patterns or rules
3. **Testing**: Test configuration changes with example programs
4. **Consistency**: Ensure token names match between lexer and parser definitions
5. **Incremental Changes**: Make small, incremental changes and test frequently

## Further Reading

- **[Configuration File Reference](config-file-reference.md)**: Detailed format specifications
- **[Configuration Examples](examples-and-best-practices.md)**: Example configurations and usage patterns
- **[Lexical Analysis Documentation](../03-lexical-analysis/lexer-design.md)**: How lexer uses configuration
- **[Syntax Analysis Documentation](../04-syntax-analysis/grammar-specification.md)**: How parser uses configuration
- **[Semantic Analysis Documentation](../05-semantic-analysis/type-system-and-checking.md)**: How semantic analyzer uses configuration

---

*Configuration files are the foundation of the compiler's language specification. Understanding them is essential for compiler maintenance and extension.*
