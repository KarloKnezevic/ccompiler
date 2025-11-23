package hr.fer.ppj.parser.lr;

import hr.fer.ppj.parser.grammar.FirstSetComputer;
import hr.fer.ppj.parser.grammar.Grammar;
import hr.fer.ppj.parser.grammar.GrammarParser.Production;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Computes CLOSURE of LR(1) item sets.
 * 
 * <p>For each item [A → α·Bβ, L] in the set:
 * <ul>
 *   <li>For each production B → γ:</li>
 *   <li>Compute T = FIRST(β)</li>
 *   <li>If β can derive ε, add L to T</li>
 *   <li>Add [B → ·γ, T] to the set</li>
 * </ul>
 * 
 * <p>Repeat until no new items can be added.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRClosure {
  
  private final Grammar grammar;
  private final FirstSetComputer firstComputer;
  
  public LRClosure(Grammar grammar, FirstSetComputer firstComputer) {
    this.grammar = grammar;
    this.firstComputer = firstComputer;
  }
  
  /**
   * Computes the closure of an item set.
   * 
   * @param itemSet The initial item set
   * @return The closure of the item set
   */
  public LRItemSet closure(LRItemSet itemSet) {
    LRItemSet result = new LRItemSet(itemSet);
    boolean changed = true;
    int iterations = 0;
    final int MAX_ITERATIONS = 1000; // Safety limit
    
    while (changed && iterations < MAX_ITERATIONS) {
      iterations++;
      changed = false;
      List<LRItem> itemsToProcess = new ArrayList<>(result.getItems());
      
      for (LRItem item : itemsToProcess) {
        if (!item.isReduceItem()) {
          String nextSymbol = item.getNextSymbol();
          
          // If next symbol is a non-terminal, add items for its productions
          if (grammar.isNonTerminal(nextSymbol)) {
            List<Production> prods = grammar.getProductions(nextSymbol);
            List<String> remaining = item.getRemainingSymbols();
            
            // Compute FIRST(remaining)
            Set<String> firstOfRemaining = new HashSet<>(firstComputer.computeFirst(remaining));
            
            // If remaining can derive epsilon, add lookahead from item
            if (firstComputer.hasEpsilon(remaining)) {
              firstOfRemaining.addAll(item.getLookahead());
            }
            
            // Add items for each production of nextSymbol
            for (Production prod : prods) {
              LRItem newItem = new LRItem(prod, 0, firstOfRemaining);
              if (!result.contains(newItem)) {
                result.addItem(newItem);
                changed = true;
              } else {
                // Merge with existing item
                LRItem existing = result.getItem(prod, 0);
                if (existing != null) {
                  LRItem merged = existing.merge(newItem);
                  // Check if merged item is different from existing
                  if (!merged.getLookahead().equals(existing.getLookahead())) {
                    result.addItem(merged);
                    changed = true;
                  }
                }
              }
            }
          }
        }
      }
    }
    
    if (iterations >= MAX_ITERATIONS) {
      throw new IllegalStateException("CLOSURE exceeded maximum iterations (" + MAX_ITERATIONS + ")");
    }
    
    return result;
  }
}

