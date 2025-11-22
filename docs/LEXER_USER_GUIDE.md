# Lexical Analyzer - User Guide

## Table of Contents

1. [Introduction](#introduction)
2. [Lexer Specification Format](#lexer-specification-format)
3. [Writing Lexer Rules](#writing-lexer-rules)
4. [Examples](#examples)
5. [Generating and Using the Lexer](#generating-and-using-the-lexer)
6. [Output Format](#output-format)
7. [Error Messages](#error-messages)
8. [Best Practices](#best-practices)

---

## Introduction

The lexical analyzer (lexer) converts source code into a sequence of tokens (lexical units). The lexer is generated from a specification file that describes the patterns for recognizing tokens.

### Key Features

- **Text-based specification**: Rules are written in a simple text format
- **Regular expressions**: Support for regex operators (`|`, `*`, `()`, etc.)
- **Macros**: Ability to define and reuse patterns
- **Multi-state analysis**: Support for different states (comments, string literals, etc.)
- **Actions**: State transitions, backtracking, position updates
- **Maximal munch**: Always selects the longest possible match
- **Rule priority**: Earlier rules win in case of ties

---

## Lexer Specification Format

The lexer specification file (`config/lexer_definition.txt`) uses the PPJ format with several sections:

### 1. Macro Definitions

Macros define reusable patterns that can be referenced in rules:

```
{macroName} pattern
```

**Examples:**

```
{znak} a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z|A|B|C|D|E|F|G|H|I|J|K|L|M|N|O|P|Q|R|S|T|U|V|W|X|Y|Z
{znamenka} 0|1|2|3|4|5|6|7|8|9
{bjelina} \t|\n|\_
```

**Notes:**
- Macros can reference other macros: `{hexZnamenka} {znamenka}|a|b|c|d|e|f|A|B|C|D|E|F`
- Macro values are automatically wrapped in parentheses when expanded to preserve precedence
- Macros are expanded recursively until no more references remain

### 2. State Declarations

Declares all lexer states:

```
%X state1 state2 state3 ...
```

**Example:**
```
%X S_pocetno S_komentar S_jednolinijskiKomentar S_string
```

**States:**
- `S_pocetno`: Initial state, handles most tokens
- `S_string`: String literal state
- `S_komentar`: Multi-line comment state
- `S_jednolinijskiKomentar`: Single-line comment state

### 3. Token Declarations

Declares all token types:

```
%L TOKEN1 TOKEN2 TOKEN3 ...
```

**Example:**
```
%L IDN BROJ ZNAK NIZ_ZNAKOVA KR_BREAK KR_CHAR KR_CONST KR_CONTINUE ...
```

### 4. Lexer Rules

Defines matching rules for each state:

```
<state>pattern { actions }
```

**Components:**
- `<state>`: The lexer state in which this rule applies
- `pattern`: Regular expression pattern to match
- `{ actions }`: Actions to execute when pattern matches

---

## Writing Lexer Rules

### Pattern Syntax

The lexer supports the following regex operators:

- **Concatenation**: `ab` (implicit, no operator)
- **Union**: `a|b` (alternation)
- **Kleene Star**: `a*` (zero or more)
- **Groups**: `(a|b)*` (parentheses for grouping)
- **Epsilon**: `$` (empty string)

### Quoted Patterns

Patterns can be quoted:

- **Literal strings**: `"break"` → pattern is `break` (quotes removed)
- **Regex patterns with quotes**: `"({macro}|\\")*"` → pattern includes quotes (quotes preserved)

The parser automatically detects if a quoted pattern contains regex operators to determine whether to keep or remove quotes.

### Escape Sequences

Supported escape sequences:

- `\\` → backslash
- `\"` → double quote
- `\n` → newline
- `\t` → tab
- `\_` → space

### Actions

Three types of actions are supported:

#### 1. Token Type

The token type to assign when the pattern matches:

```
{
TOKEN_NAME
}
```

If the token type is `-` or empty, the match is skipped (used for whitespace/comments).

#### 2. UDJI_U_STANJE (Enter State)

Changes the current lexer state:

```
{
UDJI_U_STANJE stateName
}
```

**Example:**
```
<S_pocetno>"
{
-
UDJI_U_STANJE S_string
VRATI_SE 0
}
```

#### 3. VRATI_SE (Backtrack)

Returns characters to the input buffer:

```
{
VRATI_SE n
}
```

Of the matched characters, keep the first `n` in the token, return the rest to the input buffer.

**Example:**
```
<S_pocetno>"
{
-
UDJI_U_STANJE S_string
VRATI_SE 0
}
```

This matches the opening `"`, but `VRATI_SE 0` means consume 0 characters, so the `"` remains in the buffer for the next match (which will use the `S_string` DFA).

#### 4. NOVI_REDAK (New Line)

Increments the line number:

```
{
NOVI_REDAK
}
```

---

## Examples

### Example 1: Keyword

```
<S_pocetno>"int"
{
KR_INT
}
```

- Matches the literal string `"int"`
- Produces token type `KR_INT`

### Example 2: Identifier

```
<S_pocetno>{znak}({znak}|{znamenka}|_)*
{
IDN
}
```

- Matches identifiers: letter followed by letters, digits, or underscores
- Uses macro `{znak}` for letters
- Produces token type `IDN`

### Example 3: String Literal Entry

```
<S_pocetno>"
{
-
UDJI_U_STANJE S_string
VRATI_SE 0
}
```

- Matches opening quote `"`
- Changes state to `S_string`
- Returns the quote to buffer (`VRATI_SE 0`)
- No token produced (`-`)

### Example 4: String Literal Content

```
<S_string>"({sveOsimDvostrukogNavodnikaINovogReda}|\\")*"
{
NIZ_ZNAKOVA
UDJI_U_STANJE S_pocetno
}
```

- Matches string content: any character except `"` and `\n`, or escaped quote `\"`
- Produces token type `NIZ_ZNAKOVA`
- Returns to `S_pocetno` state

### Example 5: Two-Character Operator

```
<S_pocetno>"++"
{
OP_INC
}
```

- Matches `++`
- Produces token type `OP_INC`

**Note**: The lexer uses maximal munch, so `++` will match before `+` if both rules exist. Rule order (P3) determines which wins if they match the same length.

### Example 6: Comment Entry

```
<S_pocetno>"//"
{
-
UDJI_U_STANJE S_jednolinijskiKomentar
}
```

- Matches `//`
- Changes state to `S_jednolinijskiKomentar`
- No token produced

### Example 7: Comment Content

```
<S_jednolinijskiKomentar>{sveOsimNovogRedaITaba}*
{
-
}
```

- Matches any character except newline and tab
- No token produced (comment is skipped)

---

## Generating and Using the Lexer

### Command-Line Interface

```bash
# Tokenize input from stdin
echo "int x = 42;" | mvn -q -pl cli -am exec:java \
  -Dexec.mainClass=hr.fer.ppj.cli.Main \
  -Dexec.args="lexer"
```

### Programmatic Usage

```java
// Load lexer definition
Path lexerDefinitionPath = LexerConfig.getLexerDefinitionPath();
LexerGenerator generator = new LexerGenerator();
LexerGeneratorResult result;
try (FileReader reader = new FileReader(lexerDefinitionPath.toFile())) {
  result = generator.generate(reader);
}

// Create lexer
Lexer lexer = new Lexer(result);

// Tokenize input
List<Token> tokens;
try (StringReader reader = new StringReader("int x = 42;")) {
  tokens = lexer.tokenize(reader);
}

// Access symbol table
List<SymbolTableEntry> symbolTable = lexer.getSymbolTable();
```

---

## Output Format

### Symbol Table

The symbol table lists all unique tokens:

```
tablica znakova:
indeks   uniformni znak   izvorni tekst
     0   KR_INT            int
     1   IDN               x
     2   OP_PRIDRUZI       =
     3   BROJ              42
     4   TOCKAZAREZ        ;
```

### Token Stream

The token stream references symbol table indices:

```
niz uniformnih znakova:
uniformni znak    redak    indeks u tablicu znakova
KR_INT               1       0
IDN                  1       1
OP_PRIDRUZI          1       2
BROJ                 1       3
TOCKAZAREZ           1       4
```

---

## Error Messages

### Unrecognized Character

```
Leksička greška na retku 5, stupcu 12: neprepoznat znak '@' (0x40)
```

- **Location**: Line 5, column 12
- **Character**: `@` (hex: 0x40)
- **Recovery**: Character is discarded, tokenization continues

### Unterminated String Literal

```
Leksička greška na retku 3, stupcu 1: nezatvoren string literal
```

- **Location**: Line 3, column 1 (start of string)
- **Recovery**: All characters up to newline are discarded, lexer returns to `S_pocetno` state

---

## Best Practices

### 1. Rule Order Matters

Rules are processed in order. Earlier rules have priority if they match the same length. Place more specific rules before general ones:

```
// Correct order
<S_pocetno>"++" { OP_INC }
<S_pocetno>"+" { PLUS }

// Wrong order (++ would never match)
<S_pocetno>"+" { PLUS }
<S_pocetno>"++" { OP_INC }
```

### 2. Use Macros for Reusability

Define common patterns as macros:

```
{znak} a|b|c|...|z|A|B|...|Z
{znamenka} 0|1|2|3|4|5|6|7|8|9

// Then use in rules
<S_pocetno>{znak}({znak}|{znamenka}|_)* { IDN }
```

### 3. Handle State Transitions Correctly

When entering a new state, use `VRATI_SE 0` to keep the triggering character in the buffer:

```
<S_pocetno>"
{
-
UDJI_U_STANJE S_string
VRATI_SE 0
}
```

### 4. Test Edge Cases

Test your lexer with:
- Empty input
- Unterminated strings
- Invalid characters
- Very long identifiers
- Nested comments (if supported)

### 5. Document Complex Patterns

Add comments to explain complex patterns:

```
// Match floating-point numbers: digits.digits or digits.digitsE+digits
<S_pocetno>{znamenka}+\.{znamenka}+({eksponent})? { BROJ }
```

---

## Summary

This user guide covers the essential aspects of writing lexer specifications. For detailed technical information about the implementation, see [LEXER_IMPLEMENTATION.md](LEXER_IMPLEMENTATION.md).
