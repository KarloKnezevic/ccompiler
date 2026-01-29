package hr.fer.ppj.ir;

import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.util.IrNormalizer;
import hr.fer.ppj.lexer.config.LexerConfig;
import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.Parser;
import hr.fer.ppj.parser.Parser.ParserException;
import hr.fer.ppj.parser.io.TokenReader;
import hr.fer.ppj.parser.tree.ParseTree;
import hr.fer.ppj.semantics.analysis.SemanticAnalyzer;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Golden test harness for IR generation.
 *
 * <p>Tests IR generation by comparing output with expected golden files.
 * Supports update mode via system property {@code -DupdateGolden=true} or
 * environment variable {@code UPDATE_GOLDEN=true}.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public class IrGoldenTest {

  private static final boolean UPDATE_GOLDEN =
      Boolean.parseBoolean(System.getProperty("updateGolden", System.getenv("UPDATE_GOLDEN")));

  private static final Path TEST_RESOURCES = Paths.get("src/test/resources");
  private static final Path GOLDEN_DIR = TEST_RESOURCES.resolve("ir-golden");
  private static final Path TEST_CASES_DIR = TEST_RESOURCES.resolve("cases");

  /**
   * Runs a golden test for a given test case.
   *
   * <p>If update mode is enabled, writes/overwrites the golden file.
   * Otherwise, compares the generated IR with the golden file.
   *
   * @param testCaseName the name of the test case (without .c extension)
   */
  private void runGoldenTest(String testCaseName) throws IOException {
    Path sourceFile = TEST_CASES_DIR.resolve(testCaseName + ".c");
    Path goldenFile = GOLDEN_DIR.resolve(testCaseName + ".ir");

    if (!Files.exists(sourceFile)) {
      throw new IllegalArgumentException("Test case file not found: " + sourceFile);
    }

    // Read source
    String source = Files.readString(sourceFile, StandardCharsets.UTF_8);

    // Run lexer
    LexerGenerator generator = new LexerGenerator();
    LexerGeneratorResult generatorResult;
    try (FileReader reader = new FileReader(LexerConfig.getLexerDefinitionPath().toFile())) {
      generatorResult = generator.generate(reader);
    } catch (Exception e) {
      throw new AssertionError("Lexer generation failed for " + testCaseName + ": "
          + e.getMessage(), e);
    }

    Lexer lexer = new Lexer(generatorResult);
    List<Token> lexerTokens;
    try (StringReader reader = new StringReader(source)) {
      lexerTokens = lexer.tokenize(reader);
    }

    // Convert lexer tokens to parser tokens
    List<TokenReader.Token> parserTokens = new ArrayList<>();
    for (Token token : lexerTokens) {
      parserTokens.add(new TokenReader.Token(token.type(), token.line(), token.value()));
    }

    // Run parser
    Parser parser = new Parser();
    ParseTree parseTree;
    try {
      parseTree = parser.parseTokens(parserTokens);
    } catch (ParserException e) {
      throw new AssertionError("Parsing failed for " + testCaseName + ": "
          + e.getMessage(), e);
    }

    // Run semantic analysis
    ByteArrayOutputStream semOut = new ByteArrayOutputStream();
    PrintStream semPrintStream = new PrintStream(semOut, true, StandardCharsets.UTF_8);
    SemanticAnalyzer analyzer = new SemanticAnalyzer();
    SemanticAnalyzer.SemanticAnalysisResult result;
    try {
      result = analyzer.analyzeWithResults(parseTree, semPrintStream, null);
    } catch (hr.fer.ppj.semantics.errors.SemanticException e) {
      throw new AssertionError("Semantic analysis failed for " + testCaseName + ": "
          + e.getMessage(), e);
    }

    // Generate IR
    IrProgram irProgram = IrPipeline.generate(result.globalScope(), result.parseTree());

    // Verify IR
    try {
      IrPipeline.verify(irProgram);
    } catch (hr.fer.ppj.ir.verify.IrVerifier.IrVerificationException e) {
      throw new AssertionError("IR verification failed for " + testCaseName + ": "
          + e.getMessage(), e);
    }

    // Print IR
    String irOutput = IrPipeline.print(irProgram);

    // Update or compare
    if (UPDATE_GOLDEN) {
      // Create directory if it doesn't exist
      Files.createDirectories(GOLDEN_DIR);
      // Write golden file
      Files.writeString(goldenFile, irOutput, StandardCharsets.UTF_8);
      System.out.println("Updated golden file: " + goldenFile);
    } else {
      // Compare with golden file
      if (!Files.exists(goldenFile)) {
        throw new AssertionError("Golden file not found: " + goldenFile
            + "\nRun with -DupdateGolden=true to generate it.\n\nGenerated IR:\n" + irOutput);
      }

      String expected = Files.readString(goldenFile, StandardCharsets.UTF_8);
      // Use normalized comparison (ignores blank lines)
      if (!IrNormalizer.equalsNormalized(expected, irOutput)) {
        // Generate a readable diff hint using normalized comparison
        String diffHint = IrNormalizer.generateDiffHint(expected, irOutput);
        throw new AssertionError("IR output does not match golden file: " + goldenFile
            + "\n\n" + diffHint
            + "\n\nRun with -DupdateGolden=true to update the golden file.");
      }
    }
  }


  /**
   * Test case: simple function.
   */
  @Test
  public void testSimpleFunction() throws IOException {
    runGoldenTest("simple_function");
  }

  /**
   * Test case: arithmetic operations.
   */
  @Test
  public void testArithmetic() throws IOException {
    runGoldenTest("arithmetic");
  }

  /**
   * Test case: if statement.
   */
  @Test
  public void testIfStatement() throws IOException {
    runGoldenTest("if_statement");
  }

  /**
   * Test case: while loop.
   */
  @Test
  public void testWhileLoop() throws IOException {
    runGoldenTest("while_loop");
  }
}

