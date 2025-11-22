package hr.fer.ppj.lexer;

import hr.fer.ppj.lexer.nfa.NFA;
import hr.fer.ppj.lexer.regex.RegexParser;
import org.junit.jupiter.api.Test;

/**
 * Test regex parser with simple patterns.
 */
public final class RegexParserTest {
  
  @Test
  void testSimpleConcatenation() {
    NFA nfa = new NFA();
    int[] nextId = {1};
    RegexParser parser = new RegexParser(nfa, () -> nextId[0]++);
    
    RegexParser.StatePair pair = parser.parse("int");
    
    System.out.println("Pattern 'int' -> states " + pair.start() + " to " + pair.end());
    System.out.println("NFA transitions: " + nfa.getTransitions().size());
    System.out.println("NFA epsilon transitions: " + nfa.getEpsilonTransitions().size());
    
    // Check transitions
    var transitions = nfa.getTransitions();
    for (var entry : transitions.entrySet()) {
      System.out.println("State " + entry.getKey() + " transitions: " + entry.getValue());
    }
  }
}

