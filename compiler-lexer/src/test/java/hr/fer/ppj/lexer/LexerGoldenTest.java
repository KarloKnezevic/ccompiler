package hr.fer.ppj.lexer;

import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Lexer.SymbolTableEntry;
import hr.fer.ppj.lexer.io.Token;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Golden file tests for lexer.
 * 
 * <p>Compares lexer output with expected output from leksicke_jedinke.txt files.
 */
public final class LexerGoldenTest {
  
  private static LexerGeneratorResult generatorResult;
  
  @BeforeAll
  static void setup() throws Exception {
    LexerGenerator generator = new LexerGenerator();
    Path lexerDefinitionPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    try (FileReader reader = new FileReader(lexerDefinitionPath.toFile())) {
      generatorResult = generator.generate(reader);
    }
  }
  
  @ParameterizedTest(name = "Lexer test: {0}")
  @MethodSource("provideTestCases")
  void testLexerOutput(String caseId, String program, String expectedOutput) throws Exception {
    Lexer lexer = new Lexer(generatorResult);
    List<Token> tokens;
    try (StringReader reader = new StringReader(program)) {
      tokens = lexer.tokenize(reader);
    }
    
    String actualOutput = formatLexerOutput(lexer, tokens);
    
    // Normalize whitespace for comparison
    String normalizedActual = normalizeWhitespace(actualOutput);
    String normalizedExpected = normalizeWhitespace(expectedOutput);
    
    if (!normalizedActual.equals(normalizedExpected)) {
      System.err.println("=== ACTUAL OUTPUT ===");
      System.err.println(actualOutput);
      System.err.println("=== EXPECTED OUTPUT ===");
      System.err.println(expectedOutput);
      throw new AssertionError(
          String.format("Output mismatch for case %s%n%nActual:%n%s%n%nExpected:%n%s",
              caseId, actualOutput, expectedOutput));
    }
  }
  
  private String formatLexerOutput(Lexer lexer, List<Token> tokens) {
    StringBuilder sb = new StringBuilder();
    
    // Symbol table
    sb.append("tablica znakova:\n");
    sb.append("indeks   uniformni znak   izvorni tekst\n");
    List<SymbolTableEntry> symbolTable = lexer.getSymbolTable();
    for (int i = 0; i < symbolTable.size(); i++) {
      SymbolTableEntry entry = symbolTable.get(i);
      sb.append(String.format("     %d   %-18s %s%n", 
          i, entry.token(), entry.text()));
    }
    
    // Token stream
    sb.append("\nniz uniformnih znakova:\n");
    sb.append("uniformni znak    redak    indeks u tablicu znakova\n");
    for (Token token : tokens) {
      sb.append(String.format("%-18s %5d       %d%n",
          token.type(),
          token.line(),
          token.symbolTableIndex()));
    }
    
    return sb.toString();
  }
  
  private String normalizeWhitespace(String text) {
    // Normalize line endings and whitespace
    // Split into lines, normalize each line, then rejoin
    String[] lines = text.replaceAll("\r\n", "\n").split("\n");
    StringBuilder result = new StringBuilder();
    for (String line : lines) {
      // Normalize multiple spaces to single space
      String normalized = line.replaceAll(" +", " ");
      // Remove trailing spaces
      normalized = normalized.replaceAll(" +$", "");
      result.append(normalized).append("\n");
    }
    // Remove trailing newline and trim
    String resultStr = result.toString();
    if (resultStr.endsWith("\n")) {
      resultStr = resultStr.substring(0, resultStr.length() - 1);
    }
    return resultStr.trim();
  }
  
  static Stream<Arguments> provideTestCases() throws IOException {
    try {
      // Get test resources from classpath
      java.net.URL resourceUrl = LexerGoldenTest.class.getClassLoader()
          .getResource("ppjc_case_00");
      if (resourceUrl == null) {
        // Try to find resources in src/test/resources
        Path testResources = Paths.get("src/test/resources");
        if (!Files.exists(testResources)) {
          return Stream.empty();
        }
        return loadTestCasesFromPath(testResources);
      }
      
      // Load from classpath
      Path testResources = Paths.get(resourceUrl.toURI()).getParent();
      return loadTestCasesFromPath(testResources);
    } catch (URISyntaxException e) {
      throw new IOException(e);
    }
  }
  
  private static Stream<Arguments> loadTestCasesFromPath(Path testResources) throws IOException {
    return Files.list(testResources)
        .filter(Files::isDirectory)
        .filter(p -> p.getFileName().toString().startsWith("ppjc_case_"))
        .map(p -> {
          String caseId = p.getFileName().toString();
          try {
            Path programFile = p.resolve("program.c");
            Path goldenFile = p.resolve("leksicke_jedinke.txt");
            
            if (!Files.exists(programFile) || !Files.exists(goldenFile)) {
              return null;
            }
            
            String program = Files.readString(programFile);
            String expected = Files.readString(goldenFile);
            
            return Arguments.of(caseId, program, expected);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        })
        .filter(java.util.Objects::nonNull);
  }
}

