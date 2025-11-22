package hr.fer.ppj.lexer.nfa;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ε-NFA (Nondeterministic Finite Automaton with epsilon transitions).
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class NFA {
  
  private final Map<Integer, Map<Character, Set<Integer>>> transitions = new HashMap<>();
  private final Map<Integer, Set<Integer>> epsilonTransitions = new HashMap<>();
  private final Set<Integer> acceptingStates = new HashSet<>();
  private int startState = -1;
  
  public void addTransition(int from, int to, char symbol) {
    transitions.computeIfAbsent(from, k -> new HashMap<>())
        .computeIfAbsent(symbol, k -> new HashSet<>())
        .add(to);
  }
  
  public void addEpsilonTransition(int from, int to) {
    epsilonTransitions.computeIfAbsent(from, k -> new HashSet<>()).add(to);
  }
  
  public void setStartState(int state) {
    this.startState = state;
  }
  
  public void addAcceptingState(int state) {
    acceptingStates.add(state);
  }
  
  public Set<Integer> getEpsilonClosure(Set<Integer> states) {
    Set<Integer> closure = new HashSet<>(states);
    boolean changed = true;
    while (changed) {
      changed = false;
      Set<Integer> newStates = new HashSet<>();
      for (int state : closure) {
        Set<Integer> eps = epsilonTransitions.getOrDefault(state, Set.of());
        for (int next : eps) {
          if (!closure.contains(next)) {
            newStates.add(next);
            changed = true;
          }
        }
      }
      closure.addAll(newStates);
    }
    return closure;
  }
  
  public Set<Integer> move(Set<Integer> states, char symbol) {
    Set<Integer> result = new HashSet<>();
    for (int state : states) {
      Map<Character, Set<Integer>> stateTrans = transitions.getOrDefault(state, Map.of());
      Set<Integer> nextStates = stateTrans.getOrDefault(symbol, Set.of());
      result.addAll(nextStates);
    }
    return getEpsilonClosure(result);
  }
  
  public int getStartState() {
    return startState;
  }
  
  public Set<Integer> getAcceptingStates() {
    return Set.copyOf(acceptingStates);
  }
  
  public Map<Integer, Map<Character, Set<Integer>>> getTransitions() {
    return Map.copyOf(transitions);
  }
  
  public Map<Integer, Set<Integer>> getEpsilonTransitions() {
    return Map.copyOf(epsilonTransitions);
  }
}

