package hr.fer.ppj.parser.grammar;

import hr.fer.ppj.parser.grammar.GrammarParser.Production;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a context-free grammar with augmented start symbol.
 * 
 * <p>The grammar is automatically augmented with a new start symbol S'
 * that produces the original start symbol S.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class Grammar {
  
  private static final String AUGMENTED_START = "<pocetni_nezavrsni_znak>";
  
  private final List<String> nonTerminals;
  private final List<String> terminals;
  private final List<String> syncTokens;
  private final Map<String, List<GrammarParser.Production>> productions;
  private final String startSymbol;
  private final GrammarParser.Production augmentedStartProduction;
  private final List<GrammarParser.Production> allProductions; // All productions with indices
  
  /**
   * Creates a grammar from a parsed grammar definition.
   * 
   * @param parser The parsed grammar definition
   */
  public Grammar(GrammarParser parser) {
    this.nonTerminals = new ArrayList<>(parser.getNonTerminals());
    this.terminals = new ArrayList<>(parser.getTerminals());
    this.syncTokens = new ArrayList<>(parser.getSyncTokens());
    this.productions = new HashMap<>(parser.getProductions());
    
    // Determine start symbol (first non-terminal in %V)
    if (nonTerminals.isEmpty()) {
      throw new IllegalArgumentException("Grammar must have at least one non-terminal");
    }
    this.startSymbol = nonTerminals.get(0);
    
    // Augment grammar: S' -> S
    if (!nonTerminals.contains(AUGMENTED_START)) {
      nonTerminals.add(0, AUGMENTED_START); // Add at beginning
    }
    
    List<String> startRHS = List.of(startSymbol);
    this.augmentedStartProduction = new GrammarParser.Production(AUGMENTED_START, startRHS);
    
    // Add augmented production
    productions.put(AUGMENTED_START, List.of(augmentedStartProduction));
    
    // Build indexed list of all productions
    this.allProductions = buildProductionList();
  }
  
  /**
   * Builds a flat list of all productions with indices.
   * Productions are ordered by non-terminal order, then by definition order.
   */
  private List<GrammarParser.Production> buildProductionList() {
    List<GrammarParser.Production> result = new ArrayList<>();
    
    // First add augmented start production
    result.add(augmentedStartProduction);
    
    // Then add all other productions in non-terminal order
    for (String nt : nonTerminals) {
      if (nt.equals(AUGMENTED_START)) {
        continue; // Already added
      }
      List<GrammarParser.Production> prods = productions.get(nt);
      if (prods != null) {
        result.addAll(prods);
      }
    }
    
    return result;
  }
  
  /**
   * Gets the augmented start symbol.
   */
  public String getAugmentedStartSymbol() {
    return AUGMENTED_START;
  }
  
  /**
   * Gets the original start symbol.
   */
  public String getStartSymbol() {
    return startSymbol;
  }
  
  /**
   * Gets all non-terminals (including augmented start).
   */
  public List<String> getNonTerminals() {
    return List.copyOf(nonTerminals);
  }
  
  /**
   * Gets all terminals.
   */
  public List<String> getTerminals() {
    return List.copyOf(terminals);
  }
  
  /**
   * Gets synchronization tokens.
   */
  public List<String> getSyncTokens() {
    return List.copyOf(syncTokens);
  }
  
  /**
   * Gets all productions for a non-terminal.
   */
  public List<GrammarParser.Production> getProductions(String nonTerminal) {
    return List.copyOf(productions.getOrDefault(nonTerminal, List.of()));
  }
  
  /**
   * Gets all productions as a flat indexed list.
   */
  public List<GrammarParser.Production> getAllProductions() {
    return List.copyOf(allProductions);
  }
  
  /**
   * Gets the index of a production in the flat list.
   */
  public int getProductionIndex(GrammarParser.Production production) {
    return allProductions.indexOf(production);
  }
  
  /**
   * Checks if a symbol is a non-terminal.
   */
  public boolean isNonTerminal(String symbol) {
    return nonTerminals.contains(symbol);
  }
  
  /**
   * Checks if a symbol is a terminal.
   */
  public boolean isTerminal(String symbol) {
    return terminals.contains(symbol);
  }
  
  /**
   * Checks if epsilon is in FIRST set of a sequence.
   * This is determined by checking if all symbols in the sequence can derive epsilon.
   */
  public boolean canDeriveEpsilon(List<String> symbols) {
    for (String symbol : symbols) {
      if (isTerminal(symbol)) {
        return false; // Terminal cannot derive epsilon
      }
      if (!canNonTerminalDeriveEpsilon(symbol)) {
        return false;
      }
    }
    return true; // All symbols can derive epsilon
  }
  
  /**
   * Checks if a non-terminal can derive epsilon.
   */
  public boolean canNonTerminalDeriveEpsilon(String nonTerminal) {
    // Check if there's a production with empty RHS
    List<GrammarParser.Production> prods = productions.get(nonTerminal);
    if (prods == null) {
      return false;
    }
    for (GrammarParser.Production prod : prods) {
      if (prod.rhs().isEmpty()) {
        return true;
      }
    }
    return false;
  }
}

