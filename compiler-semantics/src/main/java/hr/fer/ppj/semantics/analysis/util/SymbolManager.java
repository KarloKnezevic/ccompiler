package hr.fer.ppj.semantics.analysis.util;

import hr.fer.ppj.semantics.analysis.context.SemanticContext;
import hr.fer.ppj.semantics.errors.SemanticErrorReporter;
import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages symbol declarations and lookups during semantic analysis.
 * 
 * <p>This class provides methods for:
 * <ul>
 *   <li>Declaring variables and functions</li>
 *   <li>Registering function prototypes and definitions</li>
 *   <li>Managing struct type tags</li>
 *   <li>Declaring function parameters</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SymbolManager {
  
  private final SemanticContext context;
  private final SemanticErrorReporter errorReporter;
  private final TypeChecker typeChecker;
  private final SymbolTable globalScope;
  private final Map<String, FunctionSymbol> functions = new LinkedHashMap<>();
  private final Map<String, StructType> structTags = new LinkedHashMap<>();
  
  /**
   * Creates a new symbol manager with the specified context and error reporter.
   * 
   * @param context the semantic context
   * @param errorReporter the error reporter
   * @param typeChecker the type checker for validation
   * @throws NullPointerException if any parameter is null
   */
  public SymbolManager(
      SemanticContext context,
      SemanticErrorReporter errorReporter,
      TypeChecker typeChecker) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.errorReporter = Objects.requireNonNull(errorReporter, "errorReporter must not be null");
    this.typeChecker = Objects.requireNonNull(typeChecker, "typeChecker must not be null");
    this.globalScope = context.currentScope();
  }
  
  /**
   * Declares a variable in the current scope.
   * 
   * <p>The variable type must not be void. If a variable with the same name
   * already exists in the current scope, an error is reported.
   * 
   * @param name the variable name
   * @param type the variable type
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the declaration is invalid
   */
  public void declareVariable(String name, Type type, NonTerminalNode ctx) {
    typeChecker.ensureNotVoid(type, ctx);
    VariableSymbol symbol = new VariableSymbol(name, type, TypeSystem.isConst(type));
    if (!context.currentScope().declare(symbol)) {
      errorReporter.reportError(ctx);
    }
  }
  
  /**
   * Registers a function prototype (declaration) in the current scope.
   * 
   * <p>If a function with the same name already exists, its type must match.
   * If a variable with the same name exists, an error is reported.
   * 
   * @param name the function name
   * @param type the function type
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the prototype is invalid
   */
  public void registerFunctionPrototype(String name, FunctionType type, NonTerminalNode ctx) {
    Symbol existing = context.currentScope().lookupLocal(name).orElse(null);
    if (existing == null) {
      context.currentScope().declare(new FunctionSymbol(name, type, false));
    } else if (existing instanceof FunctionSymbol fn) {
      if (!fn.type().equals(type)) {
        errorReporter.reportError(ctx);
      }
    } else {
      errorReporter.reportError(ctx);
    }
    recordFunction(name, type, false, ctx);
  }
  
  /**
   * Registers a function definition in the global scope.
   * 
   * <p>If a function with the same name already exists, it must be a declaration
   * (not a definition) and the types must match. If a variable with the same
   * name exists, an error is reported.
   * 
   * @param name the function name
   * @param type the function type
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the definition is invalid
   */
  public void registerFunctionDefinition(String name, FunctionType type, NonTerminalNode ctx) {
    Symbol existing = globalScope.lookupLocal(name).orElse(null);
    if (existing == null) {
      globalScope.declare(new FunctionSymbol(name, type, true));
    } else if (existing instanceof FunctionSymbol fn) {
      if (!fn.type().equals(type) || fn.defined()) {
        errorReporter.reportError(ctx);
      }
      globalScope.update(fn.markDefined());
    } else {
      errorReporter.reportError(ctx);
    }
    recordFunction(name, type, true, ctx);
  }
  
  /**
   * Records a function in the internal function registry.
   * 
   * <p>This method maintains a global registry of all functions for later
   * validation (e.g., ensuring all declared functions are defined).
   * 
   * @param name the function name
   * @param type the function type
   * @param defined whether this is a definition (true) or declaration (false)
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if there's a conflict
   */
  private void recordFunction(String name, FunctionType type, boolean defined, NonTerminalNode ctx) {
    FunctionSymbol existing = functions.get(name);
    if (existing == null) {
      functions.put(name, new FunctionSymbol(name, type, defined));
      return;
    }
    if (!existing.type().equals(type)) {
      errorReporter.reportError(ctx);
    }
    if (defined && existing.defined()) {
      errorReporter.reportError(ctx);
    }
    if (defined) {
      functions.put(name, existing.markDefined());
    }
  }
  
  /**
   * Declares function parameters in the current scope.
   * 
   * <p>Each parameter is declared as a variable in the function's local scope.
   * Parameter types must not be void.
   * 
   * @param names the parameter names
   * @param types the parameter types
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if the parameters are invalid
   */
  public void declareFunctionParameters(List<String> names, List<Type> types, NonTerminalNode ctx) {
    if (names == null || types == null) {
      return;
    }
    if (names.size() != types.size()) {
      errorReporter.reportError(ctx);
    }
    for (int i = 0; i < names.size(); i++) {
      declareVariable(names.get(i), types.get(i), ctx);
    }
  }
  
  /**
   * Registers a forward declaration of a struct type (with empty fields).
   * 
   * <p>This allows self-referential structs. If the struct tag is already
   * fully defined, an error is reported.
   * 
   * @param tag the struct tag name
   * @param structType the struct type to register (should have empty fields)
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if there's a conflict
   */
  public void registerStructTagForward(String tag, StructType structType, NonTerminalNode ctx) {
    StructType existing = structTags.get(tag);
    if (existing != null && !existing.fields().isEmpty()) {
      // Struct tag already fully defined
      errorReporter.reportError(ctx); // Conflicting struct definitions
    } else {
      structTags.put(tag, structType);
    }
  }
  
  /**
   * Registers a struct type with the given tag name.
   * 
   * <p>If a struct with the same tag already exists, it must be a forward
   * declaration (empty fields) or have the same type.
   * 
   * @param tag the struct tag name
   * @param structType the struct type to register
   * @param ctx the parse node context for error reporting
   * @throws SemanticException if there's a conflict
   */
  public void registerStructTag(String tag, StructType structType, NonTerminalNode ctx) {
    StructType existing = structTags.get(tag);
    if (existing != null) {
      // Struct tag already defined - check if it's the same type
      if (!existing.equals(structType)) {
        // If existing is forward declaration (empty fields), replace it
        if (existing.fields().isEmpty() && existing.tag().equals(tag)) {
          structTags.put(tag, structType);
        } else {
          errorReporter.reportError(ctx); // Conflicting struct definitions
        }
      }
    } else {
      structTags.put(tag, structType);
    }
  }
  
  /**
   * Looks up a struct type by its tag name.
   * 
   * @param tag the struct tag name
   * @return the struct type, or null if not found
   */
  public StructType lookupStructTag(String tag) {
    return structTags.get(tag);
  }
  
  /**
   * Returns all registered functions.
   * 
   * @return a map of function names to function symbols
   */
  public Map<String, FunctionSymbol> getAllFunctions() {
    return Map.copyOf(functions);
  }
  
  /**
   * Returns the global symbol table.
   * 
   * @return the global scope
   */
  public SymbolTable getGlobalScope() {
    return globalScope;
  }
}

