package hr.fer.ppj.parser.grammar;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes FIRST sets for grammar symbols and sequences.
 * 
 * <p>FIRST(α) is the set of terminals that can begin strings derived from α.
 * If α can derive ε, then ε is also in FIRST(α).
 * 
 * <p>Rules:
 * <ul>
 *   <li>FIRST(a) = {a} for terminal a</li>
 *   <li>FIRST(ε) = {ε}</li>
 *   <li>For non-terminal X: FIRST(X) = ⋃ FIRST(α) for all X → α</li>
 *   <li>For sequence αβ: FIRST(αβ) = FIRST(α) if ε ∉ FIRST(α),
 *       else FIRST(α) ∪ FIRST(β)</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FirstSetComputer {
  
  private static final String EPSILON = "$";
  
  private final Grammar grammar;
  private final Set<String> computedNonTerminals = new HashSet<>();
  private final java.util.Map<String, Set<String>> firstCache = new java.util.HashMap<>();
  
  public FirstSetComputer(Grammar grammar) {
    this.grammar = grammar;
  }
  
  /**
   * Computes FIRST set for a single symbol.
   * 
   * @param symbol The symbol (terminal or non-terminal)
   * @return FIRST set for the symbol
   */
  public Set<String> computeFirst(String symbol) {
    // Check cache
    if (firstCache.containsKey(symbol)) {
      return new HashSet<>(firstCache.get(symbol));
    }
    
    Set<String> first = new HashSet<>();
    
    // Terminal: FIRST(a) = {a}
    if (grammar.isTerminal(symbol)) {
      first.add(symbol);
      firstCache.put(symbol, first);
      return first;
    }
    
    // Non-terminal: compute recursively
    if (grammar.isNonTerminal(symbol)) {
      computeFirstForNonTerminal(symbol, first);
      firstCache.put(symbol, first);
      return first;
    }
    
    // Unknown symbol
    return first;
  }
  
  /**
   * Computes FIRST set for a sequence of symbols.
   * 
   * @param symbols The sequence of symbols
   * @return FIRST set for the sequence
   */
  public Set<String> computeFirst(List<String> symbols) {
    if (symbols.isEmpty()) {
      // Empty sequence can derive epsilon
      return Set.of(EPSILON);
    }
    
    Set<String> first = new HashSet<>();
    boolean allCanDeriveEpsilon = true;
    
    for (String symbol : symbols) {
      Set<String> symbolFirst = computeFirst(symbol);
      
      // Add all terminals from this symbol's FIRST (excluding epsilon)
      for (String terminal : symbolFirst) {
        if (!terminal.equals(EPSILON)) {
          first.add(terminal);
        }
      }
      
      // Check if this symbol can derive epsilon
      if (!symbolFirst.contains(EPSILON)) {
        allCanDeriveEpsilon = false;
        break; // Stop here, no need to continue
      }
    }
    
    // If all symbols can derive epsilon, add epsilon to FIRST
    if (allCanDeriveEpsilon) {
      first.add(EPSILON);
    }
    
    return first;
  }
  
  /**
   * Computes FIRST set for a non-terminal using fixed-point iteration.
   */
  private void computeFirstForNonTerminal(String nonTerminal, Set<String> result) {
    if (computedNonTerminals.contains(nonTerminal)) {
      // Already computing - avoid infinite recursion
      return;
    }
    
    computedNonTerminals.add(nonTerminal);
    
    List<GrammarParser.Production> prods = grammar.getProductions(nonTerminal);
    if (prods == null) {
      return;
    }
    
    for (GrammarParser.Production prod : prods) {
      List<String> rhs = prod.rhs();
      
      if (rhs.isEmpty()) {
        // Epsilon production: add epsilon to FIRST
        result.add(EPSILON);
      } else {
        // Compute FIRST of RHS
        Set<String> rhsFirst = computeFirst(rhs);
        result.addAll(rhsFirst);
      }
    }
    
    computedNonTerminals.remove(nonTerminal);
  }
  
  /**
   * Checks if epsilon is in FIRST set of a sequence.
   */
  public boolean hasEpsilon(List<String> symbols) {
    if (symbols.isEmpty()) {
      return true;
    }
    
    for (String symbol : symbols) {
      Set<String> symbolFirst = computeFirst(symbol);
      if (!symbolFirst.contains(EPSILON)) {
        return false;
      }
    }
    return true;
  }
  
  /**
   * Clears the cache (useful for recomputation after grammar changes).
   */
  public void clearCache() {
    firstCache.clear();
    computedNonTerminals.clear();
  }
}

