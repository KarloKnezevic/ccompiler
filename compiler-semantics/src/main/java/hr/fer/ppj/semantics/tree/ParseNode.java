package hr.fer.ppj.semantics.tree;

/**
 * Marker interface for nodes in the generative parse tree used by the semantic analyser.
 *
 * <p>The tree is reconstructed from {@code generativno_stablo.txt} emitted by the parser.
 * Each node is either a non-terminal (internal node) or a terminal (leaf node) that
 * carries lexical information such as the line number and lexeme.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface ParseNode permits NonTerminalNode, TerminalNode {

  /**
   * Returns the grammar symbol for this node.
   *
   * @return the non-terminal (e.g. {@code <izraz>}) or terminal name (e.g. {@code IDN})
   */
  String symbol();
}

