# Configuration Examples and Best Practices

## Overview

This document provides practical examples and best practices for working with PPJ compiler configuration files. It demonstrates common configuration patterns and provides guidance for extending the compiler.

## Lexer Configuration Examples

### Adding a New Keyword

**Scenario**: Add support for a new keyword `switch`

**Steps**:

1. **Add token declaration**:
```
%L ... KR_SWITCH ...
```

2. **Add lexer rule**:
```
<S_pocetno>switch
{
KR_SWITCH
}
```

3. **Add to parser terminal declarations**:
```
%T ... KR_SWITCH ...
```

4. **Add grammar productions** (if needed):
```
<naredba> ::= ... | KR_SWITCH ...
```

### Adding a New Operator

**Scenario**: Add support for `**` (power operator)

**Steps**:

1. **Add token declaration**:
```
%L ... OP_POWER ...
```

2. **Add lexer rule** (before `*` rule to ensure longest match):
```
<S_pocetno>\*\*
{
OP_POWER
}
```

3. **Add to parser**:
```
%T ... OP_POWER ...
```

4. **Add grammar production**:
```
<multiplikativni_izraz> ::= ... | <multiplikativni_izraz> OP_POWER <cast_izraz>
```

### Adding a New Lexer State

**Scenario**: Add support for raw strings (different from regular strings)

**Steps**:

1. **Add state declaration**:
```
%X ... S_raw_string
```

2. **Add entry rule**:
```
<S_pocetno>R"
{
-
UDJI_U_STANJE S_raw_string
VRATI_SE 0
}
```

3. **Add matching rules**:
```
<S_raw_string>"({sveOsimDvostrukogNavodnikaINovogReda})*"
{
NIZ_ZNAKOVA
UDJI_U_STANJE S_pocetno
}
```

## Parser Configuration Examples

### Adding a New Statement Type

**Scenario**: Add `do-while` loop support

**Steps**:

1. **Add token** (if not already present):
```
%T ... KR_DO ...
```

2. **Add production**:
```
<naredba_petlje> ::= ... | KR_DO <naredba> KR_WHILE L_ZAGRADA <izraz> D_ZAGRADA TOCKAZAREZ
```

3. **Update semantic rules** (in code):
   - Add handling for new production in `SemanticChecker`

### Adding a New Expression Type

**Scenario**: Add ternary operator `?:`

**Steps**:

1. **Add tokens** (if needed):
```
%T ... UPITNIK DVOTOCKA ...
```

2. **Add production** (with proper precedence):
```
<izraz_pridruzivanja> ::= ... | <log_ili_izraz> UPITNIK <izraz> DVOTOCKA <izraz_pridruzivanja>
```

3. **Update semantic rules**:
   - Type checking for ternary operator
   - Code generation for conditional expression

## Configuration Best Practices

### 1. Token Naming Conventions

**Follow existing conventions**:
- Keywords: `KR_*` prefix (e.g., `KR_IF`, `KR_WHILE`)
- Operators: `OP_*` prefix (e.g., `OP_EQ`, `OP_INC`)
- Delimiters: Descriptive names (e.g., `L_ZAGRADA`, `TOCKAZAREZ`)

**Example**:
```
KR_SWITCH    # Keyword
OP_POWER     # Operator
L_UGL_ZAGRADA # Left bracket
```

### 2. Grammar Organization

**Organize productions logically**:
- Group related productions together
- Use consistent naming
- Document non-obvious productions

**Example**:
```
# Expression hierarchy (precedence order)
<izraz> ::= ...
<izraz_pridruzivanja> ::= ...
<log_ili_izraz> ::= ...
<log_i_izraz> ::= ...
```

### 3. Lexer Rule Ordering

**Order matters**:
- Longer patterns before shorter ones (maximal munch)
- Specific patterns before general ones
- Keywords before identifiers

**Example**:
```
# Correct order
<S_pocetno>int      # Keyword (specific)
<S_pocetno>integer  # Identifier (would match "int" + "eger" if int came after)
<S_pocetno>(_|{znak})(_|{znak}|{znamenka})*  # Identifier pattern (general)
```

### 4. Macro Usage

**Use macros for reusable patterns**:
- Define common patterns once
- Reference macros in rules
- Document macro purposes

**Example**:
```
{znak} a|b|c|...|Z
{znamenka} 0|1|2|...|9
{hexZnamenka} {znamenka}|a|b|c|d|e|f|A|B|C|D|E|F
```

### 5. Error Recovery

**Define synchronization tokens**:
- Include statement terminators
- Include block delimiters
- Test error recovery scenarios

**Example**:
```
%Syn TOCKAZAREZ D_VIT_ZAGRADA
```

## Common Patterns

### Pattern 1: Optional Elements

**Grammar**: Optional else clause in if statement

**Production**:
```
<naredba_grananja> ::= KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba>
                     | KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba> KR_ELSE <naredba>
```

### Pattern 2: Lists

**Grammar**: Comma-separated list

**Production**:
```
<lista_parametara> ::= <deklaracija_parametra>
                      | <lista_parametara> ZAREZ <deklaracija_parametra>
```

### Pattern 3: Precedence

**Grammar**: Operator precedence

**Productions** (lower precedence first):
```
<izraz> ::= <izraz_pridruzivanja>
<izraz_pridruzivanja> ::= <log_ili_izraz>
<log_ili_izraz> ::= <log_i_izraz> | <log_ili_izraz> OP_ILI <log_i_izraz>
<log_i_izraz> ::= <bin_ili_izraz> | <log_i_izraz> OP_I <bin_ili_izraz>
```

## Testing Configuration Changes

### Step 1: Update Configuration

Make changes to configuration files:
- `config/lexer_definition.txt`
- `config/parser_definition.txt`
- `config/semantics_definition.txt`

### Step 2: Rebuild Compiler

```bash
mvn clean compile
```

### Step 3: Test with Example

```bash
# Create test program
echo "int main(void) { return 0; }" > test.c

# Compile
./run.sh test.c

# Check output
cat compiler-bin/a.frisc
```

### Step 4: Validate Output

- Check for syntax errors
- Verify generated code correctness
- Test edge cases

## Troubleshooting

### Issue: Token Not Recognized

**Symptoms**: Lexer doesn't recognize new token

**Solutions**:
1. Check token declaration in `%L` section
2. Verify lexer rule pattern matches input
3. Check rule ordering (longer patterns first)
4. Verify state transitions

### Issue: Parse Error

**Symptoms**: Parser reports syntax error

**Solutions**:
1. Check grammar production syntax
2. Verify token names match lexer output
3. Check for grammar conflicts (shift-reduce, reduce-reduce)
4. Verify production order

### Issue: Semantic Error

**Symptoms**: Semantic analyzer reports error

**Solutions**:
1. Check semantic rule implementation
2. Verify type compatibility rules
3. Check symbol table construction
4. Review semantic pass order

## Further Reading

- **[Configuration Overview](configuration-overview.md)**: Configuration system overview
- **[Config File Reference](config-file-reference.md)**: Complete format specifications
- **[Lexical Analysis](../03-lexical-analysis/lexer-design.md)**: Lexer implementation details
- **[Syntax Analysis](../04-syntax-analysis/grammar-specification.md)**: Parser implementation details

---

*Following these examples and best practices ensures consistent, maintainable configuration files and successful compiler extensions.*
