# Struct Grammar and Parsing

## Overview

Struct types are a fundamental feature of the PPJ-C language, allowing programmers to define composite data types with named fields. The parser handles struct declarations, definitions, and member access expressions through a carefully designed grammar that supports both tagged and anonymous structs, nested structures, and complex field access patterns.

This chapter describes the complete grammar rules for struct types, how the parser recognizes and constructs abstract syntax trees for struct-related constructs, and the AST representation used throughout the compilation pipeline.

## Grammar Rules for Struct Types

The PPJ-C grammar defines struct types through several interconnected production rules that handle struct declarations, field lists, and member access. These rules integrate seamlessly with the broader type system and expression grammar.

### Struct Type Specifiers

The grammar recognizes three forms of struct type specifiers:

```bnf
<struct_specifikator> ::= KR_STRUCT IDN L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
                         | KR_STRUCT L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
                         | KR_STRUCT IDN
```

**Production 1: Tagged Struct Definition**
```
KR_STRUCT IDN L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
```
This production defines a struct with a tag name. The tag allows the struct type to be referenced later without repeating the field list. Example: `struct Point { int x; int y; }`

**Production 2: Anonymous Struct Definition**
```
KR_STRUCT L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
```
This production defines a struct without a tag name. Anonymous structs can only be used immediately in variable declarations and cannot be referenced later. Example: `struct { int x; int y; } p;`

**Production 3: Tagged Struct Reference**
```
KR_STRUCT IDN
```
This production references a previously defined struct by its tag name. The struct must have been defined earlier in the translation unit. Example: `struct Point p;`

### Struct Field Declarations

Struct fields are declared using a list structure that mirrors variable declarations but with restrictions:

```bnf
<struct_lista_deklaracija> ::= <struct_deklaracija>
                              | <struct_lista_deklaracija> <struct_deklaracija>

<struct_deklaracija> ::= <lista_specifikatora_kvalifikatora> <struct_lista_deklaratora> TOCKAZAREZ

<struct_lista_deklaratora> ::= <struct_deklarator>
                              | <struct_lista_deklaratora> ZAREZ <struct_deklarator>

<struct_deklarator> ::= IDN
                       | IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
                       | <pokazivac> IDN
                       | <pokazivac> IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
```

The field declaration grammar allows:
- Simple fields: `int x;`
- Array fields: `int arr[10];`
- Pointer fields: `int *ptr;`
- Pointer-to-array fields: `int *arr[5];`

Fields are separated by commas within a single declaration and terminated by semicolons. Multiple declarations can appear in sequence within a struct definition.

### Struct Member Access

Struct member access is integrated into the postfix expression grammar:

```bnf
<postfiks_izraz> ::= <primarni_izraz>
                   | <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
                   | <postfiks_izraz> L_ZAGRADA D_ZAGRADA
                   | <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
                   | <postfiks_izraz> TOCKA IDN
                   | <postfiks_izraz> OP_INC
                   | <postfiks_izraz> OP_DEC
```

The production `<postfiks_izraz> TOCKA IDN` handles struct member access. The dot operator (`.`) has higher precedence than array indexing and function calls, allowing expressions like `p.arr[i]` and `p.func()` to be parsed correctly.

**Precedence and Associativity**: The dot operator is left-associative, enabling chained member access: `outer.inner.value` is parsed as `(outer.inner).value`.

## AST Representation

The parser constructs abstract syntax tree nodes for struct-related constructs that preserve the essential structure while abstracting away syntactic details.

### Struct Type Nodes

Struct type specifiers are represented in the AST using the `StructType` record:

```java
public record StructType(
    String name,  // struct tag name (null for anonymous structs)
    int line,
    int column
) implements Type {}
```

For tagged struct definitions, the `name` field contains the tag identifier. For anonymous structs, `name` is `null`. The line and column information is preserved for error reporting.

### Struct Declaration Nodes

Complete struct definitions are represented using `StructDeclaration`:

```java
public record StructDeclaration(
    String name,                    // struct tag (null if anonymous)
    List<VariableDeclaration> fields,
    int line,
    int column
) implements Declaration {}
```

The `fields` list contains `VariableDeclaration` nodes representing each field. This reuse of the variable declaration AST structure reflects the similarity between struct field declarations and variable declarations.

### Member Access Expression Nodes

Struct member access expressions appear in the AST as postfix expression nodes with a specific structure:

```
<postfiks_izraz> (TOCKA IDN)
├── <postfiks_izraz> (base expression)
├── TOCKA (terminal)
└── IDN (field name terminal)
```

The base expression can be:
- A simple identifier (variable name)
- Another member access (nested access)
- An array element access
- A function call result

This recursive structure naturally handles nested member access like `outer.middle.inner.value`.

## Parsing Examples

### Example 1: Simple Struct Definition

**Source Code:**
```c
struct Point {
    int x;
    int y;
};
```

**Parse Tree Structure:**
```
<struct_specifikator>
├── KR_STRUCT
├── IDN(Point)
├── L_VIT_ZAGRADA
├── <struct_lista_deklaracija>
│   └── <struct_deklaracija>
│       ├── <lista_specifikatora_kvalifikatora>
│       │   └── KR_INT
│       ├── <struct_lista_deklaratora>
│       │   └── IDN(x)
│       └── TOCKAZAREZ
├── D_VIT_ZAGRADA
```

**AST Representation:**
```
StructDeclaration
├── name: "Point"
├── fields:
│   ├── VariableDeclaration
│   │   ├── type: PrimitiveType.INT
│   │   └── name: "x"
│   └── VariableDeclaration
│       ├── type: PrimitiveType.INT
│       └── name: "y"
```

### Example 2: Nested Member Access

**Source Code:**
```c
struct Outer {
    struct Inner {
        int value;
    } inner;
    int data;
};

int main(void) {
    struct Outer o;
    o.inner.value = 42;
    return 0;
}
```

**Parse Tree for `o.inner.value`:**
```
<postfiks_izraz> (TOCKA IDN)
├── <postfiks_izraz> (TOCKA IDN)
│   ├── <primarni_izraz>
│   │   └── IDN(o)
│   ├── TOCKA
│   └── IDN(inner)
├── TOCKA
└── IDN(value)
```

The parser correctly recognizes this as two chained member accesses: first `o.inner`, then `.value` applied to the result.

### Example 3: Array Field Access

**Source Code:**
```c
struct Data {
    int arr[10];
};

int main(void) {
    struct Data d;
    d.arr[0] = 5;
    return 0;
}
```

**Parse Tree for `d.arr[0]`:**
```
<postfiks_izraz> (L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA)
├── <postfiks_izraz> (TOCKA IDN)
│   ├── <primarni_izraz>
│   │   └── IDN(d)
│   ├── TOCKA
│   └── IDN(arr)
├── L_UGL_ZAGRADA
├── <izraz>
│   └── BROJ(0)
└── D_UGL_ZAGRADA
```

The parser correctly recognizes that `d.arr` is evaluated first (member access), then `[0]` is applied (array indexing). This reflects the left-to-right associativity and precedence rules.

## Integration with Type System

Struct type specifiers integrate with the broader type system through the `<specifikator_tipa>` production:

```bnf
<specifikator_tipa> ::= KR_VOID
                      | KR_CHAR
                      | KR_INT
                      | KR_FLOAT
                      | <struct_specifikator>
```

This allows struct types to appear anywhere a type specifier is expected:
- Variable declarations: `struct Point p;`
- Function parameters: `void f(struct Point p);`
- Function return types: `struct Point makePoint(void);`
- Pointer types: `struct Point *ptr;`
- Array element types: `struct Point arr[10];`

## Parsing Algorithm Details

The LR(1) parser handles struct-related constructs through its standard shift-reduce algorithm. The parser state machine includes approximately 823 states, with many states dedicated to handling the various struct declaration and access patterns.

**Key Parsing Challenges:**

1. **Forward References**: Tagged struct references (`struct Tag`) must be resolved to previously defined structs. The parser defers this resolution to semantic analysis, but the grammar ensures that struct tags are recognized as identifiers.

2. **Nested Structures**: Struct definitions can contain other struct types as field types. The parser handles this recursively, processing nested struct definitions within the field declaration list.

3. **Member Access Precedence**: The dot operator must have correct precedence relative to array indexing and function calls. The grammar ensures that `a.b[i]` is parsed as `(a.b)[i]` and `a.func()` is parsed as `(a.func)()`.

4. **Anonymous vs. Tagged**: The parser distinguishes between anonymous and tagged structs based on the presence of an identifier between `KR_STRUCT` and `L_VIT_ZAGRADA`. This requires lookahead to determine which production to use.

## Error Handling

The parser detects syntax errors in struct-related constructs:

- **Missing braces**: `struct Point { int x;` (missing closing brace)
- **Missing semicolons**: `struct Point { int x int y; }` (missing semicolon after `x`)
- **Invalid field declarations**: `struct Point { void x; }` (void fields not allowed)
- **Malformed member access**: `p.` (missing field name)

When syntax errors are detected, the parser attempts error recovery using synchronization tokens (typically `TOCKAZAREZ`, `D_VIT_ZAGRADA`, and statement terminators) to continue parsing the rest of the program.

## Summary

The struct grammar in PPJ-C provides comprehensive support for:
- Tagged and anonymous struct definitions
- Nested struct types
- Array and pointer fields
- Chained member access expressions
- Integration with the broader type system

The parser constructs AST nodes that preserve the essential structure while abstracting away syntactic details, enabling efficient semantic analysis and code generation in later compilation phases.
