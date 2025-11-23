package hr.fer.ppj.parser.tree;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a parse tree node for generative and syntax trees.
 * 
 * <p>Each node can be:
 * <ul>
 *   <li>An internal node (non-terminal) with children</li>
 *   <li>A leaf node (terminal) with token information</li>
 * </ul>
 * 
 * <p>The parse tree is built during LR(1) parsing and can be output in two formats:
 * <ul>
 *   <li><b>Generative tree</b>: Complete parse tree showing all grammar productions</li>
 *   <li><b>Syntax tree</b>: Simplified AST with intermediate nodes removed</li>
 * </ul>
 * 
 * <p>The syntax tree is optimized for semantic analysis and code generation by
 * removing grammar artifacts that don't add semantic value.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ParseTree {
  
  private final String symbol; // Non-terminal or terminal
  private final int line; // Line number (for terminals)
  private final String lexicalUnit; // Lexical unit (for terminals)
  private final List<ParseTree> children;
  private final boolean isTerminal;
  
  /**
   * Creates an internal node (non-terminal).
   */
  public ParseTree(String nonTerminal) {
    this.symbol = nonTerminal;
    this.line = 0;
    this.lexicalUnit = null;
    this.children = new ArrayList<>();
    this.isTerminal = false;
  }
  
  /**
   * Creates a leaf node (terminal).
   */
  public ParseTree(String terminal, int line, String lexicalUnit) {
    this.symbol = terminal;
    this.line = line;
    this.lexicalUnit = lexicalUnit;
    this.children = new ArrayList<>();
    this.isTerminal = true;
  }
  
  /**
   * Adds a child node.
   */
  public void addChild(ParseTree child) {
    if (isTerminal) {
      throw new IllegalStateException("Cannot add children to terminal node");
    }
    children.add(child);
  }
  
  /**
   * Adds multiple children.
   */
  public void addChildren(List<ParseTree> children) {
    if (isTerminal) {
      throw new IllegalStateException("Cannot add children to terminal node");
    }
    this.children.addAll(children);
  }
  
  public String getSymbol() {
    return symbol;
  }
  
  public int getLine() {
    return line;
  }
  
  public String getLexicalUnit() {
    return lexicalUnit;
  }
  
  public List<ParseTree> getChildren() {
    return List.copyOf(children);
  }
  
  public boolean isTerminal() {
    return isTerminal;
  }
  
  /**
   * Generates the generative tree output format.
   * 
   * <p>Format:
   * <pre>
   * 0:&lt;symbol&gt;
   *     1:&lt;child1&gt;
   *         2:&lt;child2&gt;
   *     ...
   * </pre>
   */
  public String toGenerativeTreeString() {
    StringBuilder sb = new StringBuilder();
    toGenerativeTreeString(sb, 0, 0);
    return sb.toString();
  }
  
  private int toGenerativeTreeString(StringBuilder sb, int nodeNumber, int depth) {
    int currentNumber = nodeNumber;
    
    // Print this node
    for (int i = 0; i < depth; i++) {
      sb.append("    ");
    }
    sb.append(currentNumber).append(":");
    
    if (isTerminal) {
      sb.append(symbol);
      if (lexicalUnit != null && !lexicalUnit.isEmpty()) {
        sb.append(" , ").append(lexicalUnit);
      }
    } else {
      sb.append(symbol);
    }
    sb.append("\n");
    
    // Print children
    int nextNumber = currentNumber + 1;
    for (ParseTree child : children) {
      nextNumber = child.toGenerativeTreeString(sb, nextNumber, depth + 1);
    }
    
    return nextNumber;
  }
  
  /**
   * Generates the syntax tree (AST) output format.
   * 
   * <p>The syntax tree is a simplified, compact representation suitable for semantic analysis
   * and code generation. It removes intermediate grammar nodes and focuses on essential
   * program structure.
   * 
   * <p>Format is the same as generative tree but with simplified structure.
   */
  public String toSyntaxTreeString() {
    // Generate simplified syntax tree by removing unnecessary intermediate nodes
    StringBuilder sb = new StringBuilder();
    toSyntaxTreeString(sb, 0, 0);
    return sb.toString();
  }
  
  /**
   * Recursively generates syntax tree string, skipping intermediate nodes.
   */
  private int toSyntaxTreeString(StringBuilder sb, int nodeNumber, int depth) {
    // Skip certain intermediate grammar nodes that don't add semantic value
    if (shouldSkipInSyntaxTree()) {
      // Pass through to children
      int nextNumber = nodeNumber;
      for (ParseTree child : children) {
        nextNumber = child.toSyntaxTreeString(sb, nextNumber, depth);
      }
      return nextNumber;
    }
    
    int currentNumber = nodeNumber;
    
    // Print this node
    for (int i = 0; i < depth; i++) {
      sb.append("    ");
    }
    sb.append(currentNumber).append(":");
    
    if (isTerminal) {
      sb.append(symbol);
      if (lexicalUnit != null && !lexicalUnit.isEmpty()) {
        sb.append(" , ").append(lexicalUnit);
      }
    } else {
      sb.append(symbol);
    }
    sb.append("\n");
    
    // Print children
    int nextNumber = currentNumber + 1;
    for (ParseTree child : children) {
      nextNumber = child.toSyntaxTreeString(sb, nextNumber, depth + 1);
    }
    
    return nextNumber;
  }
  
  /**
   * Determines if this node should be skipped in syntax tree output.
   * Intermediate grammar nodes that don't add semantic value are skipped.
   */
  private boolean shouldSkipInSyntaxTree() {
    if (isTerminal) {
      return false;
    }
    
    // Skip intermediate nodes that are just wrappers
    // These are grammar artifacts that don't add semantic meaning
    String symbol = this.symbol;
    return symbol.startsWith("<lista_") && symbol.contains("_naredba")
        || symbol.startsWith("<izraz>") && symbol.contains("_i_")
        || symbol.equals("<slozena_naredba>") && children.size() == 1
        || symbol.startsWith("<specifikator_") && children.size() == 1;
  }
}

