package hr.fer.ppj.parser.lr;

import hr.fer.ppj.parser.grammar.GrammarParser.Production;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * LR(1) item: [A → α·β, L]
 * 
 * <p>Represents a production with a dot position and lookahead set.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRItem {
  
  private final Production production;
  private final int dotPosition;
  private final Set<String> lookahead;
  
  public LRItem(Production production, int dotPosition, Set<String> lookahead) {
    this.production = production;
    this.dotPosition = dotPosition;
    this.lookahead = new HashSet<>(lookahead);
  }
  
  /**
   * Creates an LR item with a single lookahead symbol.
   */
  public LRItem(Production production, int dotPosition, String lookahead) {
    this(production, dotPosition, Set.of(lookahead));
  }
  
  public Production getProduction() {
    return production;
  }
  
  public int getDotPosition() {
    return dotPosition;
  }
  
  public Set<String> getLookahead() {
    return Set.copyOf(lookahead);
  }
  
  /**
   * Checks if a symbol is in the lookahead set.
   */
  public boolean hasLookahead(String symbol) {
    return lookahead.contains(symbol);
  }
  
  public boolean isReduceItem() {
    return dotPosition >= production.rhs().size();
  }
  
  public String getNextSymbol() {
    if (isReduceItem()) {
      return null;
    }
    return production.rhs().get(dotPosition);
  }
  
  public List<String> getRemainingSymbols() {
    if (isReduceItem()) {
      return List.of();
    }
    return production.rhs().subList(dotPosition + 1, production.rhs().size());
  }
  
  public LRItem advance() {
    if (isReduceItem()) {
      return this;
    }
    return new LRItem(production, dotPosition + 1, lookahead);
  }
  
  /**
   * Merges this item with another item that has the same production and dot position.
   * Returns a new item with the union of lookahead sets.
   */
  public LRItem merge(LRItem other) {
    if (!production.equals(other.production) || dotPosition != other.dotPosition) {
      throw new IllegalArgumentException("Cannot merge items with different productions or dot positions");
    }
    Set<String> mergedLookahead = new HashSet<>(this.lookahead);
    mergedLookahead.addAll(other.lookahead);
    return new LRItem(production, dotPosition, mergedLookahead);
  }
  
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    LRItem other = (LRItem) obj;
    return dotPosition == other.dotPosition
        && Objects.equals(production, other.production)
        && Objects.equals(lookahead, other.lookahead);
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(production, dotPosition, lookahead);
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    sb.append(production.lhs());
    sb.append(" → ");
    List<String> rhs = production.rhs();
    for (int i = 0; i < rhs.size(); i++) {
      if (i == dotPosition) {
        sb.append("·");
      }
      sb.append(rhs.get(i));
      if (i < rhs.size() - 1) {
        sb.append(" ");
      }
    }
    if (dotPosition >= rhs.size()) {
      sb.append("·");
    }
    sb.append(", {");
    sb.append(String.join(", ", lookahead));
    sb.append("}]");
    return sb.toString();
  }
}
