package hr.fer.ppj.semantics.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Non-terminal node representing an internal production in the generative tree. Each node owns a
 * {@link SemanticAttributes} instance that semantic passes can populate when walking the tree.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class NonTerminalNode implements ParseNode {

  private final String symbol;
  private final List<ParseNode> children = new ArrayList<>();
  private final SemanticAttributes attributes = new SemanticAttributes();

  public NonTerminalNode(String symbol) {
    this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
  }

  @Override
  public String symbol() {
    return symbol;
  }

  public List<ParseNode> children() {
    return List.copyOf(children);
  }

  public void addChild(ParseNode child) {
    children.add(Objects.requireNonNull(child));
  }

  public SemanticAttributes attributes() {
    return attributes;
  }
}

