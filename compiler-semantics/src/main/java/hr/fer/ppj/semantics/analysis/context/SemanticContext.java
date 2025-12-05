package hr.fer.ppj.semantics.analysis.context;

import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.types.FunctionType;
import java.util.Objects;

/**
 * Manages the semantic analysis context during AST traversal.
 * 
 * <p>This class maintains the current state of semantic analysis, including:
 * <ul>
 *   <li>Current lexical scope (for symbol lookup)</li>
 *   <li>Current function context (for return statement validation)</li>
 *   <li>Loop nesting depth (for break/continue validation)</li>
 * </ul>
 * 
 * <p>The context is thread-local to the semantic analysis pass and is updated
 * as the analyzer traverses the AST.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SemanticContext {
  
  private SymbolTable currentScope;
  private FunctionType currentFunction;
  private int loopDepth;
  
  /**
   * Creates a new semantic context with the specified global scope.
   * 
   * @param globalScope the root symbol table for global declarations
   * @throws NullPointerException if globalScope is null
   */
  public SemanticContext(SymbolTable globalScope) {
    this.currentScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.currentFunction = null;
    this.loopDepth = 0;
  }
  
  /**
   * Returns the current lexical scope.
   * 
   * @return the current symbol table
   */
  public SymbolTable currentScope() {
    return currentScope;
  }
  
  /**
   * Sets the current lexical scope.
   * 
   * @param scope the new current scope
   * @throws NullPointerException if scope is null
   */
  public void setCurrentScope(SymbolTable scope) {
    this.currentScope = Objects.requireNonNull(scope, "scope must not be null");
  }
  
  /**
   * Returns the current function type, or null if not inside a function.
   * 
   * @return the current function type, or null
   */
  public FunctionType currentFunction() {
    return currentFunction;
  }
  
  /**
   * Sets the current function type.
   * 
   * @param function the current function type, or null to clear
   */
  public void setCurrentFunction(FunctionType function) {
    this.currentFunction = function;
  }
  
  /**
   * Returns the current loop nesting depth.
   * 
   * <p>A depth of 0 means we are not inside any loop. Each nested loop
   * increments the depth by 1.
   * 
   * @return the loop nesting depth
   */
  public int loopDepth() {
    return loopDepth;
  }
  
  /**
   * Increments the loop nesting depth.
   */
  public void enterLoop() {
    loopDepth++;
  }
  
  /**
   * Decrements the loop nesting depth.
   * 
   * @throws IllegalStateException if loop depth is already 0
   */
  public void exitLoop() {
    if (loopDepth == 0) {
      throw new IllegalStateException("Cannot exit loop: not inside a loop");
    }
    loopDepth--;
  }
  
  /**
   * Executes an action within a new lexical scope.
   * 
   * <p>This method creates a child scope, executes the action, and then
   * restores the previous scope. The scope is restored even if the action
   * throws an exception.
   * 
   * @param action the action to execute in the new scope
   */
  public void withNewScope(Runnable action) {
    SymbolTable previous = currentScope;
    currentScope = currentScope.enterChildScope();
    try {
      action.run();
    } finally {
      currentScope = previous;
    }
  }
  
  /**
   * Executes an action within a loop context.
   * 
   * <p>This method increments the loop depth, executes the action, and then
   * decrements the depth. The depth is restored even if the action throws
   * an exception.
   * 
   * @param action the action to execute in the loop context
   */
  public void withinLoop(Runnable action) {
    enterLoop();
    try {
      action.run();
    } finally {
      exitLoop();
    }
  }
}

