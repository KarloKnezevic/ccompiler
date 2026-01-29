# Compiler-IR Module: Responsibility Map

## Current State Analysis

### Key Responsibilities
1. **IR Model** (`model/`): IR AST representation (Program, Function, Block, Instruction, Type, Operand)
2. **IR Generation** (`generate/`): Lowering semantic tree to IR (MASSIVE GOD CLASS - 4212 lines)
3. **IR Building** (`build/`): Builders and factories for IR construction
4. **IR Printing** (`print/`): Pretty printer for IR text output (499 lines - could be split)
5. **IR Verification** (`verify/`): Validators and consistency checks (298 lines)
6. **IR Types** (`types/`): Type system representation
7. **Pipeline** (`IrPipeline.java`): Public API facade

### Current Classes & Responsibilities

#### Model Package (`hr.fer.ppj.ir.model`)
- `IrProgram`: Root IR program (globals, structs, functions)
- `IrFunction`: Function definition (params, frame, slots, blocks)
- `IrBlock`: Basic block (label, instructions, terminator)
- `IrInstruction`: Instructions (assign, store, void call)
- `IrTerminator`: Terminators (br, jmp, ret)
- `IrRhs`: Right-hand side operations (251 lines - OK)
- `IrValue`: Values (temp, const)
- `IrTemp`: Temporary variable
- `IrConst`: Constants
- `IrSlot`: Slot declarations (param, local, spill)
- `IrGlobalVar`: Global variable
- `IrStructDef`: Struct type definition
- `IrSymbolRef`: Symbol reference

#### Types Package (`hr.fer.ppj.ir.types`)
- `IrType`: Base type interface
- `IrPrimitiveType`: Primitive types (int32, char, float, bool)
- `IrPointerType`: Pointer types
- `IrArrayType`: Array types
- `IrStructType`: Struct types

#### Build Package (`hr.fer.ppj.ir.build`)
- `IrFunctionBuilder`: Function builder (276 lines - slightly over limit)
- `TempFactory`: Temporary variable factory
- `LabelFactory`: Label factory
- `TypeMapper`: Type mapping utilities (220 lines - OK)

#### Generate Package (`hr.fer.ppj.ir.generate`)
- `IrGenerator`: **GOD CLASS** (4212 lines) - handles everything:
  - Program-level generation (globals, structs, functions)
  - Function generation (frame, slots, blocks)
  - Expression lowering (emitLValue, emitRValue)
  - Statement lowering (if/else, while, for, return)
  - Type conversions and promotions
  - Variable name management
  - Address reuse optimization
  - Constant evaluation

#### Print Package (`hr.fer.ppj.ir.print`)
- `IrPrettyPrinter`: Pretty printer (499 lines - complex formatting logic)

#### Verify Package (`hr.fer.ppj.ir.verify`)
- `IrVerifier`: IR verification (298 lines - OK)

### Code Smells Identified

1. **God Class**: `IrGenerator` (4212 lines) violates SRP
   - Too many responsibilities
   - Hard to test individual parts
   - Hard to maintain and extend

2. **Deep Nesting**: Complex control flow in `IrGenerator`
   - Multiple levels of if/switch statements
   - Hard to follow logic

3. **Duplication**: Similar patterns repeated across methods
   - Expression handling patterns
   - Type conversion patterns

4. **Unclear Naming**: Some methods have unclear purposes
   - `emitRValueBinary` vs `emitRValueBinaryImpl`
   - Multiple overloads with similar names

5. **Large Methods**: Some methods exceed 100+ lines
   - Complex logic that should be extracted

6. **Tight Coupling**: `IrGenerator` depends on many semantic analysis types
   - Should use interfaces/abstractions where possible

### Stable Boundaries (Interfaces)
- `IrProgram`, `IrFunction`, `IrBlock`, `IrInstruction` - IR model (stable)
- `IrPipeline` - Public API (must remain stable)
- `IrType` hierarchy - Type system (stable)

### Volatile Parts (Implementation Details)
- `IrGenerator` - Should be split into focused generators
- Lowering logic - Can be refactored without breaking API
- Internal helpers - Can be reorganized

## Target Package Structure

```
hr.fer.ppj.ir/
├── api/                    # Public interfaces & types (NEW)
│   └── IrPipeline.java     # Public API facade (move from root)
├── model/                  # IR AST/model (EXISTING - keep as-is)
├── builder/                # Builders/factories (RENAME from build/)
├── lowering/               # Lowering pipeline orchestration (NEW)
│   ├── LoweringPipeline.java
│   └── LoweringContext.java
├── passes/                 # Individual lowering passes (NEW)
│   ├── ProgramLoweringPass.java
│   ├── FunctionLoweringPass.java
│   ├── ExpressionLoweringPass.java
│   └── StatementLoweringPass.java
├── generators/             # Expression/statement generators (NEW)
│   ├── expression/
│   │   ├── LValueGenerator.java
│   │   ├── RValueGenerator.java
│   │   ├── PrimaryExpressionGenerator.java
│   │   ├── BinaryExpressionGenerator.java
│   │   └── ...
│   └── statement/
│       ├── IfStatementGenerator.java
│       ├── LoopStatementGenerator.java
│       └── ...
├── format/                 # Pretty printer (RENAME from print/)
├── validation/             # Validators (RENAME from verify/)
├── types/                  # Type system (EXISTING - keep as-is)
└── util/                   # Shared helpers (NEW)
    ├── VariableNameManager.java
    ├── ConstantEvaluator.java
    └── TypePromoter.java
```

## Refactoring Strategy

### Phase 1: Extract Context Management
- Create `FunctionContext` to manage function-level state
- Create `LoopContext` to manage loop state
- Create `VariableNameManager` for name mapping

### Phase 2: Extract Program-Level Generators
- `ProgramGenerator`: Orchestrates program generation
- `GlobalGenerator`: Generates global variables
- `StructGenerator`: Generates struct definitions

### Phase 3: Extract Function Generator
- `FunctionGenerator`: Generates function definitions
- Extract frame/slot computation

### Phase 4: Extract Expression Generators
- `LValueGenerator`: Generates L-values
- `RValueGenerator`: Generates R-values
- Specialized generators for each expression type

### Phase 5: Extract Statement Generators
- `StatementGenerator`: Orchestrates statement generation
- `IfStatementGenerator`: If/else statements
- `LoopStatementGenerator`: While/for/do-while loops
- `JumpStatementGenerator`: Return/break/continue

### Phase 6: Extract Utilities
- `ConstantEvaluator`: Constant folding
- `TypePromoter`: Type promotion/conversion
- `AddressReuseOptimizer`: Address reuse logic

### Phase 7: Split Large Classes
- Split `IrPrettyPrinter` if needed
- Split `IrFunctionBuilder` if needed

### Phase 8: Testing & Validation
- Run all tests after each phase
- Fix any regressions immediately
- Ensure golden tests pass
