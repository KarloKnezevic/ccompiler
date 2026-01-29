package hr.fer.ppj.ir.lowering;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.util.AddressReuseContext;
import hr.fer.ppj.ir.util.BlockLocalSymbolAddressCache;
import hr.fer.ppj.ir.util.VariableNameManager;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import java.util.Objects;

/**
 * Manages function-level context during IR generation.
 *
 * <p>This context tracks:
 * <ul>
 *   <li>Current function builder</li>
 *   <li>Function scope (symbol table)</li>
 *   <li>Return type (for type promotion)</li>
 *   <li>Local offset for slot allocation</li>
 *   <li>Logical result counter</li>
 *   <li>Variable name manager (for shadowing)</li>
 *   <li>Address reuse context (for optimizations)</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionContext {

  private final IrFunctionBuilder functionBuilder;
  private final SymbolTable functionScope;
  private final IrType returnType;
  private final VariableNameManager variableNameManager;
  private final AddressReuseContext addressReuseContext;
  private final BlockLocalSymbolAddressCache blockLocalAddressCache;
  private int localOffset;
  private int logicalResultCounter;
  private LoopContext loopContext;
  private String nextExpressionMergeLabel;
  private hr.fer.ppj.ir.model.IrTemp deferredPostfixIncrementValue;
  private hr.fer.ppj.ir.model.IrTemp deferredPostfixIncrementAddr;
  private String deferredPostfixIncrementVarName;
  private hr.fer.ppj.semantics.tree.NonTerminalNode deferredLogicalAndRightSide;
  private String deferredLogicalAndTrueLabel;
  private String deferredLogicalAndFalseLabel;
  private hr.fer.ppj.semantics.tree.NonTerminalNode deferredLogicalOrRightSide;
  private String deferredLogicalOrTrueLabel;
  private String deferredLogicalOrFalseLabel;
  private boolean deferredLogicalOrAlwaysTruthy;
  private boolean deferredLogicalOrLeftSideIsEquality;
  private hr.fer.ppj.ir.model.IrTemp deferredLogicalOrLoadedValue;

  public FunctionContext(
      IrFunctionBuilder functionBuilder,
      SymbolTable functionScope,
      IrType returnType) {
    this.functionBuilder = Objects.requireNonNull(functionBuilder, "functionBuilder must not be null");
    this.functionScope = Objects.requireNonNull(functionScope, "functionScope must not be null");
    this.returnType = returnType; // Can be null for void functions
    this.variableNameManager = new VariableNameManager();
    this.addressReuseContext = new AddressReuseContext();
    this.blockLocalAddressCache = new BlockLocalSymbolAddressCache();
    this.localOffset = 0;
    this.logicalResultCounter = 0;
    this.loopContext = LoopContext.empty();
  }

  public IrFunctionBuilder functionBuilder() {
    return functionBuilder;
  }

  public SymbolTable functionScope() {
    return functionScope;
  }

  public IrType returnType() {
    return returnType;
  }

  public int localOffset() {
    return localOffset;
  }

  public void setLocalOffset(int localOffset) {
    this.localOffset = localOffset;
  }

  public int logicalResultCounter() {
    return logicalResultCounter;
  }

  public void setLogicalResultCounter(int logicalResultCounter) {
    this.logicalResultCounter = logicalResultCounter;
  }

  public int incrementLogicalResultCounter() {
    return logicalResultCounter++;
  }

  public VariableNameManager variableNameManager() {
    return variableNameManager;
  }

  public AddressReuseContext addressReuseContext() {
    return addressReuseContext;
  }

  public BlockLocalSymbolAddressCache blockLocalAddressCache() {
    return blockLocalAddressCache;
  }

  /**
   * Called when a new basic block starts.
   *
   * <p>This clears block-local caches to ensure addresses are only reused within the same block.
   *
   * @param label the label of the new block
   */
  public void onNewBlock(String label) {
    blockLocalAddressCache.clearForNewBlock();
    // Also clear statement-local caches when starting a new block
    addressReuseContext.beginStatement();
  }

  public LoopContext loopContext() {
    return loopContext;
  }

  public void setLoopContext(LoopContext loopContext) {
    this.loopContext = loopContext != null ? loopContext : LoopContext.empty();
  }

  /**
   * Gets the next expression merge label (for chaining expression statements).
   * Returns null if not set.
   */
  public String nextExpressionMergeLabel() {
    return nextExpressionMergeLabel;
  }

  /**
   * Sets the next expression merge label (for chaining expression statements).
   */
  public void setNextExpressionMergeLabel(String label) {
    this.nextExpressionMergeLabel = label;
  }

  /**
   * Stores the value and address loaded for a deferred postfix increment.
   * Used in binary expressions like x + y++ where we load y, add, then increment.
   */
  public void setDeferredPostfixIncrementValue(String varName, hr.fer.ppj.ir.model.IrTemp value, hr.fer.ppj.ir.model.IrTemp addr) {
    this.deferredPostfixIncrementVarName = varName;
    this.deferredPostfixIncrementValue = value;
    this.deferredPostfixIncrementAddr = addr;
  }

  /**
   * Gets the deferred postfix increment value if it matches the variable name.
   */
  public hr.fer.ppj.ir.model.IrTemp getDeferredPostfixIncrementValue(String varName) {
    if (deferredPostfixIncrementVarName != null && deferredPostfixIncrementVarName.equals(varName)) {
      return deferredPostfixIncrementValue;
    }
    return null;
  }

  /**
   * Gets the deferred postfix increment address if it matches the variable name.
   */
  public hr.fer.ppj.ir.model.IrTemp getDeferredPostfixIncrementAddr(String varName) {
    if (deferredPostfixIncrementVarName != null && deferredPostfixIncrementVarName.equals(varName)) {
      return deferredPostfixIncrementAddr;
    }
    return null;
  }

  /**
   * Clears the deferred postfix increment value and address.
   */
  public void clearDeferredPostfixIncrementValue() {
    this.deferredPostfixIncrementVarName = null;
    this.deferredPostfixIncrementValue = null;
    this.deferredPostfixIncrementAddr = null;
  }

  /**
   * Stores the right side of a logical AND expression for deferred evaluation in the then branch.
   * Used when the condition is A && B and B should be evaluated in the then branch block.
   */
  public void setDeferredLogicalAndRightSide(
      hr.fer.ppj.semantics.tree.NonTerminalNode rightNode, String trueLabel, String falseLabel) {
    this.deferredLogicalAndRightSide = rightNode;
    this.deferredLogicalAndTrueLabel = trueLabel;
    this.deferredLogicalAndFalseLabel = falseLabel;
  }

  /**
   * Gets the deferred logical AND right side node without clearing it.
   * Returns null if not set.
   */
  public hr.fer.ppj.semantics.tree.NonTerminalNode getDeferredLogicalAndRightSide() {
    return deferredLogicalAndRightSide;
  }

  /**
   * Clears the deferred logical AND right side node and labels.
   */
  public void clearDeferredLogicalAndRightSide() {
    deferredLogicalAndRightSide = null;
    deferredLogicalAndTrueLabel = null;
    deferredLogicalAndFalseLabel = null;
  }

  /**
   * Gets the true label for deferred logical AND evaluation.
   */
  public String deferredLogicalAndTrueLabel() {
    return deferredLogicalAndTrueLabel;
  }

  /**
   * Gets the false label for deferred logical AND evaluation.
   */
  public String deferredLogicalAndFalseLabel() {
    return deferredLogicalAndFalseLabel;
  }

  /**
   * Stores the right side of a logical OR expression for deferred evaluation in the else branch.
   * Used when the condition is A || B and B should be evaluated in the else branch block.
   */
  public void setDeferredLogicalOrRightSide(
      hr.fer.ppj.semantics.tree.NonTerminalNode rightNode, 
      String trueLabel, 
      String falseLabel,
      boolean alwaysTruthy) {
    this.deferredLogicalOrRightSide = rightNode;
    this.deferredLogicalOrTrueLabel = trueLabel;
    this.deferredLogicalOrFalseLabel = falseLabel;
    this.deferredLogicalOrAlwaysTruthy = alwaysTruthy;
  }
  
  /**
   * Stores the right side of a logical OR expression for deferred evaluation in the else branch.
   * Also indicates whether the left side of the OR uses an equality check (for optimization decisions).
   */
  public void setDeferredLogicalOrRightSide(
      hr.fer.ppj.semantics.tree.NonTerminalNode rightNode, 
      String trueLabel, 
      String falseLabel,
      boolean alwaysTruthy,
      boolean leftSideIsEquality) {
    this.deferredLogicalOrRightSide = rightNode;
    this.deferredLogicalOrTrueLabel = trueLabel;
    this.deferredLogicalOrFalseLabel = falseLabel;
    this.deferredLogicalOrAlwaysTruthy = alwaysTruthy;
    this.deferredLogicalOrLeftSideIsEquality = leftSideIsEquality;
  }
  
  /**
   * Stores the right side of a logical OR expression for deferred evaluation in the else branch.
   * Simplified version without alwaysTruthy flag.
   */
  public void setDeferredLogicalOrRightSide(
      hr.fer.ppj.semantics.tree.NonTerminalNode rightNode, 
      String trueLabel, 
      String falseLabel) {
    setDeferredLogicalOrRightSide(rightNode, trueLabel, falseLabel, false, false);
  }
  
  /**
   * Returns true if the left side of the deferred OR expression uses an equality check (==).
   */
  public boolean deferredLogicalOrLeftSideIsEquality() {
    return deferredLogicalOrLeftSideIsEquality;
  }

  /**
   * Gets the deferred logical OR right side node without clearing it.
   * Returns null if not set.
   */
  public hr.fer.ppj.semantics.tree.NonTerminalNode getDeferredLogicalOrRightSide() {
    return deferredLogicalOrRightSide;
  }

  /**
   * Clears the deferred logical OR right side node and labels.
   */
  public void clearDeferredLogicalOrRightSide() {
    deferredLogicalOrRightSide = null;
    deferredLogicalOrTrueLabel = null;
    deferredLogicalOrFalseLabel = null;
    deferredLogicalOrAlwaysTruthy = false;
    deferredLogicalOrLeftSideIsEquality = false;
  }

  /**
   * Gets the true label for deferred logical OR evaluation.
   */
  public String deferredLogicalOrTrueLabel() {
    return deferredLogicalOrTrueLabel;
  }

  /**
   * Gets the false label for deferred logical OR evaluation.
   */
  public String deferredLogicalOrFalseLabel() {
    return deferredLogicalOrFalseLabel;
  }

  /**
   * Returns true if the deferred logical OR right side is always truthy.
   */
  public boolean deferredLogicalOrAlwaysTruthy() {
    return deferredLogicalOrAlwaysTruthy;
  }

  /**
   * Stores the loaded value from assignment evaluation for deferred logical OR.
   */
  public void setDeferredLogicalOrLoadedValue(hr.fer.ppj.ir.model.IrTemp loadedValue) {
    this.deferredLogicalOrLoadedValue = loadedValue;
  }

  /**
   * Gets and clears the deferred logical OR loaded value.
   */
  public hr.fer.ppj.ir.model.IrTemp getAndClearDeferredLogicalOrLoadedValue() {
    hr.fer.ppj.ir.model.IrTemp result = deferredLogicalOrLoadedValue;
    deferredLogicalOrLoadedValue = null;
    return result;
  }

  /**
   * Loads a scalar variable value with statement-local reuse.
   *
   * <p>This is the unified API for loading scalar variables. It:
   * <ol>
   *   <li>Builds the cache key exactly once: "&lt;kind&gt;:&lt;name&gt;"</li>
   *   <li>Checks statement-local rvalue cache; if present with same type, returns it</li>
   *   <li>Otherwise emits a load instruction, stores it in cache, and returns it</li>
   * </ol>
   *
   * @param cacheKey the fully-qualified cache key (e.g., "param:m", "local:x")
   * @param addr the address temp to load from
   * @param irType the IR type of the value
   * @return the loaded value temp (may be reused from cache)
   */
  public hr.fer.ppj.ir.model.IrTemp loadScalarWithReuse(
      String cacheKey, hr.fer.ppj.ir.model.IrTemp addr, hr.fer.ppj.ir.types.IrType irType) {
    // Check cache first
    hr.fer.ppj.ir.model.IrTemp reusedValue = addressReuseContext.getLastLoadedValue(cacheKey);
    if (reusedValue != null && reusedValue.type().equals(irType)) {
      return reusedValue;
    }

    // Emit load instruction
    hr.fer.ppj.ir.model.IrRhs.Load load = new hr.fer.ppj.ir.model.IrRhs.Load(addr, irType);
    hr.fer.ppj.ir.model.IrTemp valueTemp = functionBuilder.tempFactory().newTemp(irType);
    functionBuilder.addInstruction(new hr.fer.ppj.ir.model.IrInstruction.IrAssignInstr(valueTemp, load));

    // Store in cache for reuse
    addressReuseContext.setLastLoadedValue(cacheKey, valueTemp);

    return valueTemp;
  }

  /**
   * Creates a fully-qualified cache key for a symbol.
   *
   * @param kind the symbol kind (PARAM, LOCAL, GLOBAL)
   * @param actualVarName the actual variable name (may be renamed for shadowing)
   * @return the cache key
   */
  public static String createCacheKey(hr.fer.ppj.ir.model.IrSymbolRef.Kind kind, String actualVarName) {
    return hr.fer.ppj.ir.util.BlockLocalSymbolAddressCache.createSymbolRefKey(kind, actualVarName);
  }
}
