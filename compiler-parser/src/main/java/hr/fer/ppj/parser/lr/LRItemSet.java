package hr.fer.ppj.parser.lr;

import hr.fer.ppj.parser.grammar.GrammarParser.Production;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Set of LR(1) items with automatic merging of items with same production and dot position.
 * 
 * <p>Items with the same production and dot position but different lookaheads are automatically
 * merged by taking the union of their lookahead sets.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRItemSet {
  
  // Map from (production, dotPosition) to merged item
  private final Map<ItemKey, LRItem> items = new HashMap<>();
  
  public LRItemSet() {}
  
  public LRItemSet(LRItemSet other) {
    this.items.putAll(other.items);
  }
  
  /**
   * Adds an item to the set, merging with existing items if they have the same production and dot position.
   */
  public void addItem(LRItem item) {
    ItemKey key = new ItemKey(item.getProduction(), item.getDotPosition());
    LRItem existing = items.get(key);
    if (existing != null) {
      items.put(key, existing.merge(item));
    } else {
      items.put(key, item);
    }
  }
  
  /**
   * Adds all items from a list.
   */
  public void addAllItems(List<LRItem> itemsToAdd) {
    for (LRItem item : itemsToAdd) {
      addItem(item);
    }
  }
  
  /**
   * Gets all items in the set.
   */
  public Set<LRItem> getItems() {
    return Set.copyOf(items.values());
  }
  
  /**
   * Gets items as a list (for iteration order).
   */
  public List<LRItem> getItemsList() {
    return new ArrayList<>(items.values());
  }
  
  /**
   * Checks if the set contains an item with the same production and dot position.
   */
  public boolean contains(LRItem item) {
    ItemKey key = new ItemKey(item.getProduction(), item.getDotPosition());
    return items.containsKey(key);
  }
  
  /**
   * Gets the merged item for a given production and dot position.
   */
  public LRItem getItem(Production production, int dotPosition) {
    ItemKey key = new ItemKey(production, dotPosition);
    return items.get(key);
  }
  
  /**
   * Key for indexing items by production and dot position.
   */
  private static final class ItemKey {
    private final Production production;
    private final int dotPosition;
    
    ItemKey(Production production, int dotPosition) {
      this.production = production;
      this.dotPosition = dotPosition;
    }
    
    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      ItemKey other = (ItemKey) obj;
      return dotPosition == other.dotPosition && Objects.equals(production, other.production);
    }
    
    @Override
    public int hashCode() {
      return Objects.hash(production, dotPosition);
    }
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    LRItemSet other = (LRItemSet) obj;
    return Objects.equals(items, other.items);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(items);
  }
  
  @Override
  public String toString() {
    return items.toString();
  }
}

