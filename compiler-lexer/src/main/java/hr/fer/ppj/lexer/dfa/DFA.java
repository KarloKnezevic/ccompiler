package hr.fer.ppj.lexer.dfa;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * DFA (Deterministic Finite Automaton).
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class DFA {
  
  private final Map<Integer, Map<Character, Integer>> transitions = new HashMap<>();
  private final Set<Integer> acceptingStates = new HashSet<>();
  private final Map<Integer, String> acceptingStateTokens = new HashMap<>();
  private final Map<Integer, List<String>> acceptingStateActions = new HashMap<>();
  private int startState = -1;
  
  public void addTransition(int from, char symbol, int to) {
    transitions.computeIfAbsent(from, k -> new HashMap<>()).put(symbol, to);
  }
  
  public void setStartState(int state) {
    this.startState = state;
  }
  
  public void addAcceptingState(int state, String token) {
    acceptingStates.add(state);
    acceptingStateTokens.put(state, token);
  }
  
  public void addAcceptingState(int state, String token, List<String> actions) {
    acceptingStates.add(state);
    acceptingStateTokens.put(state, token);
    if (actions != null && !actions.isEmpty()) {
      acceptingStateActions.put(state, new ArrayList<>(actions));
    }
  }
  
  public List<String> getActions(int state) {
    return acceptingStateActions.getOrDefault(state, List.of());
  }
  
  public Integer getTransition(int state, char symbol) {
    Map<Character, Integer> stateTrans = transitions.get(state);
    if (stateTrans == null) {
      return null;
    }
    return stateTrans.get(symbol);
  }
  
  public boolean isAccepting(int state) {
    return acceptingStates.contains(state);
  }
  
  public String getToken(int state) {
    return acceptingStateTokens.get(state);
  }
  
  public int getStartState() {
    return startState;
  }
  
  public Set<Integer> getAcceptingStates() {
    return Set.copyOf(acceptingStates);
  }
  
  // Debug method
  public Map<Integer, Map<Character, Integer>> getTransitions() {
    return Map.copyOf(transitions);
  }
}

