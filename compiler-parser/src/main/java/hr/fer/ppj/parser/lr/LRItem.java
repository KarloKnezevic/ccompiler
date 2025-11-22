package hr.fer.ppj.parser.lr;

import hr.fer.ppj.parser.grammar.GrammarParser.Production;
import java.util.List;
import java.util.Objects;

/**
 * LR(1) item: [A → α·β, a]
 * 
 * <p>Represents a production with a dot position and lookahead symbol.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRItem {
  
  private final Production production;
  private final int dotPosition;
  private final String lookahead;
  
  public LRItem(Production production, int dotPosition, String lookahead) {
    this.production = production;
    this.dotPosition = dotPosition;
    this.lookahead = lookahead;
  }
  
  public Production getProduction() {
    return production;
  }
  
  public int getDotPosition() {
    return dotPosition;
  }
  
  public String getLookahead() {
    return lookahead;
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
    sb.append(", ");
    sb.append(lookahead);
    sb.append("]");
    return sb.toString();
  }
}

