package hr.fer.ppj.semantics.tree;

import java.util.Objects;

/**
 * Terminal node representing a token in the generative parse tree.
 *
 * @param symbol the terminal symbol name (e.g. {@code IDN}, {@code KR_INT})
 * @param line line number in the original source (1-based)
 * @param lexeme the lexeme as produced by the lexer
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record TerminalNode(String symbol, int line, String lexeme) implements ParseNode {

  public TerminalNode {
    Objects.requireNonNull(symbol, "symbol must not be null");
    Objects.requireNonNull(lexeme, "lexeme must not be null");
  }
}

