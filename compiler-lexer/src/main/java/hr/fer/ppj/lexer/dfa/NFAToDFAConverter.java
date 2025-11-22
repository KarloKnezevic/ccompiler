package hr.fer.ppj.lexer.dfa;

import hr.fer.ppj.lexer.nfa.NFA;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Converts ε-NFA to DFA using subset construction.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class NFAToDFAConverter {
  
  private final Map<Set<Integer>, Integer> dfaStateMap = new HashMap<>();
  private final List<Set<Integer>> dfaStates = new ArrayList<>();
  private int dfaStateCounter = 0;
  
  public DFA convert(NFA nfa, Map<Integer, String> nfaStateTokens) {
    return convert(nfa, nfaStateTokens, Map.of(), Map.of());
  }
  
  public DFA convert(NFA nfa, Map<Integer, String> nfaStateTokens, 
                     Map<Integer, List<String>> nfaStateActions) {
    return convert(nfa, nfaStateTokens, nfaStateActions, Map.of());
  }
  
  public DFA convert(NFA nfa, Map<Integer, String> nfaStateTokens, 
                     Map<Integer, List<String>> nfaStateActions,
                     Map<Integer, Integer> nfaStateRuleOrder) {
    DFA dfa = new DFA();
    
    // Start with epsilon closure of NFA start state
    Set<Integer> startSet = nfa.getEpsilonClosure(Set.of(nfa.getStartState()));
    int dfaStart = getOrCreateDFAState(startSet);
    dfa.setStartState(dfaStart);
    
    // Process all DFA states
    List<Integer> toProcess = new ArrayList<>();
    toProcess.add(dfaStart);
    
    while (!toProcess.isEmpty()) {
      int dfaState = toProcess.remove(0);
      Set<Integer> nfaStates = dfaStates.get(dfaState);
      
      // Check if this DFA state is accepting
      // Process in order to preserve rule priority (earlier rules win)
      // Use rule order if available, otherwise fall back to state number
      List<Integer> acceptingStates = new ArrayList<>();
      for (int nfaState : nfaStates) {
        if (nfa.getAcceptingStates().contains(nfaState)) {
          // Include all accepting states, even if token is null (for whitespace/comments)
          acceptingStates.add(nfaState);
        }
      }
      
      if (!acceptingStates.isEmpty()) {
        // Sort by rule order (earlier rules have lower order, so they win)
        if (!nfaStateRuleOrder.isEmpty()) {
          acceptingStates.sort((a, b) -> {
            int orderA = nfaStateRuleOrder.getOrDefault(a, Integer.MAX_VALUE);
            int orderB = nfaStateRuleOrder.getOrDefault(b, Integer.MAX_VALUE);
            if (orderA != orderB) {
              return Integer.compare(orderA, orderB);
            }
            // If same order, use state number for deterministic ordering
            return Integer.compare(a, b);
          });
        } else {
          // Fallback: sort by state number
          acceptingStates.sort(Integer::compareTo);
        }
        
        // Use the first (highest priority) accepting state
        int bestState = acceptingStates.get(0);
        String token = nfaStateTokens.get(bestState); // May be null for whitespace/comments
        List<String> actions = nfaStateActions.getOrDefault(bestState, List.of());
        dfa.addAcceptingState(dfaState, token, actions);
      }
      
      // Find all possible transitions
      Set<Character> alphabet = collectAlphabet(nfa, nfaStates);
      
      int transitionsAdded = 0;
      for (char symbol : alphabet) {
        Set<Integer> nextNFAStates = nfa.move(nfaStates, symbol);
        if (!nextNFAStates.isEmpty()) {
          // Check if this state already exists before creating
          Set<Integer> keySet = new HashSet<>(nextNFAStates);
          boolean isNewState = !dfaStateMap.containsKey(keySet);
          int nextDFAState = getOrCreateDFAState(nextNFAStates);
          dfa.addTransition(dfaState, symbol, nextDFAState);
          transitionsAdded++;
          
          // Only add to process queue if it's a new state
          if (isNewState) {
            toProcess.add(nextDFAState);
          }
        }
      }
      
      // Debug code removed for performance
    }
    
    return dfa;
  }
  
  private int getOrCreateDFAState(Set<Integer> nfaStates) {
    // Create a new HashSet for key lookup (since Map uses HashSet as key)
    Set<Integer> keySet = new HashSet<>(nfaStates);
    if (dfaStateMap.containsKey(keySet)) {
      return dfaStateMap.get(keySet);
    }
    
    int dfaState = dfaStateCounter++;
    dfaStateMap.put(keySet, dfaState);
    dfaStates.add(new HashSet<>(nfaStates));
    return dfaState;
  }
  
  private Set<Character> collectAlphabet(NFA nfa, Set<Integer> states) {
    Set<Character> alphabet = new HashSet<>();
    for (int state : states) {
      Map<Character, Set<Integer>> trans = nfa.getTransitions().getOrDefault(state, Map.of());
      alphabet.addAll(trans.keySet());
    }
    return alphabet;
  }
}

