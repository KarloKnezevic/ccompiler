# Float Representation on FRISC

This document describes how floating-point values are represented and manipulated on the FRISC architecture, which has no native hardware support for floating-point operations.

## Design Decision: Q16.16 Fixed-Point Representation

We use **Q16.16 fixed-point representation** to implement floating-point arithmetic on FRISC. This choice provides:

- **Simplicity**: Arithmetic operations are straightforward using integer operations
- **Precision**: 16 bits of fractional precision (approximately 0.00001526)
- **Range**: Approximately -32768.0 to +32767.9999847
- **Efficiency**: No complex bit manipulation required for basic operations

## Q16.16 Format

A float value is stored as a 32-bit signed integer where:

- **Bits 31-16**: Integer part (signed 16-bit)
- **Bits 15-0**: Fractional part (unsigned 16-bit)

The actual float value is computed as:

```
actual_float = stored_integer / 65536.0
```

### Examples

| Float Value | Q16.16 Integer | Calculation |
|-------------|----------------|-------------|
| 1.0         | 65536          | 1.0 × 65536 = 65536 |
| 1.5         | 98304          | 1.5 × 65536 = 98304 |
| 0.5         | 32768          | 0.5 × 65536 = 32768 |
| -1.0        | -65536         | -1.0 × 65536 = -65536 |
| 0.0         | 0              | 0.0 × 65536 = 0 |

## FRISC Runtime Library

The compiler generates helper functions for float operations:

### F_FADD: Float Addition
```frisc
push b (Q16.16)
push a (Q16.16)
CALL F_FADD
ADD R7, %D 8, R7  ; cleanup
; result in R6 (Q16.16)
```

**Implementation**: Since both operands are scaled by 65536, addition works directly:
```
(a*65536) + (b*65536) = (a+b)*65536
```

### F_FSUB: Float Subtraction
Similar to F_FADD, but performs subtraction:
```
(a*65536) - (b*65536) = (a-b)*65536
```

### F_FMUL: Float Multiplication
```frisc
push b (Q16.16)
push a (Q16.16)
CALL F_FMUL
ADD R7, %D 8, R7
; result in R6 (Q16.16)
```

**Implementation**: 
1. Multiply the Q16.16 integers: `(a*65536) * (b*65536) = (a*b) * 65536²`
2. Shift right by 16 bits to divide by 65536: `(a*b) * 65536`

### F_FDIV: Float Division
```frisc
push b (Q16.16)
push a (Q16.16)
CALL F_FDIV
ADD R7, %D 8, R7
; result in R6 (Q16.16)
```

**Implementation**:
1. Shift dividend left by 16 bits: `a*65536*65536`
2. Divide by divisor: `(a*65536*65536) / (b*65536) = (a*65536) / b = (a/b) * 65536`

### F_FCMP: Float Comparison
```frisc
push b (Q16.16)
push a (Q16.16)
CALL F_FCMP
ADD R7, %D 8, R7
; result in R6: -1 (a < b), 0 (a == b), 1 (a > b)
```

**Implementation**: Compare Q16.16 values as signed integers. The ordering is preserved because the scaling factor (65536) is positive.

### F_I2F: Integer to Float Conversion
```frisc
push int_value
CALL F_I2F
ADD R7, %D 4, R7
; result in R6 (Q16.16)
```

**Implementation**: Shift left by 16 bits (multiply by 65536):
```
i * 65536 = (float)i * 65536
```

### F_F2I: Float to Integer Conversion
```frisc
push float_value (Q16.16)
CALL F_F2I
ADD R7, %D 4, R7
; result in R6 (integer, truncated)
```

**Implementation**: Shift right by 16 bits (divide by 65536, truncate):
```
f / 65536 = (int)(float_value)
```

## Type Conversions

### Implicit Conversions

When mixing `int`/`char` with `float` in arithmetic operations:

1. **int/char → float**: Automatically converted using `F_I2F`
2. **Result type**: If either operand is `float`, result is `float`

### Explicit Casts

- `(float)int_expr`: Converts integer to float using `F_I2F`
- `(int)float_expr`: Converts float to integer using `F_F2I` (truncates)

## Test Interpretation

When a program returns a float, register `R6` contains the Q16.16 fixed-point integer.

**Test harnesses should interpret**:
```java
int r6Value = // value from R6 register
float actualFloat = r6Value / 65536.0f;
```

### Example

If a program returns `1.5f`:
- R6 contains: `98304` (Q16.16)
- Test interprets: `98304 / 65536.0 = 1.5`

## Limitations

1. **Range**: Limited to approximately ±32768.0
2. **Precision**: Approximately 0.00001526 (1/65536)
3. **No special values**: Does not handle infinity, NaN, or denormals
4. **Rounding**: Truncation-based (no proper rounding)

These limitations are acceptable for educational purposes and the subset of C being compiled.

## Code Generation

The code generator:

1. **Float literals**: Converts to Q16.16 at compile time using `FloatCodegenHelper.parseFloatLiteral()`
2. **Float variables**: Stored as Q16.16 integers in memory (4 bytes)
3. **Float operations**: Generates calls to appropriate helper functions
4. **Type checking**: Uses semantic attributes to determine when float operations are needed

## Example Generated Code

For `float f(void) { return 1.5 + 2.0; }`:

```frisc
F_MAIN
    PUSH R5
    MOVE R7, R5
    
    ; Load 1.5f (Q16.16 = 98304)
    MOVE %D 98304, R0
    PUSH R0
    
    ; Load 2.0f (Q16.16 = 131072)
    MOVE %D 131072, R0
    MOVE R0, R1
    POP R0
    
    ; Call float addition
    PUSH R1
    PUSH R0
    CALL F_FADD
    ADD R7, %D 8, R7
    MOVE R6, R0
    
    ; Result: 3.5f (Q16.16 = 229376)
    MOVE R0, R6
    
    POP R5
    RET
```

## Future Improvements

For production use, consider:

1. **IEEE-754 encoding**: More standard, handles special values
2. **Proper rounding**: Round-to-nearest instead of truncation
3. **Extended precision**: Use 64-bit Q32.32 for better range/precision
4. **Optimization**: Inline simple operations instead of function calls

