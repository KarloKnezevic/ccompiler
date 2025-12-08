# Runtime Library

## Overview

The PPJ compiler includes a runtime library of helper functions that implement operations not directly supported by the FRISC architecture. These functions are automatically generated when needed and provide essential functionality for program execution.

## Runtime Functions

### Integer Arithmetic Helpers

#### F_MUL: 32-bit Integer Multiplication

**Purpose**: Multiply two 32-bit integers

**Signature**: `int F_MUL(int a, int b)`

**Implementation**: Uses shift-and-add algorithm

**Usage**: Automatically called for `*` operator on integers

**See Also**: [Helper Functions on FRISC](helper-functions-on-frisc.md)

#### F_DIV: 32-bit Integer Division

**Purpose**: Divide two 32-bit integers

**Signature**: `int F_DIV(int dividend, int divisor)`

**Implementation**: Uses repeated subtraction algorithm

**Usage**: Automatically called for `/` operator on integers

**See Also**: [Helper Functions on FRISC](helper-functions-on-frisc.md)

### Floating-Point Helpers

The compiler implements floating-point operations using Q16.16 fixed-point representation.

#### F_FADD: Float Addition

**Purpose**: Add two Q16.16 fixed-point numbers

**Signature**: `int F_FADD(int a, int b)` (where int represents Q16.16)

**Implementation**: Integer addition (Q16.16 format)

**Usage**: Automatically called for `+` operator on floats

#### F_FSUB: Float Subtraction

**Purpose**: Subtract two Q16.16 fixed-point numbers

**Signature**: `int F_FSUB(int a, int b)`

**Implementation**: Integer subtraction

**Usage**: Automatically called for `-` operator on floats

#### F_FMUL: Float Multiplication

**Purpose**: Multiply two Q16.16 fixed-point numbers

**Signature**: `int F_FMUL(int a, int b)`

**Implementation**: 64-bit intermediate result, then shift

**Usage**: Automatically called for `*` operator on floats

#### F_FDIV: Float Division

**Purpose**: Divide two Q16.16 fixed-point numbers

**Signature**: `int F_FDIV(int dividend, int divisor)`

**Implementation**: Fixed-point division algorithm

**Usage**: Automatically called for `/` operator on floats

#### F_FCMP: Float Comparison

**Purpose**: Compare two Q16.16 fixed-point numbers

**Signature**: `int F_FCMP(int a, int b)` (returns -1, 0, or 1)

**Implementation**: Integer comparison with sign handling

**Usage**: Automatically called for comparison operators on floats

#### F_I2F: Integer to Float Conversion

**Purpose**: Convert integer to Q16.16 fixed-point

**Signature**: `int F_I2F(int value)`

**Implementation**: Multiply by 65536 (2^16)

**Usage**: Implicit conversion from int to float

#### F_F2I: Float to Integer Conversion

**Purpose**: Convert Q16.16 fixed-point to integer

**Signature**: `int F_F2I(int value)`

**Implementation**: Shift right by 16 bits

**Usage**: Implicit conversion from float to int

**See Also**: [Float on FRISC](helper-functions-on-frisc.md) for detailed implementation

## Helper Function Generation

### Automatic Generation

Helper functions are automatically generated when needed:

**Detection**: During code generation, detect operations requiring helpers
**Generation**: Generate helper function code before main program
**Flags**: Track which helpers are needed to avoid duplicate generation

### Generation Order

**Critical Ordering**:
1. Float helpers generated first
2. Integer helpers generated second

**Rationale**: Float helpers may call integer helpers internally

### Code Structure

Generated helper functions follow standard calling convention:

```assembly
F_MUL:
    ; Function prologue
    ; Implementation
    ; Function epilogue
    RET
```

## Runtime Initialization

### Program Entry

Every program includes initialization code:

```assembly
; Initialize stack pointer
MOVE 40000, R7

; Call main function
CALL F_MAIN

; Terminate program
HALT
```

### Stack Initialization

Stack pointer (R7) initialized to high memory (40000) to allow downward growth.

### Global Variable Initialization

Global variables initialized before main:

- Initialized globals: Values placed in data section
- Uninitialized arrays: Space allocated with `.space` directive

## Memory Management

### Stack Management

**Current Implementation**: Simple stack-based allocation

**Limitations**: No heap allocation, no dynamic memory

**Future Enhancement**: Heap allocator for dynamic memory

### Memory Layout

```
High Memory (40000+)
├── Stack (grows downward)
│   └── Activation records
│
├── Global Variables
│   └── Static data
│
└── Code
    └── Functions and helpers
```

## Error Handling

### Current Status

**No Exception Handling**: Programs terminate on errors

**Error Detection**: Semantic errors detected at compile time

**Runtime Errors**: Not handled (division by zero, etc.)

### Future Enhancement

Exception handling would require:
- Exception table
- Unwind mechanism
- Exception propagation

## FRISC Simulator

The compiler integrates with the FRISCjs simulator for testing and debugging generated code.

**See Also**: **[FRISC Simulator Guide](frisc_simulator_guide.md)**: Complete guide to using the FRISC simulator, including console and web interfaces, debugging features, and integration with the compiler.

## Further Reading

- **[Helper Functions on FRISC](helper-functions-on-frisc.md)**: Detailed helper function implementations
- **[Calling Conventions](../07-code-generation/calling-conventions-and-runtime.md)**: Function calling details
- **[FRISC Architecture](../07-code-generation/frisc-codegen-details.md)**: Target architecture reference

---

*The runtime library provides essential functionality for program execution, automatically generating helper functions as needed for operations not directly supported by FRISC hardware.*
