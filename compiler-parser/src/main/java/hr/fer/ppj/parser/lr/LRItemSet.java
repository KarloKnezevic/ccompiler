package hr.fer.ppj.parser.lr;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Set of LR(1) items.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRItemSet {
  
  private final Set<LRItem> items = new HashSet<>();
  
  public LRItemSet() {}
  
  public LRItemSet(LRItemSet other) {
    this.items.addAll(other.items);
  }
  
  public void addItem(LRItem item) {
    items.add(item);
  }
  
  public void addAllItems(List<LRItem> items) {
    this.items.addAll(items);
  }
  
  public Set<LRItem> getItems() {
    return Set.copyOf(items);
  }
  
  public boolean contains(LRItem item) {
    return items.contains(item);
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

