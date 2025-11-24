package hr.fer.ppj.parser.lr;

import hr.fer.ppj.parser.grammar.Grammar;
import hr.fer.ppj.parser.grammar.GrammarParser.Production;
import hr.fer.ppj.parser.io.TokenReader.Token;
import hr.fer.ppj.parser.table.LRTable;
import hr.fer.ppj.parser.tree.ParseTree;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Logger;

/**
 * LR(1) parser runtime.
 * 
 * <p>Uses ACTION/GOTO tables to parse tokens into a parse tree.
 * 
 * <p>Algorithm:
 * <ol>
 *   <li>Initialize with start state</li>
 *   <li>For each token, look up ACTION</li>
 *   <li>SHIFT: push state and create leaf node</li>
 *   <li>REDUCE: pop RHS symbols, create parent node, perform GOTO</li>
 *   <li>ACCEPT: return root of parse tree</li>
 * </ol>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRParser {
  
  private static final Logger LOG = Logger.getLogger(LRParser.class.getName());
  private static final String END_MARKER = "#";
  
  private final LRTable table;
  private final Grammar grammar;
  
  public LRParser(LRTable table, Grammar grammar) {
    this.table = table;
    this.grammar = grammar;
  }
  
  /**
   * Parses a list of tokens into a parse tree.
   * 
   * @param tokens The input tokens
   * @return The root of the parse tree
   * @throws ParseException If parsing fails
   */
  public ParseTree parse(List<Token> tokens) throws ParseException {
    Stack<Integer> stateStack = new Stack<>();
    Stack<ParseTree> treeStack = new Stack<>();
    
    stateStack.push(0); // Start state
    
    int tokenIndex = 0;
    
    // Add end marker
    List<Token> tokensWithEnd = new ArrayList<>(tokens);
    tokensWithEnd.add(new Token(END_MARKER, tokens.isEmpty() ? 1 : tokens.get(tokens.size() - 1).line(), ""));
    
    while (tokenIndex < tokensWithEnd.size()) {
      Token token = tokensWithEnd.get(tokenIndex);
      int currentState = stateStack.peek();
      
      String action = table.getAction(currentState, token.type());
      
      if (action == null) {
        // Error - log details for debugging
        LOG.warning(String.format(
            "Parse error at line %d: no action for token %s in state %d. Stack size: %d",
            token.line(), token.type(), currentState, stateStack.size()));
        // Try to find what actions are available in this state
        if (stateStack.size() < 5) {
          LOG.info("Available actions in state " + currentState + " (sample):");
          // This would require access to table internals, skip for now
        }
        // Error - try recovery
        handleError(token, currentState, stateStack, treeStack);
        // After error recovery, continue with next token
        tokenIndex++;
        continue;
      }
      
      if (action.equals("acc")) {
        // Accept
        if (treeStack.size() != 1) {
          throw new ParseException("Parse error: expected single root node on accept");
        }
        return treeStack.pop();
      } else if (action.startsWith("s")) {
        // Shift
        int nextState = Integer.parseInt(action.substring(1));
        stateStack.push(nextState);
        
        // Create leaf node for terminal
        ParseTree leaf = new ParseTree(token.type(), token.line(), token.lexicalUnit());
        treeStack.push(leaf);
        
        tokenIndex++;
      } else if (action.startsWith("r")) {
        // Reduce
        int productionIndex = Integer.parseInt(action.substring(1));
        Production prod = grammar.getAllProductions().get(productionIndex);
        
        // Pop RHS symbols (in reverse order)
        // Handle epsilon productions (empty RHS)
        List<ParseTree> children = new ArrayList<>();
        if (!prod.rhs().isEmpty() && !prod.rhs().get(0).equals("$")) {
          for (int i = 0; i < prod.rhs().size(); i++) {
            stateStack.pop();
            children.add(0, treeStack.pop()); // Insert at beginning to reverse order
          }
        }
        // For epsilon productions, no nodes are popped
        
        // Create parent node
        ParseTree parent = new ParseTree(prod.lhs());
        parent.addChildren(children);
        treeStack.push(parent);
        
        // GOTO
        int gotoState = table.getGoto(stateStack.peek(), prod.lhs());
        if (gotoState < 0) {
          throw new ParseException("Parse error: invalid GOTO for " + prod.lhs());
        }
        stateStack.push(gotoState);
        
        // Don't advance token index - reduce doesn't consume input
      } else {
        throw new ParseException("Parse error: unknown action " + action);
      }
    }
    
    throw new ParseException("Parse error: end of input reached without accept");
  }
  
  /**
   * Handles parse errors using panic mode recovery with synchronization tokens.
   * Generates detailed error message in Croatian.
   */
  private void handleError(Token token, int currentState, Stack<Integer> stateStack,
                           Stack<ParseTree> treeStack) throws ParseException {
    LOG.warning(String.format("Parse error at line %d, token %s", token.line(), token.type()));
    
    // Get available actions for current state to determine expected tokens
    Map<String, String> availableActions = table.getAvailableActions(currentState);
    List<String> expectedTokens = new ArrayList<>();
    
    // Collect expected terminals (those that have actions in current state)
    for (Map.Entry<String, String> entry : availableActions.entrySet()) {
      String symbol = entry.getKey();
      String action = entry.getValue();
      
      // Only include terminals that have valid actions (SHIFT, REDUCE, ACCEPT)
      // Exclude non-terminals (which are in GOTO table, not ACTION table)
      if (action != null && !action.isEmpty() && grammar.isTerminal(symbol)) {
        expectedTokens.add(symbol);
      }
    }
    
    // Build error message in Croatian
    StringBuilder errorMsg = new StringBuilder();
    errorMsg.append("Sintaksna greška na retku ");
    errorMsg.append(token.line());
    errorMsg.append(".\n");
    
    errorMsg.append("Pročitan uniformni znak: ");
    errorMsg.append(token.type());
    if (token.lexicalUnit() != null && !token.lexicalUnit().isEmpty()) {
      errorMsg.append(" (");
      errorMsg.append(token.lexicalUnit());
      errorMsg.append(")");
    }
    errorMsg.append(".\n");
    
    if (!expectedTokens.isEmpty()) {
      errorMsg.append("Očekivani uniformni znakovi: ");
      // Sort for consistent output
      expectedTokens.sort(String::compareTo);
      for (int i = 0; i < expectedTokens.size(); i++) {
        if (i > 0) {
          errorMsg.append(", ");
        }
        errorMsg.append(expectedTokens.get(i));
      }
      errorMsg.append(".\n");
    } else {
      errorMsg.append("Nema dostupnih očekivanih uniformnih znakova u trenutnom stanju.\n");
    }
    
    // Try to find a synchronization token
    List<String> syncTokens = grammar.getSyncTokens();
    
    // Skip tokens until we find a sync token or end of input
    // For now, just throw an exception with detailed message
    // TODO: Implement proper error recovery
    throw new ParseException(errorMsg.toString());
  }
  
  /**
   * Exception thrown when parsing fails.
   */
  public static final class ParseException extends Exception {
    public ParseException(String message) {
      super(message);
    }
  }
}

