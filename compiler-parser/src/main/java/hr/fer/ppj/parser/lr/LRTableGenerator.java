package hr.fer.ppj.parser.lr;

import hr.fer.ppj.parser.grammar.GrammarParser;
import hr.fer.ppj.parser.grammar.GrammarParser.Production;
import hr.fer.ppj.parser.table.LRTable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Generates LR(1) parsing tables from a grammar.
 * 
 * <p>Implements canonical LR(1) item set construction.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRTableGenerator {
  
  private final GrammarParser grammar;
  private final List<LRItemSet> itemSets = new ArrayList<>();
  private final Map<LRItemSet, Integer> itemSetMap = new HashMap<>();
  
  public LRTableGenerator(GrammarParser grammar) {
    this.grammar = grammar;
  }
  
  public LRTable generate() {
    LRTable table = new LRTable();
    
    // Build canonical collection of LR(1) item sets
    buildItemSets();
    
    // Generate ACTION and GOTO tables
    for (int i = 0; i < itemSets.size(); i++) {
      LRItemSet itemSet = itemSets.get(i);
      
      // Generate ACTION entries
      for (LRItem item : itemSet.getItems()) {
        if (item.isReduceItem()) {
          // Reduce action
          if (item.getProduction().lhs().equals("<prijevodna_jedinica>")) {
            table.setAction(i, "$", "acc");
          } else {
            table.setAction(i, item.getLookahead(), "r" + getProductionIndex(item.getProduction()));
          }
        } else {
          // Shift action
          String nextSymbol = item.getNextSymbol();
          LRItemSet nextSet = gotoSet(itemSet, nextSymbol);
          int nextState = itemSetMap.get(nextSet);
          table.setAction(i, nextSymbol, "s" + nextState);
        }
      }
      
      // Generate GOTO entries
      for (String nonTerminal : grammar.getNonTerminals()) {
        LRItemSet nextSet = gotoSet(itemSet, nonTerminal);
        if (nextSet != null) {
          int nextState = itemSetMap.get(nextSet);
          table.setGoto(i, nonTerminal, nextState);
        }
      }
    }
    
    return table;
  }
  
  private void buildItemSets() {
    // Start with initial item set
    LRItemSet initial = closure(createInitialItemSet());
    itemSets.add(initial);
    itemSetMap.put(initial, 0);
    
    // Build all item sets
    List<LRItemSet> toProcess = new ArrayList<>();
    toProcess.add(initial);
    
    while (!toProcess.isEmpty()) {
      LRItemSet current = toProcess.remove(0);
      
      // Find all symbols that can follow the dot
      Set<String> symbols = new HashSet<>();
      for (LRItem item : current.getItems()) {
        if (!item.isReduceItem()) {
          symbols.add(item.getNextSymbol());
        }
      }
      
      // Create new item sets for each symbol
      for (String symbol : symbols) {
        LRItemSet nextSet = gotoSet(current, symbol);
        if (nextSet != null && !itemSetMap.containsKey(nextSet)) {
          int newState = itemSets.size();
          itemSets.add(nextSet);
          itemSetMap.put(nextSet, newState);
          toProcess.add(nextSet);
        }
      }
    }
  }
  
  private LRItemSet createInitialItemSet() {
    // Find start production
    Production startProd = grammar.getProductions().get("<prijevodna_jedinica>").get(0);
    LRItem initialItem = new LRItem(startProd, 0, "$");
    LRItemSet set = new LRItemSet();
    set.addItem(initialItem);
    return set;
  }
  
  private LRItemSet closure(LRItemSet itemSet) {
    LRItemSet result = new LRItemSet(itemSet);
    boolean changed = true;
    
    while (changed) {
      changed = false;
      List<LRItem> toAdd = new ArrayList<>();
      
      for (LRItem item : result.getItems()) {
        if (!item.isReduceItem()) {
          String nextSymbol = item.getNextSymbol();
          if (grammar.getNonTerminals().contains(nextSymbol)) {
            // Add items for all productions of this non-terminal
            List<Production> prods = grammar.getProductions().get(nextSymbol);
            Set<String> firstSet = computeFirst(item.getRemainingSymbols(), item.getLookahead());
            
            for (Production prod : prods) {
              for (String lookahead : firstSet) {
                LRItem newItem = new LRItem(prod, 0, lookahead);
                if (!result.contains(newItem)) {
                  toAdd.add(newItem);
                  changed = true;
                }
              }
            }
          }
        }
      }
      
      result.addAllItems(toAdd);
    }
    
    return result;
  }
  
  private LRItemSet gotoSet(LRItemSet itemSet, String symbol) {
    LRItemSet result = new LRItemSet();
    
    for (LRItem item : itemSet.getItems()) {
      if (!item.isReduceItem() && item.getNextSymbol().equals(symbol)) {
        LRItem nextItem = item.advance();
        result.addItem(nextItem);
      }
    }
    
    if (result.getItems().isEmpty()) {
      return null;
    }
    
    return closure(result);
  }
  
  private Set<String> computeFirst(List<String> symbols, String lookahead) {
    Set<String> first = new HashSet<>();
    
    if (symbols.isEmpty()) {
      first.add(lookahead);
      return first;
    }
    
    String firstSymbol = symbols.get(0);
    if (grammar.getTerminals().contains(firstSymbol)) {
      first.add(firstSymbol);
    } else {
      // Non-terminal - add FIRST set
      // TODO: Implement proper FIRST set computation
      first.addAll(grammar.getTerminals());
    }
    
    return first;
  }
  
  private int getProductionIndex(Production prod) {
    // TODO: Map production to index
    return 0;
  }
}

