package hr.fer.ppj.lexer;

import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Basic lexer test to verify functionality.
 */
public final class LexerBasicTest {
  
  private static LexerGeneratorResult generatorResult;
  
  @BeforeAll
  static void setup() throws Exception {
    LexerGenerator generator = new LexerGenerator();
    Path lexerDefinitionPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    try (FileReader reader = new FileReader(lexerDefinitionPath.toFile())) {
      generatorResult = generator.generate(reader);
    }
    System.out.println("States: " + generatorResult.states());
    System.out.println("State DFAs: " + generatorResult.stateDFAs().keySet());
  }
  
  @Test
  void testSimpleTokenization() throws Exception {
    // Debug: Check DFA for S_pocetno
    var dfa = generatorResult.stateDFAs().get("S_pocetno");
    System.out.println("DFA for S_pocetno: " + (dfa != null ? "exists" : "null"));
    if (dfa != null) {
      System.out.println("Start state: " + dfa.getStartState());
      System.out.println("Accepting states: " + dfa.getAcceptingStates());
      var transitions = dfa.getTransitions();
      System.out.println("Has transitions: " + !transitions.isEmpty());
      System.out.println("Total states with transitions: " + transitions.size());
      if (!transitions.isEmpty()) {
        var startTrans = transitions.get(dfa.getStartState());
        if (startTrans != null) {
          System.out.println("Start state transitions: " + startTrans.size());
          System.out.println("Sample transitions from start: " + startTrans.entrySet().stream().limit(10).toList());
          // Check if 'i' is in transitions
          Integer iTransition = startTrans.get('i');
          System.out.println("Transition for 'i': " + iTransition);
          // Check what characters are in transitions
          var chars = startTrans.keySet().stream().sorted().limit(20).toList();
          System.out.println("First 20 transition chars: " + chars);
        } else {
          System.out.println("WARNING: Start state has no transitions!");
        }
      }
    }
    
    Lexer lexer = new Lexer(generatorResult);
    String input = "int main() { return 0; }";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    System.out.println("Tokens found: " + tokens.size());
    for (Token token : tokens) {
      System.out.println("Token: " + token.type() + " = '" + token.value() + "'");
    }
    
    // At least should find some tokens
    assert tokens.size() > 0 : "Expected at least some tokens";
  }
}

