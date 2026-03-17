# Appendix D. Lexer Specification Reference

This appendix provides a complete annotated reference for the FRISCcc lexer
specification file (`config/lexer_definition.txt`). The specification drives the
lexer generator, which constructs a deterministic finite automaton (DFA) from the
declared character class macros, lexer states, token types, and pattern-action
rules. The generated DFA performs lexical analysis on C-subset source programs,
transforming a stream of characters into a stream of typed tokens.

## D.1 Specification Format

The lexer definition file uses a compact domain-specific notation inspired by the
traditional lex/flex format, with extensions for explicit state management. The
file consists of four sections, described below in the order they appear.

### Character Class Macros

Lines of the form `{name} expansion` define reusable character classes. Within
the expansion, individual characters are separated by the pipe character `|`.
The special escape sequences `\t` (tab), `\n` (newline), and `\_` (space)
represent whitespace characters. The escapes `\(`, `\)`, `\{`, `\}`, `\|`,
`\*`, `\\`, and `\$` represent their literal characters, which otherwise have
syntactic meaning in the regex notation. The symbol `$` denotes the empty string
(epsilon), used in optional sub-patterns.

### Lexer States

The `%X` directive declares a set of named lexer states. The lexer begins
execution in the first listed state. State transitions are triggered by
`UDJI_U_STANJE <state>` actions within rule bodies.

### Token Type Declarations

The `%L` directive lists every token type that the lexer may emit. These names
must match exactly the terminal symbols expected by the parser grammar (`%T` line
in the parser definition).

### Pattern-Action Rules

Each rule has the following form:

```
<state>pattern
{
action-line-1
action-line-2
...
}
```

The `<state>` prefix restricts the rule to a particular lexer state. The pattern
is a regular expression defined over the character class macros and literal
characters. Inside the braces, the action block contains one or more directives:

| Directive | Meaning |
|---|---|
| `-` | Discard the matched text (no token emitted) |
| `TOKEN_NAME` | Emit a token of the given type |
| `UDJI_U_STANJE S` | Transition to lexer state *S* |
| `NOVI_REDAK` | Increment the source line counter |
| `VRATI_SE n` | Push back *n* characters into the input stream |

When a token name appears as the first action line, the matched lexeme is
emitted as a token of that type. When `-` appears, the matched text is consumed
silently (used for whitespace and comment bodies).

## D.2 Character Class Macros

The specification defines nine character class macros. Their Croatian names,
English translations, and definitions are listed below.

| Macro | English | Definition |
|---|---|---|
| `{znak}` | letter | `a`--`z`, `A`--`Z` (52 ASCII letters) |
| `{znamenka}` | digit | `0`--`9` (10 ASCII digits) |
| `{hexZnamenka}` | hex digit | `{znamenka}` union `a`--`f`, `A`--`F` |
| `{bjelina}` | whitespace | `\t` (tab), `\n` (newline), `\_` (space) |
| `{eksponent}` | exponent | `(e\|E)($\|+\|-)` followed by one or more digits |
| `{sviZnakovi}` | all characters | Every printable ASCII character plus whitespace |
| `{sveOsimDvostrukogNavodnikaINovogReda}` | all except `"` and `\n` | All characters minus double-quote and newline |
| `{sveOsimJednostrukogNavodnikaNovogRedaITaba}` | all except `'`, `\n`, `\t` | All characters minus single-quote, newline, tab |
| `{sveOsimNovogRedaITaba}` | all except `\n` and `\t` | All characters minus newline and tab |

The `{eksponent}` macro is a compound pattern used within floating-point literal
rules. The `$` symbol within it denotes the empty string, making the sign
character (`+` or `-`) optional. The remaining macros enumerate large character
sets explicitly because the specification language does not support range
notation such as `[a-z]`.

## D.3 Lexer States

The lexer uses four exclusive states to handle context-dependent tokenization.

| State | English | Purpose |
|---|---|---|
| `S_pocetno` | initial | Default state; recognizes all tokens |
| `S_komentar` | block comment | Entered on `/*`; consumes until `*/` |
| `S_jednolinijskiKomentar` | line comment | Entered on `//`; consumes until newline |
| `S_string` | string literal | Entered on opening `"`; matches complete quoted string |

The state machine for comments is essential because comments may span multiple
lines and may contain characters that would otherwise be recognized as operators
or keywords. The string state uses a `VRATI_SE 0` directive to push the opening
`"` back into the input stream, so that the subsequent string-matching pattern
can match the complete quoted literal including its delimiters.

## D.4 Token Types

The lexer declares 47 token types on the `%L` directive line, organized below
by category.

### D.4.1 Keywords (13 tokens)

| Token | C Keyword | Description |
|---|---|---|
| `KR_BREAK` | `break` | Loop break statement |
| `KR_CHAR` | `char` | Character type specifier |
| `KR_CONST` | `const` | Const type qualifier |
| `KR_CONTINUE` | `continue` | Loop continue statement |
| `KR_ELSE` | `else` | Else branch of conditional |
| `KR_FLOAT` | `float` | Floating-point type specifier |
| `KR_FOR` | `for` | For-loop statement |
| `KR_IF` | `if` | Conditional statement |
| `KR_INT` | `int` | Integer type specifier |
| `KR_RETURN` | `return` | Function return statement |
| `KR_STRUCT` | `struct` | Structure type definition or reference |
| `KR_VOID` | `void` | Void type specifier |
| `KR_WHILE` | `while` | While-loop statement |

The prefix `KR_` abbreviates *kljucna rijec* (Croatian for "keyword").

### D.4.2 Identifiers and Literals (4 tokens)

| Token | Pattern Description | Emitted For |
|---|---|---|
| `IDN` | `(_\|letter)(letter\|digit\|_)*` | Variable names, function names, type names |
| `BROJ` | Decimal, hexadecimal, or floating-point literal | Numeric constants |
| `ZNAK` | `'c'` or `'\c'` (plain or escape character) | Character constants |
| `NIZ_ZNAKOVA` | `"..."` (double-quoted string) | String literals |

### D.4.3 Arithmetic and Assignment Operators (8 tokens)

| Token | Lexeme | Description |
|---|---|---|
| `PLUS` | `+` | Addition or unary plus |
| `MINUS` | `-` | Subtraction or unary minus |
| `ASTERISK` | `*` | Multiplication or pointer dereference |
| `OP_DIJELI` | `/` | Division |
| `OP_MOD` | `%` | Modulo (remainder) |
| `OP_INC` | `++` | Increment (prefix and postfix) |
| `OP_DEC` | `--` | Decrement (prefix and postfix) |
| `OP_PRIDRUZI` | `=` | Assignment |

### D.4.4 Relational and Equality Operators (6 tokens)

| Token | Lexeme | Description |
|---|---|---|
| `OP_LT` | `<` | Less than |
| `OP_LTE` | `<=` | Less than or equal |
| `OP_GT` | `>` | Greater than |
| `OP_GTE` | `>=` | Greater than or equal |
| `OP_EQ` | `==` | Equal |
| `OP_NEQ` | `!=` | Not equal |

### D.4.5 Logical and Bitwise Operators (7 tokens)

| Token | Lexeme | Description |
|---|---|---|
| `OP_NEG` | `!` | Logical NOT |
| `OP_TILDA` | `~` | Bitwise NOT (one's complement) |
| `OP_I` | `&&` | Logical AND (short-circuit) |
| `OP_ILI` | `\|\|` | Logical OR (short-circuit) |
| `AMPERSAND` | `&` | Address-of (unary) or bitwise AND (binary) |
| `OP_BIN_ILI` | `\|` | Bitwise OR |
| `OP_BIN_XILI` | `^` | Bitwise XOR |

The `AMPERSAND` token serves double duty: the parser grammar resolves the
ambiguity between address-of and bitwise AND based on syntactic context (unary
versus binary operator position).

### D.4.6 Delimiters and Punctuation (9 tokens)

| Token | Lexeme | Description |
|---|---|---|
| `ZAREZ` | `,` | Comma (argument/declarator separator) |
| `TOCKAZAREZ` | `;` | Semicolon (statement terminator) |
| `TOCKA` | `.` | Dot (struct member access) |
| `L_ZAGRADA` | `(` | Left parenthesis |
| `D_ZAGRADA` | `)` | Right parenthesis |
| `L_UGL_ZAGRADA` | `[` | Left square bracket (array subscript) |
| `D_UGL_ZAGRADA` | `]` | Right square bracket |
| `L_VIT_ZAGRADA` | `{` | Left curly brace (block, initializer list) |
| `D_VIT_ZAGRADA` | `}` | Right curly brace |

## D.5 Lexer Rules: Annotated Reference

The following provides an annotated walkthrough of all lexer rules in the
specification, organized by functional group.

### D.5.1 Whitespace Rules

Whitespace characters (tab and space) in state `S_pocetno` are consumed silently
with the `-` action. Newline characters are also consumed but additionally
trigger a `NOVI_REDAK` action to increment the source line counter, which is
essential for accurate error and diagnostic messages.

### D.5.2 Comment Rules

**Single-line comments** (`//`): Upon matching `//` in `S_pocetno`, the lexer
transitions to `S_jednolinijskiKomentar`. In that state, all characters are
consumed silently. A newline terminates the comment, increments the line counter,
and returns the lexer to `S_pocetno`.

**Block comments** (`/* ... */`): Upon matching `/*` in `S_pocetno`, the lexer
transitions to `S_komentar`. In that state, all characters are consumed silently.
Newlines within the comment increment the line counter. The closing sequence `*/`
terminates the comment and returns the lexer to `S_pocetno`. Block comments may
span an arbitrary number of lines.

### D.5.3 String Literal Rules

When a double-quote `"` is encountered in `S_pocetno`, the lexer transitions to
`S_string` and pushes the `"` back into the input with `VRATI_SE 0`. In
`S_string`, the pattern `"({sveOsimDvostrukogNavodnikaINovogReda}|\\")*"` matches
the complete string literal, including any internal escaped double-quotes. The
matched text is emitted as a `NIZ_ZNAKOVA` token and the lexer returns to
`S_pocetno`.

This two-step approach (enter state, then match) prevents the string-matching
pattern from interfering with the division operator `/` or comment-start
sequences in the initial state.

### D.5.4 Keyword Rules

Each of the 13 C keywords is matched as a literal string in `S_pocetno`. Because
the lexer generator applies longest-match semantics, a keyword like `int` will
only match when it is not a prefix of a longer identifier: the identifier rule
would produce a longer match for `integer`, so `integer` is correctly tokenized
as `IDN` rather than `KR_INT` followed by other tokens. The keyword rules are
listed before the identifier rule, so that in cases of equal-length matches the
keyword takes priority.

### D.5.5 Identifier Rule

The pattern `(_|{znak})(_|{znak}|{znamenka})*` matches C-style identifiers: an
initial letter or underscore followed by zero or more letters, digits, or
underscores. This emits an `IDN` token.

### D.5.6 Numeric Literal Rules

Four patterns handle the three forms of numeric literal:

1. **Decimal integers**: `{znamenka}{znamenka}*` -- one or more decimal digits.
2. **Hexadecimal integers**: `0(X|x){hexZnamenka}{hexZnamenka}*` -- prefix `0x`
   or `0X` followed by one or more hexadecimal digits.
3. **Floating-point (integer part present)**:
   `{znamenka}{znamenka}*.{znamenka}*($|{eksponent})` -- matches forms such as
   `3.14`, `1.0e5`, and `2.`.
4. **Floating-point (fractional part leads)**:
   `{znamenka}*.{znamenka}{znamenka}*($|{eksponent})` -- matches forms such as
   `.5` and `.25e-3`.

All four patterns emit a `BROJ` token. The parser and semantic analysis phases
distinguish integer from floating-point values based on the lexeme text.

### D.5.7 Character Literal Rules

Two patterns handle character literals:

1. **Plain characters**: `'{sveOsimJednostrukogNavodnikaNovogRedaITaba}'` --
   matches literals such as `'a'`, `'Z'`, `'0'`, and `' '`.
2. **Escape sequences**: `'\\{sveOsimNovogRedaITaba}'` -- matches escaped
   literals such as `'\n'`, `'\t'`, `'\\'`, and `'\0'`.

Both patterns emit a `ZNAK` token.

### D.5.8 Operator Rules

Operators are matched as literal character sequences in `S_pocetno`.
Multi-character operators (`++`, `--`, `<=`, `>=`, `==`, `!=`, `&&`, `||`) are
listed before their single-character prefixes (`+`, `-`, `<`, `>`, `=`, `!`,
`&`, `|`) to ensure that longest-match semantics yield the correct token. For
example, the input `++` matches `OP_INC` rather than two consecutive `PLUS`
tokens.

The patterns `\*`, `\|\|`, and `\|` use backslash escaping because `*`, `|`,
and parentheses have special meaning in the specification's regex notation.

### D.5.9 Delimiter Rules

Commas, semicolons, dots, parentheses, brackets, and braces are each matched as
single characters. Parentheses and braces require backslash escaping (`\(`,
`\)`, `\{`, `\}`) because they are meta-characters in the regex notation. Square
brackets (`[`, `]`) do not require escaping.

## D.6 Disambiguation Rules

The FRISCcc lexer generator resolves ambiguous matches using two standard rules
derived from classical lexer theory:

1. **Longest match (maximal munch)**: When multiple rules can match at the
   current input position, the rule that consumes the longest input string wins.

2. **First match (priority ordering)**: When two or more rules match strings of
   equal length, the rule that appears earliest in the specification wins. This
   is why keyword rules are listed before the general identifier rule.

These two rules together guarantee that the lexer is deterministic: for any
valid input string, exactly one tokenization is produced.

## D.7 Complete Specification Listing

The following is the verbatim content of `config/lexer_definition.txt`.

```
{znak} a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z|A|B|C|D|E|F|G|H|I|J|K|L|M|N|O|P|Q|R|S|T|U|V|W|X|Y|Z
{znamenka} 0|1|2|3|4|5|6|7|8|9
{hexZnamenka} {znamenka}|a|b|c|d|e|f|A|B|C|D|E|F
{bjelina} \t|\n|\_
{eksponent} (e|E)($|+|-){znamenka}{znamenka}*
{sviZnakovi} \(|\)|\{|\}|\||\*|\\|\$|\t|\n|\_|!|"|#|%|&|'|+|,|-|.|/|0|1|2|3|4|5|6|7|8|9|:|;|<|=|>|?|@|A|B|C|D|E|F|G|H|I|J|K|L|M|N|O|P|Q|R|S|T|U|V|W|X|Y|Z|[|]|^|_|`|a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z|~
{sveOsimDvostrukogNavodnikaINovogReda} \(|\)|\{|\}|\||\*|\\|\$|\t|\_|!|#|%|&|'|+|,|-|.|/|0|1|2|3|4|5|6|7|8|9|:|;|<|=|>|?|@|A|B|C|D|E|F|G|H|I|J|K|L|M|N|O|P|Q|R|S|T|U|V|W|X|Y|Z|[|]|^|_|`|a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z|~
{sveOsimJednostrukogNavodnikaNovogRedaITaba} \(|\)|\{|\}|\||\*|\\|\$|\_|!|"|#|%|&|+|,|-|.|/|0|1|2|3|4|5|6|7|8|9|:|;|<|=|>|?|@|A|B|C|D|E|F|G|H|I|J|K|L|M|N|O|P|Q|R|S|T|U|V|W|X|Y|Z|[|]|^|_|`|a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z|~
{sveOsimNovogRedaITaba} \(|\)|\{|\}|\||\*|\\|\$|\_|!|"|#|%|&|'|+|,|-|.|/|0|1|2|3|4|5|6|7|8|9|:|;|<|=|>|?|@|A|B|C|D|E|F|G|H|I|J|K|L|M|N|O|P|Q|R|S|T|U|V|W|X|Y|Z|[|]|^|_|`|a|b|c|d|e|f|g|h|i|j|k|l|m|n|o|p|q|r|s|t|u|v|w|x|y|z|~
%X S_pocetno S_komentar S_jednolinijskiKomentar S_string
%L IDN BROJ ZNAK NIZ_ZNAKOVA KR_BREAK KR_CHAR KR_CONST KR_CONTINUE KR_ELSE KR_FLOAT KR_FOR KR_IF KR_INT KR_RETURN KR_STRUCT KR_VOID KR_WHILE PLUS OP_INC MINUS OP_DEC ASTERISK OP_DIJELI OP_MOD OP_PRIDRUZI OP_LT OP_LTE OP_GT OP_GTE OP_EQ OP_NEQ OP_NEG OP_TILDA OP_I OP_ILI AMPERSAND OP_BIN_ILI OP_BIN_XILI ZAREZ TOCKAZAREZ TOCKA L_ZAGRADA D_ZAGRADA L_UGL_ZAGRADA D_UGL_ZAGRADA L_VIT_ZAGRADA D_VIT_ZAGRADA
<S_pocetno>\t|\_
{
-
}
<S_pocetno>\n
{
-
NOVI_REDAK
}
<S_pocetno>//
{
-
UDJI_U_STANJE S_jednolinijskiKomentar
}
<S_jednolinijskiKomentar>\n
{
-
NOVI_REDAK
UDJI_U_STANJE S_pocetno
}
<S_jednolinijskiKomentar>{sviZnakovi}
{
-
}
<S_pocetno>/\*
{
-
UDJI_U_STANJE S_komentar
}
<S_komentar>\*/
{
-
UDJI_U_STANJE S_pocetno
}
<S_komentar>\n
{
-
NOVI_REDAK
}
<S_komentar>{sviZnakovi}
{
-
}
<S_pocetno>"
{
-
UDJI_U_STANJE S_string
VRATI_SE 0
}
<S_string>"({sveOsimDvostrukogNavodnikaINovogReda}|\\")*"
{
NIZ_ZNAKOVA
UDJI_U_STANJE S_pocetno
}
<S_pocetno>break
{
KR_BREAK
}
<S_pocetno>char
{
KR_CHAR
}
<S_pocetno>const
{
KR_CONST
}
<S_pocetno>continue
{
KR_CONTINUE
}
<S_pocetno>else
{
KR_ELSE
}
<S_pocetno>float
{
KR_FLOAT
}
<S_pocetno>for
{
KR_FOR
}
<S_pocetno>if
{
KR_IF
}
<S_pocetno>int
{
KR_INT
}
<S_pocetno>return
{
KR_RETURN
}
<S_pocetno>struct
{
KR_STRUCT
}
<S_pocetno>void
{
KR_VOID
}
<S_pocetno>while
{
KR_WHILE
}
<S_pocetno>(_|{znak})(_|{znak}|{znamenka})*
{
IDN
}
<S_pocetno>{znamenka}{znamenka}*
{
BROJ
}
<S_pocetno>0(X|x){hexZnamenka}{hexZnamenka}*
{
BROJ
}
<S_pocetno>{znamenka}{znamenka}*.{znamenka}*($|{eksponent})
{
BROJ
}
<S_pocetno>{znamenka}*.{znamenka}{znamenka}*($|{eksponent})
{
BROJ
}
<S_pocetno>'{sveOsimJednostrukogNavodnikaNovogRedaITaba}'
{
ZNAK
}
<S_pocetno>'\\{sveOsimNovogRedaITaba}'
{
ZNAK
}
<S_pocetno>++
{
OP_INC
}
<S_pocetno>--
{
OP_DEC
}
<S_pocetno>+
{
PLUS
}
<S_pocetno>-
{
MINUS
}
<S_pocetno>\*
{
ASTERISK
}
<S_pocetno>/
{
OP_DIJELI
}
<S_pocetno>%
{
OP_MOD
}
<S_pocetno>=
{
OP_PRIDRUZI
}
<S_pocetno><
{
OP_LT
}
<S_pocetno><=
{
OP_LTE
}
<S_pocetno>>
{
OP_GT
}
<S_pocetno>>=
{
OP_GTE
}
<S_pocetno>==
{
OP_EQ
}
<S_pocetno>!=
{
OP_NEQ
}
<S_pocetno>!
{
OP_NEG
}
<S_pocetno>~
{
OP_TILDA
}
<S_pocetno>&&
{
OP_I
}
<S_pocetno>\|\|
{
OP_ILI
}
<S_pocetno>&
{
AMPERSAND
}
<S_pocetno>\|
{
OP_BIN_ILI
}
<S_pocetno>^
{
OP_BIN_XILI
}
<S_pocetno>,
{
ZAREZ
}
<S_pocetno>;
{
TOCKAZAREZ
}
<S_pocetno>.
{
TOCKA
}
<S_pocetno>\(
{
L_ZAGRADA
}
<S_pocetno>\)
{
D_ZAGRADA
}
<S_pocetno>\{
{
L_VIT_ZAGRADA
}
<S_pocetno>\}
{
D_VIT_ZAGRADA
}
<S_pocetno>[
{
L_UGL_ZAGRADA
}
<S_pocetno>]
{
D_UGL_ZAGRADA
}
```
