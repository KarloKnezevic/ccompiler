package hr.fer.ppj.semantics.io;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code generativno_stablo.txt} into an in-memory tree representation.
 *
 * <p>The parser expects indentation using four spaces per depth level and node lines in the form:
 *
 * <pre>
 * depth:&lt;non-terminal&gt;
 * depth:TERMINAL , lexeme
 * </pre>
 *
 * <p>Terminal nodes are enriched with line information by consuming {@link SemanticToken}s in the
 * same order as the lexer output.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class GenerativeTreeParser {

  private static final Pattern LINE_PATTERN = Pattern.compile("^(\\s*)(?:(\\d+):)?(.*)$");

  private final List<String> lines;
  private final List<SemanticToken> tokens;
  private int tokenIndex = 0;

  public GenerativeTreeParser(List<String> lines, List<SemanticToken> tokens) {
    this.lines = Objects.requireNonNull(lines, "lines must not be null");
    this.tokens = Objects.requireNonNull(tokens, "tokens must not be null");
  }

  public static NonTerminalNode parse(Reader treeReader, List<SemanticToken> tokens)
      throws IOException {
    try (BufferedReader bufferedReader = new BufferedReader(treeReader)) {
      List<String> lines = new ArrayList<>();
      String line;
      while ((line = bufferedReader.readLine()) != null) {
        if (!line.isBlank()) {
          lines.add(line);
        }
      }
      return new GenerativeTreeParser(lines, tokens).parse();
    }
  }

  public NonTerminalNode parse() {
    if (lines.isEmpty()) {
      throw new IllegalStateException("Generative tree is empty");
    }

    List<NonTerminalNode> stack = new ArrayList<>();
    NonTerminalNode root = null;

    for (String rawLine : lines) {
      Matcher matcher = LINE_PATTERN.matcher(rawLine);
      if (!matcher.matches()) {
        throw new IllegalStateException("Invalid generative tree line: " + rawLine);
      }

      int depth = indentToDepth(matcher.group(1));
      String content = matcher.group(3).trim();

      while (stack.size() > depth) {
        stack.remove(stack.size() - 1);
      }

      ParseNode node = createNode(content);

      if (stack.isEmpty()) {
        if (!(node instanceof NonTerminalNode nonTerminal)) {
          throw new IllegalStateException("Root must be a non-terminal node");
        }
        root = nonTerminal;
        stack.add(nonTerminal);
      } else {
        NonTerminalNode parent = stack.get(stack.size() - 1);
        parent.addChild(node);
        if (node instanceof NonTerminalNode nonTerminal) {
          stack.add(nonTerminal);
        }
      }
    }

    if (tokenIndex != tokens.size()) {
      throw new IllegalStateException(
          "Unconsumed tokens detected: expected "
              + tokenIndex
              + " but had "
              + tokens.size());
    }

    return root;
  }

  private ParseNode createNode(String content) {
    if (content.startsWith("<") && content.endsWith(">")) {
      return new NonTerminalNode(content);
    }

    if ("$".equals(content)) {
      // Epsilon production placeholder. It has no lexical information.
      return new NonTerminalNode("$");
    }

    int commaIndex = content.indexOf(" , ");
    if (commaIndex < 0) {
      throw new IllegalStateException("Terminal node missing lexeme part: " + content);
    }

    String tokenName = content.substring(0, commaIndex).trim();
    SemanticToken token = nextToken(tokenName);
    return new TerminalNode(tokenName, token.line(), token.lexeme());
  }

  private SemanticToken nextToken(String expectedType) {
    if (tokenIndex >= tokens.size()) {
      throw new IllegalStateException("Ran out of tokens while parsing terminals");
    }
    SemanticToken token = tokens.get(tokenIndex++);
    if (!token.type().equals(expectedType)) {
      throw new IllegalStateException(
          "Unexpected token type. Expected "
              + expectedType
              + " but found "
              + token.type());
    }
    return token;
  }

  private static int indentToDepth(String indent) {
    int length = indent.length();
    if (length % 4 != 0) {
      throw new IllegalStateException("Invalid indentation (must be multiples of 4 spaces)");
    }
    return length / 4;
  }
}

