# Lexical Analyzer - Complete Documentation

This document provides a comprehensive overview of the lexical analyzer implementation. For detailed information, see:

- **[LEXER_IMPLEMENTATION.md](LEXER_IMPLEMENTATION.md)**: Detailed technical documentation covering all algorithms and implementation details
- **[LEXER_USER_GUIDE.md](LEXER_USER_GUIDE.md)**: User guide for writing lexer specifications and using the lexer
- **[README.md](README.md)**: Project overview and quick start guide

## Quick Reference

### Language Subset

**Keywords**: `break`, `else`, `return`, `char`, `for`, `void`, `const`, `if`, `while`, `continue`, `int`, `struct`, `float`

**Operators**: `+`, `-`, `*`, `/`, `%`, `++`, `--`, `&`, `|`, `*`, `+`, `-`, `~`, `!`, `<`, `>`, `<=`, `>=`, `==`, `!=`, `^`, `&&`, `||`, `=`

**Delimiters**: `[`, `]`, `(`, `)`, `{`, `}`, `;`, `,`, `.`

### Lexer Generation Pipeline

1. **Parse Specification** → Extract macros, states, tokens, rules
2. **Expand Macros** → Recursively expand macro references
3. **Build ε-NFA** → Convert regex patterns to epsilon-NFAs (Thompson's construction)
4. **Combine NFAs** → Merge all rules for each state into single ε-NFA
5. **Convert to DFA** → Use subset construction algorithm
6. **Runtime** → Tokenize input using generated DFAs

### Key Algorithms

- **Thompson's Construction**: Regex → ε-NFA
- **Subset Construction**: ε-NFA → DFA
- **Maximal Munch (P2)**: Always select longest match
- **Rule Priority (P3)**: Earlier rules win in case of ties
- **Error Recovery (Algorithm C)**: Discard unrecognized characters

### Output Format

```
tablica znakova:
indeks   uniformni znak   izvorni tekst
     0   KR_INT            int
     1   IDN               x

niz uniformnih znakova:
uniformni znak    redak    indeks u tablicu znakova
KR_INT               1       0
IDN                  1       1
```

For complete documentation, see the referenced documents above.
