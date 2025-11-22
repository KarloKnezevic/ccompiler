package hr.fer.ppj.lexer;

import hr.fer.ppj.lexer.dfa.DFA;
import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.gen.LexerSpecParser;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Debug test to identify lexer issues.
 */
public final class LexerDebugTest {
  
  private static LexerGeneratorResult generatorResult;
  
  @BeforeAll
  static void setup() throws Exception {
    LexerGenerator generator = new LexerGenerator();
    Path lexerDefinitionPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    try (FileReader reader = new FileReader(lexerDefinitionPath.toFile())) {
      generatorResult = generator.generate(reader);
    }
  }
  
  @Test
  void debugLexerSpecParsing() throws Exception {
    LexerSpecParser parser = new LexerSpecParser();
    Path lexerDefinitionPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    try (FileReader reader = new FileReader(lexerDefinitionPath.toFile())) {
      parser.parse(reader);
    }
    
    System.out.println("=== MACROS ===");
    parser.getMacros().forEach((k, v) -> System.out.println("  " + k + " = " + v));
    
    System.out.println("\n=== STATES ===");
    parser.getStates().forEach(s -> System.out.println("  " + s));
    
    System.out.println("\n=== RULES FOR S_pocetno ===");
    parser.getRules().stream()
        .filter(r -> r.state().equals("S_pocetno"))
        .limit(10)
        .forEach(r -> System.out.println("  Pattern: '" + r.pattern() + "' -> Token: " + r.token()));
  }
  
  @Test
  void debugDFAConstruction() {
    DFA dfa = generatorResult.stateDFAs().get("S_pocetno");
    if (dfa == null) {
      System.out.println("ERROR: DFA for S_pocetno is null!");
      return;
    }
    
    System.out.println("=== DFA for S_pocetno ===");
    System.out.println("Start state: " + dfa.getStartState());
    System.out.println("Accepting states: " + dfa.getAcceptingStates());
    
    // Check transitions from start state
    int start = dfa.getStartState();
    System.out.println("\nTransitions from start state " + start + ":");
    for (char c = 'a'; c <= 'z'; c++) {
      Integer next = dfa.getTransition(start, c);
      if (next != null) {
        System.out.println("  '" + c + "' -> " + next);
      }
    }
    for (char c = 'A'; c <= 'Z'; c++) {
      Integer next = dfa.getTransition(start, c);
      if (next != null) {
        System.out.println("  '" + c + "' -> " + next);
      }
    }
    for (char c = '0'; c <= '9'; c++) {
      Integer next = dfa.getTransition(start, c);
      if (next != null) {
        System.out.println("  '" + c + "' -> " + next);
      }
    }
    
    // Check for keyword "int"
    System.out.println("\nChecking 'int' pattern:");
    int state = start;
    for (char c : "int".toCharArray()) {
      Integer next = dfa.getTransition(state, c);
      System.out.println("  '" + c + "' -> " + next);
      if (next == null) {
        break;
      }
      state = next;
      if (dfa.isAccepting(state)) {
        System.out.println("  ACCEPTING! Token: " + dfa.getToken(state));
      }
    }
  }
  
  @Test
  void debugTokenization() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "int";
    System.out.println("=== Tokenizing: '" + input + "' ===");
    
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    System.out.println("Tokens found: " + tokens.size());
    for (Token token : tokens) {
      System.out.println("  " + token);
    }
  }
  
  @Test
  void debugStringPattern() throws Exception {
    LexerSpecParser parser = new LexerSpecParser();
    Path lexerDefinitionPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    try (FileReader reader = new FileReader(lexerDefinitionPath.toFile())) {
      parser.parse(reader);
    }
    
    System.out.println("=== RULES FOR S_string ===");
    parser.getRules().stream()
        .filter(r -> r.state().equals("S_string"))
        .forEach(r -> {
          System.out.println("  Pattern: '" + r.pattern() + "'");
          System.out.println("  Token: " + r.token());
          System.out.println("  Actions: " + r.actions());
        });
    
    // Test string tokenization
    System.out.println("\n=== Testing string tokenization ===");
    Lexer lexer = new Lexer(generatorResult);
    String input = "\"a\";";
    System.out.println("Input: '" + input + "'");
    
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    System.out.println("Tokens found: " + tokens.size());
    for (Token token : tokens) {
      System.out.println("  " + token.type() + " = '" + token.value() + "'");
    }
  }
}

