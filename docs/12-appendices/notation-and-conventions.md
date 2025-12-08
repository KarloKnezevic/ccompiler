# Notation and Conventions

## Overview

This document defines the notation and conventions used throughout the PPJ compiler documentation.

## Grammar Notation

### BNF Notation

**Non-terminals**: Enclosed in angle brackets `<non_terminal>`

**Terminals**: Written in uppercase `TOKEN` or quoted `"string"`

**Productions**: Written as `<non_terminal> ::= alternative1 | alternative2`

**Epsilon**: Denoted `$` or `ε` for empty production

**Example**:
```
<izraz> ::= <izraz_pridruzivanja>
          | <izraz> ZAREZ <izraz_pridruzivanja>
```

### Extended BNF

**Optional**: `[optional]` or `alternative?`

**Repetition**: `{repetition}*` (zero or more) or `{repetition}+` (one or more)

**Grouping**: `(group)` for precedence

## Regular Expression Notation

**Concatenation**: `ab` (a followed by b)

**Union**: `a|b` (a or b)

**Kleene Star**: `a*` (zero or more a)

**Plus**: `a+` (one or more a)

**Question Mark**: `a?` (zero or one a)

**Character Classes**: `[a-z]` (any character in range)

**Escaped Characters**: `\t` (tab), `\n` (newline), `\\` (backslash)

## Mathematical Notation

### Sets

**Set Notation**: `{a, b, c}` (set containing a, b, c)

**Set Operations**:
- Union: `A ∪ B`
- Intersection: `A ∩ B`
- Difference: `A - B`
- Subset: `A ⊆ B`
- Element: `a ∈ A`

### Functions

**Function Notation**: `f: A → B` (function from A to B)

**Function Application**: `f(x)` (apply f to x)

### Algorithms

**Pseudocode**: Algorithms written in structured English with mathematical notation

**Example**:
```text
function CLOSURE(I):
    repeat
        for each [A → α · Bβ, a] in I:
            for each production B → γ:
                for each b in FIRST(βa):
                    add [B → · γ, b] to I
    until no changes
    return I
```

## Code Conventions

### Java Code

**Package Names**: `hr.fer.ppj.module.component`

**Class Names**: PascalCase `LexerGenerator`

**Method Names**: camelCase `generateLexer()`

**Constants**: UPPER_SNAKE_CASE `MAX_STATES`

### C Code

**Function Names**: snake_case `main()`, `factorial()`

**Variable Names**: snake_case `result`, `counter`

**Type Names**: Keywords `int`, `char`, `void`

### FRISC Assembly

**Instructions**: Uppercase `MOVE`, `ADD`, `CALL`

**Registers**: R0-R7 `R0`, `R1`, `R6`, `R7`

**Labels**: Uppercase with underscore `F_MAIN`, `F_MUL`

**Comments**: Semicolon `; comment`

## File Naming Conventions

### Source Files

**Java**: `ClassName.java`

**C**: `program.c`, `function.c`

**FRISC Assembly**: `program.frisc`, `a.frisc`

### Configuration Files

**Lexer Definition**: `lexer_definition.txt`

**Parser Definition**: `parser_definition.txt`

**Semantics Definition**: `semantics_definition.txt`

### Output Files

**Lexer Output**: `leksicke_jedinke.txt`

**Parse Tree**: `generativno_stablo.txt`, `sintaksno_stablo.txt`

**Symbol Table**: `tablica_simbola.txt`

**Assembly Output**: `a.frisc`

## Diagram Conventions

### Flowcharts

**Rectangles**: Processes

**Diamonds**: Decisions

**Ovals**: Start/End

**Arrows**: Flow direction

### State Diagrams

**Circles**: States

**Arrows**: Transitions (labeled with input symbols)

**Double Circles**: Accepting states

### Tree Diagrams

**Nodes**: Tree nodes (labeled with symbols)

**Edges**: Parent-child relationships

**Root**: Top node

**Leaves**: Bottom nodes (terminals)

## Documentation Conventions

### Cross-References

**Chapter References**: `[Chapter Name](path/to/chapter.md)`

**Section References**: `[Section Name](#section-name)`

**Code References**: `` `ClassName` `` or `` `methodName()` ``

### Code Blocks

**Java Code**:
```java
public class Example {
    public void method() {
        // Implementation
    }
}
```

**C Code**:
```c
int main(void) {
    return 0;
}
```

**FRISC Assembly**:
```assembly
MOVE 40000, R7
CALL F_MAIN
HALT
```

**Pseudocode**:
```text
function algorithm(input):
    // Algorithm steps
    return result
```

## Terminology

### Compiler Phases

1. **Lexical Analysis**: Tokenization
2. **Syntax Analysis**: Parsing
3. **Semantic Analysis**: Type checking, scope resolution
4. **Code Generation**: Assembly generation

### Data Structures

**Parse Tree**: Complete derivation tree

**Abstract Syntax Tree (AST)**: Simplified semantic tree

**Symbol Table**: Identifier-to-symbol mapping

**Activation Record**: Function call stack frame

## Further Reading

- **[Glossary](glossary.md)**: Complete term definitions
- **[Bibliography](bibliography-and-further-reading.md)**: References and resources

---

*These conventions ensure consistency across all compiler documentation and code.*
