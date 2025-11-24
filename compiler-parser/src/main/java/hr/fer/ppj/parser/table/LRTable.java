package hr.fer.ppj.parser.table;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * LR parsing table containing ACTION and GOTO entries.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRTable implements Serializable {
  
  private static final long serialVersionUID = 1L;
  
  private final Map<Integer, Map<String, String>> actionTable = new HashMap<>();
  private final Map<Integer, Map<String, Integer>> gotoTable = new HashMap<>();
  
  public void setAction(int state, String symbol, String action) {
    actionTable.computeIfAbsent(state, k -> new HashMap<>()).put(symbol, action);
  }
  
  public void setGoto(int state, String nonTerminal, int nextState) {
    gotoTable.computeIfAbsent(state, k -> new HashMap<>()).put(nonTerminal, nextState);
  }
  
  public String getAction(int state, String symbol) {
    Map<String, String> stateActions = actionTable.get(state);
    if (stateActions == null) {
      return null;
    }
    return stateActions.get(symbol);
  }
  
  public int getGoto(int state, String nonTerminal) {
    Map<String, Integer> stateGotos = gotoTable.get(state);
    if (stateGotos == null) {
      return -1;
    }
    Integer result = stateGotos.get(nonTerminal);
    return result != null ? result : -1;
  }
  
  /**
   * Returns all available actions for a given state.
   * Used for error reporting to show expected tokens.
   * 
   * @param state The state number
   * @return Map of symbol -> action, or empty map if state has no actions
   */
  public Map<String, String> getAvailableActions(int state) {
    Map<String, String> stateActions = actionTable.get(state);
    if (stateActions == null) {
      return new HashMap<>();
    }
    return new HashMap<>(stateActions);
  }
}

