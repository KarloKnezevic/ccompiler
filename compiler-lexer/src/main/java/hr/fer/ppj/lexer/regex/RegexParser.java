package hr.fer.ppj.lexer.regex;

import hr.fer.ppj.lexer.nfa.NFA;
import java.util.ArrayList;
import java.util.List;

/**
 * Manual regex parser following regex_pseudokod.txt.
 * 
 * <p>Converts regex patterns to ε-NFA without using any regex libraries.
 * Supports: concatenation, union (|), Kleene star (*), groups (), epsilon ($).
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class RegexParser {
  
  private final NFA nfa;
  private java.util.function.IntSupplier stateSupplier;
  private int localStateCounter = 0;
  
  public RegexParser() {
    this.nfa = new NFA();
    final int[] counter = {0};
    this.stateSupplier = () -> counter[0]++;
  }
  
  /**
   * Creates a parser that uses a shared NFA and state supplier.
   */
  public RegexParser(NFA sharedNFA, java.util.function.IntSupplier stateSupplier) {
    this.nfa = sharedNFA;
    this.stateSupplier = stateSupplier;
  }
  
  /**
   * Parses a regex pattern and returns an NFA.
   * 
   * @param pattern the regex pattern string
   * @return pair of (start state, end state)
   */
  public StatePair parse(String pattern) {
    return convert(pattern, nfa);
  }
  
  /**
   * Main conversion function following the pseudocode.
   */
  private StatePair convert(String expression, NFA automaton) {
    // Check for union operator (|) at top level
    List<String> choices = splitByUnion(expression);
    
    int leftState = newState(automaton);
    int rightState = newState(automaton);
    
    if (choices.size() > 1) {
      // Union case: create epsilon transitions from left to each choice
      for (String choice : choices) {
        StatePair temp = convert(choice, automaton);
        automaton.addEpsilonTransition(leftState, temp.start());
        automaton.addEpsilonTransition(temp.end(), rightState);
      }
    } else {
      // No union - process as concatenation
      boolean prefixed = false;
      int lastState = leftState;
      
      for (int i = 0; i < expression.length(); i++) {
        int a, b;
        
        if (prefixed) {
          // Case 1: escaped character
          prefixed = false;
          char transitionChar;
          char c = expression.charAt(i);
          if (c == 't') {
            transitionChar = '\t';
          } else if (c == 'n') {
            transitionChar = '\n';
          } else if (c == '_') {
            transitionChar = ' ';
          } else {
            transitionChar = c;
          }
          
          a = newState(automaton);
          b = newState(automaton);
          automaton.addTransition(a, b, transitionChar);
        } else {
          // Case 2: normal character or group
          if (expression.charAt(i) == '\\') {
            prefixed = true;
            continue;
          }
          
          if (expression.charAt(i) != '(') {
            // Case 2a: single character
            a = newState(automaton);
            b = newState(automaton);
            char c = expression.charAt(i);
            if (c == '$') {
              automaton.addEpsilonTransition(a, b);
            } else {
              automaton.addTransition(a, b, c);
            }
          } else {
            // Case 2b: group
            int j = findMatchingParen(expression, i);
            StatePair temp = convert(
                expression.substring(i + 1, j), automaton);
            a = temp.start();
            b = temp.end();
            i = j;
          }
        }
        
        // Check for Kleene star
        if (i + 1 < expression.length() && expression.charAt(i + 1) == '*') {
          int x = a;
          int y = b;
          a = newState(automaton);
          b = newState(automaton);
          automaton.addEpsilonTransition(a, x);
          automaton.addEpsilonTransition(y, b);
          automaton.addEpsilonTransition(a, b);
          automaton.addEpsilonTransition(y, x);
          i++;
        }
        
        // Connect to rest of automaton (concatenation)
        automaton.addEpsilonTransition(lastState, a);
        lastState = b;
      }
      
      automaton.addEpsilonTransition(lastState, rightState);
    }
    
    return new StatePair(leftState, rightState);
  }
  
  /**
   * Splits expression by union operator (|), respecting parentheses and escaping.
   */
  private List<String> splitByUnion(String expression) {
    List<String> choices = new ArrayList<>();
    int parenCount = 0;
    int start = 0;
    
    for (int i = 0; i < expression.length(); i++) {
      if (isOperator(expression, i, '(')) {
        parenCount++;
      } else if (isOperator(expression, i, ')')) {
        parenCount--;
      } else if (parenCount == 0 && isOperator(expression, i, '|')) {
        choices.add(expression.substring(start, i));
        start = i + 1;
      }
    }
    
    if (choices.isEmpty()) {
      choices.add(expression);
    } else {
      choices.add(expression.substring(start));
    }
    
    return choices;
  }
  
  /**
   * Checks if character at position i is an operator (not escaped).
   */
  private boolean isOperator(String expression, int i, char op) {
    if (expression.charAt(i) != op) {
      return false;
    }
    // Count backslashes before this position
    int backslashCount = 0;
    for (int j = i - 1; j >= 0 && expression.charAt(j) == '\\'; j--) {
      backslashCount++;
    }
    return backslashCount % 2 == 0;
  }
  
  /**
   * Finds matching closing parenthesis.
   */
  private int findMatchingParen(String expression, int openPos) {
    int count = 1;
    for (int i = openPos + 1; i < expression.length(); i++) {
      if (isOperator(expression, i, '(')) {
        count++;
      } else if (isOperator(expression, i, ')')) {
        count--;
        if (count == 0) {
          return i;
        }
      }
    }
    throw new IllegalArgumentException("Unmatched parenthesis");
  }
  
  private int newState(NFA automaton) {
    return stateSupplier.getAsInt();
  }
  
  public record StatePair(int start, int end) {}
  
  public NFA getNFA() {
    return nfa;
  }
}

