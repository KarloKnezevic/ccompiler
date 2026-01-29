package hr.fer.ppj.semantics.api;

import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.StructLayout;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Clean API for accessing semantic information computed during semantic analysis.
 *
 * <p>This class provides a uniform interface for IR generation to access semantic facts
 * without relying on fragile attribute propagation or implementation details. All methods
 * are deterministic and return consistent results.
 *
 * <p>This class is designed to be used by the IR generation module after semantic
 * analysis has completed successfully.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SemanticInfo {

  private final SymbolTable globalScope;
  private final Map<String, StructLayout> structLayouts;
  private final StructLayout.TypeSizeCalculator typeSizeCalculator;
  private final StructLayout.TypeAlignmentCalculator typeAlignmentCalculator;
  private final StructTypeLookup structTypeLookup;

  /**
   * Creates a new SemanticInfo instance.
   *
   * @param globalScope the global symbol table from semantic analysis
   * @param typeSizeCalculator function to compute type sizes
   * @param typeAlignmentCalculator function to compute type alignments
   * @param structTypeLookup function to look up struct types by tag
   */
  public SemanticInfo(
      SymbolTable globalScope,
      StructLayout.TypeSizeCalculator typeSizeCalculator,
      StructLayout.TypeAlignmentCalculator typeAlignmentCalculator,
      StructTypeLookup structTypeLookup) {
    this.globalScope = Objects.requireNonNull(globalScope, "globalScope must not be null");
    this.typeSizeCalculator = Objects.requireNonNull(
        typeSizeCalculator, "typeSizeCalculator must not be null");
    this.typeAlignmentCalculator = Objects.requireNonNull(
        typeAlignmentCalculator, "typeAlignmentCalculator must not be null");
    this.structTypeLookup = Objects.requireNonNull(
        structTypeLookup, "structTypeLookup must not be null");
    this.structLayouts = new LinkedHashMap<>(); // Computed lazily
  }

  /**
   * Gets the type of a parse tree node.
   *
   * <p>This method extracts the type from the node's semantic attributes.
   * If the node has no type attribute, returns empty.
   *
   * @param node the parse tree node
   * @return the type, or empty if not available
   */
  public Optional<Type> getType(ParseNode node) {
    if (node instanceof NonTerminalNode nonTerminal) {
      Type type = nonTerminal.attributes().type();
      return Optional.ofNullable(type);
    }
    return Optional.empty();
  }

  /**
   * Gets the resolved symbol for an identifier node.
   *
   * <p>This method looks up the symbol in the symbol table starting from the
   * appropriate scope. For identifier nodes, it uses the symbol stored in
   * the node's attributes.
   *
   * @param node the identifier node (must be an IDN terminal or contain one)
   * @param currentScope the current scope for symbol lookup (can be null for global lookup)
   * @return the resolved symbol, or empty if not found
   */
  public Optional<Symbol> getResolvedSymbol(ParseNode node, SymbolTable currentScope) {
    String identifier = extractIdentifier(node);
    if (identifier == null) {
      return Optional.empty();
    }

    SymbolTable scope = currentScope != null ? currentScope : globalScope;
    return scope.lookup(identifier);
  }

  /**
   * Gets the function type for a function symbol.
   *
   * @param symbol the function symbol
   * @return the function type, or empty if symbol is not a function
   */
  public Optional<FunctionType> getFunctionType(Symbol symbol) {
    if (symbol instanceof FunctionSymbol functionSymbol) {
      return Optional.of(functionSymbol.type());
    }
    return Optional.empty();
  }

  /**
   * Gets the struct layout for a struct tag.
   *
   * <p>Layouts are computed lazily and cached for performance.
   *
   * @param structTag the struct tag name
   * @return the struct layout, or empty if struct not found or has no tag
   */
  public Optional<StructLayout> getStructLayout(String structTag) {
    if (structTag == null) {
      return Optional.empty();
    }

    // Check cache first
    StructLayout cached = structLayouts.get(structTag);
    if (cached != null) {
      return Optional.of(cached);
    }

    // Look up struct type
    Optional<StructType> structTypeOpt = structTypeLookup.lookup(structTag);
    if (structTypeOpt.isEmpty()) {
      return Optional.empty();
    }

    StructType structType = structTypeOpt.get();
    if (structType.fields().isEmpty()) {
      // Forward declaration - no layout yet
      return Optional.empty();
    }

    // Compute layout
    StructLayout layout = StructLayout.compute(
        structType,
        typeSizeCalculator,
        typeAlignmentCalculator);

    // Cache it
    structLayouts.put(structTag, layout);
    return Optional.of(layout);
  }

  /**
   * Gets the array dimensions for a type.
   *
   * <p>Returns the dimensions list (outermost first) for array types.
   * For non-array types, returns an empty list.
   *
   * @param type the type
   * @return the dimensions list (empty for non-array types)
   */
  public List<Integer> getArrayDimensions(Type type) {
    if (type instanceof ArrayType arrayType) {
      return new ArrayList<>(arrayType.dimensions());
    }
    return List.of();
  }

  /**
   * Checks if an expression node is addressable (can be used as an l-value).
   *
   * <p>An expression is addressable if it is:
   * <ul>
   *   <li>An identifier (variable, parameter, or global)</li>
   *   <li>A dereference expression: {@code *expr}</li>
   *   <li>An array indexing expression: {@code expr[index]}</li>
   *   <li>A struct field access: {@code expr.field}</li>
   * </ul>
   *
   * <p>This method does NOT rely on lvalue flags in semantic attributes.
   * Instead, it determines addressability by examining the expression form.
   *
   * @param node the expression node
   * @return true if the expression is addressable, false otherwise
   */
  public boolean isAddressableExpression(ParseNode node) {
    if (!(node instanceof NonTerminalNode nonTerminal)) {
      return false;
    }

    String symbol = nonTerminal.symbol();

    // Primary expression: identifier
    if (symbol.equals("<primarni_izraz>")) {
      List<ParseNode> children = nonTerminal.children();
      if (!children.isEmpty() && children.get(0) instanceof TerminalNode term) {
        return term.symbol().equals("IDN");
      }
    }

    // Postfix expression: array indexing or field access
    if (symbol.equals("<postfiks_izraz>")) {
      List<ParseNode> children = nonTerminal.children();
      if (children.size() >= 3) {
        ParseNode second = children.get(1);
        if (second instanceof TerminalNode term) {
          // Array indexing: expr[index]
          if (term.symbol().equals("L_UGL_ZAGRADA")) {
            return true;
          }
          // Field access: expr.field
          if (term.symbol().equals("TOCKA")) {
            return true;
          }
        }
      }
    }

    // Unary expression: dereference
    if (symbol.equals("<unarni_izraz>")) {
      List<ParseNode> children = nonTerminal.children();
      if (children.size() >= 2) {
        ParseNode first = children.get(0);
        if (first instanceof TerminalNode term && term.symbol().equals("OP_PUTA")) {
          return true; // Dereference: *expr
        }
      }
    }

    return false;
  }

  /**
   * Gets the storage class of a symbol (local, param, or global).
   *
   * @param symbol the symbol
   * @param currentScope the current function scope (can be null)
   * @return the storage class
   */
  public StorageClass getStorageClass(Symbol symbol, SymbolTable currentScope) {
    if (currentScope == null) {
      return StorageClass.GLOBAL;
    }

    // Check if it's a parameter (parameters are in function scope)
    if (symbol instanceof VariableSymbol) {
      // Parameters are declared in the function scope itself
      Optional<Symbol> found = currentScope.lookupLocal(symbol.name());
      if (found.isPresent() && found.get() == symbol) {
        // Check if it's a parameter by checking if it's in the function's parameter list
        // This is a simplified check - in a full implementation, we'd track parameter symbols
        return StorageClass.PARAM;
      }
    }

    // Check if it's a local (in current scope or nested scope)
    Optional<Symbol> found = currentScope.lookup(symbol.name());
    if (found.isPresent() && found.get() == symbol && currentScope != globalScope) {
      return StorageClass.LOCAL;
    }

    // Otherwise, it's global
    return StorageClass.GLOBAL;
  }

  /**
   * Extracts the identifier name from a node.
   */
  private String extractIdentifier(ParseNode node) {
    if (node instanceof TerminalNode term && term.symbol().equals("IDN")) {
      return term.lexeme();
    }
    if (node instanceof NonTerminalNode nonTerminal) {
      String identifier = nonTerminal.attributes().identifier();
      if (identifier != null) {
        return identifier;
      }
      // Try to find IDN terminal in children
      for (ParseNode child : nonTerminal.children()) {
        String id = extractIdentifier(child);
        if (id != null) {
          return id;
        }
      }
    }
    return null;
  }


  /**
   * Storage class enumeration.
   */
  public enum StorageClass {
    /** Local variable in a function */
    LOCAL,
    /** Function parameter */
    PARAM,
    /** Global variable */
    GLOBAL
  }

  /**
   * Functional interface for looking up struct types by tag.
   */
  @FunctionalInterface
  public interface StructTypeLookup {
    /**
     * Looks up a struct type by its tag name.
     *
     * @param tag the struct tag name
     * @return the struct type, or empty if not found
     */
    Optional<StructType> lookup(String tag);
  }
}

