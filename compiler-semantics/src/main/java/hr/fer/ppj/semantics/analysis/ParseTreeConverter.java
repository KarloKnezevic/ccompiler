package hr.fer.ppj.semantics.analysis;

import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;

/**
 * Converts the parser's {@link ParseTree} representation into the semantic analyser's mutable tree
 * model ({@link NonTerminalNode}/{@link TerminalNode}). The semantic phase requires mutable nodes so
 * that it can attach {@link hr.fer.ppj.semantics.tree.SemanticAttributes} as it walks the tree, so
 * we perform this one-time conversion up front.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class ParseTreeConverter {

  /**
   * Converts the full parse tree. The method guarantees that the root element is a non-terminal,
   * because the semantic analyzer starts from {@code <prijevodna_jedinica>}.
   */
  NonTerminalNode convert(ParseTree parseTree) {
    ParseNode node = convertNode(parseTree);
    if (node instanceof NonTerminalNode nonTerminal) {
      return nonTerminal;
    }
    throw new IllegalArgumentException("Root of generative tree must be non-terminal");
  }

  /**
   * Recursively converts children while preserving ordering. Terminals are turned into immutable
   * {@link TerminalNode} instances that retain line numbers and lexemes for error reporting.
   */
  private ParseNode convertNode(ParseTree node) {
    if (node.isTerminal()) {
      String lexeme = node.getLexicalUnit() == null ? "" : node.getLexicalUnit();
      return new TerminalNode(node.getSymbol(), node.getLine(), lexeme);
    }

    NonTerminalNode target = new NonTerminalNode(node.getSymbol());
    for (ParseTree child : node.getChildren()) {
      target.addChild(convertNode(child));
    }
    return target;
  }
}

