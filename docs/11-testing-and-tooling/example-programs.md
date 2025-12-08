# Example Programs

## Overview

The PPJ compiler includes extensive example programs for testing and validation. These examples demonstrate language features and serve as regression tests.

## Example Organization

### Valid Programs (`examples/valid/`)

**90 test programs** covering supported language features:

**Basic Features**:
- `program1.c` - `program10.c`: Basic functions, variables, expressions
- `program11.c` - `program20.c`: Control flow (if, while, for)
- `program21.c` - `program30.c`: Function calls and parameters

**Advanced Features**:
- `program31.c` - `program50.c`: Arrays, pointers, complex expressions
- `program51.c` - `program70.c`: Nested structures, recursion
- `program71.c` - `program90.c`: Edge cases, optimizations

### Invalid Programs (`examples/invalid/`)

**70+ error test cases** for error detection:

- Syntax errors: Missing semicolons, unmatched brackets
- Type errors: Type mismatches, incompatible operations
- Scope errors: Undefined identifiers, duplicate declarations
- Semantic errors: Invalid control flow, missing returns

## Program Categories

### Arithmetic Tests (`examples/arithmetic_tests/`)

**20 test programs** focusing on arithmetic operations:

- Basic arithmetic: `+`, `-`, `*`, `/`, `%`
- Operator precedence
- Expression evaluation
- Integer overflow handling

### Type Tests (`examples/types/`)

**60 test programs** covering type system:

- Primitive types: `int`, `char`, `void`
- Array types: Declaration, indexing, initialization
- Pointer types: Basic pointer operations
- Type conversions: Implicit and explicit

### Float Arithmetic Tests (`examples/float_arith_tests/`)

**30 test programs** for floating-point operations:

- Float literals
- Float arithmetic operations
- Float comparisons
- Float conversions

**Note**: Float support uses Q16.16 fixed-point representation.

### Float Tests (`examples/floats/`)

**36 test programs** for comprehensive float coverage:

- Float declarations and assignments
- Float expressions
- Float function parameters and returns
- Float arrays

## Test Execution

### Running Individual Tests

```bash
# Compile a test program
./run.sh examples/valid/program1.c

# View generated assembly
cat compiler-bin/a.frisc

# Run on FRISC simulator
node node_modules/friscjs/consoleapp/frisc-console.js compiler-bin/a.frisc
```

### Batch Testing

```bash
# Generate HTML reports for all examples
java -cp "cli/target/ccompiler.jar" hr.fer.ppj.examples.ExamplesReportGenerator

# Reports generated:
# - examples/report_valid.html
# - examples/report_invalid.html
```

## Example Program Structure

### Minimal Program

```c
int main(void) {
    return 0;
}
```

### Function with Parameters

```c
int add(int a, int b) {
    return a + b;
}

int main(void) {
    return add(3, 4);
}
```

### Control Flow

```c
int main(void) {
    int x = 5;
    if (x > 0) {
        return 1;
    } else {
        return 0;
    }
}
```

### Loops

```c
int main(void) {
    int sum = 0;
    int i;
    for (i = 1; i <= 10; i++) {
        sum = sum + i;
    }
    return sum;
}
```

## Test Results Summary

### Successfully Compiled Programs

**74 out of 90 valid programs** (82.2% success rate)

**Supported Features**:
- ✅ Basic functions and variables
- ✅ Control flow (if, while, for)
- ✅ Arithmetic and logical operations
- ✅ Function calls with parameters
- ✅ Arrays (basic operations)
- ✅ Pointers (basic operations)
- ✅ Stack management
- ✅ Calling conventions

### Unsupported Features

**16 programs fail** due to unsupported features:

- **Float types** (4 programs): Full float support not yet implemented
- **Struct types** (4 programs): Struct member access not yet implemented
- **Advanced pointers** (8 programs): Pointer arithmetic, complex pointer operations

## Further Reading

- **[Test Strategy](test-strategy.md)**: Testing methodology
- **[Debugging Workflow](debugging-workflow.md)**: Debugging techniques
- **[Code Generation](../07-code-generation/instruction-selection.md)**: How programs are compiled

---

*Example programs provide comprehensive coverage of compiler features and serve as regression tests for compiler correctness.*
