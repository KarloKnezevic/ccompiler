# Debugging Workflow

## Overview

This document describes debugging techniques and workflows for the PPJ compiler. Effective debugging is essential for understanding compiler behavior and fixing issues.

## Debugging Strategy

### Systematic Approach

1. **Reproduce the Issue**: Create minimal test case
2. **Identify the Phase**: Determine which compiler phase fails
3. **Examine Intermediate Outputs**: Check phase-specific output files
4. **Trace Execution**: Follow data flow through compiler phases
5. **Fix and Verify**: Make fix and verify with test case

## Phase-Specific Debugging

### Lexical Analysis Debugging

**Output File**: `compiler-bin/leksicke_jedinke.txt`

**Contents**:
- Token table (symbol table)
- Token stream with line numbers

**Debugging Steps**:

1. **Check Token Recognition**:
   ```bash
   ./run.sh lexer program.c
   cat compiler-bin/leksicke_jedinke.txt
   ```

2. **Verify Token Patterns**:
   - Check if expected tokens are recognized
   - Verify token line numbers
   - Check for unrecognized characters

3. **Common Issues**:
   - Token not recognized: Check lexer rule pattern
   - Wrong token: Check rule ordering (maximal munch)
   - Missing token: Check state transitions

**Example Debug Output**:
```
tablica znakova:
indeks   uniformni znak   izvorni tekst
     0   KR_INT            int
     1   IDN               main
     2   L_ZAGRADA         (
```

### Syntax Analysis Debugging

**Output Files**:
- `compiler-bin/generativno_stablo.txt` (generative tree)
- `compiler-bin/sintaksno_stablo.txt` (syntax tree)

**Debugging Steps**:

1. **Check Parse Tree Structure**:
   ```bash
   ./run.sh syntax program.c
   cat compiler-bin/sintaksno_stablo.txt
   ```

2. **Verify Grammar Productions**:
   - Check if expected productions are applied
   - Verify tree structure matches grammar
   - Check for parse errors

3. **Common Issues**:
   - Parse error: Check grammar productions
   - Wrong tree structure: Check production alternatives
   - Missing nodes: Check grammar completeness

**Example Debug Output**:
```
0:<prijevodna_jedinica>
    1:<vanjska_deklaracija>
        2:<definicija_funkcije>
            3:KR_INT
            4:IDN main
            5:L_ZAGRADA
            ...
```

### Semantic Analysis Debugging

**Output Files**:
- `compiler-bin/tablica_simbola.txt` (symbol table)
- `compiler-bin/semanticko_stablo.txt` (annotated AST)

**Debugging Steps**:

1. **Check Symbol Table**:
   ```bash
   ./run.sh semantic program.c
   cat compiler-bin/tablica_simbola.txt
   ```

2. **Verify Type Information**:
   - Check variable types
   - Verify function signatures
   - Check scope hierarchy

3. **Common Issues**:
   - Undefined identifier: Check symbol table construction
   - Type mismatch: Check type checking rules
   - Scope error: Check scope management

**Example Debug Output**:
```
Global Scope:
  main : int(void) [defined=true]
Function Scope - main:
  x : int [const=false]
```

### Code Generation Debugging

**Output File**: `compiler-bin/a.frisc`

**Debugging Steps**:

1. **Check Generated Assembly**:
   ```bash
   ./run.sh program.c
   cat compiler-bin/a.frisc
   ```

2. **Verify Code Structure**:
   - Check function prologues/epilogues
   - Verify stack management
   - Check register usage

3. **Common Issues**:
   - Wrong instructions: Check code generation rules
   - Stack imbalance: Check stack management
   - Wrong values: Check expression evaluation

**Example Debug Output**:
```assembly
F_MAIN:
    SUB R7, 4, R7      ; Allocate local variable
    MOVE 42, R0
    STORE R0, R7+0     ; Store local variable
    ADD R7, 4, R7      ; Deallocate
    MOVE R0, R6        ; Return value
    RET
```

## Execution Debugging

### FRISC Simulator Debugging

**Console Interface**:
```bash
node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc
```

**Web Interface**:
```bash
open node_modules/friscjs/webapp/index.html
# Load compiler-bin/a.frisc in web interface
```

**Debugging Features**:
- Step-by-step execution
- Register inspection
- Memory inspection
- Breakpoints (web interface)

### Execution Issues

**Wrong Return Value**:
1. Check expression evaluation
2. Verify return value placement in R6
3. Check function epilogue

**Stack Overflow**:
1. Check stack pointer initialization
2. Verify stack frame size
3. Check for stack imbalance

**Infinite Loop**:
1. Check loop condition evaluation
2. Verify loop increment/decrement
3. Check control flow instructions

## Debugging Tools

### Logging

**Enable Debug Logging**:
```java
Logger.getLogger("hr.fer.ppj").setLevel(Level.FINE);
```

**Log Output**:
- Phase transitions
- Error messages
- Intermediate states

### Print Statements

**Add Debug Output**:
```java
System.out.println("Debug: " + debugInfo);
```

**Remove Before Commit**: Always remove debug print statements

### Unit Tests

**Write Focused Tests**:
```java
@Test
void testSpecificFeature() {
    // Test minimal case
    // Verify expected behavior
}
```

**Isolate Issues**: Create minimal test cases to isolate problems

## Common Debugging Scenarios

### Scenario 1: Token Not Recognized

**Symptoms**: Lexer doesn't recognize expected token

**Debugging**:
1. Check lexer rule pattern
2. Verify macro expansion
3. Check state transitions
4. Verify rule ordering

**Fix**: Update lexer rule or add new rule

### Scenario 2: Parse Error

**Symptoms**: Parser reports syntax error

**Debugging**:
1. Check token stream (lexer output)
2. Verify grammar productions
3. Check for grammar conflicts
4. Review error recovery

**Fix**: Update grammar or fix source code

### Scenario 3: Type Error

**Symptoms**: Semantic analyzer reports type mismatch

**Debugging**:
1. Check symbol table (variable types)
2. Verify type checking rules
3. Check implicit conversions
4. Review type synthesis

**Fix**: Update type checking rules or fix source code

### Scenario 4: Wrong Assembly Output

**Symptoms**: Generated assembly doesn't match expected

**Debugging**:
1. Check AST structure
2. Verify code generation rules
3. Check register allocation
4. Review stack management

**Fix**: Update code generation rules

## Best Practices

### 1. Minimal Test Cases

Create smallest possible test case that reproduces issue:
```c
// Minimal test case
int main(void) {
    return 0;  // Add minimal code to reproduce issue
}
```

### 2. Incremental Debugging

Test changes incrementally:
- Make small changes
- Test after each change
- Verify intermediate outputs

### 3. Document Findings

Keep notes during debugging:
- What was tested
- What was found
- What was fixed

### 4. Use Version Control

Commit working state before debugging:
```bash
git commit -m "Working state before debugging"
```

### 5. Verify Fixes

Always verify fixes:
- Test with original failing case
- Test with related cases
- Run full test suite

## Further Reading

- **[Test Strategy](test-strategy.md)**: Testing methodology
- **[Example Programs](example-programs.md)**: Test program catalog
- **[FRISC Simulator Guide](../09-runtime-and-support/runtime-library.md)**: Simulator usage

---

*Effective debugging requires systematic approach, understanding of compiler phases, and use of appropriate debugging tools. Following these practices leads to efficient issue resolution.*
