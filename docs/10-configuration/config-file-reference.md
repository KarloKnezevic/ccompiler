# Configuration File Reference

## Overview

This document provides a **complete reference** for all configuration file formats used by the PPJ compiler. The compiler uses **declarative configuration files** to specify language syntax and semantics, rather than hardcoding these specifications in the compiler source code. This approach provides several advantages:

- **Separability**: Language specifications can be modified without recompiling the compiler
- **Clarity**: Specifications are written in domain-specific formats that are easier to read than code
- **Maintainability**: Changes to language features can be made by editing text files rather than complex code
- **Educational Value**: Specifications are visible and understandable, making it easier to learn how the compiler works

Each configuration file serves as the **authoritative specification** for a compiler phase. The compiler reads these files at startup and uses them to configure its behavior. Understanding the configuration file formats is essential for:
- Understanding how the compiler recognizes language constructs
- Extending the compiler to support new language features
- Debugging compilation issues related to language specification
- Learning how formal language specifications translate to compiler implementation

This reference provides complete specifications for all configuration file formats, including syntax, semantics, examples, and usage guidelines. For practical examples and best practices, see [Configuration Examples and Best Practices](examples-and-best-practices.md).

## Lexer Definition (`config/lexer_definition.txt`)

### File Structure

The lexer definition file consists of four sections:

1. **Macro Definitions**: Reusable regex patterns
2. **State Declarations**: Lexer state machine states
3. **Token Declarations**: Token type names
4. **Lexer Rules**: Pattern matching rules with actions

### Section 1: Macro Definitions

**Format**:
```
{macroName} pattern
```

**Description**: Macro definitions create **reusable regular expression patterns** that can be referenced in lexer rules. Macros improve readability and maintainability by allowing common patterns to be defined once and reused multiple times.

Macros are similar to named constants in programming languages—they define a name for a pattern, and that name can be used wherever the pattern is needed. When a macro is referenced in a lexer rule, the macro's pattern is substituted for the macro name, effectively inlining the pattern.

**Macro Expansion**: Macros are expanded **recursively**—if a macro references another macro, both are expanded until no macro references remain. This allows complex patterns to be built from simpler components.

**Example**:
```
{znak} a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z|A|B|C|D|E|F|G|H|I|J|K|L|M|N|O|P|Q|R|S|T|U|V|W|X|Y|Z
{znamenka} 0|1|2|3|4|5|6|7|8|9
{hexZnamenka} {znamenka}|a|b|c|d|e|f|A|B|C|D|E|F
{bjelina} \t|\n|\_
```

**Explanation of Examples**:
- `{znak}`: Defines a macro for letters (both lowercase and uppercase). This pattern uses union (`|`) to match any single letter.
- `{znamenka}`: Defines a macro for digits (0-9). This is a simple union of all digit characters.
- `{hexZnamenka}`: Defines a macro for hexadecimal digits. This macro **references** `{znamenka}`, demonstrating macro composition. When expanded, it becomes: `0|1|2|3|4|5|6|7|8|9|a|b|c|d|e|f|A|B|C|D|E|F`.
- `{bjelina}`: Defines a macro for whitespace characters. The pattern uses escaped characters: `\t` (tab), `\n` (newline), `\_` (space, represented as underscore in the format).

**Macro Usage in Rules**: Once defined, macros can be used in lexer rules by referencing them with curly braces. For example:
```
<S_pocetno>(_|{znak})(_|{znak}|{znamenka})*
{
IDN
}
```

This rule uses `{znak}` and `{znamenka}` macros to define the identifier pattern: an identifier starts with a letter or underscore, followed by zero or more letters, digits, or underscores.

**Rules and Constraints**:
- **Macro Names**: Must be enclosed in curly braces `{name}`. The name itself is case-sensitive and can contain letters, digits, and underscores.
- **Pattern Syntax**: Patterns use standard regular expression operators:
  - `|` (union): Matches either the left or right pattern
  - `*` (Kleene star): Matches zero or more repetitions
  - `()` (grouping): Groups subpatterns for precedence control
  - `+` (plus): Matches one or more repetitions (if supported)
  - `?` (question mark): Matches zero or one occurrence (if supported)
- **Special Characters**: Must be escaped with backslash:
  - `\t` represents a tab character
  - `\n` represents a newline character
  - `\_` represents a space character (underscore in the format)
  - `\\` represents a literal backslash
  - `\"` represents a literal double quote
  - `\'` represents a literal single quote
- **Recursive Expansion**: Macros can reference other macros. Expansion continues until no macro references remain. Circular references (a macro that directly or indirectly references itself) should be avoided, as they cause infinite expansion.
- **Expansion Order**: Macros are expanded when referenced in rules, not when defined. This means macro definitions can appear in any order, as long as all referenced macros are defined before the rule that uses them is processed.

### Section 2: State Declarations

**Format**:
```
%X state1 state2 state3 ...
```

**Description**: Declares all lexer states used in the state machine.

**Example**:
```
%X S_pocetno S_komentar S_jednolinijskiKomentar S_string
```

**States**:
- `S_pocetno`: Initial state (default state for most tokens)
- `S_string`: String literal state (entered when `"` is encountered)
- `S_komentar`: Multi-line comment state (entered when `/*` is encountered)
- `S_jednolinijskiKomentar`: Single-line comment state (entered when `//` is encountered)

**Rules**:
- First state listed is typically the initial state
- States are referenced in lexer rules as `<state>`
- State transitions are controlled by actions in rules

### Section 3: Token Declarations

**Format**:
```
%L TOKEN1 TOKEN2 TOKEN3 ...
```

**Description**: Declares all token types that can be produced by the lexer.

**Example**:
```
%L IDN BROJ ZNAK NIZ_ZNAKOVA KR_BREAK KR_CHAR KR_CONST KR_CONTINUE KR_ELSE KR_FLOAT KR_FOR KR_IF KR_INT KR_RETURN KR_STRUCT KR_VOID KR_WHILE PLUS OP_INC MINUS OP_DEC ASTERISK OP_DIJELI OP_MOD OP_PRIDRUZI OP_LT OP_LTE OP_GT OP_GTE OP_EQ OP_NEQ OP_NEG OP_TILDA OP_I OP_ILI AMPERSAND OP_BIN_ILI OP_BIN_XILI ZAREZ TOCKAZAREZ TOCKA L_ZAGRADA D_ZAGRADA L_UGL_ZAGRADA D_UGL_ZAGRADA L_VIT_ZAGRADA D_VIT_ZAGRADA
```

**Token Categories**:
- **Identifiers**: `IDN`
- **Literals**: `BROJ` (number), `ZNAK` (character), `NIZ_ZNAKOVA` (string)
- **Keywords**: `KR_INT`, `KR_CHAR`, `KR_IF`, `KR_WHILE`, etc.
- **Operators**: `PLUS`, `MINUS`, `OP_INC`, `OP_DEC`, `OP_EQ`, etc.
- **Delimiters**: `L_ZAGRADA`, `D_ZAGRADA`, `TOCKAZAREZ`, etc.

**Rules**:
- Token names must match those used in parser definition
- Token names are case-sensitive
- Convention: Keywords prefixed with `KR_`, operators prefixed with `OP_`

### Section 4: Lexer Rules

**Format**:
```
<state>pattern { actions }
```

**Description**: Defines pattern matching rules with associated actions.

**Example**:
```
<S_pocetno>int
{
KR_INT
}

<S_pocetno>(_|{znak})(_|{znak}|{znamenka})*
{
IDN
}

<S_pocetno>{znamenka}{znamenka}*
{
BROJ
}

<S_pocetno>"
{
-
UDJI_U_STANJE S_string
VRATI_SE 0
}
```

**Pattern Syntax**:
- Standard regex operators: `|`, `*`, `+`, `?`, `()`
- Character classes: `[a-z]`, `[0-9]`
- Escaped characters: `\t`, `\n`, `\_`, `\"`, `\'`
- Macro references: `{macroName}`

**Actions**:
- **Token Return**: `TOKEN_NAME` (return token to parser)
- **State Transition**: `UDJI_U_STANJE stateName` (change lexer state)
- **Backtrack**: `VRATI_SE n` (backtrack n characters, re-enter state)
- **Line Count**: `NOVI_REDAK` (increment line counter)
- **No Action**: `-` (consume input without returning token)

**Matching Rules**:
1. **Maximal Munch**: Always select longest matching pattern
2. **Rule Priority**: If multiple patterns match same length, earlier rule wins
3. **State-Specific**: Rules only apply in specified state

### Complete Example

```
{znak} a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z|A|B|C|D|E|F|G|H|I|J|K|L|M|N|O|P|Q|R|S|T|U|V|W|X|Y|Z
{znamenka} 0|1|2|3|4|5|6|7|8|9
%X S_pocetno S_string
%L IDN BROJ KR_INT
<S_pocetno>int
{
KR_INT
}
<S_pocetno>(_|{znak})(_|{znak}|{znamenka})*
{
IDN
}
<S_pocetno>{znamenka}{znamenka}*
{
BROJ
}
```

## Parser Definition (`config/parser_definition.txt`)

### File Structure

The parser definition file consists of four sections:

1. **Non-terminal Declarations**: `%V` section
2. **Terminal Declarations**: `%T` section
3. **Synchronization Tokens**: `%Syn` section
4. **Production Rules**: Grammar productions

### Section 1: Non-terminal Declarations

**Format**:
```
%V <non_terminal1> <non_terminal2> ... <non_terminalN>
```

**Description**: Declares all non-terminal symbols in the grammar. The first non-terminal is the start symbol.

**Example**:
```
%V <prijevodna_jedinica> <vanjska_deklaracija> <deklaracija> <definicija_funkcije> ...
```

**Rules**:
- Non-terminals are enclosed in angle brackets `<name>`
- First non-terminal is the grammar start symbol
- All non-terminals used in productions must be declared

### Section 2: Terminal Declarations

**Format**:
```
%T TOKEN1 TOKEN2 ... TOKENN
```

**Description**: Declares all terminal symbols (tokens) used in the grammar.

**Example**:
```
%T IDN BROJ ZNAK NIZ_ZNAKOVA KR_BREAK KR_CHAR KR_CONST ...
```

**Rules**:
- Terminal names must match token names from lexer definition
- Terminals are case-sensitive
- All terminals used in productions must be declared

### Section 3: Synchronization Tokens

**Format**:
```
%Syn TOKEN1 TOKEN2 ...
```

**Description**: Declares synchronization tokens used for error recovery.

**Example**:
```
%Syn TOCKAZAREZ D_VIT_ZAGRADA
```

**Usage**: When a parse error occurs, the parser skips tokens until it finds a synchronization token, then attempts to recover.

**Rules**:
- Synchronization tokens must be declared terminals
- Typically include statement terminators (`TOCKAZAREZ`) and block delimiters (`D_VIT_ZAGRADA`)

### Section 4: Production Rules

**Format (Alternative 1 - Multi-line)**:
```
<non_terminal>
 <alternative1>
 <alternative2>
 <alternative3>
```

**Format (Alternative 2 - Single-line)**:
```
<non_terminal> ::= <alternative1> | <alternative2> | <alternative3>
```

**Description**: Defines grammar productions. Each production specifies how a non-terminal can be rewritten.

**Example**:
```
<izraz>
 <izraz_pridruzivanja>
 <izraz> ZAREZ <izraz_pridruzivanja>
```

This defines:
- `<izraz> → <izraz_pridruzivanja>`
- `<izraz> → <izraz> ZAREZ <izraz_pridruzivanja>`

**Epsilon Productions**:
```
<non_terminal>
 $
```

The `$` symbol represents an empty production (epsilon).

**Rules**:
- Left-hand side (LHS) is a non-terminal (no leading space)
- Right-hand side (RHS) alternatives are indented (leading space) or separated by `|`
- Alternatives can contain terminals, non-terminals, or epsilon (`$`)
- Productions are processed in order (affects conflict resolution)

### Complete Example

```
%V <izraz> <izraz_pridruzivanja> <log_ili_izraz>
%T IDN BROJ PLUS MINUS OP_I OP_ILI
%Syn TOCKAZAREZ
<izraz>
 <izraz_pridruzivanja>
 <izraz> ZAREZ <izraz_pridruzivanja>
<izraz_pridruzivanja>
 <log_ili_izraz>
<log_ili_izraz>
 IDN
 BROJ
 <log_ili_izraz> OP_I <log_ili_izraz>
 <log_ili_izraz> OP_ILI <log_ili_izraz>
```

## Semantics Definition (`config/semantics_definition.txt`)

### File Structure

**Note**: The semantics definition file currently contains grammar productions (same format as parser definition). Semantic rules are implemented directly in the semantic analyzer code.

**Future Format** (proposed):
```
RULE: <production>
CONSTRAINT: <condition>
ACTION: <semantic action>
```

**Current Implementation**: Semantic rules are hardcoded in `SemanticChecker` class. See [Semantic Analysis Documentation](../05-semantic-analysis/type-system-and-checking.md) for semantic rule specifications.

## Configuration File Validation

### Lexer Definition Validation

- Macro definitions must be well-formed regex patterns
- States must be declared before use
- Tokens must be declared before use in rules
- Rules must reference valid states and tokens

### Parser Definition Validation

- Non-terminals must be declared before use
- Terminals must be declared before use
- Productions must reference declared symbols
- Grammar must be LR(1) parseable (no conflicts)

### Error Messages

Invalid configuration files produce descriptive error messages:
- Line numbers for syntax errors
- Undefined symbol references
- Grammar conflicts (shift-reduce, reduce-reduce)

## Further Reading

- **[Configuration Overview](configuration-overview.md)**: Configuration system overview
- **[Configuration Examples](examples-and-best-practices.md)**: Example configurations
- **[Lexical Analysis](../03-lexical-analysis/lexer-design.md)**: How lexer uses configuration
- **[Syntax Analysis](../04-syntax-analysis/grammar-specification.md)**: How parser uses configuration

---

*This reference provides complete specifications for all configuration file formats. Consult the examples document for practical usage patterns.*
