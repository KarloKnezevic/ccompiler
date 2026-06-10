# Appendix F. Semantics Definition Reference

> **📖 From the book.** This chapter accompanies *Building a C-Subset Compiler for the FRISC Architecture: From Formal Languages to Executable Code* by Dr. Karlo Knežević (Zenodo, 2026). For the complete treatment — formal development, proofs, and figures — read the book: [📄 PDF](../book/Building-a-C-Subset-Compiler-for-the-FRISC-Architecture.pdf) · DOI [10.5281/zenodo.20511073](https://doi.org/10.5281/zenodo.20511073) · ISBN 978-953-47198-0-0.


This appendix provides an annotated reference for the FRISCcc semantic checking
grammar (`config/semantics_definition.txt`). The semantics definition specifies
the production rules against which the semantic analysis phase operates. Each
production corresponds to a tree node in the abstract syntax tree; the semantic
analyzer traverses these nodes and applies type-checking rules, scope resolution,
and implicit conversion insertion.

## F.1 Relationship to the Parser Grammar

The semantics grammar is a refinement of the parser grammar (Appendix E) with
several structural differences that reflect the needs of semantic analysis
rather than parsing:

1. **Simplified function definitions.** The parser grammar allows four forms of
   function definition (with and without explicit type specifiers, with old-style
   and new-style parameters). The semantics grammar normalizes this to two
   forms: `<ime_tipa> IDN L_ZAGRADA KR_VOID D_ZAGRADA <slozena_naredba>` for
   void-parameter functions and `<ime_tipa> IDN L_ZAGRADA <lista_parametara>
   D_ZAGRADA <slozena_naredba>` for parameterized functions.

2. **Explicit address-of and dereference.** The parser grammar subsumes `&` and
   `*` under the general `<unarni_operator>` nonterminal. The semantics grammar
   separates `AMPERSAND <cast_izraz>` and `ASTERISK <cast_izraz>` as distinct
   alternatives in `<unarni_izraz>`, enabling targeted type-checking rules for
   address-of (which produces a pointer type) and dereference (which requires a
   pointer operand).

3. **Simplified compound statement.** The semantics grammar omits the empty-block
   and declarations-only-block productions. Compound statements always have the
   form `{ statements }` or `{ declarations statements }`.

4. **Flattened struct declarators.** Struct member declarators in the semantics
   grammar permit pointer-prefixed identifiers and array declarators directly,
   whereas the parser grammar delegates through `<deklarator>`.

5. **Distinct multiplication token.** The semantics grammar uses `OP_PUTA` for
   multiplication instead of `ASTERISK`, resolving the lexical ambiguity between
   the multiplication operator and the pointer dereference operator at the
   semantic level.

6. **Multi-level pointers.** The semantics grammar allows recursive `<pokazivac>`
   productions (`ASTERISK <pokazivac>` and `ASTERISK KR_CONST <pokazivac>`),
   supporting multi-level pointer types such as `int **` and `const int * const *`.

## F.2 Semantic Checking Infrastructure

The semantic analyzer performs a single recursive-descent pass over the AST,
applying the following categories of checks at each production:

**Type checking.** Every expression node computes a result type. Binary operators
require compatible operand types; the analyzer inserts implicit conversions
(e.g., `char` to `int`, `int` to `float`) where the C standard permits them.
Assignment requires that the right-hand side type is assignable to the left-hand
side type.

**Scope resolution.** Identifier references are resolved against the current
scope chain. Each compound statement introduces a new scope. Function parameters
are introduced into the function body's scope. Struct member names are resolved
within the struct's type definition.

**Lvalue checking.** The left-hand side of an assignment, and the operand of
`&` (address-of), `++`, and `--`, must be an lvalue -- an expression that
designates a modifiable storage location. The semantic analyzer tracks lvalue
status through expression evaluation.

**Function signature validation.** Function calls are checked against the
declared parameter list. The number of arguments must match the number of
parameters, and each argument type must be assignable to the corresponding
parameter type.

**Control flow validation.** `break` and `continue` statements are valid only
within loops. `return` statements must return a value compatible with the
enclosing function's declared return type (or no value if the function returns
`void`).

**Struct type completeness.** A struct type used in a declaration must be
complete (i.e., its definition must be visible). Pointer-to-struct types may
reference incomplete structs, enabling recursive data structures.

## F.3 Differences from Standard C89

The semantics grammar and checking rules implement a subset of C89 with the
following notable restrictions:

- No `switch` statement, `do-while` loop, `goto`, or labels.
- No `enum`, `union`, `typedef`, or storage-class specifiers (`static`,
  `extern`, `auto`, `register`).
- No function pointers as first-class values (though function calls through
  identifiers are supported).
- No variadic functions.
- No bitfield struct members.
- The `float` type is supported syntactically and semantically, but the backend
  implements it via Q16.16 fixed-point arithmetic rather than IEEE 754.

## F.4 Complete Semantics Grammar Listing

The following is the verbatim content of `config/semantics_definition.txt`.

```text
<primarni_izraz> ::= IDN
	| BROJ
	| ZNAK
	| NIZ_ZNAKOVA
	| L_ZAGRADA <izraz> D_ZAGRADA
<postfiks_izraz> ::= <primarni_izraz>
	| <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
	| <postfiks_izraz> L_ZAGRADA D_ZAGRADA
	| <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
	| <postfiks_izraz> TOCKA IDN
	| <postfiks_izraz> OP_INC
	| <postfiks_izraz> OP_DEC
<lista_argumenata> ::= <izraz_pridruzivanja>
	| <lista_argumenata> ZAREZ <izraz_pridruzivanja>
<unarni_izraz> ::= <postfiks_izraz>
	| OP_INC <unarni_izraz>
	| OP_DEC <unarni_izraz>
	| <unarni_operator> <cast_izraz>
	| AMPERSAND <cast_izraz>
	| ASTERISK <cast_izraz>
<unarni_operator> ::= PLUS
	| MINUS
	| OP_TILDA
	| OP_NEG
<cast_izraz> ::= <unarni_izraz>
	| L_ZAGRADA <ime_tipa> D_ZAGRADA <cast_izraz>
<ime_tipa> ::= <lista_specifikatora_kvalifikatora>
	| <lista_specifikatora_kvalifikatora> <pokazivac>
<lista_specifikatora_kvalifikatora> ::= <specifikator_tipa>
	| <lista_specifikatora_kvalifikatora> <specifikator_tipa>
	| <lista_specifikatora_kvalifikatora> KR_CONST
<specifikator_tipa> ::= KR_VOID
	| KR_CHAR
	| KR_INT
	| KR_FLOAT
	| <struct_specifikator>
<pokazivac> ::= ASTERISK
	| ASTERISK KR_CONST
	| ASTERISK <pokazivac>
	| ASTERISK KR_CONST <pokazivac>
<struct_specifikator> ::= KR_STRUCT IDN L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
	| KR_STRUCT L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
	| KR_STRUCT IDN
<struct_lista_deklaracija> ::= <struct_deklaracija>
	| <struct_lista_deklaracija> <struct_deklaracija>
<struct_deklaracija> ::= <lista_specifikatora_kvalifikatora> <struct_lista_deklaratora> TOCKAZAREZ
<struct_lista_deklaratora> ::= <struct_deklarator>
	| <struct_lista_deklaratora> ZAREZ <struct_deklarator>
<struct_deklarator> ::= IDN
	| IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
	| <pokazivac> IDN
	| <pokazivac> IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
<multiplikativni_izraz> ::= <cast_izraz>
	| <multiplikativni_izraz> OP_PUTA <cast_izraz>
	| <multiplikativni_izraz> OP_DIJELI <cast_izraz>
	| <multiplikativni_izraz> OP_MOD <cast_izraz>
<aditivni_izraz> ::= <multiplikativni_izraz>
	| <aditivni_izraz> PLUS <multiplikativni_izraz>
	| <aditivni_izraz> MINUS <multiplikativni_izraz>
<odnosni_izraz> ::= <aditivni_izraz>
	| <odnosni_izraz> OP_LT <aditivni_izraz>
	| <odnosni_izraz> OP_GT <aditivni_izraz>
	| <odnosni_izraz> OP_LTE <aditivni_izraz>
	| <odnosni_izraz> OP_GTE <aditivni_izraz>
<jednakosni_izraz> ::= <odnosni_izraz>
	| <jednakosni_izraz> OP_EQ <odnosni_izraz>
	| <jednakosni_izraz> OP_NEQ <odnosni_izraz>
<bin_i_izraz> ::= <jednakosni_izraz>
	| <bin_i_izraz> OP_BIN_I <jednakosni_izraz>
<bin_xili_izraz> ::= <bin_i_izraz>
	| <bin_xili_izraz> OP_BIN_XILI <bin_i_izraz>
<bin_ili_izraz> ::= <bin_xili_izraz>
	| <bin_ili_izraz> OP_BIN_ILI <bin_xili_izraz>
<log_i_izraz> ::= <bin_ili_izraz>
	| <log_i_izraz> OP_I <bin_ili_izraz>
<log_ili_izraz> ::= <log_i_izraz>
	| <log_ili_izraz> OP_ILI <log_i_izraz>
<izraz_pridruzivanja> ::= <log_ili_izraz>
	| <postfiks_izraz> OP_PRIDRUZI <izraz_pridruzivanja>
<izraz> ::= <izraz_pridruzivanja>
	| <izraz> ZAREZ <izraz_pridruzivanja>
<slozena_naredba> ::= L_VIT_ZAGRADA <lista_naredbi> D_VIT_ZAGRADA
	| L_VIT_ZAGRADA <lista_deklaracija> <lista_naredbi> D_VIT_ZAGRADA
<lista_naredbi> ::= <naredba>
	| <lista_naredbi> <naredba>
<naredba> ::= <slozena_naredba>
	| <izraz_naredba>
	| <naredba_grananja>
	| <naredba_petlje>
	| <naredba_skoka>
<izraz_naredba> ::= TOCKAZAREZ
	| <izraz> TOCKAZAREZ
<naredba_grananja> ::= KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba>
	| KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba> KR_ELSE <naredba>
<naredba_petlje> ::= KR_WHILE L_ZAGRADA <izraz> D_ZAGRADA <naredba>
	| KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> D_ZAGRADA <naredba>
	| KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> <izraz> D_ZAGRADA <naredba>
<naredba_skoka> ::= KR_CONTINUE TOCKAZAREZ
	| KR_BREAK TOCKAZAREZ
	| KR_RETURN TOCKAZAREZ
	| KR_RETURN <izraz> TOCKAZAREZ
<prijevodna_jedinica> ::= <vanjska_deklaracija>
	| <prijevodna_jedinica> <vanjska_deklaracija>
<vanjska_deklaracija> ::= <definicija_funkcije>
	| <deklaracija>
<definicija_funkcije> ::= <ime_tipa> IDN L_ZAGRADA KR_VOID D_ZAGRADA <slozena_naredba>
	| <ime_tipa> IDN L_ZAGRADA <lista_parametara> D_ZAGRADA <slozena_naredba>
<lista_parametara> ::= <deklaracija_parametra>
	| <lista_parametara> ZAREZ <deklaracija_parametra>
<deklaracija_parametra> ::= <ime_tipa> IDN
	| <ime_tipa> IDN L_UGL_ZAGRADA D_UGL_ZAGRADA
<lista_deklaracija> ::= <deklaracija>
	| <lista_deklaracija> <deklaracija>
<deklaracija> ::= <ime_tipa> <lista_init_deklaratora> TOCKAZAREZ
<lista_init_deklaratora> ::= <init_deklarator>
	| <lista_init_deklaratora> ZAREZ <init_deklarator>
<init_deklarator> ::= <izravni_deklarator>
	| <izravni_deklarator> OP_PRIDRUZI <inicijalizator>
<izravni_deklarator> ::= IDN
	| IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
	| IDN L_ZAGRADA KR_VOID D_ZAGRADA
	| IDN L_ZAGRADA <lista_parametara> D_ZAGRADA
<inicijalizator> ::= <izraz_pridruzivanja>
	| L_VIT_ZAGRADA <lista_izraza_pridruzivanja> D_VIT_ZAGRADA
<lista_izraza_pridruzivanja> ::= <izraz_pridruzivanja>
	| <lista_izraza_pridruzivanja> ZAREZ <izraz_pridruzivanja>
```
