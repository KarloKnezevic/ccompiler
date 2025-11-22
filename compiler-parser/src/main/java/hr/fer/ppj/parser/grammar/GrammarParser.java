package hr.fer.ppj.parser.grammar;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for grammar specification files (ppjLang_sintaksa.txt format).
 * 
 * <p>Parses:
 * <ul>
 *   <li>Non-terminal declarations: %V &lt;nt1&gt; &lt;nt2&gt; ...</li>
 *   <li>Terminal declarations: %T TOKEN1 TOKEN2 ...</li>
 *   <li>Synchronization tokens: %Syn TOKEN1 TOKEN2 ...</li>
 *   <li>Productions: &lt;nt&gt; ::= rhs1 | rhs2 | ...</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * 
 * <p>NO REGEX libraries used - manual parsing only.
 */
public final class GrammarParser {
  
  private final List<String> nonTerminals = new ArrayList<>();
  private final List<String> terminals = new ArrayList<>();
  private final List<String> syncTokens = new ArrayList<>();
  private final Map<String, List<Production>> productions = new HashMap<>();
  
  public void parse(Reader reader) throws IOException {
    try (BufferedReader br = new BufferedReader(reader)) {
      String line;
      while ((line = br.readLine()) != null) {
        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        
        if (line.startsWith("%V")) {
          parseNonTerminals(line);
        } else if (line.startsWith("%T")) {
          parseTerminals(line);
        } else if (line.startsWith("%Syn")) {
          parseSyncTokens(line);
        } else if (line.startsWith("<")) {
          parseProduction(line);
        }
      }
    }
  }
  
  private void parseNonTerminals(String line) {
    String[] parts = line.substring(2).trim().split("\\s+");
    for (String part : parts) {
      if (part.startsWith("<") && part.endsWith(">")) {
        nonTerminals.add(part);
      }
    }
  }
  
  private void parseTerminals(String line) {
    String[] parts = line.substring(2).trim().split("\\s+");
    for (String part : parts) {
      if (!part.isEmpty()) {
        terminals.add(part);
      }
    }
  }
  
  private void parseSyncTokens(String line) {
    String[] parts = line.substring(4).trim().split("\\s+");
    for (String part : parts) {
      if (!part.isEmpty()) {
        syncTokens.add(part);
      }
    }
  }
  
  private void parseProduction(String line) {
    int arrowPos = line.indexOf("::=");
    if (arrowPos == -1) {
      // Alternative format: <nt> followed by lines with alternatives
      int ntEnd = line.indexOf(">");
      if (ntEnd == -1) {
        return;
      }
      String nt = line.substring(0, ntEnd + 1);
      String rhs = line.substring(ntEnd + 1).trim();
      if (!rhs.isEmpty()) {
        addProduction(nt, rhs);
      }
    } else {
      String lhs = line.substring(0, arrowPos).trim();
      String rhs = line.substring(arrowPos + 3).trim();
      addProduction(lhs, rhs);
    }
  }
  
  private void addProduction(String lhs, String rhs) {
    if (lhs.startsWith("<") && lhs.endsWith(">")) {
      productions.computeIfAbsent(lhs, k -> new ArrayList<>())
          .add(new Production(lhs, parseRHS(rhs)));
    }
  }
  
  private List<String> parseRHS(String rhs) {
    List<String> symbols = new ArrayList<>();
    String[] parts = rhs.trim().split("\\s+");
    for (String part : parts) {
      if (!part.isEmpty()) {
        symbols.add(part);
      }
    }
    return symbols;
  }
  
  public List<String> getNonTerminals() {
    return List.copyOf(nonTerminals);
  }
  
  public List<String> getTerminals() {
    return List.copyOf(terminals);
  }
  
  public List<String> getSyncTokens() {
    return List.copyOf(syncTokens);
  }
  
  public Map<String, List<Production>> getProductions() {
    return Map.copyOf(productions);
  }
  
  public record Production(String lhs, List<String> rhs) {}
}

