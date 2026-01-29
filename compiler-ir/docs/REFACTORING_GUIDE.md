# Refactoring Guide: IrGenerator (4212 lines → <200 lines per class)

## Current State

`IrGenerator.java` is a 4212-line god class that violates SRP. It handles:
- Program-level generation (globals, structs, functions)
- Function-level generation (frame, slots, blocks)
- Expression lowering (LValue, RValue, all expression types)
- Statement lowering (if/else, while, for, return, etc.)
- Type conversions and promotions
- Variable name management
- Address reuse optimization
- Constant evaluation

## Refactoring Strategy

### Phase 1: Extract Context Management ✅ COMPLETED
- `FunctionContext`: Function-level state
- `LoopContext`: Loop state
- `VariableNameManager`: Variable name mapping
- `AddressReuseContext`: Address reuse optimization

### Phase 2: Extract Program-Level Generators
**Status**: In progress

Create:
- `ProgramGenerator`: Orchestrates program generation
- `GlobalGenerator`: Generates global variables
- `StructGenerator`: Generates struct definitions

**Extraction Pattern**:
1. Identify methods in `IrGenerator` that handle globals/structs
2. Move them to `GlobalGenerator`/`StructGenerator`
3. Pass required context (globalScope, programBuilder) via constructor
4. Update `ProgramGenerator` to use new generators
5. Test after each extraction

### Phase 3: Extract Function Generator
Create:
- `FunctionGenerator`: Generates function definitions
- Extract frame/slot computation to `FrameLayoutCalculator`

**Methods to extract**:
- `generateFunctionDefinition`
- `generateParameterSlots`
- `computeFrameSize`
- `generateCompoundStatement`

### Phase 4: Extract Expression Generators
Create specialized generators:
- `LValueGenerator`: Generates l-values
- `RValueGenerator`: Generates r-values (orchestrator)
- `PrimaryExpressionGenerator`: Literals, identifiers
- `BinaryExpressionGenerator`: Binary operations
- `UnaryExpressionGenerator`: Unary operations
- `CallExpressionGenerator`: Function calls
- `CastExpressionGenerator`: Type casts

**Methods to extract**:
- `emitLValue*` → `LValueGenerator`
- `emitRValue*` → `RValueGenerator` and specialized generators
- `isAddressableExpressionForm` → `LValueGenerator`
- `extractConstantFromExpression` → `ConstantEvaluator`

### Phase 5: Extract Statement Generators
Create:
- `StatementGenerator`: Routes statements to specialized generators
- `IfStatementGenerator`: If/else statements
- `LoopStatementGenerator`: While/for/do-while loops
- `JumpStatementGenerator`: Return/break/continue
- `DeclarationGenerator`: Variable declarations (local)

**Methods to extract**:
- `generateStatement*` → `StatementGenerator`
- `generateIfStatement` → `IfStatementGenerator`
- `generateLoopStatement` → `LoopStatementGenerator`
- `generateJumpStatement` → `JumpStatementGenerator`
- `generateDeclaration` (local part) → `DeclarationGenerator`

### Phase 6: Extract Utilities
Create:
- `ConstantEvaluator`: Constant folding (from `extractConstantFromExpression`)
- `TypePromoter`: Type promotion/conversion (from `promoteValue`)
- `SymbolResolver`: Symbol resolution (from `determineSymbolKind`)

### Phase 7: Update IrGenerator
After all extractions:
1. `IrGenerator` becomes a thin wrapper that delegates to `ProgramGenerator`
2. Maintains public API compatibility
3. Eventually can be deprecated in favor of direct `ProgramGenerator` usage

## Testing Strategy

After EACH extraction:
1. Run unit tests: `mvn test -pl compiler-ir`
2. Run golden tests: Verify IR output matches exactly
3. Fix any regressions immediately
4. Commit incrementally

## Example: Extracting GlobalGenerator

### Step 1: Identify methods
- `generateDeclaration` (when `isGlobal=true`)
- `generateInitDeclarator` (global part)
- `evaluateGlobalInitializer`
- `evaluateGlobalArrayInitializer`
- `extractConstantFromExpression`

### Step 2: Create GlobalGenerator class
```java
public final class GlobalGenerator {
  private final SymbolTable globalScope;
  private final IrProgram.Builder programBuilder;
  private final ConstantEvaluator constantEvaluator;
  
  public void generateDeclaration(NonTerminalNode node) {
    // Extract logic from IrGenerator.generateDeclaration(isGlobal=true)
  }
  
  private void generateInitDeclarator(...) {
    // Extract global-specific logic
  }
}
```

### Step 3: Update IrGenerator
- Remove extracted methods
- Add delegation to `GlobalGenerator`
- Test

### Step 4: Repeat for next generator

## Size Targets

Each generator class should be:
- **< 200 lines** (excluding comments/blank lines)
- **Single responsibility**: One reason to change
- **Testable**: Can be tested in isolation

If a generator exceeds 200 lines:
- Split into smaller generators (e.g., `BinaryExpressionGenerator` → `ArithmeticGenerator`, `BitwiseGenerator`)
- Extract utilities to shared classes
- Use composition to delegate

## Dependency Rules

- Generators depend on `model` (not vice versa)
- Generators can depend on `builder` (for factories)
- Generators can depend on `util` (for helpers)
- `lowering` orchestrates generators
- `api` depends on `lowering` (not generators directly)

## Migration Path

1. **Phase 1-2**: Extract program/global/struct generators
2. **Phase 3**: Extract function generator
3. **Phase 4**: Extract expression generators (largest effort)
4. **Phase 5**: Extract statement generators
5. **Phase 6**: Extract utilities
6. **Phase 7**: Update `IrGenerator` to delegate, maintain API compatibility
7. **Phase 8**: Eventually deprecate `IrGenerator` in favor of direct `ProgramGenerator` usage

## Notes

- Maintain exact IR output (golden tests must pass)
- Keep public API (`IrPipeline`) stable
- Extract incrementally, test frequently
- Use composition over inheritance
- Prefer small, focused classes over large ones
