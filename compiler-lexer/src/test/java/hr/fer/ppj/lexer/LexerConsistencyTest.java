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
import static org.junit.jupiter.api.Assertions.*;

/**
 * Testovi za provjeru konzistentnosti s algoritmima leksičkog analizatora.
 * 
 * <p>Testira:
 * - Algoritam B: Max-munch i redoslijed pravila
 * - Algoritam C: Oporavak od greške
 * - VRATI_SE akciju
 * - Komentare i state transitions
 */
public final class LexerConsistencyTest {
  
  private static LexerGeneratorResult generatorResult;
  
  @BeforeAll
  static void setup() throws Exception {
    LexerGenerator generator = new LexerGenerator();
    Path lexerDefinitionPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    try (FileReader reader = new FileReader(lexerDefinitionPath.toFile())) {
      generatorResult = generator.generate(reader);
    }
  }
  
  /**
   * Test Algoritam B - P2 (max-munch): dulji match pobjeđuje.
   */
  @Test
  void testMaxMunch() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    // "int" je dulji od "in" - treba matchati "int"
    String input = "int x = 5;";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    // Pronađi prvi token
    assertFalse(tokens.isEmpty(), "Should have tokens");
    Token firstToken = tokens.get(0);
    assertEquals("KR_INT", firstToken.type(), "Should match 'int' not 'in'");
    assertEquals("int", firstToken.value(), "Should match full 'int'");
  }
  
  /**
   * Test Algoritam B - P3 (redoslijed pravila): ranije pravilo pobjeđuje.
   * 
   * <p>Ovo zahtijeva dva pravila koja matchaju istu duljinu.
   * U specifikaciji, ključne riječi su obično ranije od IDN, pa "if" treba biti KR_IF, ne IDN.
   */
  @Test
  void testRulePriority() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "if (x) { }";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    Token firstToken = tokens.get(0);
    assertEquals("KR_IF", firstToken.type(), "Keyword 'if' should match before IDN pattern");
    assertEquals("if", firstToken.value());
  }
  
  /**
   * Test VRATI_SE akcija - provjerava da se znakovi ne gube nakon komentara.
   */
  @Test
  void testCommentAfterVRATI_SE() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    // Komentar praćen s "fun" - ne smije se izgubiti 'f'
    String input = "/* comment */void fun(int xYz) {";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    // Pronađi "fun" token
    Token funToken = tokens.stream()
        .filter(t -> t.type().equals("IDN") && t.value().startsWith("f"))
        .findFirst()
        .orElse(null);
    
    assertNotNull(funToken, "Should find 'fun' token");
    assertEquals("fun", funToken.value(), "Should match 'fun', not 'un'");
    
    // Provjeri da se 'xYz' također ne gubi
    Token xYzToken = tokens.stream()
        .filter(t -> t.type().equals("IDN") && t.value().contains("Yz"))
        .findFirst()
        .orElse(null);
    
    assertNotNull(xYzToken, "Should find 'xYz' token");
    assertEquals("xYz", xYzToken.value(), "Should match 'xYz', not 'Yz'");
  }
  
  /**
   * Test Algoritam C - Oporavak od greške: panic mode odbacuje prvi znak.
   */
  @Test
  void testErrorRecovery() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    // Nevaljan znak '@' koji nije u specifikaciji
    String input = "@invalid";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    // Trebao bi odbaciti '@' i nastaviti s "invalid"
    // Provjeri da postoji token "invalid"
    Token invalidToken = tokens.stream()
        .filter(t -> t.value().equals("invalid"))
        .findFirst()
        .orElse(null);
    
    assertNotNull(invalidToken, "Should recover and find 'invalid' after discarding '@'");
  }
  
  /**
   * Test state transitions - komentari mijenjaju stanje.
   */
  @Test
  void testStateTransitions() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "/* start comment\nstill in comment\nend */int x;";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    // Trebao bi naći "int" nakon komentara
    Token intToken = tokens.stream()
        .filter(t -> t.type().equals("KR_INT"))
        .findFirst()
        .orElse(null);
    
    assertNotNull(intToken, "Should find 'int' after multi-line comment");
    assertEquals("int", intToken.value());
  }
  
  /**
   * Test NOVI_REDAK akcija - provjerava da se redak broji ispravno.
   */
  @Test
  void testNOVI_REDAK() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "int x;\nint y;";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    // Pronađi "y" token - trebao bi biti na retku 2
    Token yToken = tokens.stream()
        .filter(t -> t.value().equals("y"))
        .findFirst()
        .orElse(null);
    
    assertNotNull(yToken, "Should find 'y' token");
    assertEquals(2, yToken.line(), "Token 'y' should be on line 2");
  }
}

