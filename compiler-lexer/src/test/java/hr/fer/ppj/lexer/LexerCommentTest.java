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
 * Test to debug comment handling.
 */
public final class LexerCommentTest {
  
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
  void testCommentHandling() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    // Test with comment followed by "fun"
    String input = "/* comment */void fun(int xYz) {";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    System.out.println("Tokens found: " + tokens.size());
    for (Token token : tokens) {
      System.out.println("Token: " + token.type() + " = '" + token.value() + "'");
    }
    
    // Should find "fun" not "un"
    boolean foundFun = tokens.stream()
        .anyMatch(t -> t.type().equals("IDN") && t.value().equals("fun"));
    assert foundFun : "Expected to find 'fun', but got: " + tokens;
  }
}

