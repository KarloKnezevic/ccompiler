package hr.fer.ppj.lexer.gen;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for lexer specification files.
 * 
 * <p>This parser processes lexer definition files in the PPJ format, which consists of:
 * <ul>
 *   <li><strong>Macro definitions:</strong> {@code {name} pattern} - Define reusable regex patterns</li>
 *   <li><strong>State declarations:</strong> {@code %X state1 state2 ...} - Declare lexer states</li>
 *   <li><strong>Token declarations:</strong> {@code %L TOKEN1 TOKEN2 ...} - Declare token types</li>
 *   <li><strong>Lexer rules:</strong> {@code <state>pattern { action }} - Define matching rules</li>
 * </ul>
 * 
 * <p>The parser performs manual parsing without using any external regex libraries.
 * All pattern matching and string processing is done using standard Java string operations.
 * 
 * <p><strong>Parsing Algorithm:</strong>
 * <ol>
 *   <li>Read the specification file line by line</li>
 *   <li>Identify line type by prefix ({@code %X}, {@code %L}, {@code {}}, {@code <})</li>
 *   <li>Parse each line according to its type</li>
 *   <li>Handle quoted patterns (distinguishing between literal strings and regex patterns)</li>
 *   <li>Extract action blocks (enclosed in braces)</li>
 * </ol>
 * 
 * <p><strong>Pattern Processing:</strong>
 * <ul>
 *   <li>Quoted patterns like {@code "break"} are treated as literal strings (quotes removed)</li>
 *   <li>Quoted patterns containing regex operators like {@code "({macro}|\\")*"} are treated
 *       as regex patterns (quotes preserved)</li>
 *   <li>Escape sequences are processed: {@code \\}, {@code \"}, {@code \n}, {@code \t}</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @version 1.0
 */
public final class LexerSpecParser {
  
  private final Map<String, String> macros = new HashMap<>();
  private final List<String> states = new ArrayList<>();
  private final List<String> tokens = new ArrayList<>();
  private final List<LexerRule> rules = new ArrayList<>();
  
  private int lineNumber = 0;
  
  public record LexerRule(
      String state,
      String pattern,
      String token,
      List<String> actions
  ) {}
  
  public void parse(Reader reader) throws IOException {
    try (BufferedReader br = new BufferedReader(reader)) {
      String line;
      while ((line = br.readLine()) != null) {
        lineNumber++;
        line = line.trim();
        if (line.isEmpty() || line.startsWith("//")) {
          continue;
        }
        
        if (line.startsWith("%X")) {
          parseStateDeclaration(line);
        } else if (line.startsWith("%L")) {
          parseTokenDeclaration(line);
        } else if (line.startsWith("{")) {
          parseMacroDefinition(line);
        } else if (line.startsWith("<")) {
          parseRule(br, line);
        }
      }
    }
  }
  
  private void parseStateDeclaration(String line) {
    String[] parts = line.substring(2).trim().split("\\s+");
    for (String part : parts) {
      if (!part.isEmpty()) {
        states.add(part);
      }
    }
  }
  
  private void parseTokenDeclaration(String line) {
    String[] parts = line.substring(2).trim().split("\\s+");
    for (String part : parts) {
      if (!part.isEmpty()) {
        tokens.add(part);
      }
    }
  }
  
  private void parseMacroDefinition(String line) {
    int braceEnd = line.indexOf('}');
    if (braceEnd == -1) {
      throw new IllegalArgumentException("Invalid macro definition at line " + lineNumber);
    }
    String namePart = line.substring(1, braceEnd).trim();
    String pattern = line.substring(braceEnd + 1).trim();
    macros.put(namePart, pattern);
  }
  
  private void parseRule(BufferedReader br, String firstLine) throws IOException {
    int stateEnd = firstLine.indexOf('>');
    if (stateEnd == -1) {
      throw new IllegalArgumentException("Invalid rule at line " + lineNumber);
    }
    String state = firstLine.substring(1, stateEnd);
    
    String pattern = firstLine.substring(stateEnd + 1).trim();
    // Handle quoted patterns
    // IMPORTANT: Quotes can be either:
    // 1. Delimiters for a simple literal pattern (like "break")
    // 2. Part of the regex pattern itself (like "pattern" where quotes match literal quotes)
    if (pattern.startsWith("\"")) {
      // Check if pattern is just a single quote (like <S_pocetno>")
      if (pattern.equals("\"")) {
        pattern = "\""; // Pattern is just the quote character
      } else {
        // Find the matching closing quote, respecting escaped quotes
        int quoteEnd = -1;
        for (int i = 1; i < pattern.length(); i++) {
          if (pattern.charAt(i) == '"' && (i == 1 || pattern.charAt(i - 1) != '\\')) {
            quoteEnd = i;
            break;
          }
        }
        if (quoteEnd == -1) {
          throw new IllegalArgumentException("Unclosed string in pattern at line " + lineNumber);
        }
        
        // Extract content between quotes
        String content = pattern.substring(1, quoteEnd);
        
        // Check if this is a regex pattern (contains operators like |, *, (, ), etc.)
        // If it's a regex pattern, the quotes are part of the pattern and should be kept
        // If it's a simple literal (like "break"), remove the quotes
        boolean isRegexPattern = containsRegexOperators(content);
        
        if (isRegexPattern) {
          // Quotes are part of the regex pattern - keep them
          // Unescape escaped characters in the content, but keep the outer quotes
          content = unescapePattern(content);
          pattern = "\"" + content + "\"";
        } else {
          // Simple literal pattern - remove quotes and unescape
          pattern = unescapePattern(content);
        }
      }
    }
    // If no quotes, pattern is a literal string (like "break", "char", etc.)
    
    // Read action block
    StringBuilder actionBlock = new StringBuilder();
    String line = br.readLine();
    lineNumber++;
    if (line != null && line.trim().startsWith("{")) {
      int braceCount = 1;
      actionBlock.append(line).append("\n");
      while (braceCount > 0 && (line = br.readLine()) != null) {
        lineNumber++;
        actionBlock.append(line).append("\n");
        for (char c : line.toCharArray()) {
          if (c == '{') braceCount++;
          if (c == '}') braceCount--;
        }
      }
    }
    
    String actionText = actionBlock.toString().trim();
    List<String> actions = parseActions(actionText);
    String token = extractToken(actions);
    
    rules.add(new LexerRule(state, pattern, token, actions));
  }
  
  private List<String> parseActions(String actionText) {
    List<String> actions = new ArrayList<>();
    if (actionText.startsWith("{") && actionText.endsWith("}")) {
      String content = actionText.substring(1, actionText.length() - 1).trim();
      String[] lines = content.split("\n");
      for (String line : lines) {
        line = line.trim();
        if (!line.isEmpty() && !line.equals("-")) {
          actions.add(line);
        }
      }
    }
    return actions;
  }
  
  private String extractToken(List<String> actions) {
    for (String action : actions) {
      if (!action.startsWith("UDJI_U_STANJE") 
          && !action.startsWith("VRATI_SE") 
          && !action.startsWith("NOVI_REDAK")
          && !action.equals("-")
          && !action.isEmpty()) {
        return action;
      }
    }
    return null;
  }
  
  public Map<String, String> getMacros() {
    return Map.copyOf(macros);
  }
  
  public List<String> getStates() {
    return List.copyOf(states);
  }
  
  public List<String> getTokens() {
    return List.copyOf(tokens);
  }
  
  public List<LexerRule> getRules() {
    return List.copyOf(rules);
  }
  
  /**
   * Checks if a pattern contains regex operators, indicating it's a regex pattern
   * rather than a simple literal string.
   */
  private boolean containsRegexOperators(String pattern) {
    // Check for common regex operators: |, *, (, ), {, }
    // But we need to respect escaping
    boolean inEscape = false;
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (inEscape) {
        inEscape = false;
        continue;
      }
      if (c == '\\') {
        inEscape = true;
        continue;
      }
      // Check for regex operators
      if (c == '|' || c == '*' || c == '(' || c == ')' || c == '{' || c == '}') {
        return true;
      }
    }
    return false;
  }
  
  /**
   * Unescapes pattern string (handles \\, \", etc.).
   */
  private String unescapePattern(String pattern) {
    StringBuilder result = new StringBuilder();
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '\\' && i + 1 < pattern.length()) {
        char next = pattern.charAt(i + 1);
        switch (next) {
          case 'n':
            result.append('\n');
            i++;
            break;
          case 't':
            result.append('\t');
            i++;
            break;
          case '\\':
            result.append('\\');
            i++;
            break;
          case '"':
            result.append('"');
            i++;
            break;
          default:
            result.append(c);
        }
      } else {
        result.append(c);
      }
    }
    return result.toString();
  }
}

