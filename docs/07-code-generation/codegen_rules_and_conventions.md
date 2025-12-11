# Code Generation Rules and Conventions

This document specifies the detailed rules, conventions, and implementation guidelines for the FRISC code generator. It serves as a reference for understanding how different C language constructs are translated to FRISC assembly.

**Author**: Karlo Knežević

## Table of Contents

- [General Rules](#general-rules)
- [Expression Evaluation Rules](#expression-evaluation-rules)
- [Statement Generation Rules](#statement-generation-rules)
- [Function Calling Rules](#function-calling-rules)
- [Memory Access Rules](#memory-access-rules)
- [Type Conversion Rules](#type-conversion-rules)
- [Label Naming Rules](#label-naming-rules)
- [Code Formatting Rules](#code-formatting-rules)
- [Stack Management Rules](#stack-management-rules)
- [Register Usage Rules](#register-usage-rules)

## General Rules

### Rule 1: Expression Result Location
**All expression evaluations leave their result in register R0.**

- Simple expressions: Direct computation into R0
- Complex expressions: Intermediate values may use stack, final result in R0
- Function calls: Return value moved from R6 to R0 for expression evaluation

### Rule 2: Stack Discipline
**Stack pointer (R7) must be properly maintained throughout execution.**

- Function entry: Allocate locals with `SUB R7, size, R7`
- Function exit: Deallocate locals with `ADD R7, size, R7` before `RET`
- Argument cleanup: Caller removes arguments after function return
- Temporary storage: Use `PUSH`/`POP` for expression temporaries

### Rule 3: Frame Pointer Convention
**R5 is used as frame pointer (fixed during function execution).**

- R5 is set at function entry: `MOVE R7, R5` (after saving old R5)
- Parameters accessed via positive offsets: `R5 + 8`, `R5 + 12`, etc.
- Local variables accessed via negative offsets: `R5 - 4`, `R5 - 8`, etc.
- R5 is restored before function return: `POP R5`

### Rule 4: Return Value Convention
**Function return values are placed in register R6.**

- All functions return via R6 (even void functions set R6 to 0)
- Caller moves R6 to R0 for expression evaluation
- Return statement: `MOVE R0, R6` then `RET`

## Expression Evaluation Rules

### Rule 5: Binary Operation Evaluation Order
**Binary operations evaluate operands in left-to-right order, but push arguments in reverse order.**

1. Evaluate left operand → result in R0
2. Push R0 to stack (save left operand)
3. Evaluate right operand → result in R0
4. Move R0 to R1 (right operand)
5. Pop stack to R0 (restore left operand)
6. Perform operation: `R0 op R1 → R0`

**Example**: `a + b`
```assembly
        ; Evaluate a
        LOAD R0, (R7+0)        ; a in R0
        PUSH R0                ; Save a
        ; Evaluate b
        LOAD R0, (R7+4)        ; b in R0
        MOVE R0, R1            ; b in R1
        POP R0                  ; a in R0
        ADD R0, R1, R0         ; a + b in R0
```

### Rule 6: Short-Circuit Evaluation
**Logical AND (`&&`) and OR (`||`) use short-circuit evaluation.**

**Logical AND (`&&`)**:
- If left operand is false (0), skip right operand evaluation
- Result is 1 if both operands are true, 0 otherwise

**Logical OR (`||`)**:
- If left operand is true (non-zero), skip right operand evaluation
- Result is 1 if either operand is true, 0 otherwise

**Implementation Pattern**:
```assembly
        ; Evaluate left operand
        ; ... left evaluation code ...
        CMP R0, 0
        JP_EQ falseLabel       ; Short-circuit if false (AND) or true (OR)
        ; Evaluate right operand
        ; ... right evaluation code ...
        CMP R0, 0
        JP_EQ falseLabel       ; Check right operand
        MOVE 1, R0             ; True result
        JP endLabel
falseLabel:
        MOVE 0, R0             ; False result
endLabel:
```

### Rule 7: Assignment Evaluation Order
**Assignment expressions evaluate right-hand side first, then store to left-hand side.**

1. Evaluate right-hand side expression → result in R0
2. Generate L-value address for left-hand side
3. Store R0 to L-value address

**L-Value Types**:
- Simple variable: Direct store to stack offset or global label
- Array element: Calculate element address, then store

### Rule 8: Increment/Decrement Semantics
**Pre-increment/decrement modify before use; post-increment/decrement use before modify.**

**Pre-increment (`++x`)**:
1. Load variable value
2. Increment value
3. Store back to variable
4. Use incremented value

**Post-increment (`x++`)**:
1. Load variable value
2. Save value for use
3. Increment value
4. Store back to variable
5. Use original value

### Rule 9: Array Indexing
**Array element access requires address calculation before load/store.**

**Address Calculation**:
- Base address: Variable address (local or global)
- Index: Evaluated expression result in R0
- Element size: 4 bytes for `int`, 1 byte for `char`
- Final address: `base + (index * element_size)`

**Implementation**:
```assembly
        ; Calculate array element address
        MOVE base_address, R1  ; Base address
        LOAD R0, (R7+index_offset)  ; Index value
        SHL R0, 2, R0          ; Multiply by 4 (for int arrays)
        ADD R1, R0, R0         ; Final address in R0
        ; Load or store using (R0)
```

### Rule 10: Function Call Argument Passing
**Arguments are evaluated left-to-right but pushed right-to-left (reverse order).**

1. Evaluate all arguments in left-to-right order
2. Push arguments to stack in reverse order (rightmost first)
3. Call function with `CALL` instruction
4. Clean up arguments: `ADD R7, (arg_count * 4), R7`
5. Move return value: `MOVE R6, R0`

**Example**: `f(a, b, c)`
```assembly
        ; Evaluate arguments (left-to-right)
        LOAD R0, (R7+0)        ; a
        PUSH R0                 ; Push a (will be third)
        LOAD R0, (R7+4)        ; b
        PUSH R0                 ; Push b (will be second)
        LOAD R0, (R7+8)        ; c
        PUSH R0                 ; Push c (will be first)
        CALL F_F               ; Call function
        ADD R7, 12, R7         ; Clean up 3 arguments (3 * 4 bytes)
        MOVE R6, R0            ; Return value in R0
```

## Statement Generation Rules

### Rule 11: If Statement Structure
**If statements generate conditional jump with proper label management.**

**If without else**:
1. Evaluate condition → result in R0
2. Test condition: `CMP R0, 0`
3. Jump to end if false: `JP_EQ endLabel`
4. Generate then block
5. End label

**If with else**:
1. Evaluate condition → result in R0
2. Test condition: `CMP R0, 0`
3. Jump to else if false: `JP_EQ elseLabel`
4. Generate then block
5. Jump over else: `JP endLabel`
6. Else label and else block
7. End label

### Rule 12: While Loop Structure
**While loops generate condition test at loop start with proper jump structure.**

1. Loop start label
2. Evaluate condition → result in R0
3. Test condition: `CMP R0, 0`
4. Jump to end if false: `JP_EQ endLabel`
5. Generate loop body
6. Jump back to start: `JP startLabel`
7. Loop end label

**Break/Continue Handling**:
- `break`: Jump to loop end label
- `continue`: Jump to loop start label (after condition test)

### Rule 13: For Loop Structure
**For loops generate initialization, condition test, body, and update sections.**

1. Generate initialization (if present)
2. Loop start label (condition test)
3. Evaluate condition → result in R0 (if present)
4. Test condition: `CMP R0, 0` (if present)
5. Jump to end if false: `JP_EQ endLabel` (if condition present)
6. Generate loop body
7. Continue label (update point)
8. Generate update expression (if present)
9. Jump back to start: `JP startLabel`
10. Loop end label

**Break/Continue Handling**:
- `break`: Jump to loop end label
- `continue`: Jump to continue label (skips update, goes to condition)

### Rule 14: Return Statement
**Return statements set return value and clean up stack before returning.**

1. Evaluate return expression (if present) → result in R0
2. Move result to R6: `MOVE R0, R6`
3. Deallocate local variables: `ADD R7, local_size, R7`
4. Restore frame pointer: `POP R5`
5. Return: `RET`

**Void Return**:
- Set R6 to 0: `MOVE 0, R6`
- Clean up and return

## Function Calling Rules

### Rule 15: Function Prolog
**Every function must establish frame pointer and allocate local variables.**

**Standard Prolog**:
```assembly
F_FUNCTION:
        PUSH R5                 ; Save old frame pointer
        MOVE R7, R5             ; Establish new frame pointer
        SUB R7, local_size, R7  ; Allocate local variables
```

**Parameter Access**:
- First parameter: `R5 + 8` (after old R5 at +0 and return address at +4)
- Second parameter: `R5 + 12`
- Nth parameter: `R5 + (8 + (n-1)*4)`

### Rule 16: Function Epilog
**Every function must clean up stack and restore frame pointer before returning.**

**Standard Epilog**:
```assembly
        ADD R7, local_size, R7  ; Deallocate local variables
        POP R5                   ; Restore old frame pointer
        RET                      ; Return to caller
```

**Return Value**:
- Must be set in R6 before `RET`
- Default: `MOVE 0, R6` for void functions

### Rule 17: Caller Responsibilities
**Caller must clean up arguments from stack after function return.**

1. Push arguments in reverse order
2. Call function
3. Clean up arguments: `ADD R7, (arg_count * 4), R7`
4. Use return value from R6

**Argument Size**:
- All arguments are 4 bytes (32 bits)
- Arrays and other types passed as pointers (4 bytes)

## Memory Access Rules

### Rule 18: Local Variable Access
**Local variables are accessed via negative offsets from frame pointer R5.**

- First local: `R5 - 4`
- Second local: `R5 - 8`
- Nth local: `R5 - (n * 4)`

**Load Example**:
```assembly
        LOAD R0, (R5-4)         ; Load first local variable
```

**Store Example**:
```assembly
        STORE R0, (R5-4)        ; Store to first local variable
```

### Rule 19: Parameter Access
**Parameters are accessed via positive offsets from frame pointer R5.**

- First parameter: `R5 + 8`
- Second parameter: `R5 + 12`
- Nth parameter: `R5 + (8 + (n-1)*4)`

**Load Example**:
```assembly
        LOAD R0, (R5+8)         ; Load first parameter
```

### Rule 20: Global Variable Access
**Global variables are accessed via their labels.**

**Load Example**:
```assembly
        LOAD R0, (G_VARIABLE)   ; Load global variable
```

**Store Example**:
```assembly
        STORE R0, (G_VARIABLE)  ; Store to global variable
```

### Rule 21: Array Element Access
**Array elements require address calculation before access.**

**Local Array**:
```assembly
        ; Calculate element address
        MOVE R5, R1             ; Base address (frame pointer)
        SUB R1, array_offset, R1 ; Array base (negative offset)
        LOAD R0, (R7+index_offset)  ; Index value
        SHL R0, 2, R0           ; Multiply by 4 (element size)
        ADD R1, R0, R0          ; Final address in R0
        LOAD R1, (R0)           ; Load element
```

**Global Array**:
```assembly
        ; Calculate element address
        MOVE G_ARRAY, R1        ; Base address (label)
        LOAD R0, (R7+index_offset)  ; Index value
        SHL R0, 2, R0           ; Multiply by 4 (element size)
        ADD R1, R0, R0          ; Final address in R0
        LOAD R1, (R0)           ; Load element
```

## Type Conversion Rules

### Rule 22: Char to Int Conversion
**Char to int conversion is implicit (sign extension handled by FRISC).**

- No explicit conversion needed
- FRISC load operations automatically sign-extend 8-bit values

### Rule 23: Int to Char Conversion
**Int to char conversion requires truncation to lower 8 bits.**

```assembly
        AND R0, 255, R0         ; Truncate to lower 8 bits (char)
```

**Explanation**:
- `AND R0, 255, R0` masks upper 24 bits, keeping only lower 8 bits
- 255 in binary: `00000000000000000000000011111111`

### Rule 24: Type Sizes
**All types are stored as 32-bit values on the stack.**

- `int`: 4 bytes (32 bits)
- `char`: 4 bytes (stored as 32-bit value, lower 8 bits used)
- Arrays: `element_size * element_count` bytes
- Pointers: 4 bytes (32-bit addresses)

## Label Naming Rules

### Rule 25: Function Labels
**Function labels follow pattern: `F_<FUNCTION_NAME>` (uppercase).**

- `main` → `F_MAIN`
- `factorial` → `F_FACTORIAL`
- `calculateSum` → `F_CALCULATESUM`

### Rule 26: Global Variable Labels
**Global variable labels follow pattern: `G_<VARIABLE_NAME>` (uppercase).**

- `counter` → `G_COUNTER`
- `array` → `G_ARRAY`
- `buffer` → `G_BUFFER`

### Rule 27: Control Flow Labels
**Control flow labels follow pattern: `L_<TYPE>_<NUMBER>`.**

**Types**:
- `IF`: If statement labels
- `ELSE`: Else block labels
- `END`: End of control structure
- `LOOP_START`: Loop start (condition test)
- `LOOP_END`: Loop end (exit point)
- `LOOP_CONTINUE`: Loop continue point (update)
- `SC`: Short-circuit evaluation point

**Examples**:
- `L_IF_1`, `L_ELSE_1`, `L_END_1`
- `L_LOOP_START_1`, `L_LOOP_END_1`, `L_LOOP_CONTINUE_1`
- `L_SC_1`, `L_SC_2`

**Uniqueness**: Each label must be unique within the program. `LabelGenerator` ensures uniqueness by tracking all generated labels.

## Code Formatting Rules

### Rule 28: Instruction Formatting
**Instructions are formatted with consistent indentation and comment alignment.**

**Format**:
```
        MNEMONIC operand1, operand2    ; comment
```

**Components**:
- Indentation: 8 spaces
- Mnemonic: Uppercase FRISC instruction name
- Operands: Comma-separated, proper spacing
- Comment: Aligned to column 32, prefixed with `;`

### Rule 29: Label Formatting
**Labels are placed at the beginning of lines with no indentation.**

**Format**:
```
LABEL:                      ; optional comment
        instruction         ; code after label
```

### Rule 30: Comment Formatting
**Comments are aligned to column 32 and prefixed with semicolon.**

**Types**:
- Instruction comments: Inline with instruction
- Standalone comments: Full-line comments for sections
- Section headers: Separator lines with `;` and `=`

**Examples**:
```assembly
        MOVE 40000, R7      ; Initialize stack pointer
        ; Function definitions section
        ; ============================================================================
```

### Rule 31: Data Declaration Formatting
**Data declarations follow consistent format with label, directive, value, and comment.**

**Format**:
```
LABEL   DIRECTIVE value    ; comment
```

**Directives**:
- `DW`: Define word (4 bytes) - for initialized data
- `DS`: Define storage - for uninitialized arrays

**Examples**:
```assembly
G_COUNTER    DW %D 0       ; int counter = 0
G_ARRAY      DW %D 1, 2, 3  ; int array[] = {1, 2, 3}
G_BUFFER     DS %D 100      ; char buffer[100]
```

## Stack Management Rules

### Rule 32: Stack Growth Direction
**Stack grows downward (toward lower addresses).**

- Stack pointer (R7) decreases when allocating space
- Stack pointer increases when deallocating space
- Local variables allocated with `SUB R7, size, R7`
- Local variables deallocated with `ADD R7, size, R7`

### Rule 33: Stack Alignment
**All stack allocations must be aligned to 4-byte boundaries.**

- Local variables: Rounded up to multiple of 4 bytes
- Parameters: Always 4 bytes each
- Temporary storage: 4-byte aligned

### Rule 34: Stack Cleanup
**Stack must be properly cleaned up before function return.**

**Required Cleanup**:
1. Deallocate local variables: `ADD R7, local_size, R7`
2. Restore frame pointer: `POP R5`
3. Caller cleans up arguments: `ADD R7, (arg_count * 4), R7`

**Order**: Local cleanup → Frame pointer restore → Argument cleanup (by caller)

## Register Usage Rules

### Rule 35: Register Allocation
**Registers are allocated according to standard conventions.**

**Primary Registers**:
- **R0**: Primary accumulator (expression results)
- **R1**: Secondary operand (binary operations)
- **R2-R5**: Temporary registers (complex expressions)
- **R6**: Function return values
- **R7**: Stack pointer (reserved, managed by FRISC)

### Rule 36: Register Preservation
**Registers R0-R5 are caller-saved (caller must preserve if needed).**

- Function calls may modify R0-R5
- Caller must save/restore registers if values are needed after call
- Use stack for register preservation: `PUSH R0` before call, `POP R0` after call

### Rule 37: Register Spilling
**When registers are exhausted, values are spilled to stack.**

**Spill Strategy**:
1. Push register to stack: `PUSH R0`
2. Use register for other computation
3. Restore from stack: `POP R0`

**Example**:
```assembly
        ; Complex expression requiring multiple temporaries
        PUSH R0                ; Spill R0
        ; ... use R0 for other computation ...
        POP R0                 ; Restore R0
```

## Summary

These rules ensure:
- **Correctness**: Generated code correctly implements C semantics
- **Consistency**: Uniform code generation across all constructs
- **Readability**: Well-formatted assembly code with clear structure
- **Maintainability**: Predictable code patterns for debugging and extension

Following these rules guarantees that the generated FRISC assembly code:
- Executes correctly on the FRISC simulator
- Maintains proper stack discipline
- Follows standard calling conventions
- Produces correct results for all valid C programs

## Additional Documentation

For comprehensive documentation on struct code generation, including memory layout, field offset calculation, member access, and struct assignments, see:
- [Struct Code Generation](struct-code-generation.md) - Complete algorithms, memory layout conventions, and code generation patterns for struct types

