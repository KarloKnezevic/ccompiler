package hr.fer.ppj.parser.lr;

import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.ast.ASTNode;
import hr.fer.ppj.parser.grammar.GrammarParser;
import hr.fer.ppj.parser.grammar.GrammarParser.Production;
import hr.fer.ppj.parser.table.LRTable;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * LR(1) parser runtime.
 * 
 * <p>Uses ACTION/GOTO tables to parse tokens into an AST.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRParser {
  
  private final LRTable table;
  @SuppressWarnings("unused")
  private final GrammarParser grammar;
  
  public LRParser(LRTable table, GrammarParser grammar) {
    this.table = table;
    this.grammar = grammar;
  }
  
  public ASTNode parse(List<Token> tokens) {
    Stack<Integer> stateStack = new Stack<>();
    Stack<ASTNode> symbolStack = new Stack<>();
    
    stateStack.push(0); // Start state
    
    int tokenIndex = 0;
    
    while (tokenIndex < tokens.size()) {
      Token token = tokens.get(tokenIndex);
      int currentState = stateStack.peek();
      
      String action = table.getAction(currentState, token.type());
      
      if (action == null) {
        // Error - try recovery
        handleError(token, stateStack, symbolStack);
        continue;
      }
      
      if (action.startsWith("s")) {
        // Shift
        int nextState = Integer.parseInt(action.substring(1));
        stateStack.push(nextState);
        symbolStack.push(createLeafNode(token));
        tokenIndex++;
      } else if (action.startsWith("r")) {
        // Reduce
        int productionIndex = Integer.parseInt(action.substring(1));
        Production prod = getProduction(productionIndex);
        
        // Pop RHS symbols
        List<ASTNode> children = new ArrayList<>();
        for (int i = 0; i < prod.rhs().size(); i++) {
          stateStack.pop();
          children.add(0, symbolStack.pop()); // Reverse order
        }
        
        // Create parent node
        ASTNode parent = createNode(prod.lhs(), children);
        symbolStack.push(parent);
        
        // GOTO
        int gotoState = table.getGoto(stateStack.peek(), prod.lhs());
        stateStack.push(gotoState);
      } else if (action.equals("acc")) {
        // Accept
        return symbolStack.pop();
      }
    }
    
    return null; // Error
  }
  
  private ASTNode createLeafNode(Token token) {
    // TODO: Create appropriate AST node based on token type
    return null;
  }
  
  private ASTNode createNode(String nonTerminal, List<ASTNode> children) {
    // TODO: Create appropriate AST node based on non-terminal
    return null;
  }
  
  private Production getProduction(int index) {
    // TODO: Map index to production
    return null;
  }
  
  private void handleError(Token token, Stack<Integer> stateStack, 
                           Stack<ASTNode> symbolStack) {
    // TODO: Implement error recovery using %Syn tokens
  }
}

