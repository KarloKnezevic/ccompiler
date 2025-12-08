# Basic Optimizations

## Overview

This document describes **optimization techniques** that can be applied to improve the quality of generated code. **Code optimization** is the process of transforming generated code to make it more efficient—using fewer instructions, fewer memory accesses, or less execution time—while preserving program semantics (correctness).

While the current PPJ compiler prioritizes **correctness over optimization** (ensuring that generated code is semantically correct rather than optimally efficient), understanding optimization techniques is important for:
- **Future Enhancements**: The compiler can be extended with optimization passes
- **Code Quality**: Understanding what optimizations are possible helps evaluate generated code quality
- **Educational Value**: Learning optimization techniques is part of compiler construction education
- **Performance Analysis**: Understanding optimizations helps identify performance bottlenecks

Optimizations can be applied at different levels and stages of compilation:
- **Source-Level Optimizations**: Transformations on the source code or AST before code generation
- **IR-Level Optimizations**: Transformations on intermediate representations
- **Target-Level Optimizations**: Transformations on generated assembly code

This document focuses on **basic optimizations** that are relatively straightforward to implement and provide significant benefits. More advanced optimizations (such as register allocation algorithms, data flow analysis, and inter-procedural optimizations) are beyond the scope of this document but represent future enhancement opportunities.

## Optimization Categories

### Peephole Optimizations

**Definition**: Local optimizations that examine small instruction sequences

**Examples**:
- Remove redundant loads/stores
- Eliminate unnecessary moves
- Simplify arithmetic operations

**Implementation**: Post-code-generation pass examining instruction windows

### Local Optimizations

**Definition**: Optimizations within basic blocks (straight-line code)

**Examples**:
- Constant folding
- Common subexpression elimination
- Dead code elimination

**Scope**: Single basic block

### Global Optimizations

**Definition**: Optimizations across basic blocks and functions

**Examples**:
- Global dead code elimination
- Loop optimizations
- Inter-procedural optimizations

**Scope**: Entire function or program

## Constant Folding

### Concept

**Constant folding** is an optimization that evaluates **constant expressions** (expressions whose values can be determined at compile time) and replaces them with their computed values. This eliminates runtime computation for expressions that can be evaluated statically.

Constant folding is one of the simplest and most effective optimizations. It requires no complex analysis—only the ability to recognize constant expressions and perform arithmetic operations at compile time.

### Why Constant Folding Matters

Consider the expression `3 + 4`. Without constant folding, the compiler would generate code that:
1. Loads the constant 3 into a register
2. Loads the constant 4 into a register
3. Performs addition
4. Stores the result

With constant folding, the compiler recognizes that `3 + 4` always equals `7`, so it generates code that simply loads the constant 7. This saves instructions and execution time.

### Implementation

**Algorithm**: Constant folding can be implemented during semantic analysis or code generation:

1. **Identify Constant Expressions**: An expression is constant if all its operands are compile-time constants (literals or const-qualified variables with constant initializers).

2. **Evaluate at Compile Time**: Perform the operation using the compiler's own arithmetic (not generated code). For example, evaluate `3 + 4` to get `7`.

3. **Replace with Computed Constant**: Replace the expression node in the AST with a literal node containing the computed value.

**Example Transformations**:

**Simple Arithmetic**:
```c
// Before optimization
int x = 3 + 4;

// After constant folding
int x = 7;
```

**Complex Expressions**:
```c
// Before optimization
int result = 2 * 3 + 4;

// After constant folding (respecting operator precedence)
int result = 10;  // (2 * 3) + 4 = 6 + 4 = 10
```

**Nested Expressions**:
```c
// Before optimization
int x = (5 + 3) * (2 + 1);

// After constant folding
int x = 24;  // 8 * 3 = 24
```

**Boolean Expressions**:
```c
// Before optimization
int flag = (3 > 5) && (2 < 4);

// After constant folding
int flag = 0;  // false && true = false
```

### Limitations and Considerations

**Compile-Time Constants Only**: Constant folding only applies to expressions that can be evaluated at compile time. Expressions involving variables, function calls, or memory accesses cannot be folded.

**Overflow Semantics**: When folding arithmetic expressions, the compiler must respect language overflow semantics. For example, if `int` addition overflows, the folded result should match what would happen at runtime (which may involve wraparound behavior).

**Floating-Point Precision**: For floating-point constant folding, the compiler must ensure that compile-time evaluation produces the same result as runtime evaluation, accounting for floating-point precision issues.

**Side Effects**: Constant folding must not eliminate expressions with side effects. For example, `x++` has a side effect (modifying `x`) and cannot be folded, even if `x` is a constant.

### Benefits

Constant folding provides several benefits:
- **Reduced Code Size**: Eliminates instructions for constant computations
- **Improved Performance**: Removes runtime computation overhead
- **Simpler Generated Code**: Makes generated code easier to read and understand
- **Enables Further Optimizations**: Constant-folded expressions may enable other optimizations (like dead code elimination if the result is unused)

### Implementation Example

Here's how constant folding might be implemented as an AST transformation:

```text
function foldConstants(node):
    if node is BinaryExpression:
        left = foldConstants(node.left)
        right = foldConstants(node.right)
        
        if left is Constant and right is Constant:
            value = evaluate(left.value, node.operator, right.value)
            return new ConstantNode(value)
        else:
            return new BinaryExpression(left, node.operator, right)
    
    else if node is UnaryExpression:
        operand = foldConstants(node.operand)
        
        if operand is Constant:
            value = evaluate(node.operator, operand.value)
            return new ConstantNode(value)
        else:
            return new UnaryExpression(node.operator, operand)
    
    else:
        return node  // No folding possible
```

This recursive algorithm traverses the AST, folding constant subexpressions bottom-up. When both operands of a binary expression are constants, the expression is evaluated and replaced with a constant node.

## Constant Propagation

### Concept

Propagate constant values through variables:

**Before**:
```c
int x = 5;
int y = x + 1;  // y = 6
```

**After**:
```c
int x = 5;
int y = 6;  // Propagated constant
```

### Implementation

**Algorithm**:
1. Track constant assignments
2. Replace variable uses with constants
3. Stop propagation at variable modifications

**Benefits**:
- Enables further constant folding
- Reduces register pressure

## Dead Code Elimination

### Concept

Remove code that never executes:

**Examples**:
- Code after unconditional return
- Unreachable branches
- Unused variable assignments

**Before**:
```c
int x = 5;
return 0;
x = 10;  // Dead code
```

**After**:
```c
return 0;
```

### Implementation

**Algorithm**:
1. Identify unreachable code
2. Remove unreachable instructions
3. Update control flow graph

**Benefits**:
- Reduces code size
- Improves execution speed

## Common Subexpression Elimination

### Concept

Reuse computed expressions:

**Before**:
```c
int x = a + b;
int y = a + b;  // Recomputed
```

**After**:
```c
int temp = a + b;
int x = temp;
int y = temp;
```

### Implementation

**Algorithm**:
1. Identify identical expressions
2. Compute once, store in temporary
3. Reuse temporary for subsequent uses

**Scope**: Can be local (basic block) or global (function)

## Register Allocation

### Concept

Efficiently allocate registers to variables:

**Current Strategy**: Simple allocation (no optimization)

**Future Enhancement**: Graph coloring register allocation

**Benefits**:
- Reduces memory accesses
- Improves performance

### Spilling

When registers exhausted:
1. Spill variable to stack
2. Reload when needed
3. Minimize spill/reload pairs

## Loop Optimizations

### Loop Invariant Code Motion

**Concept**: Move invariant computations outside loops

**Before**:
```c
for (int i = 0; i < n; i++) {
    result = x * y + i;  // x * y is invariant
}
```

**After**:
```c
int temp = x * y;
for (int i = 0; i < n; i++) {
    result = temp + i;
}
```

### Strength Reduction

**Concept**: Replace expensive operations with cheaper ones

**Example**: Multiplication by power of 2 → shift

**Before**:
```c
x = y * 8;
```

**After**:
```c
x = y << 3;  // Shift is cheaper than multiply
```

## Instruction Selection

### Optimal Instruction Sequences

**Concept**: Select best instruction sequence for operations

**Example**: Multiplication by constant

**Before**:
```assembly
MOVE 10, R1
CALL F_MUL
```

**After** (if constant is power of 2):
```assembly
SHL R0, 3, R0  ; Multiply by 8
ADD R0, R0, R1  ; Multiply by 2
ADD R1, R0, R0  ; Total: multiply by 10
```

## Future Optimization Opportunities

### Planned Optimizations

1. **Constant Folding**: Evaluate constant expressions
2. **Dead Code Elimination**: Remove unreachable code
3. **Register Allocation**: Graph coloring algorithm
4. **Loop Optimizations**: Invariant code motion, strength reduction

### Advanced Optimizations

1. **Inlining**: Replace function calls with function bodies
2. **Tail Call Optimization**: Convert tail recursion to iteration
3. **Inter-procedural Analysis**: Cross-function optimizations
4. **Profile-Guided Optimization**: Use execution profiles

## Optimization Passes

### Pass Order

Recommended optimization pass order:

1. **Constant Folding**: Evaluate constants early
2. **Constant Propagation**: Propagate constants
3. **Dead Code Elimination**: Remove dead code
4. **Common Subexpression Elimination**: Reuse expressions
5. **Register Allocation**: Allocate registers
6. **Instruction Selection**: Select optimal instructions
7. **Peephole Optimization**: Final cleanup

### Implementation Strategy

**Phase 1**: Implement on AST (high-level optimizations)
**Phase 2**: Implement on IR (mid-level optimizations)
**Phase 3**: Implement on assembly (low-level optimizations)

## Further Reading

- **[Code Generation](../07-code-generation/instruction-selection.md)**: Current code generation approach
- **[Target Architecture](../07-code-generation/target-architecture-overview.md)**: FRISC architecture details

---

*Optimizations improve code quality and performance, but correctness must always be maintained. Future enhancements will add optimization passes while preserving semantic correctness.*
