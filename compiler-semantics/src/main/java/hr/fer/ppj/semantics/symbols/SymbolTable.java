package hr.fer.ppj.semantics.symbols;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Hierarchical symbol table used for lexical scoping in PPJ-C.
 *
 * <p>This class implements a tree-structured symbol table that mirrors the lexical scoping
 * structure of C programs. Each symbol table represents a single scope (global scope, function
 * scope, or block scope) and maintains:
 * <ul>
 *   <li>A map of symbols declared in this scope</li>
 *   <li>A reference to the parent scope (null for global scope)</li>
 *   <li>A list of child scopes (for debug output generation)</li>
 * </ul>
 *
 * <p>Symbol lookup follows lexical scoping rules:
 * <ol>
 *   <li>Search starts in the current scope</li>
 *   <li>If not found, search proceeds to the parent scope</li>
 *   <li>Continues up the parent chain until found or global scope is reached</li>
 *   <li>Inner declarations shadow outer declarations with the same name</li>
 * </ol>
 *
 * <p>Scope management:
 * <ul>
 *   <li>Global scope: Created at the start of semantic analysis</li>
 *   <li>Function scope: Created when entering a function body</li>
 *   <li>Block scope: Created when entering a compound statement</li>
 *   <li>Scopes are entered using {@link #enterChildScope()} and restored by the caller</li>
 * </ul>
 *
 * <p>Symbol types:
 * <ul>
 *   <li>{@link VariableSymbol}: Variables and constants</li>
 *   <li>{@link FunctionSymbol}: Function declarations and definitions</li>
 * </ul>
 *
 * <p>Thread safety: This class is not thread-safe. It is designed for single-threaded
 * semantic analysis during compilation.
 *
 * @see Symbol for the base symbol interface
 * @see VariableSymbol for variable symbol representation
 * @see FunctionSymbol for function symbol representation
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SymbolTable {

  private final SymbolTable parent;
  private final Map<String, Symbol> entries = new LinkedHashMap<>();
  private final List<SymbolTable> children = new ArrayList<>();

  public SymbolTable() {
    this(null);
  }

  private SymbolTable(SymbolTable parent) {
    this.parent = parent;
  }

  public SymbolTable parent() {
    return parent;
  }

  /**
   * Creates a new child scope whose parent is the current table. The caller is responsible for
   * remembering the previous table (typically via a {@code try/finally}) so that the scope can be
   * restored.
   */
  public SymbolTable enterChildScope() {
    SymbolTable child = new SymbolTable(this);
    children.add(child);
    return child;
  }

  public boolean declare(Symbol symbol) {
    Objects.requireNonNull(symbol, "symbol must not be null");
    if (entries.containsKey(symbol.name())) {
      return false;
    }
    entries.put(symbol.name(), symbol);
    return true;
  }

  /**
   * Replaces an existing symbol with a new instance. Fails if the symbol is not present in
   * the current scope.
   *
   * @return {@code true} if the symbol was replaced
   */
  public boolean update(Symbol symbol) {
    Objects.requireNonNull(symbol, "symbol must not be null");
    if (!entries.containsKey(symbol.name())) {
      return false;
    }
    entries.put(symbol.name(), symbol);
    return true;
  }

  public Optional<Symbol> lookupLocal(String name) {
    return Optional.ofNullable(entries.get(name));
  }

  /**
   * Looks up a symbol in the current scope and all of its parents. The first match wins which aligns
   * with lexical scoping (inner declarations shadow outer ones).
   */
  public Optional<Symbol> lookup(String name) {
    SymbolTable current = this;
    while (current != null) {
      Symbol symbol = current.entries.get(name);
      if (symbol != null) {
        return Optional.of(symbol);
      }
      current = current.parent;
    }
    return Optional.empty();
  }

  public Map<String, Symbol> entries() {
    return Map.copyOf(entries);
  }
  
  /**
   * Returns all symbols in this scope (not including parent scopes).
   * This method is used for semantic report generation.
   * 
   * @return a copy of all symbols in this scope
   */
  public Map<String, Symbol> getAllSymbols() {
    return Map.copyOf(entries);
  }
  
  /**
   * Returns all child scopes of this symbol table.
   * This method is used for semantic report generation.
   * 
   * @return a copy of all child scopes
   */
  public List<SymbolTable> getChildScopes() {
    return List.copyOf(children);
  }
}

