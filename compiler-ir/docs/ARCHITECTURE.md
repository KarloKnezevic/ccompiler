# Compiler-IR Module Architecture

## Overview

The `compiler-ir` module generates Intermediate Representation (IR) from semantic analysis results. The IR is a low-level, structured representation suitable for code generation.

## Package Structure

```
hr.fer.ppj.ir/
├── api/                    # Public API (consumed by other modules)
│   └── IrPipeline.java     # Main entry point for IR generation
├── model/                  # IR AST/model (immutable data structures)
│   ├── IrProgram.java
│   ├── IrFunction.java
│   ├── IrBlock.java
│   ├── IrInstruction.java
│   └── ...
├── builder/                # Builders and factories for IR construction
│   ├── IrFunctionBuilder.java
│   ├── TempFactory.java
│   ├── LabelFactory.java
│   └── TypeMapper.java
├── lowering/               # Lowering pipeline orchestration
│   ├── ProgramGenerator.java
│   ├── FunctionGenerator.java
│   ├── FunctionContext.java
│   └── LoopContext.java
├── generators/             # Specialized generators
│   ├── expression/         # Expression generators
│   │   ├── LValueGenerator.java
│   │   ├── RValueGenerator.java
│   │   ├── PrimaryExpressionGenerator.java
│   │   ├── BinaryExpressionGenerator.java
│   │   └── ...
│   └── statement/          # Statement generators
│       ├── StatementGenerator.java
│       ├── IfStatementGenerator.java
│       ├── LoopStatementGenerator.java
│       └── JumpStatementGenerator.java
├── format/                 # IR formatting/printing
│   └── IrPrettyPrinter.java
├── validation/             # IR verification
│   └── IrVerifier.java
├── types/                  # IR type system
│   ├── IrType.java
│   ├── IrPrimitiveType.java
│   ├── IrPointerType.java
│   └── ...
└── util/                   # Shared utilities
    ├── VariableNameManager.java
    ├── AddressReuseContext.java
    └── ConstantEvaluator.java
```

## Key Components

### API Layer (`api/`)

**IrPipeline**: Public facade for IR generation
- `generate(SymbolTable, NonTerminalNode)`: Generates IR from semantic tree
- `print(IrProgram)`: Pretty-prints IR to string
- `verify(IrProgram)`: Verifies IR correctness

**Stability**: This is the public API. Changes here affect other modules.

### Model Layer (`model/`)

Immutable data structures representing the IR:
- `IrProgram`: Root program (globals, structs, functions)
- `IrFunction`: Function definition (params, frame, slots, blocks)
- `IrBlock`: Basic block (label, instructions, terminator)
- `IrInstruction`: Instructions (assign, store, void call)
- `IrTerminator`: Terminators (br, jmp, ret)
- `IrValue`: Values (temp, const)
- `IrRhs`: Right-hand side operations

**Stability**: Core IR model. Changes require careful consideration.

### Lowering Layer (`lowering/`)

Orchestrates the lowering process:

**ProgramGenerator**: Top-level generator
- Generates translation units
- Routes external declarations to specialized generators

**FunctionGenerator**: Function-level generation
- Generates function definitions
- Manages function context
- Coordinates frame/slot computation

**FunctionContext**: Function-level state
- Function builder, scope, return type
- Local offset, logical result counter

**LoopContext**: Loop state
- Exit/continue labels for break/continue

### Generator Layer (`generators/`)

Specialized generators for expressions and statements:

**Expression Generators**:
- `LValueGenerator`: Generates l-values (addresses)
- `RValueGenerator`: Generates r-values (values)
- `PrimaryExpressionGenerator`: Literals, identifiers, parenthesized
- `BinaryExpressionGenerator`: Binary operations
- `UnaryExpressionGenerator`: Unary operations
- `CallExpressionGenerator`: Function calls

**Statement Generators**:
- `StatementGenerator`: Routes statements to specialized generators
- `IfStatementGenerator`: If/else statements
- `LoopStatementGenerator`: While/for/do-while loops
- `JumpStatementGenerator`: Return/break/continue

### Utility Layer (`util/`)

Shared utilities:
- `VariableNameManager`: Variable name mapping for shadowing
- `AddressReuseContext`: Address reuse optimization
- `ConstantEvaluator`: Constant folding
- `TypePromoter`: Type promotion/conversion

## Design Principles

### Single Responsibility Principle (SRP)

Each class has one reason to change:
- `LValueGenerator` only generates l-values
- `IfStatementGenerator` only generates if statements
- `VariableNameManager` only manages variable names

### Open/Closed Principle (OCP)

Extensibility through composition:
- New expression types: Add new generator class
- New statement types: Add new generator class
- New optimizations: Add new pass class

### Dependency Inversion Principle (DIP)

Dependencies point inward:
- Generators depend on model (not vice versa)
- Lowering depends on generators (not vice versa)
- API depends on lowering (not vice versa)

## Adding a New Lowering Pass

1. Create a new class in `lowering/passes/`:
   ```java
   public final class MyPass {
     public void run(IrProgram program) {
       // Transform IR
     }
   }
   ```

2. Register in `LoweringPipeline`:
   ```java
   pipeline.addPass(new MyPass());
   ```

3. Add tests in `test/` directory

## Adding a New Instruction/Type

1. Add to model (`model/` or `types/`):
   ```java
   public record MyInstruction(...) implements IrInstruction {
     // ...
   }
   ```

2. Update `IrPrettyPrinter` to print it
3. Update `IrVerifier` to verify it
4. Update generators to emit it

## Code Size Limits

- **Maximum class size**: 200 lines (excluding comments/blank lines)
- **Maximum method size**: ~40 lines
- If a class approaches the limit, split it:
  - Extract related methods into a new class
  - Use composition to delegate

## Testing Strategy

- **Unit tests**: Test individual generators in isolation
- **Golden tests**: Compare generated IR with expected output
- **Integration tests**: Test full pipeline end-to-end

Run tests after each refactoring step to catch regressions early.

## Migration Notes

The original `IrGenerator` class (4212 lines) is being refactored into:
- `ProgramGenerator`: Program-level generation
- `FunctionGenerator`: Function-level generation
- Expression generators: Expression lowering
- Statement generators: Statement lowering
- Utilities: Shared helpers

This refactoring maintains the same public API (`IrPipeline`) while improving internal structure.
