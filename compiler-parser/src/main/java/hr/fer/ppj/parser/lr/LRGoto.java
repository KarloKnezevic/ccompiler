package hr.fer.ppj.parser.lr;

/**
 * Computes GOTO transitions for LR(1) item sets.
 * 
 * <p>GOTO(I, X) = CLOSURE({ [A → αX·β, L] | [A → α·Xβ, L] ∈ I })
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRGoto {
  
  private final LRClosure closure;
  
  public LRGoto(LRClosure closure) {
    this.closure = closure;
  }
  
  /**
   * Computes GOTO(I, X) for an item set I and symbol X.
   * 
   * @param itemSet The item set I
   * @param symbol The symbol X (terminal or non-terminal)
   * @return The GOTO set, or null if empty
   */
  public LRItemSet gotoSet(LRItemSet itemSet, String symbol) {
    LRItemSet result = new LRItemSet();
    
    // Find all items [A → α·Xβ, L] where next symbol is X
    for (LRItem item : itemSet.getItems()) {
      if (!item.isReduceItem() && symbol.equals(item.getNextSymbol())) {
        // Advance the dot: [A → αX·β, L]
        LRItem advanced = item.advance();
        result.addItem(advanced);
      }
    }
    
    // If no items found, return null
    if (result.getItems().isEmpty()) {
      return null;
    }
    
    // Compute closure of the result
    return closure.closure(result);
  }
}

