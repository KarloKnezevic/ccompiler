# Code Generation Module Structure and Internals

This document provides a comprehensive guide to the internal structure, organization, and implementation details of the `compiler-codegen` module. It covers package organization, class responsibilities, code generation rules, and architectural patterns.

**Author**: Karlo Knežević

## Table of Contents

- [Module Overview](#module-overview)
- [Package Structure](#package-structure)
- [Core Components](#core-components)
- [Expression Generation Architecture](#expression-generation-architecture)
- [Code Generation Rules and Conventions](#code-generation-rules-and-conventions)
- [Stack Frame Management](#stack-frame-management)
- [Label Generation Strategy](#label-generation-strategy)
- [Code Emission System](#code-emission-system)
- [Helper Functions](#helper-functions)
- [Global Variable Handling](#global-variable-handling)
- [Extension Points](#extension-points)

## Module Overview

The `compiler-codegen` module is responsible for transforming semantically validated C programs into executable FRISC assembly code. It operates as the final phase of the compilation pipeline, taking the annotated abstract syntax tree (AST) and global symbol table from semantic analysis and producing a complete FRISC assembly program.

### Module Dependencies

```
compiler-codegen
├── compiler-semantics (for SymbolTable, TypeSystem, AST nodes)
└── Standard Java libraries only
```

### Key Design Principles

1. **Modularity**: Clear separation of concerns with dedicated generators for different language constructs
2. **Immutability**: Context objects and value types are immutable where possible
3. **Single Responsibility**: Each class has one clear purpose
4. **Visitor Pattern**: Systematic AST traversal using recursive descent
5. **Type-Directed Generation**: Code generation strategies based on type information

## Package Structure

The module is organized into logical packages based on responsibility. Each package has a `package-info.java` file documenting its purpose, grammar rules handled, and FRISC semantics.

```
hr.fer.ppj.codegen/
├── package-info.java                    # Main package documentation
├── CodeGenerator.java                    # Main orchestrator
├── CodeGenContext.java                   # Shared state management (immutable record)
├── CodeGenerationException.java          # Error handling
├── GlobalVariableGenerator.java          # Global data generation
│
├── emitter/                              # Code emission
│   ├── package-info.java                 # Emission package documentation
│   └── FriscEmitter.java                 # Assembly output formatting + large immediate handling
│
├── model/                                # Domain models
│   ├── package-info.java                 # Model package documentation
│   └── ActivationRecord.java             # Stack frame layout management
│
├── util/                                 # Utilities
│   ├── package-info.java                 # Utility package documentation
│   └── LabelGenerator.java               # Unique label generation
│
├── frisc/                                # FRISC-specific
│   ├── package-info.java                 # FRISC package documentation
│   └── HelperFunctionGenerator.java      # F_MUL, F_DIV helpers
│
├── func/                                 # Function generation
│   ├── package-info.java                 # Function package documentation
│   ├── FunctionCodeGenerator.java        # Function orchestrator
│   ├── FunctionInfoExtractor.java        # Extract function names, parameters, local variables
│   └── FunctionPrologueEpilogueGenerator.java # Generate prologue/epilogue code
│
├── stmt/                                 # Statement generation
│   ├── package-info.java                 # Statement package documentation
│   ├── StatementCodeGenerator.java       # Statement orchestrator
│   ├── BranchingStatementGenerator.java  # If-else statements
│   ├── LoopStatementGenerator.java       # While and for loops
│   ├── JumpStatementGenerator.java       # Return, break, continue
│   └── LocalDeclarationGenerator.java   # Local variable declarations
│
├── expr/                                 # Expression generation
│   ├── package-info.java                 # Expression package documentation
│   ├── ExpressionCodeGenerator.java      # Expression orchestrator
│   │
│   ├── binary/                           # Binary operations
│   │   └── BinaryExpressionGenerator.java
│   │
│   ├── logical/                          # Logical operations
│   │   └── LogicalExpressionGenerator.java
│   │
│   ├── unary/                            # Unary operations
│   │   └── UnaryExpressionGenerator.java
│   │
│   ├── assignment/                       # Assignment operations
│   │   └── AssignmentExpressionGenerator.java
│   │
│   ├── array/                            # Array operations
│   │   └── ArrayExpressionGenerator.java
│   │
│   ├── call/                             # Function calls
│   │   └── FunctionCallGenerator.java
│   │
│   └── primary/                          # Primary expressions
│       └── PrimaryExpressionGenerator.java
│
└── global/                               # Global variable utilities
    ├── package-info.java                 # Global package documentation
    ├── InitializerExtractor.java         # Parse tree initializer extraction
    └── ArraySizeExtractor.java           # Array size extraction
```

### Package Documentation

All packages now include comprehensive `package-info.java` files that document:
- Package purpose and responsibilities
- Grammar rules handled by classes in the package
- FRISC semantics and conventions
- Key classes and their roles

## Core Components

### CodeGenerator

**Location**: `hr.fer.ppj.codegen.CodeGenerator`

**Responsibility**: Main entry point and orchestrator for the entire code generation process.

**Key Methods**:
- `generate(SymbolTable, NonTerminalNode, Path)`: Main generation method
- `generateProgramInit(CodeGenContext)`: Generates program entry point
- `processTranslationUnit(CodeGenContext, NonTerminalNode)`: Processes top-level declarations

**Generation Flow**:
1. Initialize `FriscEmitter` and `LabelGenerator`
2. Create `CodeGenContext` with global scope
3. Generate program initialization (stack setup, main call, halt)
4. Process translation unit (functions and globals)
5. Generate helper functions if needed (F_MUL, F_DIV)
6. Write final assembly to file

**Example**:
```java
CodeGenerator codeGen = new CodeGenerator();
codeGen.generate(globalScope, parseTree, Paths.get("a.frisc"));
```

### CodeGenContext

**Location**: `hr.fer.ppj.codegen.CodeGenContext`

**Responsibility**: Immutable context object carrying shared state throughout code generation.

**Fields**:
- `globalScope`: Global symbol table
- `emitter`: FRISC code emitter
- `labelGenerator`: Unique label generator
- `activationRecord`: Current function's stack frame (null for global scope)
- `functionExitLabel`: Label for function exit (for return statements)
- `loopBreakLabel`: Label for loop break (for break statements)
- `loopContinueLabel`: Label for loop continue (for continue statements)

**Immutability**: Context is immutable; new contexts are created for nested scopes using builder methods:
- `withActivationRecord(ActivationRecord)`: Create context for function body
- `withFunctionExitLabel(String)`: Create context with exit label
- `withLoopLabels(String, String)`: Create context with loop labels

**Usage Pattern**:
```java
// Global context
CodeGenContext globalContext = new CodeGenContext(globalScope, emitter, labelGen, null, null, null, null);

// Function context
ActivationRecord ar = new ActivationRecord();
CodeGenContext functionContext = globalContext.withActivationRecord(ar);

// Loop context
CodeGenContext loopContext = functionContext.withLoopLabels(breakLabel, continueLabel);
```

### FriscEmitter

**Location**: `hr.fer.ppj.codegen.emitter.FriscEmitter`

**Responsibility**: Formats and emits FRISC assembly instructions with proper syntax and comments.

**Key Features**:
- Buffers all output in memory until `writeToFile()` is called
- Formats instructions with consistent indentation (8 spaces)
- Aligns comments to column 32
- Tracks which helper functions are needed (F_MUL, F_DIV)

**Key Methods**:
- `emitInstruction(String mnemonic, String operand1, String operand2, String comment)`: Emit instruction
- `emitLabel(String label, String comment)`: Emit label
- `emitData(String label, String directive, String value, String comment)`: Emit data declaration
- `emitComment(String comment)`: Emit standalone comment
- `markMulNeeded()` / `markDivNeeded()`: Mark helper functions as needed
- `writeToFile(Path)`: Write all buffered code to file

**Formatting Rules**:
- Instructions: 8-space indent, operands separated by commas, comments at column 32
- Labels: Left-aligned, no indent
- Comments: Prefixed with `;`, aligned to column 32
- Data declarations: Label, directive (DW/DS), value, comment

**Example Output**:
```assembly
        MOVE 40000, R7      ; Initialize stack pointer
        CALL F_MAIN         ; Call main function
        HALT                ; End program

F_MAIN:                     ; Function: int main(void)
        SUB R7, 4, R7       ; Allocate local variable
        MOVE 42, R0         ; Load constant 42
        STORE R0, (R7+0)    ; Store to local variable
        LOAD R0, (R7+0)     ; Load local variable
        MOVE R0, R6         ; Set return value
        ADD R7, 4, R7       ; Deallocate local variable
        RET                 ; Return to caller
```

### LabelGenerator

**Location**: `hr.fer.ppj.codegen.util.LabelGenerator`

**Responsibility**: Generates unique labels following consistent naming conventions.

**Label Types**:
- **Function labels**: `F_<FUNCTION_NAME>` (e.g., `F_MAIN`, `F_FACTORIAL`)
- **Global variable labels**: `G_<VARIABLE_NAME>` (e.g., `G_COUNTER`, `G_ARRAY`)
- **Control flow labels**: `L_<TYPE>_<NUMBER>` (e.g., `L_IF_1`, `L_LOOP_START_1`)

**Key Methods**:
- `getFunctionLabel(String name)`: Get function label
- `getGlobalVariableLabel(String name)`: Get global variable label
- `generateLabel()`: Generate unique generic label
- `generateLabel(String prefix)`: Generate label with prefix

**Uniqueness Guarantee**: All generated labels are tracked to ensure uniqueness across the entire program.

## Expression Generation Architecture

The expression generation system uses a hierarchical delegation pattern where `ExpressionCodeGenerator` orchestrates specialized generators for different expression types.

### ExpressionCodeGenerator

**Location**: `hr.fer.ppj.codegen.expr.ExpressionCodeGenerator`

**Responsibility**: Main orchestrator for expression code generation. Delegates to specialized generators based on expression type.

**Delegation Pattern**:
```java
public void generateExpression(NonTerminalNode expression) {
    switch (expression.symbol()) {
        case "<izraz_pridruzivanja>" -> assignmentGenerator.generateAssignmentExpression(expression);
        case "<log_ili_izraz>" -> logicalGenerator.generateLogicalOrExpression(expression);
        case "<log_i_izraz>" -> logicalGenerator.generateLogicalAndExpression(expression);
        case "<bin_ili_izraz>" -> binaryGenerator.generateBitwiseOrExpression(expression);
        // ... etc
    }
}
```

**Specialized Generators**:
- `BinaryExpressionGenerator`: Arithmetic, bitwise, relational, equality operations
- `LogicalExpressionGenerator`: Logical AND/OR with short-circuit evaluation
- `UnaryExpressionGenerator`: Unary operators (+, -, !, ~) and type casts
- `AssignmentExpressionGenerator`: Assignment and increment/decrement
- `ArrayExpressionGenerator`: Array indexing and array assignments
- `FunctionCallGenerator`: Function calls with argument evaluation
- `PrimaryExpressionGenerator`: Identifiers, constants, parenthesized expressions

### BinaryExpressionGenerator

**Location**: `hr.fer.ppj.codegen.expr.binary.BinaryExpressionGenerator`

**Responsibility**: Generates code for binary operations (arithmetic, bitwise, relational, equality).

**Supported Operations**:
- Arithmetic: `+`, `-`, `*`, `/`, `%`
- Bitwise: `&`, `|`, `^`, `<<`, `>>`
- Relational: `<`, `>`, `<=`, `>=`
- Equality: `==`, `!=`

**Evaluation Strategy**:
1. Evaluate left operand (result in R0)
2. Push left operand to stack
3. Evaluate right operand (result in R0)
4. Move right operand to R1
5. Pop left operand to R0
6. Generate operation (R0 op R1 → R0)

**Special Cases**:
- Multiplication: Calls `F_MUL` helper function (FRISC has no MUL instruction)
- Division: Calls `F_DIV` helper function (FRISC has no DIV instruction)
- Comparisons: Generate conditional jumps with boolean result (0 or 1)

### LogicalExpressionGenerator

**Location**: `hr.fer.ppj.codegen.expr.logical.LogicalExpressionGenerator`

**Responsibility**: Generates code for logical AND (`&&`) and OR (`||`) with short-circuit evaluation.

**Short-Circuit Evaluation**:
- **AND (`&&`)**: If left operand is false, skip right operand evaluation
- **OR (`||`)**: If left operand is true, skip right operand evaluation

**Implementation Pattern**:
```java
// Logical AND: left && right
evaluateExpression(left, context);
CMP R0, 0
JP_EQ falseLabel          // Short-circuit if left is false
evaluateExpression(right, context);
CMP R0, 0
JP_EQ falseLabel          // Right is false
MOVE 1, R0                // Both true
JP endLabel
falseLabel:
MOVE 0, R0                // Either false
endLabel:
```

### AssignmentExpressionGenerator

**Location**: `hr.fer.ppj.codegen.expr.assignment.AssignmentExpressionGenerator`

**Responsibility**: Generates code for assignment operations and increment/decrement.

**Supported Operations**:
- Simple assignment: `=`
- Pre-increment: `++x`
- Pre-decrement: `--x`
- Post-increment: `x++`
- Post-decrement: `x--`

**L-Value Handling**:
- Simple variables: Direct store to stack offset or global label
- Array elements: Calculate element address, then store

**Increment/Decrement Strategy**:
- Pre-increment/decrement: Load, modify, store, use modified value
- Post-increment/decrement: Load, use value, modify, store

### ArrayExpressionGenerator

**Location**: `hr.fer.ppj.codegen.expr.array.ArrayExpressionGenerator`

**Responsibility**: Generates code for array indexing and array element access.

**Array Indexing**:
1. Calculate element address: `base_address + (index * element_size)`
2. For reads: Load from calculated address
3. For writes: Store to calculated address

**Address Calculation**:
- Local arrays: `R7 + offset + (index * element_size)`
- Global arrays: `G_ARRAY + (index * element_size)`
- Element size: 4 bytes for `int`, 1 byte for `char`

### FunctionCallGenerator

**Location**: `hr.fer.ppj.codegen.expr.call.FunctionCallGenerator`

**Responsibility**: Generates code for function calls with proper argument passing.

**Calling Convention**:
1. Evaluate arguments in left-to-right order
2. Push arguments to stack in reverse order (right-to-left)
3. Call function with `CALL` instruction
4. Clean up arguments from stack (caller responsibility)
5. Move return value from R6 to R0 for expression evaluation

**Argument Passing**:
- All arguments are 4 bytes (32 bits)
- Arguments pushed in reverse order (rightmost first)
- Stack cleanup after return: `ADD R7, (arg_count * 4), R7`

## Code Generation Rules and Conventions

### Register Usage

**Standard Register Allocation**:
- **R0**: Primary accumulator for expression evaluation
- **R1**: Secondary operand for binary operations
- **R2-R5**: Temporary registers for complex expressions
- **R6**: Function return values (by convention)
- **R7**: Stack pointer (SP) - managed automatically by FRISC

**Register Spilling**: When registers are exhausted, values are pushed to stack using `PUSH` and restored with `POP`.

### Stack Frame Layout

**Frame Pointer Convention**: Uses R5 as frame pointer (fixed during function execution).

**Stack Layout** (after local allocation):
```
Higher addresses
+----------------+
| Parameter n    | R5 + (8 + (n-1)*4)
| ...            |
| Parameter 1    | R5 + 8
| Return address | R5 + 4
| Old R5         | R5 + 0
+----------------+ <- R5 (frame pointer)
| Local var 1    | R5 - 4
| Local var 2    | R5 - 8
| ...            |
| Local var n    | R5 - (n*4)
+----------------+ <- R7 (stack pointer)
Lower addresses
```

**Offset Calculation**:
- Parameters: Positive offsets from R5 (R5+8, R5+12, etc.)
- Local variables: Negative offsets from R5 (R5-4, R5-8, etc.)
- All offsets formatted as hexadecimal in assembly output

### Function Calling Convention

**Caller Responsibilities**:
1. Evaluate arguments in left-to-right order
2. Push arguments to stack in reverse order (right-to-left)
3. Execute `CALL` instruction
4. Clean up arguments from stack after return
5. Use return value from R6

**Callee Responsibilities**:
1. Save old R5 (frame pointer) at R5+0
2. Set R5 = R7 (establish frame pointer)
3. Allocate space for local variables: `SUB R7, local_size, R7`
4. Access parameters via positive offsets from R5
5. Access local variables via negative offsets from R5
6. Place return value in R6
7. Deallocate locals: `ADD R7, local_size, R7`
8. Restore old R5 and return via `RET`

### Label Naming Conventions

**Function Labels**: `F_<FUNCTION_NAME>`
- Uppercase function name
- Example: `F_MAIN`, `F_FACTORIAL`, `F_CALCULATE`

**Global Variable Labels**: `G_<VARIABLE_NAME>`
- Uppercase variable name
- Example: `G_COUNTER`, `G_ARRAY`, `G_BUFFER`

**Control Flow Labels**: `L_<TYPE>_<NUMBER>`
- Type prefix: `IF`, `ELSE`, `END`, `LOOP_START`, `LOOP_END`, `LOOP_CONTINUE`, `SC` (short-circuit)
- Unique number for each label
- Example: `L_IF_1`, `L_ELSE_1`, `L_LOOP_START_1`

### Type Handling

**Type Sizes**:
- `int`: 4 bytes (32 bits)
- `char`: 4 bytes (stored as 32-bit value, lower 8 bits used)
- Arrays: `element_size * element_count` bytes

**Type Conversions**:
- `char` to `int`: Implicit (sign extension handled by FRISC load operations)
- `int` to `char`: Truncation to lower 8 bits: `AND R0, 255, R0`

**Type-Directed Generation**:
- Code generation strategies vary based on operand types
- Type information from semantic analysis drives generation decisions

## Stack Frame Management

### ActivationRecord

**Location**: `hr.fer.ppj.codegen.model.ActivationRecord`

**Responsibility**: Manages stack frame layout for a single function, tracking parameter and local variable offsets.

**Key Methods**:
- `addParameter(String name)`: Add parameter, return offset (positive, relative to R5)
- `addLocalVariable(String name)`: Add local variable, return offset (negative, relative to R5)
- `addLocalVariable(String name, int size)`: Add local variable with custom size
- `getVariableOffset(String name)`: Get stack offset for variable
- `getTotalLocalSize()`: Get total size of all local variables

**Offset Management**:
- Parameters start at R5+8 (after old R5 at +0 and return address at +4)
- Local variables start at R5-4 and grow downward
- All offsets are relative to frame pointer R5

### Stack Allocation

**Function Prolog**:
```assembly
F_FUNCTION:
        PUSH R5                 ; Save old frame pointer
        MOVE R7, R5             ; Establish new frame pointer
        SUB R7, local_size, R7  ; Allocate local variables
```

**Function Epilog**:
```assembly
        ADD R7, local_size, R7  ; Deallocate local variables
        POP R5                  ; Restore old frame pointer
        RET                     ; Return to caller
```

## Code Emission System

### Instruction Formatting

**Standard Format**:
```
        MNEMONIC operand1, operand2    ; comment
```

**Components**:
- Indentation: 8 spaces for instructions
- Mnemonic: FRISC instruction name (uppercase)
- Operands: Comma-separated, formatted according to FRISC syntax
- Comment: Aligned to column 32, prefixed with `;`

**Operand Formatting**:
- Immediate values: Decimal or hexadecimal (with `%D` or `%H` prefix)
- Registers: `R0`, `R1`, ..., `R7`
- Memory addresses: `(R7+offset)` or `(label)`
- Labels: Function labels, variable labels, control flow labels

### Data Declaration Formatting

**Standard Format**:
```
LABEL   DIRECTIVE value    ; comment
```

**Directives**:
- `DW`: Define word (4 bytes) - for integers and initialized data
- `DS`: Define storage (allocate space) - for uninitialized arrays

**Value Formatting**:
- Integer constants: `%D <value>` (decimal) or `%H <value>` (hexadecimal)
- Array initializers: Comma-separated list of values
- String literals: Array of character codes

## Helper Functions

### HelperFunctionGenerator

**Location**: `hr.fer.ppj.codegen.frisc.HelperFunctionGenerator`

**Responsibility**: Generates FRISC helper functions for operations not supported by native FRISC instructions.

**Helper Functions**:
- `F_MUL`: Multiplication (FRISC has no MUL instruction)
- `F_DIV`: Division (FRISC has no DIV instruction)

**Generation Strategy**:
- Helper functions are generated only if needed (tracked by `FriscEmitter`)
- Generated before user functions in the assembly output
- Follow standard FRISC calling convention

**F_MUL Implementation**:
- Uses repeated addition for multiplication
- Handles signed multiplication correctly
- Parameters: R0 (multiplicand), R1 (multiplier)
- Result: R6 (product)

**F_DIV Implementation**:
- Uses repeated subtraction for division
- Handles signed division correctly
- Parameters: R0 (dividend), R1 (divisor)
- Result: R6 (quotient)

## Global Variable Handling

### GlobalVariableGenerator

**Location**: `hr.fer.ppj.codegen.GlobalVariableGenerator`

**Responsibility**: Generates FRISC data declarations for global variables and constants.

**Generation Process**:
1. Iterate through global symbol table
2. For each variable symbol:
   - Generate label using `LabelGenerator`
   - Extract initializer value from parse tree (if present)
   - Generate appropriate data declaration (DW for initialized, DS for uninitialized arrays)
3. Place all global variables at end of program (after functions)

**Initializer Extraction**:
- Uses `InitializerExtractor` to find initializer values in parse tree
- Handles both simple variables and arrays
- Supports integer and character constants
- Handles negative literals and escape sequences

### InitializerExtractor

**Location**: `hr.fer.ppj.codegen.global.InitializerExtractor`

**Responsibility**: Extracts initializer values from parse tree for global variables.

**Extraction Strategy**:
- Recursively searches parse tree for variable declarations
- Navigates from external declarations to initializer expressions
- Extracts constant values (integers, characters)
- Handles array initializer lists

**Supported Initializers**:
- Integer constants: `int x = 42;`
- Character constants: `char c = 'A';`
- Negative literals: `int x = -10;`
- Array initializers: `int arr[] = {1, 2, 3};`
- Escape sequences: `char c = '\n';`

### ArraySizeExtractor

**Location**: `hr.fer.ppj.codegen.global.ArraySizeExtractor`

**Responsibility**: Extracts array size information from parse tree declarations.

**Extraction Strategy**:
- Searches parse tree for array declarators
- Looks for pattern: `IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA`
- Extracts size value from `BROJ` token
- Returns size or 0 if not found

## Extension Points

### Adding New Expression Types

To add support for a new expression type:

1. Create specialized generator class in appropriate subpackage:
   ```java
   package hr.fer.ppj.codegen.expr.newtype;
   
   public final class NewTypeExpressionGenerator {
       private final CodeGenContext context;
       private final ExpressionCodeGenerator exprGen;
       
       public void generateNewTypeExpression(NonTerminalNode expr) {
           // Implementation
       }
   }
   ```

2. Add generator instance to `ExpressionCodeGenerator`:
   ```java
   private final NewTypeExpressionGenerator newTypeGenerator;
   
   public ExpressionCodeGenerator(CodeGenContext context) {
       // ...
       this.newTypeGenerator = new NewTypeExpressionGenerator(context, this);
   }
   ```

3. Add dispatch case in `generateExpression()`:
   ```java
   case "<new_type_izraz>" -> newTypeGenerator.generateNewTypeExpression(expression);
   ```

### Adding New Statement Types

To add support for a new statement type:

1. Create specialized generator class (if complex) or add method to `StatementCodeGenerator`:
   ```java
   package hr.fer.ppj.codegen.stmt;
   
   public final class NewStatementGenerator {
       private final CodeGenContext context;
       private final ExpressionCodeGenerator exprGen;
       private final StatementCodeGenerator stmtGen;
       
       public void generateNewStatement(NonTerminalNode stmt) {
           // Implementation
       }
   }
   ```

2. Add generator instance to `StatementCodeGenerator`:
   ```java
   private final NewStatementGenerator newStmtGen;
   
   public StatementCodeGenerator(CodeGenContext context) {
       // ...
       this.newStmtGen = new NewStatementGenerator(context, exprGen, this);
   }
   ```

3. Add dispatch case in `generateStatement()`:
   ```java
   case "<new_statement>" -> newStmtGen.generateNewStatement(statement);
   ```

### Adding New Helper Functions

To add a new helper function:

1. Add tracking flag to `FriscEmitter`:
   ```java
   private boolean needsNewHelper = false;
   
   public void markNewHelperNeeded() {
       needsNewHelper = true;
   }
   ```

2. Add generation method to `HelperFunctionGenerator`:
   ```java
   public void generateNewHelper(CodeGenContext context) {
       // Implementation
   }
   ```

3. Update `CodeGenerator` to check and generate:
   ```java
   if (emitter.needsNewHelper()) {
       helperGen.generateNewHelper(context);
   }
   ```

## Summary

The `compiler-codegen` module is organized with clear separation of concerns:

- **Orchestration**: `CodeGenerator` coordinates the overall process
- **Expression Generation**: Hierarchical delegation to specialized generators
- **Statement Generation**: Modular delegation to specialized statement generators
- **Function Generation**: Modular approach with separate extractors and generators
- **Infrastructure**: Shared utilities for emission, labeling, and state management

**Key Architectural Patterns**:
- **Orchestrator Pattern**: Main generators (`CodeGenerator`, `ExpressionCodeGenerator`, `StatementCodeGenerator`, `FunctionCodeGenerator`) coordinate specialized generators
- **Delegation Pattern**: Complex operations delegated to focused, single-responsibility classes
- **Extractor Pattern**: Parse tree traversal and information extraction separated from code generation

**Module Statistics**:
- **Expression Generators**: 7 specialized generators (binary, logical, unary, assignment, array, call, primary)
- **Statement Generators**: 4 specialized generators (branching, loop, jump, local declaration)
- **Function Components**: 3 classes (orchestrator, info extractor, prologue/epilogue generator)
- **Total Classes**: ~30+ classes organized across 10+ packages

This architecture provides:
- **Maintainability**: Clear responsibilities and modular design
- **Extensibility**: Easy to add new expression or statement types
- **Testability**: Components can be tested independently
- **Readability**: Well-organized code with comprehensive documentation
- **Modularity**: Each generator class is focused and can be understood in isolation

The module follows Java best practices with immutable value objects, clear naming conventions, and comprehensive Javadoc documentation throughout.

