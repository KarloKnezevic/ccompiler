package hr.fer.ppj.lexer.gen;

import hr.fer.ppj.lexer.dfa.DFA;
import hr.fer.ppj.lexer.dfa.NFAToDFAConverter;
import hr.fer.ppj.lexer.nfa.NFA;
import hr.fer.ppj.lexer.regex.RegexParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates a deterministic finite automaton (DFA) for each lexer state from a lexer specification.
 * 
 * <p>This class is the core of the lexer generator. It orchestrates the process of:
 * <ol>
 *   <li><strong>Parsing:</strong> Reads and parses the lexer specification file</li>
 *   <li><strong>Macro Expansion:</strong> Recursively expands macro definitions</li>
 *   <li><strong>NFA Construction:</strong> Builds epsilon-NFAs from regex patterns</li>
 *   <li><strong>DFA Conversion:</strong> Converts NFAs to DFAs using subset construction</li>
 * </ol>
 * 
 * <p><strong>Algorithm Overview:</strong>
 * <ul>
 *   <li>For each lexer state, collect all rules that apply to that state</li>
 *   <li>For each rule, expand macros and parse the pattern into an epsilon-NFA</li>
 *   <li>Combine all NFAs for a state into a single epsilon-NFA using epsilon transitions</li>
 *   <li>Convert the combined epsilon-NFA to a DFA using the subset construction algorithm</li>
 *   <li>Preserve rule priority (earlier rules win in case of ties)</li>
 * </ul>
 * 
 * <p><strong>Macro Expansion:</strong>
 * Macro references in patterns (e.g., {@code {znak}}) are recursively expanded.
 * Each macro value is wrapped in parentheses to preserve operator precedence.
 * The expansion continues until no more macro references remain.
 * 
 * <p><strong>NFA Construction:</strong>
 * Each regex pattern is converted to an epsilon-NFA using Thompson's construction algorithm.
 * The NFAs are then combined by adding epsilon transitions from a common start state
 * to each pattern's start state.
 * 
 * <p><strong>DFA Conversion:</strong>
 * The epsilon-NFA is converted to a DFA using the subset construction algorithm:
 * <ul>
 *   <li>Compute epsilon closures for each NFA state set</li>
 *   <li>Create DFA states from NFA state sets</li>
 *   <li>Handle accepting states and preserve rule priority</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @version 1.0
 */
public final class LexerGenerator {
  
  private final Map<String, String> macroExpansions = new HashMap<>();
  private final Map<String, DFA> stateDFAs = new HashMap<>();
  
  public LexerGeneratorResult generate(Reader specReader) throws Exception {
    LexerSpecParser parser = new LexerSpecParser();
    parser.parse(specReader);
    
    // Expand macros recursively
    expandMacros(parser.getMacros());
    
    // Build DFA for each state
    for (String state : parser.getStates()) {
      DFA dfa = buildDFAForState(state, parser.getRules());
      stateDFAs.put(state, dfa);
    }
    
    return new LexerGeneratorResult(
        parser.getStates(),
        parser.getTokens(),
        stateDFAs,
        parser.getRules());
  }
  
  private void expandMacros(Map<String, String> macros) {
    macroExpansions.putAll(macros);
    
    // Recursively expand macros until no more changes
    // When expanding macros within other macros, wrap in parentheses to preserve precedence
    boolean changed = true;
    int iterations = 0;
    while (changed && iterations < 100) { // Safety limit
      changed = false;
      iterations++;
      
      for (Map.Entry<String, String> entry : macroExpansions.entrySet()) {
        String oldValue = entry.getValue();
        String newValue = oldValue;
        
        // Replace all macro references - wrap in parentheses to preserve precedence
        for (Map.Entry<String, String> macro : macros.entrySet()) {
          String macroRef = "{" + macro.getKey() + "}";
          if (newValue.contains(macroRef)) {
            // Wrap macro value in parentheses when expanding within another macro
            // This preserves operator precedence
            String macroValue = "(" + macro.getValue() + ")";
            // Replace all occurrences
            while (newValue.contains(macroRef)) {
              newValue = newValue.replace(macroRef, macroValue);
            }
            changed = true;
          }
        }
        
        if (!newValue.equals(oldValue)) {
          entry.setValue(newValue);
        }
      }
    }
  }
  
  private DFA buildDFAForState(String state, List<LexerSpecParser.LexerRule> allRules) {
    // Filter rules for this state
    List<LexerSpecParser.LexerRule> stateRules = new ArrayList<>();
    for (LexerSpecParser.LexerRule rule : allRules) {
      if (rule.state().equals(state)) {
        stateRules.add(rule);
      }
    }
    
    // Build combined NFA for all rules in this state
    // Each pattern should be a separate path from start to accepting state
    NFA combinedNFA = new NFA();
    Map<Integer, String> stateTokens = new HashMap<>();
    Map<Integer, List<String>> stateActions = new HashMap<>();
    Map<Integer, Integer> stateRuleOrder = new HashMap<>(); // Track rule order for priority
    int startState = 0;
    combinedNFA.setStartState(startState);
    
    // Use a shared state counter across all patterns
    // Start from 1 to avoid conflict with start state 0
    final int[] nextStateId = {1};
    
    // Track rule order for priority (earlier rules win)
    final int[] ruleIndex = {0};
    
    for (LexerSpecParser.LexerRule rule : stateRules) {
      int currentRuleIndex = ruleIndex[0]++;
      String pattern = expandPattern(rule.pattern());
      try {
        // Create a temporary NFA for this pattern
        NFA patternNFA = new NFA();
        RegexParser regexParser = new RegexParser(patternNFA, () -> nextStateId[0]++);
        RegexParser.StatePair pair = regexParser.parse(pattern);
        
        patternNFA.setStartState(pair.start());
        patternNFA.addAcceptingState(pair.end());
        
        // Merge this pattern NFA into combined NFA
        // IMPORTANT: Copy epsilon transitions FIRST, then regular transitions
        // This ensures epsilon closure works correctly
        
        // Copy epsilon transitions from pattern NFA to combined NFA
        var patternEpsilons = patternNFA.getEpsilonTransitions();
        for (var entry : patternEpsilons.entrySet()) {
          int from = entry.getKey();
          for (int to : entry.getValue()) {
            combinedNFA.addEpsilonTransition(from, to);
          }
        }
        
        // Add epsilon transition from start to pattern start (AFTER copying pattern epsilons)
        combinedNFA.addEpsilonTransition(startState, pair.start());
        
        // Copy all regular transitions from pattern NFA to combined NFA
        // Note: States are already unique because we use shared state counter
        var patternTransitions = patternNFA.getTransitions();
        for (var entry : patternTransitions.entrySet()) {
          int from = entry.getKey();
          for (var charEntry : entry.getValue().entrySet()) {
            char symbol = charEntry.getKey();
            for (int to : charEntry.getValue()) {
              combinedNFA.addTransition(from, to, symbol);
            }
          }
        }
        
        // Mark accepting state
        combinedNFA.addAcceptingState(pair.end());
        // Store rule order for priority (earlier rules have lower index)
        stateRuleOrder.put(pair.end(), currentRuleIndex);
        if (rule.token() != null) {
          stateTokens.put(pair.end(), rule.token());
        }
        if (!rule.actions().isEmpty()) {
          stateActions.put(pair.end(), rule.actions());
        }
      } catch (Exception e) {
        // Log error but continue with other rules
        System.err.println("Error parsing pattern '" + pattern + "': " + e.getMessage());
        e.printStackTrace(System.err);
      }
    }
    
    // Convert to DFA
    NFAToDFAConverter converter = new NFAToDFAConverter();
    DFA dfa = converter.convert(combinedNFA, stateTokens, stateActions, stateRuleOrder);
    return dfa;
  }
  
  private String expandPattern(String pattern) {
    String result = pattern;
    
    // Expand macro references
    // Algoritam D: Za svaku referencu {ImeDefinicije} zamijeni je njezinim 
    // regularnim izrazom u zagradama (očuvanje precedencije)
    // Keep expanding until no more macro references remain
    boolean changed = true;
    int iterations = 0;
    while (changed && iterations < 100) {
      changed = false;
      iterations++;
      
      for (Map.Entry<String, String> macro : macroExpansions.entrySet()) {
        String macroRef = "{" + macro.getKey() + "}";
        if (result.contains(macroRef)) {
          // Wrap ALL macro values in parentheses to preserve precedence
          // This ensures correct operator precedence when macros are expanded
          String macroValue = macro.getValue();
          // Always wrap in parentheses as per Algorithm D
          macroValue = "(" + macroValue + ")";
          // Replace all occurrences of this macro reference
          String newResult = result.replace(macroRef, macroValue);
          if (!newResult.equals(result)) {
            result = newResult;
            changed = true;
          }
        }
      }
    }
    
    return result;
  }
}
