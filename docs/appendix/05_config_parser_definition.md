# Appendix E. Parser Grammar Reference

> **📖 From the book.** This chapter accompanies *Building a C-Subset Compiler for the FRISC Architecture: From Formal Languages to Executable Code* by Dr. Karlo Knežević (Zenodo, 2026). For the complete treatment — formal development, proofs, and figures — read the book: [📄 PDF](../book/Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf) · DOI [10.5281/zenodo.20511073](https://doi.org/10.5281/zenodo.20511073) · ISBN 978-953-47198-0-0.


This appendix provides a complete reference for the FRISCcc parser grammar
(`config/parser_definition.txt`). The grammar is a context-free grammar (CFG)
expressed in a BNF-like notation. It defines the syntactic structure of the
C-subset language accepted by FRISCcc and drives the parser generator, which
constructs the parsing tables used during syntax analysis.

## E.1 Grammar Format

The parser definition file begins with three header directives, followed by the
production rules.

### Nonterminal Declarations (`%V`)

The `%V` line lists all nonterminal symbols in angle brackets. These are the
syntactic categories of the grammar. Nonterminal names are written in Croatian;
English translations are provided in Section E.2.

### Terminal Declarations (`%T`)

The `%T` line lists all terminal symbols (token types). These must correspond
exactly to the tokens declared in the lexer specification (`%L` line in
`config/lexer_definition.txt`).

### Synchronization Tokens (`%Syn`)

The `%Syn` line declares tokens used for panic-mode error recovery. When the
parser encounters a syntax error, it discards input tokens until one of the
synchronization tokens is found, then attempts to resume parsing. The
synchronization tokens are `TOCKAZAREZ` (`;`) and `D_VIT_ZAGRADA` (`}`),
corresponding to statement boundaries and block boundaries respectively.

### Production Rules

Each nonterminal heading is followed by one or more alternative productions,
each on its own line and indented by a single space. Productions with the same
left-hand side are grouped under a single nonterminal heading, representing
alternative right-hand sides separated implicitly by newlines.

## E.2 Nonterminal Symbols

The grammar defines 48 nonterminal symbols. The following table provides the
Croatian name, an English translation, and a description of each nonterminal.

| Nonterminal | English | Purpose |
|---|---|---|
| `<prijevodna_jedinica>` | translation unit | Top-level: a sequence of external declarations |
| `<vanjska_deklaracija>` | external declaration | Either a function definition or a declaration |
| `<deklaracija>` | declaration | Variable or type declaration with optional initializers |
| `<definicija_funkcije>` | function definition | Function with specifiers, declarator, and body |
| `<specifikatori_deklaracije>` | declaration specifiers | Type specifiers and qualifiers (e.g., `const int`) |
| `<deklarator>` | declarator | Name being declared, possibly with pointer prefix |
| `<primarni_izraz>` | primary expression | Leaf expressions: identifiers, literals, parenthesized exprs |
| `<izraz>` | expression | Comma expression (lowest precedence) |
| `<postfiks_izraz>` | postfix expression | Array subscript, function call, member access, `++`/`--` |
| `<lista_argumenata>` | argument list | Comma-separated function call arguments |
| `<izraz_pridruzivanja>` | assignment expression | Assignment or pass-through to logical-OR |
| `<unarni_izraz>` | unary expression | Prefix `++`/`--`, unary operator application |
| `<unarni_operator>` | unary operator | One of `&`, `*`, `+`, `-`, `~`, `!` |
| `<cast_izraz>` | cast expression | Type cast or pass-through to unary expression |
| `<ime_tipa>` | type name | Abstract type (used in cast expressions) |
| `<multiplikativni_izraz>` | multiplicative expression | Multiplication, division, modulo |
| `<aditivni_izraz>` | additive expression | Addition, subtraction |
| `<odnosni_izraz>` | relational expression | `<`, `>`, `<=`, `>=` comparisons |
| `<jednakosni_izraz>` | equality expression | `==`, `!=` comparisons |
| `<bin_i_izraz>` | bitwise AND expression | Binary `&` operation |
| `<bin_xili_izraz>` | bitwise XOR expression | Binary `^` operation |
| `<bin_ili_izraz>` | bitwise OR expression | Binary `\|` operation |
| `<log_i_izraz>` | logical AND expression | Short-circuit `&&` |
| `<log_ili_izraz>` | logical OR expression | Short-circuit `\|\|` |
| `<specifikator_tipa>` | type specifier | `void`, `char`, `int`, `float`, or struct specifier |
| `<lista_init_deklaratora>` | init-declarator list | Comma-separated declarators with optional initializers |
| `<init_deklarator>` | init-declarator | A single declarator, optionally `= initializer` |
| `<inicijalizator>` | initializer | Expression or brace-enclosed initializer list |
| `<struct_specifikator>` | struct specifier | Struct definition or forward reference |
| `<struct_lista_deklaracija>` | struct declaration list | Sequence of member declarations within a struct body |
| `<struct_deklaracija>` | struct declaration | A single member declaration line |
| `<lista_specifikatora_kvalifikatora>` | specifier-qualifier list | Type specifiers and `const` qualifiers (no storage class) |
| `<struct_lista_deklaratora>` | struct declarator list | Comma-separated member declarators |
| `<struct_deklarator>` | struct declarator | A single member declarator |
| `<pokazivac>` | pointer | `*` or `* const` prefix on a declarator |
| `<izravni_deklarator>` | direct declarator | Identifier, array declarator, or function declarator |
| `<lista_parametara>` | parameter list | Comma-separated function parameter declarations |
| `<deklaracija_parametra>` | parameter declaration | Type specifiers with optional declarator |
| `<lista_izraza_pridruzivanja>` | assignment expression list | Comma-separated values in brace initializers |
| `<naredba>` | statement | Any statement (compound, expression, branch, loop, jump) |
| `<slozena_naredba>` | compound statement | Block: `{ declarations statements }` |
| `<izraz_naredba>` | expression statement | Expression followed by `;`, or empty `;` |
| `<naredba_grananja>` | branching statement | `if` / `if-else` |
| `<naredba_petlje>` | loop statement | `while` / `for` |
| `<naredba_skoka>` | jump statement | `continue`, `break`, `return` |
| `<lista_naredbi>` | statement list | Sequence of statements within a block |
| `<lista_deklaracija>` | declaration list | Sequence of declarations within a block |

## E.3 Expression Precedence Hierarchy

The grammar encodes operator precedence through a chain of nonterminals, where
each level delegates to the next-higher-precedence level as its base case. The
following table lists all 13 precedence levels from lowest to highest, together
with the corresponding nonterminal, operators, and associativity.

| Level | Nonterminal | Operators | Associativity |
|---|---|---|---|
| 1 | `<izraz>` | `,` (comma) | Left-to-right |
| 2 | `<izraz_pridruzivanja>` | `=` (assignment) | Right-to-left |
| 3 | `<log_ili_izraz>` | `\|\|` (logical OR) | Left-to-right |
| 4 | `<log_i_izraz>` | `&&` (logical AND) | Left-to-right |
| 5 | `<bin_ili_izraz>` | `\|` (bitwise OR) | Left-to-right |
| 6 | `<bin_xili_izraz>` | `^` (bitwise XOR) | Left-to-right |
| 7 | `<bin_i_izraz>` | `&` (bitwise AND) | Left-to-right |
| 8 | `<jednakosni_izraz>` | `==`, `!=` | Left-to-right |
| 9 | `<odnosni_izraz>` | `<`, `>`, `<=`, `>=` | Left-to-right |
| 10 | `<aditivni_izraz>` | `+`, `-` | Left-to-right |
| 11 | `<multiplikativni_izraz>` | `*`, `/`, `%` | Left-to-right |
| 12 | `<cast_izraz>` | `(type)` (type cast) | Right-to-left |
| 13 | `<unarni_izraz>` | `++`, `--`, `&`, `*`, `+`, `-`, `~`, `!` | Right-to-left |

Postfix operators (array subscript, function call, member access, postfix
`++`/`--`) are handled by `<postfiks_izraz>`, which has the highest effective
precedence. Primary expressions (identifiers, literals, parenthesized
expressions) form the base of the hierarchy in `<primarni_izraz>`.

Left-to-right associativity is encoded via left-recursive productions (e.g.,
`<aditivni_izraz> PLUS <multiplikativni_izraz>`), while right-to-left
associativity is encoded via right-recursive productions (e.g.,
`<unarni_izraz> OP_PRIDRUZI <izraz_pridruzivanja>`).

## E.4 Grammar Productions by Category

### E.4.1 Expressions

**Primary expression** -- the atomic building blocks:

```
<primarni_izraz>
  IDN
  BROJ
  ZNAK
  NIZ_ZNAKOVA
  L_ZAGRADA <izraz> D_ZAGRADA
```

**Postfix expression** -- array subscript, function call, member access,
post-increment/decrement:

```
<postfiks_izraz>
  <primarni_izraz>
  <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
  <postfiks_izraz> L_ZAGRADA D_ZAGRADA
  <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
  <postfiks_izraz> TOCKA IDN
  <postfiks_izraz> OP_INC
  <postfiks_izraz> OP_DEC
```

**Argument list**:

```
<lista_argumenata>
  <izraz_pridruzivanja>
  <lista_argumenata> ZAREZ <izraz_pridruzivanja>
```

**Unary expression** and **unary operators**:

```
<unarni_izraz>
  <postfiks_izraz>
  OP_INC <unarni_izraz>
  OP_DEC <unarni_izraz>
  <unarni_operator> <cast_izraz>

<unarni_operator>
  AMPERSAND
  ASTERISK
  PLUS
  MINUS
  OP_TILDA
  OP_NEG
```

**Cast expression**:

```
<cast_izraz>
  <unarni_izraz>
  L_ZAGRADA <ime_tipa> D_ZAGRADA <cast_izraz>
```

**Binary expressions** -- the precedence chain from multiplicative through
logical OR:

```
<multiplikativni_izraz>
  <cast_izraz>
  <multiplikativni_izraz> ASTERISK <cast_izraz>
  <multiplikativni_izraz> OP_DIJELI <cast_izraz>
  <multiplikativni_izraz> OP_MOD <cast_izraz>

<aditivni_izraz>
  <multiplikativni_izraz>
  <aditivni_izraz> PLUS <multiplikativni_izraz>
  <aditivni_izraz> MINUS <multiplikativni_izraz>

<odnosni_izraz>
  <aditivni_izraz>
  <odnosni_izraz> OP_LT <aditivni_izraz>
  <odnosni_izraz> OP_GT <aditivni_izraz>
  <odnosni_izraz> OP_LTE <aditivni_izraz>
  <odnosni_izraz> OP_GTE <aditivni_izraz>

<jednakosni_izraz>
  <odnosni_izraz>
  <jednakosni_izraz> OP_EQ <odnosni_izraz>
  <jednakosni_izraz> OP_NEQ <odnosni_izraz>

<bin_i_izraz>
  <jednakosni_izraz>
  <bin_i_izraz> AMPERSAND <jednakosni_izraz>

<bin_xili_izraz>
  <bin_i_izraz>
  <bin_xili_izraz> OP_BIN_XILI <bin_i_izraz>

<bin_ili_izraz>
  <bin_xili_izraz>
  <bin_ili_izraz> OP_BIN_ILI <bin_xili_izraz>

<log_i_izraz>
  <bin_ili_izraz>
  <log_i_izraz> OP_I <bin_ili_izraz>

<log_ili_izraz>
  <log_i_izraz>
  <log_ili_izraz> OP_ILI <log_i_izraz>
```

**Assignment expression** and **comma expression**:

```
<izraz_pridruzivanja>
  <log_ili_izraz>
  <unarni_izraz> OP_PRIDRUZI <izraz_pridruzivanja>

<izraz>
  <izraz_pridruzivanja>
  <izraz> ZAREZ <izraz_pridruzivanja>
```

Assignment is right-recursive (right-to-left associativity), allowing chained
assignments such as `a = b = 0`.

### E.4.2 Declarations

**Declaration** -- a type specifier followed by an optional list of declarators:

```
<deklaracija>
  <specifikatori_deklaracije> TOCKAZAREZ
  <specifikatori_deklaracije> <lista_init_deklaratora> TOCKAZAREZ

<specifikatori_deklaracije>
  <specifikator_tipa>
  <specifikator_tipa> <specifikatori_deklaracije>
  KR_CONST
  KR_CONST <specifikatori_deklaracije>
```

The recursive structure of `<specifikatori_deklaracije>` allows sequences such
as `const int` or `int const`.

**Init-declarator list**:

```
<lista_init_deklaratora>
  <init_deklarator>
  <lista_init_deklaratora> ZAREZ <init_deklarator>

<init_deklarator>
  <deklarator>
  <deklarator> OP_PRIDRUZI <inicijalizator>
```

**Type specifier**:

```
<specifikator_tipa>
  KR_VOID
  KR_CHAR
  KR_INT
  KR_FLOAT
  <struct_specifikator>
```

**Declarator and direct declarator**:

```
<deklarator>
  <pokazivac> <izravni_deklarator>
  <izravni_deklarator>

<izravni_deklarator>
  IDN
  <izravni_deklarator> L_UGL_ZAGRADA <log_ili_izraz> D_UGL_ZAGRADA
  <izravni_deklarator> L_UGL_ZAGRADA D_UGL_ZAGRADA
  <izravni_deklarator> L_ZAGRADA <lista_parametara> D_ZAGRADA

<pokazivac>
  ASTERISK
  ASTERISK KR_CONST
```

**Parameter list** and **parameter declaration**:

```
<lista_parametara>
  <deklaracija_parametra>
  <lista_parametara> ZAREZ <deklaracija_parametra>

<deklaracija_parametra>
  <specifikatori_deklaracije> <deklarator>
  <specifikatori_deklaracije>
```

**Type name** (used in cast expressions):

```
<ime_tipa>
  <lista_specifikatora_kvalifikatora>
  <lista_specifikatora_kvalifikatora> <pokazivac>
```

**Initializer** and **initializer list**:

```
<inicijalizator>
  <izraz_pridruzivanja>
  L_VIT_ZAGRADA <lista_izraza_pridruzivanja> D_VIT_ZAGRADA
  L_VIT_ZAGRADA <lista_izraza_pridruzivanja> ZAREZ D_VIT_ZAGRADA

<lista_izraza_pridruzivanja>
  <izraz_pridruzivanja>
  <lista_izraza_pridruzivanja> ZAREZ <izraz_pridruzivanja>
```

The trailing comma in `{ expr_list , }` is explicitly permitted by the third
initializer production, matching standard C behavior.

### E.4.3 Statements

```
<naredba>
  <slozena_naredba>
  <izraz_naredba>
  <naredba_grananja>
  <naredba_petlje>
  <naredba_skoka>
```

**Compound statement** (block):

```
<slozena_naredba>
  L_VIT_ZAGRADA D_VIT_ZAGRADA
  L_VIT_ZAGRADA <lista_naredbi> D_VIT_ZAGRADA
  L_VIT_ZAGRADA <lista_deklaracija> D_VIT_ZAGRADA
  L_VIT_ZAGRADA <lista_deklaracija> <lista_naredbi> D_VIT_ZAGRADA
```

Declarations must appear before statements within a block (C89 style). The
grammar enforces this by requiring `<lista_deklaracija>` to precede
`<lista_naredbi>`.

**Declaration and statement lists**:

```
<lista_deklaracija>
  <deklaracija>
  <lista_deklaracija> <deklaracija>

<lista_naredbi>
  <naredba>
  <lista_naredbi> <naredba>
```

**Expression statement**:

```
<izraz_naredba>
  TOCKAZAREZ
  <izraz> TOCKAZAREZ
```

**Branching statement** (`if`/`if-else`):

```
<naredba_grananja>
  KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba>
  KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba> KR_ELSE <naredba>
```

The dangling-else ambiguity is resolved by the parser in favor of the closest
`if`, matching standard C semantics.

**Loop statement** (`while`, `for`):

```
<naredba_petlje>
  KR_WHILE L_ZAGRADA <izraz> D_ZAGRADA <naredba>
  KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> D_ZAGRADA <naredba>
  KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> <izraz> D_ZAGRADA <naredba>
```

The `for` loop has two forms: one without an update expression and one with it.

**Jump statement**:

```
<naredba_skoka>
  KR_CONTINUE TOCKAZAREZ
  KR_BREAK TOCKAZAREZ
  KR_RETURN TOCKAZAREZ
  KR_RETURN <izraz> TOCKAZAREZ
```

### E.4.4 Struct Definitions

```
<struct_specifikator>
  KR_STRUCT IDN L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
  KR_STRUCT L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
  KR_STRUCT IDN
```

The three productions correspond to (1) a named struct definition, (2) an
anonymous struct definition, and (3) a reference to a previously defined struct.

**Struct body**:

```
<struct_lista_deklaracija>
  <struct_deklaracija>
  <struct_lista_deklaracija> <struct_deklaracija>

<struct_deklaracija>
  <lista_specifikatora_kvalifikatora> <struct_lista_deklaratora> TOCKAZAREZ

<lista_specifikatora_kvalifikatora>
  <specifikator_tipa> <lista_specifikatora_kvalifikatora>
  <specifikator_tipa>
  KR_CONST <lista_specifikatora_kvalifikatora>
  KR_CONST

<struct_lista_deklaratora>
  <struct_deklarator>
  <struct_lista_deklaratora> ZAREZ <struct_deklarator>

<struct_deklarator>
  <deklarator>
```

### E.4.5 Function Definitions and Translation Unit

**Translation unit** -- the top-level structure of a source file:

```
<prijevodna_jedinica>
  <vanjska_deklaracija>
  <prijevodna_jedinica> <vanjska_deklaracija>

<vanjska_deklaracija>
  <definicija_funkcije>
  <deklaracija>
```

A translation unit is a left-recursive sequence of external declarations, where
each external declaration is either a function definition or a declaration.

**Function definition**:

```
<definicija_funkcije>
  <specifikatori_deklaracije> <deklarator> <lista_deklaracija> <slozena_naredba>
  <specifikatori_deklaracije> <deklarator> <slozena_naredba>
  <deklarator> <lista_deklaracija> <slozena_naredba>
  <deklarator> <slozena_naredba>
```

The four productions accommodate: (1) a fully specified function with old-style
parameter declarations, (2) a fully specified function with modern-style
parameters, (3) an implicitly-typed function with old-style parameter
declarations, and (4) an implicitly-typed function. Productions (3) and (4)
correspond to functions without an explicit return type, which defaults to `int`
per C89 rules.

## E.5 Grammar Statistics

| Metric | Value |
|---|---|
| Nonterminal symbols | 48 |
| Terminal symbols | 47 |
| Total productions | 97 |
| Expression nonterminals | 16 |
| Declaration nonterminals | 16 |
| Statement nonterminals | 8 |
| Struct-related nonterminals | 7 |
| Top-level nonterminals | 3 |
| Synchronization tokens | 2 (`;`, `}`) |

## E.6 Complete Grammar Listing

The following is the verbatim content of `config/parser_definition.txt`.

```
%V <prijevodna_jedinica> <vanjska_deklaracija> <deklaracija> <definicija_funkcije> <specifikatori_deklaracije> <deklarator> <primarni_izraz> <izraz> <postfiks_izraz> <lista_argumenata> <izraz_pridruzivanja> <unarni_izraz> <unarni_operator> <cast_izraz> <ime_tipa> <multiplikativni_izraz> <aditivni_izraz> <odnosni_izraz> <jednakosni_izraz> <bin_i_izraz> <bin_xili_izraz> <bin_ili_izraz> <log_i_izraz> <log_ili_izraz> <specifikator_tipa> <lista_init_deklaratora> <init_deklarator> <inicijalizator> <struct_specifikator> <struct_lista_deklaracija> <struct_deklaracija> <lista_specifikatora_kvalifikatora> <struct_lista_deklaratora> <struct_deklarator> <pokazivac> <izravni_deklarator> <lista_parametara> <deklaracija_parametra> <lista_izraza_pridruzivanja> <naredba> <slozena_naredba> <izraz_naredba> <naredba_grananja> <naredba_petlje> <naredba_skoka> <lista_naredbi> <lista_deklaracija>
%T IDN BROJ ZNAK NIZ_ZNAKOVA KR_BREAK KR_CHAR KR_CONST KR_CONTINUE KR_ELSE KR_FLOAT KR_FOR KR_IF KR_INT KR_RETURN KR_STRUCT KR_VOID KR_WHILE PLUS OP_INC MINUS OP_DEC ASTERISK OP_DIJELI OP_MOD OP_PRIDRUZI OP_LT OP_LTE OP_GT OP_GTE OP_EQ OP_NEQ OP_NEG OP_TILDA OP_I OP_ILI AMPERSAND OP_BIN_ILI OP_BIN_XILI ZAREZ TOCKAZAREZ TOCKA L_ZAGRADA D_ZAGRADA L_UGL_ZAGRADA D_UGL_ZAGRADA L_VIT_ZAGRADA D_VIT_ZAGRADA
%Syn TOCKAZAREZ D_VIT_ZAGRADA
<primarni_izraz>
 IDN
 BROJ
 ZNAK
 NIZ_ZNAKOVA
 L_ZAGRADA <izraz> D_ZAGRADA
<postfiks_izraz>
 <primarni_izraz>
 <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
 <postfiks_izraz> L_ZAGRADA D_ZAGRADA
 <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
 <postfiks_izraz> TOCKA IDN
 <postfiks_izraz> OP_INC
 <postfiks_izraz> OP_DEC
<lista_argumenata>
 <izraz_pridruzivanja>
 <lista_argumenata> ZAREZ <izraz_pridruzivanja>
<unarni_izraz>
 <postfiks_izraz>
 OP_INC <unarni_izraz>
 OP_DEC <unarni_izraz>
 <unarni_operator> <cast_izraz>
<unarni_operator>
 AMPERSAND
 ASTERISK
 PLUS
 MINUS
 OP_TILDA
 OP_NEG
<cast_izraz>
 <unarni_izraz>
 L_ZAGRADA <ime_tipa> D_ZAGRADA <cast_izraz>
<multiplikativni_izraz>
 <cast_izraz>
 <multiplikativni_izraz> ASTERISK <cast_izraz>
 <multiplikativni_izraz> OP_DIJELI <cast_izraz>
 <multiplikativni_izraz> OP_MOD <cast_izraz>
<aditivni_izraz>
 <multiplikativni_izraz>
 <aditivni_izraz> PLUS <multiplikativni_izraz>
 <aditivni_izraz> MINUS <multiplikativni_izraz>
<odnosni_izraz>
 <aditivni_izraz>
 <odnosni_izraz> OP_LT <aditivni_izraz>
 <odnosni_izraz> OP_GT <aditivni_izraz>
 <odnosni_izraz> OP_LTE <aditivni_izraz>
 <odnosni_izraz> OP_GTE <aditivni_izraz>
<jednakosni_izraz>
 <odnosni_izraz>
 <jednakosni_izraz> OP_EQ <odnosni_izraz>
 <jednakosni_izraz> OP_NEQ <odnosni_izraz>
<bin_i_izraz>
 <jednakosni_izraz>
 <bin_i_izraz> AMPERSAND <jednakosni_izraz>
<bin_xili_izraz>
 <bin_i_izraz>
 <bin_xili_izraz> OP_BIN_XILI <bin_i_izraz>
<bin_ili_izraz>
 <bin_xili_izraz>
 <bin_ili_izraz> OP_BIN_ILI <bin_xili_izraz>
<log_i_izraz>
 <bin_ili_izraz>
 <log_i_izraz> OP_I <bin_ili_izraz>
<log_ili_izraz>
 <log_i_izraz>
 <log_ili_izraz> OP_ILI <log_i_izraz>
<izraz_pridruzivanja>
 <log_oli_izraz>
 <unarni_izraz> OP_PRIDRUZI <izraz_pridruzivanja>
<izraz>
 <izraz_pridruzivanja>
 <izraz> ZAREZ <izraz_pridruzivanja>
<deklaracija>
 <specifikatori_deklaracije> TOCKAZAREZ
 <specifikatori_deklaracije> <lista_init_deklaratora> TOCKAZAREZ
<specifikatori_deklaracije>
 <specifikator_tipa>
 <specifikator_tipa> <specifikatori_deklaracije>
 KR_CONST
 KR_CONST <specifikatori_deklaracije>
<lista_init_deklaratora>
 <init_deklarator>
 <lista_init_deklaratora> ZAREZ <init_deklarator>
<init_deklarator>
 <deklarator>
 <deklarator> OP_PRIDRUZI <inicijalizator>
<specifikator_tipa>
 KR_VOID
 KR_CHAR
 KR_INT
 KR_FLOAT
 <struct_specifikator>
<struct_specifikator>
 KR_STRUCT IDN L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
 KR_STRUCT L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
 KR_STRUCT IDN
<struct_lista_deklaracija>
 <struct_deklaracija>
 <struct_lista_deklaracija> <struct_deklaracija>
<struct_deklaracija>
 <lista_specifikatora_kvalifikatora> <struct_lista_deklaratora> TOCKAZAREZ
<lista_specifikatora_kvalifikatora>
 <specifikator_tipa> <lista_specifikatora_kvalifikatora>
 <specifikator_tipa>
 KR_CONST <lista_specifikatora_kvalifikatora>
 KR_CONST
<struct_lista_deklaratora>
 <struct_deklarator>
 <struct_lista_deklaratora> ZAREZ <struct_deklarator>
<struct_deklarator>
 <deklarator>
<deklarator>
 <pokazivac> <izravni_deklarator>
 <izravni_deklarator>
<izravni_deklarator>
 IDN
 <izravni_deklarator> L_UGL_ZAGRADA <log_ili_izraz> D_UGL_ZAGRADA
 <izravni_deklarator> L_UGL_ZAGRADA D_UGL_ZAGRADA
 <izravni_deklarator> L_ZAGRADA <lista_parametara> D_ZAGRADA
<pokazivac>
 ASTERISK
 ASTERISK KR_CONST
<lista_parametara>
 <deklaracija_parametra>
 <lista_parametara> ZAREZ <deklaracija_parametra>
<deklaracija_parametra>
 <specifikatori_deklaracije> <deklarator>
 <specifikatori_deklaracije>
<ime_tipa>
 <lista_specifikatora_kvalifikatora>
 <lista_specifikatora_kvalifikatora> <pokazivac>
<inicijalizator>
 <izraz_pridruzivanja>
 L_VIT_ZAGRADA <lista_izraza_pridruzivanja> D_VIT_ZAGRADA
 L_VIT_ZAGRADA <lista_izraza_pridruzivanja> ZAREZ D_VIT_ZAGRADA
<lista_izraza_pridruzivanja>
 <izraz_pridruzivanja>
 <lista_izraza_pridruzivanja> ZAREZ <izraz_pridruzivanja>
<naredba>
 <slozena_naredba>
 <izraz_naredba>
 <naredba_grananja>
 <naredba_petlje>
 <naredba_skoka>
<slozena_naredba>
 L_VIT_ZAGRADA D_VIT_ZAGRADA
 L_VIT_ZAGRADA <lista_naredbi> D_VIT_ZAGRADA
 L_VIT_ZAGRADA <lista_deklaracija> D_VIT_ZAGRADA
 L_VIT_ZAGRADA <lista_deklaracija> <lista_naredbi> D_VIT_ZAGRADA
<lista_deklaracija>
 <deklaracija>
 <lista_deklaracija> <deklaracija>
<lista_naredbi>
 <naredba>
 <lista_naredbi> <naredba>
<izraz_naredba>
 TOCKAZAREZ
 <izraz> TOCKAZAREZ
<naredba_grananja>
 KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba>
 KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba> KR_ELSE <naredba>
<naredba_petlje>
 KR_WHILE L_ZAGRADA <izraz> D_ZAGRADA <naredba>
 KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> D_ZAGRADA <naredba>
 KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> <izraz> D_ZAGRADA <naredba>
<naredba_skoka>
 KR_CONTINUE TOCKAZAREZ
 KR_BREAK TOCKAZAREZ
 KR_RETURN TOCKAZAREZ
 KR_RETURN <izraz> TOCKAZAREZ
<prijevodna_jedinica>
 <vanjska_deklaracija>
 <prijevodna_jedinica> <vanjska_deklaracija>
<vanjska_deklaracija>
 <definicija_funkcije>
 <deklaracija>
<definicija_funkcije>
 <specifikatori_deklaracije> <deklarator> <lista_deklaracija> <slozena_naredba>
 <specifikatori_deklaracije> <deklarator> <slozena_naredba>
 <deklarator> <lista_deklaracija> <slozena_naredba>
 <deklarator> <slozena_naredba>
```
