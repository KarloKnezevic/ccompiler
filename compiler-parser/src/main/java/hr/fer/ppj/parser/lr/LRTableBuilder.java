package hr.fer.ppj.parser.lr;

import hr.fer.ppj.parser.grammar.FirstSetComputer;
import hr.fer.ppj.parser.grammar.Grammar;
import hr.fer.ppj.parser.grammar.GrammarParser.Production;
import hr.fer.ppj.parser.table.LRTable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Builds LR(1) parsing tables using canonical LR(1) construction.
 * 
 * <p>Algorithm:
 * <ol>
 *   <li>Build canonical collection of LR(1) item sets</li>
 *   <li>Generate ACTION table (SHIFT, REDUCE, ACCEPT)</li>
 *   <li>Generate GOTO table</li>
 *   <li>Resolve conflicts (SHIFT/REDUCE, REDUCE/REDUCE)</li>
 * </ol>
 * 
 * <p>Conflict resolution:
 * <ul>
 *   <li>SHIFT/REDUCE: Always choose SHIFT</li>
 *   <li>REDUCE/REDUCE: Choose production that appears earlier in grammar definition</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRTableBuilder {
  
  private static final Logger LOG = Logger.getLogger(LRTableBuilder.class.getName());
  private static final String ACCEPT_ACTION = "acc";
  private static final String END_MARKER = "#";
  
  private final Grammar grammar;
  private final FirstSetComputer firstComputer;
  private final LRClosure closure;
  private final LRGoto gotoOp;
  
  private final List<LRItemSet> itemSets = new ArrayList<>();
  private final Map<LRItemSet, Integer> itemSetToState = new HashMap<>();
  private final Map<Integer, LRItemSet> stateToItemSet = new HashMap<>();
  
  public LRTableBuilder(Grammar grammar, FirstSetComputer firstComputer) {
    this.grammar = grammar;
    this.firstComputer = firstComputer;
    this.closure = new LRClosure(grammar, firstComputer);
    this.gotoOp = new LRGoto(closure);
  }
  
  /**
   * Builds the complete LR(1) parsing table.
   * 
   * @return The LR parsing table
   */
  public LRTable build() {
    // Step 1: Build canonical collection of LR(1) item sets
    buildCanonicalCollection();
    
    // Step 2: Build ACTION and GOTO tables
    LRTable table = new LRTable();
    buildActionTable(table);
    buildGotoTable(table);
    
    return table;
  }
  
  /**
   * Builds the canonical collection of LR(1) item sets.
   */
  private void buildCanonicalCollection() {
    // Create initial item set: CLOSURE({ [S' → ·S, {#}] })
    Production augmentedStart = grammar.getAllProductions().get(0);
    LRItem initialItem = new LRItem(augmentedStart, 0, Set.of(END_MARKER));
    LRItemSet initialSet = new LRItemSet();
    initialSet.addItem(initialItem);
    initialSet = closure.closure(initialSet);
    
    // Add initial set
    int state0 = addItemSet(initialSet);
    LOG.info("Created initial state 0 with " + initialSet.getItems().size() + " items");
    
    // Process all item sets
    List<Integer> toProcess = new ArrayList<>();
    toProcess.add(state0);
    
    final int MAX_STATES = 50000; // Safety limit for number of states (expected ~39000)
    int processedCount = 0;
    
    while (!toProcess.isEmpty()) {
      if (itemSets.size() > MAX_STATES) {
        throw new IllegalStateException("Exceeded maximum number of states (" + MAX_STATES + ")");
      }
      
      int currentState = toProcess.remove(0);
      LRItemSet currentSet = stateToItemSet.get(currentState);
      processedCount++;
      
      if (processedCount % 100 == 0) {
        LOG.info("Processed " + processedCount + " states, " + toProcess.size() + " remaining, total: " + itemSets.size());
      }
      
      if (itemSets.size() <= 10) {
        LOG.info("Processing state " + currentState + " with " + currentSet.getItems().size() + " items");
        for (LRItem item : currentSet.getItems()) {
          if (item.isReduceItem()) {
            LOG.fine("  Reduce: " + item.getProduction().lhs() + " -> " + item.getProduction().rhs());
          } else {
            LOG.fine("  Shift: " + item.getProduction().lhs() + " -> ... " + item.getNextSymbol() + " ...");
          }
        }
      }
      
      // Find all symbols that can follow the dot
      Set<String> symbols = new HashSet<>();
      for (LRItem item : currentSet.getItems()) {
        if (!item.isReduceItem()) {
          symbols.add(item.getNextSymbol());
        }
      }
      
      if (itemSets.size() <= 10 && !symbols.isEmpty()) {
        LOG.info("  Symbols to process: " + symbols);
      }
      
      // Track statistics
      int newStatesFromThisState = 0;
      int existingStatesFromThisState = 0;
      
      // Compute GOTO for each symbol
      for (String symbol : symbols) {
        LRItemSet nextSet = gotoOp.gotoSet(currentSet, symbol);
        if (nextSet != null && !nextSet.getItems().isEmpty()) {
          Integer existingState = findExistingState(nextSet);
          if (existingState != null) {
            // State already exists - this is normal in LR(1)
            existingStatesFromThisState++;
            if (itemSets.size() <= 10) {
              LOG.fine("GOTO(" + currentState + ", " + symbol + ") -> existing state " + existingState);
            }
          } else {
            // New state
            int newState = addItemSet(nextSet);
            toProcess.add(newState);
            newStatesFromThisState++;
            if (itemSets.size() <= 20) {
              LOG.info("GOTO(" + currentState + ", " + symbol + ") -> new state " + newState + " (" + nextSet.getItems().size() + " items)");
            }
          }
        } else {
          if (itemSets.size() <= 10) {
            LOG.fine("GOTO(" + currentState + ", " + symbol + ") -> null or empty");
          }
        }
      }
      
      // Log statistics periodically
      if (processedCount % 100 == 0 && processedCount > 0) {
        LOG.info("State " + currentState + ": " + newStatesFromThisState + " new, " + existingStatesFromThisState + " existing");
      }
    }
    
    LOG.info("Built canonical collection with " + itemSets.size() + " states (processed " + processedCount + ")");
  }
  
  /**
   * Adds an item set and returns its state number.
   */
  private int addItemSet(LRItemSet itemSet) {
    int state = itemSets.size();
    itemSets.add(itemSet);
    itemSetToState.put(itemSet, state);
    stateToItemSet.put(state, itemSet);
    return state;
  }
  
  /**
   * Finds if an equivalent item set already exists.
   * In canonical LR(1), item sets are compared exactly (including lookaheads).
   * 
   * <p>Uses HashMap lookup which should be O(1) if hashCode is correct.
   */
  private Integer findExistingState(LRItemSet itemSet) {
    // Use HashMap lookup - should be O(1) if hashCode is correct
    return itemSetToState.get(itemSet);
  }
  
  /**
   * Builds the ACTION table.
   */
  private void buildActionTable(LRTable table) {
    for (int state = 0; state < itemSets.size(); state++) {
      LRItemSet itemSet = itemSets.get(state);
      
      // Map from terminal to list of actions (for conflict detection)
      Map<String, List<Action>> terminalActions = new HashMap<>();
      
      for (LRItem item : itemSet.getItems()) {
        if (item.isReduceItem()) {
          // REDUCE action
          Production prod = item.getProduction();
          
          // Check for ACCEPT
          if (prod.lhs().equals(grammar.getAugmentedStartSymbol())) {
            // S' → S·, lookahead is #
            for (String lookahead : item.getLookahead()) {
              if (lookahead.equals(END_MARKER)) {
                addAction(terminalActions, lookahead, Action.accept());
              }
            }
          } else {
            // Regular REDUCE
            int prodIndex = grammar.getProductionIndex(prod);
            for (String lookahead : item.getLookahead()) {
              addAction(terminalActions, lookahead, Action.reduce(prodIndex, prod));
            }
          }
        } else {
          // SHIFT action
          String nextSymbol = item.getNextSymbol();
          if (grammar.isTerminal(nextSymbol)) {
            LRItemSet nextSet = gotoOp.gotoSet(itemSet, nextSymbol);
            if (nextSet != null && !nextSet.getItems().isEmpty()) {
              Integer nextState = findExistingState(nextSet);
              if (nextState != null) {
                addAction(terminalActions, nextSymbol, Action.shift(nextState));
              } else {
                // This should not happen - all states should be in the collection
                LOG.warning("State not found for GOTO(" + state + ", " + nextSymbol + ")");
              }
            }
          }
        }
      }
      
      // Resolve conflicts and set actions
      for (Map.Entry<String, List<Action>> entry : terminalActions.entrySet()) {
        String terminal = entry.getKey();
        List<Action> actions = entry.getValue();
        
        Action resolved = resolveConflicts(actions, terminal, state);
        if (resolved != null) {
          table.setAction(state, terminal, resolved.toString());
        }
      }
    }
  }
  
  /**
   * Adds an action to the terminal actions map.
   */
  private void addAction(Map<String, List<Action>> terminalActions, String terminal, Action action) {
    terminalActions.computeIfAbsent(terminal, k -> new ArrayList<>()).add(action);
  }
  
  /**
   * Resolves conflicts between multiple actions for the same terminal.
   */
  private Action resolveConflicts(List<Action> actions, String terminal, int state) {
    if (actions.isEmpty()) {
      return null;
    }
    
    if (actions.size() == 1) {
      return actions.get(0);
    }
    
    // Multiple actions - resolve conflicts
    Action shiftAction = null;
    List<Action> reduceActions = new ArrayList<>();
    Action acceptAction = null;
    
    for (Action action : actions) {
      switch (action.type()) {
        case SHIFT:
          shiftAction = action;
          break;
        case REDUCE:
          reduceActions.add(action);
          break;
        case ACCEPT:
          acceptAction = action;
          break;
      }
    }
    
    // SHIFT/REDUCE conflict: choose SHIFT
    if (shiftAction != null && !reduceActions.isEmpty()) {
      LOG.warning(String.format(
          "SHIFT/REDUCE conflict in state %d for terminal %s: choosing SHIFT",
          state, terminal));
      return shiftAction;
    }
    
    // REDUCE/REDUCE conflict: choose earlier production
    if (reduceActions.size() > 1) {
      Action chosen = reduceActions.get(0);
      for (Action action : reduceActions) {
        if (action.productionIndex() < chosen.productionIndex()) {
          chosen = action;
        }
      }
      LOG.warning(String.format(
          "REDUCE/REDUCE conflict in state %d for terminal %s: choosing production %d",
          state, terminal, chosen.productionIndex()));
      return chosen;
    }
    
    // Multiple ACCEPT (shouldn't happen)
    if (acceptAction != null) {
      return acceptAction;
    }
    
    // Fallback: return first action
    return actions.get(0);
  }
  
  /**
   * Builds the GOTO table.
   */
  private void buildGotoTable(LRTable table) {
    for (int state = 0; state < itemSets.size(); state++) {
      LRItemSet itemSet = itemSets.get(state);
      
      for (String nonTerminal : grammar.getNonTerminals()) {
        LRItemSet nextSet = gotoOp.gotoSet(itemSet, nonTerminal);
        if (nextSet != null && !nextSet.getItems().isEmpty()) {
          Integer nextState = itemSetToState.get(nextSet);
          if (nextState != null) {
            table.setGoto(state, nonTerminal, nextState);
          }
        }
      }
    }
  }
  
  /**
   * Represents an ACTION table entry.
   */
  private record Action(ActionType type, Integer shiftState, Integer productionIndex, Production production) {
    static Action shift(int state) {
      return new Action(ActionType.SHIFT, state, null, null);
    }
    
    static Action reduce(int prodIndex, Production prod) {
      return new Action(ActionType.REDUCE, null, prodIndex, prod);
    }
    
    static Action accept() {
      return new Action(ActionType.ACCEPT, null, null, null);
    }
    
    @Override
    public String toString() {
      return switch (type) {
        case SHIFT -> "s" + shiftState;
        case REDUCE -> "r" + productionIndex;
        case ACCEPT -> ACCEPT_ACTION;
      };
    }
  }
  
  private enum ActionType {
    SHIFT, REDUCE, ACCEPT
  }
  
  /**
   * Gets the number of states in the automaton.
   */
  public int getStateCount() {
    return itemSets.size();
  }
  
  /**
   * Gets the item set for a state (for debugging).
   */
  public LRItemSet getItemSet(int state) {
    return stateToItemSet.get(state);
  }
}

