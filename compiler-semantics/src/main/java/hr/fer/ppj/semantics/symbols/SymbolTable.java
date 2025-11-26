package hr.fer.ppj.semantics.symbols;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Hierarchical symbol table used for lexical scoping. Each table stores the symbols declared in a
 * single block and keeps a pointer to its parent. Lookup walks up the parent chain which mimics the
 * lexical scoping rules of ppjC.
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

