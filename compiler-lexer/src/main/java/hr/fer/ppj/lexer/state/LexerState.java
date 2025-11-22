package hr.fer.ppj.lexer.state;

import java.util.Stack;

/**
 * Manages lexer state including current DFA state and state stack.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LexerState {
  
  private String currentState;
  private final Stack<String> stateStack = new Stack<>();
  private int lineNumber = 1;
  private int columnNumber = 1;
  
  public LexerState(String initialState) {
    this.currentState = initialState;
  }
  
  public String getCurrentState() {
    return currentState;
  }
  
  public void enterState(String state) {
    stateStack.push(currentState);
    currentState = state;
  }
  
  public void returnToPreviousState() {
    if (!stateStack.isEmpty()) {
      currentState = stateStack.pop();
    }
  }
  
  public void newLine() {
    lineNumber++;
    columnNumber = 1;
  }
  
  public void advanceColumn() {
    columnNumber++;
  }
  
  public void setColumn(int col) {
    this.columnNumber = col;
  }
  
  public int getLineNumber() {
    return lineNumber;
  }
  
  public int getColumnNumber() {
    return columnNumber;
  }
}

