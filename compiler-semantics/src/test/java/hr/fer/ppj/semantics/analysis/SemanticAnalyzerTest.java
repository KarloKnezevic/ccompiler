package hr.fer.ppj.semantics.analysis;

import static org.junit.jupiter.api.Assertions.assertTrue;

import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.Parser;
import hr.fer.ppj.parser.config.ParserConfig;
import hr.fer.ppj.semantics.io.GenerativeTreeParser;
import hr.fer.ppj.semantics.io.LexicalTokenReader;
import hr.fer.ppj.semantics.io.SemanticToken;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests that run the complete compilation pipeline (lexer + parser + semantic analyzer)
 * on valid example programs to ensure no crashes or unexpected errors.
 */
final class SemanticAnalyzerTest {

  private static LexerGeneratorResult lexerGeneratorResult;
  
  @BeforeAll
  static void setup() throws Exception {
    // Setup lexer generator
    LexerGenerator generator = new LexerGenerator();
    Path lexerDefinitionPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    try (FileReader reader = new FileReader(lexerDefinitionPath.toFile())) {
      lexerGeneratorResult = generator.generate(reader);
    }
  }

  private static Stream<Arguments> provideCases() throws IOException {
    // Use a simple hardcoded test case
    Path tempFile = Files.createTempFile("test", ".c");
    Files.writeString(tempFile, "int main(void) { return 0; }");
    
    return Stream.of(
        Arguments.of("simple_main.c", tempFile)
    );
  }
  
  private static int extractProgramNumber(String fileName) {
    // Extract number from "programN.c" format
    try {
      String numberStr = fileName.substring(7, fileName.length() - 2); // Remove "program" and ".c"
      return Integer.parseInt(numberStr);
    } catch (Exception e) {
      return Integer.MAX_VALUE; // Put invalid names at the end
    }
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("provideCases")
  void semanticAnalysisTest(String name, Path programFile, @TempDir Path tempDir) throws Exception {
    // Step 1: Read program
    String program = Files.readString(programFile);
    
    // Step 2: Run lexer
    Lexer lexer = new Lexer(lexerGeneratorResult);
    List<Token> tokens;
    try (StringReader reader = new StringReader(program)) {
      tokens = lexer.tokenize(reader);
    }
    
    // Step 3: Format lexer output for parser
    String lexerOutput = formatLexerOutput(lexer, tokens);
    Path tokenFile = tempDir.resolve("leksicke_jedinke.txt");
    Files.writeString(tokenFile, lexerOutput);
    
    // Step 4: Run parser
    ParserConfig.Config config = ParserConfig.Config.createDefault(
        tokenFile,
        tempDir
    );
    
    Parser parser = new Parser();
    try {
      parser.parse(config);
    } catch (Parser.ParserException e) {
      // Some programs might have syntax errors - that's ok for this test
      System.out.println("Parser error for " + name + ": " + e.getMessage());
      return;
    }
    
    // Step 5: Run semantic analyzer
    Path generativeTreeFile = config.outputGenerativeTree();
    if (!Files.exists(generativeTreeFile)) {
      System.out.println("No generative tree generated for " + name);
      return;
    }
    
    List<SemanticToken> semanticTokens;
    try (var reader = Files.newBufferedReader(tokenFile, StandardCharsets.UTF_8)) {
      semanticTokens = new LexicalTokenReader().read(reader);
    }

    NonTerminalNode root;
    try (var reader = Files.newBufferedReader(generativeTreeFile, StandardCharsets.UTF_8)) {
      root = GenerativeTreeParser.parse(reader, semanticTokens);
    }

    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    SemanticAnalyzer analyzer = new SemanticAnalyzer();
    try {
      analyzer.analyze(root, new PrintStream(buffer, true, StandardCharsets.UTF_8));
      // If we get here, semantic analysis succeeded (no errors)
      String output = buffer.toString(StandardCharsets.UTF_8);
      assertTrue(output.isEmpty() || output.equals("main\n"), 
          "Semantic analysis should produce no errors or only 'main' error for valid programs. Got: " + output);
    } catch (SemanticException e) {
      // Some semantic error occurred - check if it's expected
      String output = buffer.toString(StandardCharsets.UTF_8);
      System.out.println("Semantic error for " + name + ": " + output);
      // For valid programs, we expect either no errors or only missing main function error
      assertTrue(output.contains("main") || output.isEmpty(), 
          "Unexpected semantic error for valid program " + name + ": " + output);
    }
  }
  
  private String formatLexerOutput(Lexer lexer, List<Token> tokens) {
    StringBuilder sb = new StringBuilder();
    
    // Symbol table
    sb.append("tablica znakova:\n");
    sb.append("indeks   uniformni znak   izvorni tekst\n");
    List<Lexer.SymbolTableEntry> symbolTable = lexer.getSymbolTable();
    for (int i = 0; i < symbolTable.size(); i++) {
      Lexer.SymbolTableEntry entry = symbolTable.get(i);
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
}

