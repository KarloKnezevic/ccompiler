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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

/**
 * Validates IR generation for all programs in examples/valid.
 *
 * <p>This test suite runs the full compilation pipeline (lexer → parser →
 * semantic analysis → IR generation) for each C program in examples/valid
 * and compares the generated IR with the expected .ir files using normalized
 * comparison (blank lines are ignored).
 *
 * <p>Generated IR files are written to compiler-bin/ preserving the directory structure.
 *
 * <p>To run a specific test, use:
 * <pre>{@code
 * mvn test -Dtest=ExamplesValidationTest#testProgram1
 * }</pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public class ExamplesValidationTest {

  // Paths relative to project root
  private static final Path PROJECT_ROOT = findProjectRoot();
  private static final Path EXAMPLES_DIR = PROJECT_ROOT.resolve("examples/valid");
  private static final Path OUTPUT_DIR = PROJECT_ROOT.resolve("compiler-bin");

  /**
   * Finds the project root by looking for pom.xml or examples/ directory.
   */
  private static Path findProjectRoot() {
    // Start from the current working directory
    Path current = Paths.get(".").toAbsolutePath().normalize();
    
    // Walk up the directory tree looking for project root indicators
    Path testPath = current;
    for (int i = 0; i < 10; i++) {
      // Check if this directory has both pom.xml (parent POM) and examples/ directory
      if (Files.exists(testPath.resolve("pom.xml"))
          && Files.exists(testPath.resolve("examples"))) {
        return testPath;
      }
      
      // Also check if we're in a module directory (has pom.xml but parent has examples)
      if (Files.exists(testPath.resolve("pom.xml"))) {
        Path parent = testPath.getParent();
        if (parent != null && Files.exists(parent.resolve("examples"))) {
          return parent;
        }
      }
      
      Path parent = testPath.getParent();
      if (parent == null) {
        break;
      }
      testPath = parent;
    }
    
    // Fallback: try to find examples directory anywhere up the tree
    testPath = current;
    for (int i = 0; i < 10; i++) {
      if (Files.exists(testPath.resolve("examples"))) {
        return testPath;
      }
      Path parent = testPath.getParent();
      if (parent == null) {
        break;
      }
      testPath = parent;
    }
    
    // Last resort: use current directory
    return current;
  }

  /**
   * Runs validation for a single program.
   */
  private void validateProgram(String programName) throws IOException {
    Path sourceFile = EXAMPLES_DIR.resolve(programName + ".c");
    Path expectedIrFile = EXAMPLES_DIR.resolve(programName + ".ir");
    Path outputIrFile = OUTPUT_DIR.resolve(programName + ".ir");

    if (!Files.exists(sourceFile)) {
      throw new AssertionError("Source file not found: " + sourceFile);
    }

    if (!Files.exists(expectedIrFile)) {
      throw new AssertionError("Expected IR file not found: " + expectedIrFile);
    }

    // Read source
    String source = Files.readString(sourceFile, StandardCharsets.UTF_8);

    // Run lexer
    LexerGenerator generator = new LexerGenerator();
    LexerGeneratorResult generatorResult;
    try (FileReader reader = new FileReader(LexerConfig.getLexerDefinitionPath().toFile())) {
      generatorResult = generator.generate(reader);
    } catch (Exception e) {
      throw new AssertionError("Lexer generation failed for " + programName + ": "
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
      throw new AssertionError("Parsing failed for " + programName + ": "
          + e.getMessage(), e);
    }

    // Run semantic analysis
    PrintStream nullStream = new PrintStream(new java.io.ByteArrayOutputStream());
    SemanticAnalyzer analyzer = new SemanticAnalyzer();
    SemanticAnalyzer.SemanticAnalysisResult result;
    try {
      result = analyzer.analyzeWithResults(parseTree, nullStream, null);
    } catch (hr.fer.ppj.semantics.errors.SemanticException e) {
      throw new AssertionError("Semantic analysis failed for " + programName + ": "
          + e.getMessage(), e);
    }

    // Generate IR
    IrProgram irProgram;
    try {
      irProgram = IrPipeline.generate(result.globalScope(), result.parseTree());
    } catch (Exception e) {
      throw new AssertionError("IR generation failed for " + programName + ": "
          + e.getMessage(), e);
    }

    // Verify IR
    try {
      IrPipeline.verify(irProgram);
    } catch (hr.fer.ppj.ir.verify.IrVerifier.IrVerificationException e) {
      throw new AssertionError("IR verification failed for " + programName + ": "
          + e.getMessage(), e);
    }

    // Print IR
    String irOutput = IrPipeline.print(irProgram);

    // Write output to compiler-bin/
    Files.createDirectories(OUTPUT_DIR);
    Files.writeString(outputIrFile, irOutput, StandardCharsets.UTF_8);

    // Compare with expected IR using normalized comparison
    String expected = Files.readString(expectedIrFile, StandardCharsets.UTF_8);
    if (!IrNormalizer.equalsNormalized(expected, irOutput)) {
      String diffHint = IrNormalizer.generateDiffHint(expected, irOutput);
      throw new AssertionError("IR output does not match expected for " + programName
          + "\n\n" + diffHint
          + "\n\nGenerated IR written to: " + outputIrFile);
    }
  }

  /**
   * Discovers all program*.c files in examples/valid and generates test methods.
   */
  @Test
  @Disabled("Use testAllPrograms() instead")
  public void discoverAndTest() throws IOException {
    if (!Files.exists(EXAMPLES_DIR) || !Files.isDirectory(EXAMPLES_DIR)) {
      throw new AssertionError("Examples directory not found: " + EXAMPLES_DIR
          + " (project root: " + PROJECT_ROOT + ")");
    }

    List<String> programs = new ArrayList<>();
    try (Stream<Path> paths = Files.list(EXAMPLES_DIR)) {
      paths.filter(p -> p.toString().endsWith(".c"))
          .map(p -> p.getFileName().toString().replace(".c", ""))
          .filter(name -> name.startsWith("program"))
          .sorted()
          .forEach(programs::add);
    }

    if (programs.isEmpty()) {
      throw new AssertionError("No program*.c files found in " + EXAMPLES_DIR);
    }

    int passed = 0;
    int failed = 0;
    List<String> failures = new ArrayList<>();

    System.out.println("Running validation for " + programs.size() + " programs...");
    System.out.println("Project root: " + PROJECT_ROOT);
    System.out.println("Examples dir: " + EXAMPLES_DIR);
    System.out.println("Output dir: " + OUTPUT_DIR);
    System.out.println();

    for (String program : programs) {
      try {
        validateProgram(program);
        passed++;
        System.out.println("PASS: " + program);
      } catch (AssertionError e) {
        failed++;
        failures.add(program);
        System.err.println("FAIL: " + program + " - " + e.getMessage());
      } catch (Exception e) {
        failed++;
        failures.add(program);
        System.err.println("ERROR: " + program + " - " + e.getMessage());
        e.printStackTrace(System.err);
      }
    }

    System.out.println("\nSummary:");
    System.out.println("  Total:  " + programs.size());
    System.out.println("  Passed: " + passed);
    System.out.println("  Failed: " + failed);

    if (failed > 0) {
      System.err.println("\nFailed programs:");
      for (String program : failures) {
        System.err.println("  - " + program);
      }
      throw new AssertionError(failed + " program(s) failed validation");
    }
  }

  /**
   * Test all programs in examples/valid.
   * 
   * <p>This method validates all program*.c files found in examples/valid.
   */
  @Test
  public void testAllPrograms() throws IOException {
    discoverAndTest();
  }
}
