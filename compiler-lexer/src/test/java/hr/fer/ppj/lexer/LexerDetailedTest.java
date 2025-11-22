package hr.fer.ppj.lexer;

import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Detaljni testovi za leksički analizator.
 * 
 * <p>Testira:
 * - Max-munch algoritam (P2)
 * - Redoslijed pravila (P3)
 * - VRATI_SE akciju
 * - Error recovery (Algoritam C)
 * - State transitions (UDJI_U_STANJE)
 * - NOVI_REDAK akciju
 * - Komentare (jednolinijski i višelinijski)
 * - String literale
 */
public final class LexerDetailedTest {
  
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
   * 
   * <p>Testira da "int" matcha kao KR_INT, ne kao "in" + nešto.
   */
  @Test
  void testMaxMunchLongerMatch() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "int x = 5;";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    Token firstToken = tokens.get(0);
    assertEquals("KR_INT", firstToken.type(), "Should match 'int' as keyword");
    assertEquals("int", firstToken.value(), "Should match full 'int'");
    assertEquals(1, firstToken.line(), "Should be on line 1");
  }
  
  /**
   * Test Algoritam B - P2 (max-munch): operatori s više znakova.
   * 
   * <p>Testira da "++" matcha kao OP_INC, ne kao dva "+".
   */
  @Test
  void testMaxMunchMultiCharOperator() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "x++";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    // Pronađi OP_INC token
    Token incToken = tokens.stream()
        .filter(t -> t.type().equals("OP_INC"))
        .findFirst()
        .orElse(null);
    assertNotNull(incToken, "Should find OP_INC token");
    assertEquals("++", incToken.value(), "Should match '++' as single token");
  }
  
  /**
   * Test Algoritam B - P3 (redoslijed pravila): ranije pravilo pobjeđuje.
   * 
   * <p>Testira da ključne riječi (koje su ranije u specifikaciji) pobjeđuju IDN.
   */
  @Test
  void testRulePriorityKeywords() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "if else return break continue";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    assertEquals("KR_IF", tokens.get(0).type(), "'if' should be keyword");
    assertEquals("KR_ELSE", tokens.get(1).type(), "'else' should be keyword");
    assertEquals("KR_RETURN", tokens.get(2).type(), "'return' should be keyword");
    assertEquals("KR_BREAK", tokens.get(3).type(), "'break' should be keyword");
    assertEquals("KR_CONTINUE", tokens.get(4).type(), "'continue' should be keyword");
  }
  
  /**
   * Test VRATI_SE akciju - vraćanje znakova u ulazni niz.
   * 
   * <p>Testira da se znakovi vraćaju u ulazni niz kada se koristi VRATI_SE.
   */
  @Test
  void testVratiSeAction() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    // String literal koristi VRATI_SE 0 da vrati znak " u ulazni niz
    String input = "\"test\"";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    Token stringToken = tokens.stream()
        .filter(t -> t.type().equals("NIZ_ZNAKOVA"))
        .findFirst()
        .orElse(null);
    assertNotNull(stringToken, "Should find NIZ_ZNAKOVA token");
    assertEquals("\"test\"", stringToken.value(), "Should match full string literal");
  }
  
  /**
   * Test error recovery - Algoritam C.
   * 
   * <p>Testira da se neprepoznati znakovi odbacuju i ispisuje se greška na stderr.
   */
  @Test
  void testErrorRecovery() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "int x @ y;"; // @ je neprepoznat znak
    List<Token> tokens;
    
    // Capture stderr
    ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(errContent));
    
    try {
      try (StringReader reader = new StringReader(input)) {
        tokens = lexer.tokenize(reader);
      }
    } finally {
      System.setErr(originalErr);
    }
    
    String errorOutput = errContent.toString();
    assertTrue(errorOutput.contains("Leksička greška"), "Should print error message");
    assertTrue(errorOutput.contains("@"), "Error should mention unrecognized character");
    
    // Trebali bismo dobiti tokene prije i poslije greške
    assertFalse(tokens.isEmpty(), "Should have some tokens");
    assertEquals("KR_INT", tokens.get(0).type(), "Should tokenize 'int'");
  }
  
  /**
   * Test state transitions - UDJI_U_STANJE.
   * 
   * <p>Testira da se stanje mijenja kada se uđe u komentar.
   */
  @Test
  void testStateTransitionComment() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "int x; /* comment */ int y;";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    // Komentar se ne vraća kao token
    // Trebali bismo dobiti samo tokene prije i poslije komentara
    assertFalse(tokens.isEmpty(), "Should have tokens");
    assertEquals("KR_INT", tokens.get(0).type(), "First token should be 'int'");
    // Pronađi drugi 'int' token
    Token secondInt = tokens.stream()
        .filter(t -> t.type().equals("KR_INT") && t.value().equals("int"))
        .skip(1)
        .findFirst()
        .orElse(null);
    assertNotNull(secondInt, "Should find second 'int' after comment");
  }
  
  /**
   * Test jednolinijski komentari.
   */
  @Test
  void testSingleLineComment() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "int x; // comment\nint y;";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    assertEquals("KR_INT", tokens.get(0).type(), "First token should be 'int'");
    // Pronađi drugi 'int' token
    Token secondInt = tokens.stream()
        .filter(t -> t.type().equals("KR_INT") && t.value().equals("int"))
        .skip(1)
        .findFirst()
        .orElse(null);
    assertNotNull(secondInt, "Should find second 'int' after comment");
    assertEquals(2, secondInt.line(), "Second 'int' should be on line 2");
  }
  
  /**
   * Test NOVI_REDAK akciju.
   * 
   * <p>Testira da se broj retka povećava kada se naiđe na novi redak.
   */
  @Test
  void testNoviRedakAction() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "int x;\nint y;";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    Token firstInt = tokens.get(0);
    Token secondInt = tokens.stream()
        .filter(t -> t.type().equals("KR_INT") && t.value().equals("int"))
        .skip(1)
        .findFirst()
        .orElse(null);
    
    assertNotNull(secondInt, "Should find second 'int'");
    assertEquals(1, firstInt.line(), "First 'int' should be on line 1");
    assertEquals(2, secondInt.line(), "Second 'int' should be on line 2");
  }
  
  /**
   * Test string literali s escape sekvencama.
   */
  @Test
  void testStringLiteralWithEscapes() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "\"test\\nstring\"";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    Token stringToken = tokens.stream()
        .filter(t -> t.type().equals("NIZ_ZNAKOVA"))
        .findFirst()
        .orElse(null);
    assertNotNull(stringToken, "Should find NIZ_ZNAKOVA token");
    assertTrue(stringToken.value().contains("\\n"), "Should contain escape sequence");
  }
  
  /**
   * Test brojevi - dekadski, oktalni, heksadekadski.
   */
  @Test
  void testNumbers() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "123 0123 0x123";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    List<Token> numberTokens = tokens.stream()
        .filter(t -> t.type().equals("BROJ"))
        .toList();
    
    assertEquals(3, numberTokens.size(), "Should have 3 number tokens");
    assertEquals("123", numberTokens.get(0).value(), "First should be decimal");
    assertEquals("0123", numberTokens.get(1).value(), "Second should be octal");
    assertEquals("0x123", numberTokens.get(2).value(), "Third should be hexadecimal");
  }
  
  /**
   * Test operatori - svi tipovi operatora.
   */
  @Test
  void testOperators() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "+ - * / % ++ -- == != < > <= >= && || & | ^";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    // Provjeri da su svi operatori prepoznati
    assertTrue(tokens.stream().anyMatch(t -> t.type().equals("PLUS")), "Should have PLUS");
    assertTrue(tokens.stream().anyMatch(t -> t.type().equals("MINUS")), "Should have MINUS");
    assertTrue(tokens.stream().anyMatch(t -> t.type().equals("OP_INC")), "Should have OP_INC");
    assertTrue(tokens.stream().anyMatch(t -> t.type().equals("OP_DEC")), "Should have OP_DEC");
    assertTrue(tokens.stream().anyMatch(t -> t.type().equals("OP_EQ")), "Should have OP_EQ");
    assertTrue(tokens.stream().anyMatch(t -> t.type().equals("OP_NEQ")), "Should have OP_NEQ");
  }
  
  /**
   * Test whitespace - bjelina se ignorira.
   */
  @Test
  void testWhitespaceIgnored() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = "int   x   =   5;";
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    // Whitespace se ne vraća kao token
    // Trebali bismo dobiti samo tokene bez whitespace-a
    assertEquals("KR_INT", tokens.get(0).type(), "First should be 'int'");
    Token idnToken = tokens.stream()
        .filter(t -> t.type().equals("IDN"))
        .findFirst()
        .orElse(null);
    assertNotNull(idnToken, "Should find IDN token");
    assertEquals("x", idnToken.value(), "IDN should be 'x'");
  }
  
  /**
   * Test kompleksniji program s više konstrukata.
   */
  @Test
  void testComplexProgram() throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    String input = """
        int main(void) {
            int x = 5;
            if (x > 0) {
                return x;
            }
            return 0;
        }
        """;
    List<Token> tokens;
    try (StringReader reader = new StringReader(input)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    // Provjeri da su svi ključni tokeni prisutni
    assertTrue(tokens.stream().anyMatch(t -> t.type().equals("KR_INT")), "Should have KR_INT");
    assertTrue(tokens.stream().anyMatch(t -> t.type().equals("IDN") && t.value().equals("main")), "Should have main");
    assertTrue(tokens.stream().anyMatch(t -> t.type().equals("KR_IF")), "Should have KR_IF");
    assertTrue(tokens.stream().anyMatch(t -> t.type().equals("KR_RETURN")), "Should have KR_RETURN");
  }
}

