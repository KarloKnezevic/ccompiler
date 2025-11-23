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
      String currentLHS = null;
      boolean hasProductionsForCurrentLHS = false;
      boolean lastLineWasEmpty = false;
      
      while ((line = br.readLine()) != null) {
        String originalLine = line;
        boolean hasLeadingSpace = !originalLine.isEmpty() && Character.isWhitespace(originalLine.charAt(0));
        line = line.trim();
        boolean isEmpty = line.isEmpty();
        
        if (isEmpty) {
          lastLineWasEmpty = true;
          continue;
        }
        
        if (line.startsWith("%V")) {
          // Finish current LHS if any
          if (currentLHS != null && !hasProductionsForCurrentLHS) {
            addProduction(currentLHS, "$"); // Epsilon production
          }
          parseNonTerminals(line);
          currentLHS = null;
          hasProductionsForCurrentLHS = false;
          lastLineWasEmpty = false;
        } else if (line.startsWith("%T")) {
          // Finish current LHS if any
          if (currentLHS != null && !hasProductionsForCurrentLHS) {
            addProduction(currentLHS, "$"); // Epsilon production
          }
          parseTerminals(line);
          currentLHS = null;
          hasProductionsForCurrentLHS = false;
          lastLineWasEmpty = false;
        } else if (line.startsWith("%Syn")) {
          // Finish current LHS if any
          if (currentLHS != null && !hasProductionsForCurrentLHS) {
            addProduction(currentLHS, "$"); // Epsilon production
          }
          parseSyncTokens(line);
          currentLHS = null;
          hasProductionsForCurrentLHS = false;
          lastLineWasEmpty = false;
        } else if (line.startsWith("<")) {
          // Check if this is a non-terminal declaration (just <nt>)
          int ntEnd = line.indexOf(">");
          if (ntEnd != -1 && ntEnd == line.length() - 1) {
            // This is just <nonterminal>
            // Check if this is a new LHS or an alternative for current LHS
            // Rule: If line has leading space, it's an alternative. Otherwise, it's a new LHS.
            if (currentLHS != null && hasLeadingSpace) {
              // This is an alternative for current LHS (starts with non-terminal, has leading space)
              addProduction(currentLHS, line);
              hasProductionsForCurrentLHS = true;
            } else {
              // This is a new LHS (no leading space, or no currentLHS)
              // Finish previous LHS if any
              if (currentLHS != null && !hasProductionsForCurrentLHS) {
                addProduction(currentLHS, "$"); // Epsilon production
              }
              currentLHS = line;
              hasProductionsForCurrentLHS = false;
            }
            lastLineWasEmpty = false;
          } else {
            // This line contains <nt> followed by something
            // Split to get potential LHS and RHS
            String[] parts = line.split("\\s+", 2);
            if (parts.length >= 1 && parts[0].startsWith("<") && parts[0].endsWith(">")) {
              String potentialLHS = parts[0];
              String potentialRHS = parts.length > 1 ? parts[1] : "";
              
              // Check if this is a new LHS or an alternative for current LHS
              // Rule: If line has leading space, it's an alternative. Otherwise, it's a new LHS.
              if (currentLHS != null && hasLeadingSpace) {
                // This is an alternative for current LHS (could start with non-terminal, has leading space)
                addProduction(currentLHS, line); // Use entire line as RHS
                hasProductionsForCurrentLHS = true;
              } else {
                // New LHS (blank line before, or no currentLHS)
                // Finish previous LHS if any
                if (currentLHS != null && !hasProductionsForCurrentLHS) {
                  addProduction(currentLHS, "$"); // Epsilon production
                }
                currentLHS = potentialLHS;
                hasProductionsForCurrentLHS = false;
                if (!potentialRHS.isEmpty()) {
                  addProduction(currentLHS, potentialRHS);
                  hasProductionsForCurrentLHS = true;
                }
              }
            }
            lastLineWasEmpty = false;
          }
        } else if (currentLHS != null) {
          // Continuation of production alternatives (doesn't start with <)
          addProduction(currentLHS, line);
          hasProductionsForCurrentLHS = true;
          lastLineWasEmpty = false;
        }
      }
      
      // Finish last LHS if any
      if (currentLHS != null && !hasProductionsForCurrentLHS) {
        addProduction(currentLHS, "$"); // Epsilon production
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
  
  
  private void addProduction(String lhs, String rhs) {
    if (lhs.startsWith("<") && lhs.endsWith(">")) {
      productions.computeIfAbsent(lhs, k -> new ArrayList<>())
          .add(new Production(lhs, parseRHS(rhs)));
    }
  }
  
  private List<String> parseRHS(String rhs) {
    List<String> symbols = new ArrayList<>();
    rhs = rhs.trim();
    
    // Handle epsilon ($)
    if (rhs.equals("$")) {
      return symbols; // Empty list for epsilon
    }
    
    if (rhs.isEmpty()) {
      return symbols;
    }
    
    String[] parts = rhs.split("\\s+");
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

