package hr.fer.ppj.cli.commands;

import hr.fer.ppj.cli.args.CliOptions;
import hr.fer.ppj.cli.pipeline.CompilationPipeline;
import hr.fer.ppj.ir.util.IrNormalizer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Command for running golden IR tests.
 *
 * <p>Usage: {@code ir-test --golden <dir> [--out <outDir>] [--recursive]}
 *
 * <p>Discovers test pairs by convention: for each {@code *.c} file, expects a sibling
 * {@code *.ir} file with the same basename. Compares generated IR against golden IR
 * using normalized comparison (ignores blank lines and trailing whitespace).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IrTestCommand implements Command {

  private final CompilationPipeline pipeline;

  public IrTestCommand() {
    this.pipeline = new CompilationPipeline();
  }

  public IrTestCommand(CompilationPipeline pipeline) {
    this.pipeline = pipeline;
  }

  @Override
  public int execute(CliOptions options) {
    Path goldenDir = options.goldenDir()
        .orElseThrow(() -> new IllegalArgumentException("Golden directory is required"));
    Path outputDir = options.outputDir();
    boolean recursive = options.recursive();

    if (!Files.exists(goldenDir)) {
      System.err.println("Error: Golden directory not found: " + goldenDir);
      return 1;
    }

    if (!Files.isDirectory(goldenDir)) {
      System.err.println("Error: Golden path is not a directory: " + goldenDir);
      return 1;
    }

    try {
      ensureOutputDirectory(outputDir);
      List<TestCase> testCases = discoverTestCases(goldenDir, recursive);

      if (testCases.isEmpty()) {
        System.err.println("No test cases found in: " + goldenDir);
        return 1;
      }

      System.err.println("Found " + testCases.size() + " test cases in: " + goldenDir);
      System.err.println("Output directory: " + outputDir);
      System.err.println();

      return runTests(testCases, outputDir);

    } catch (Exception e) {
      System.err.println("Error: " + e.getMessage());
      e.printStackTrace(System.err);
      return 1;
    }
  }

  private List<TestCase> discoverTestCases(Path goldenDir, boolean recursive) throws IOException {
    List<TestCase> testCases = new ArrayList<>();

    try (Stream<Path> files = recursive
        ? Files.walk(goldenDir)
        : Files.list(goldenDir)) {

      files.filter(p -> p.toString().endsWith(".c"))
          .filter(Files::isRegularFile)
          .sorted(Comparator.comparing(p -> p.getFileName().toString()))
          .forEach(sourceFile -> {
            Path goldenIrFile = getGoldenIrPath(sourceFile);
            if (Files.exists(goldenIrFile)) {
              testCases.add(new TestCase(sourceFile, goldenIrFile));
            } else {
              System.err.println("Warning: No golden IR file for: " + sourceFile);
            }
          });
    }

    return testCases;
  }

  private Path getGoldenIrPath(Path sourceFile) {
    String filename = sourceFile.getFileName().toString();
    String basename = filename.substring(0, filename.length() - 2); // Remove ".c"
    return sourceFile.getParent().resolve(basename + ".ir");
  }

  private int runTests(List<TestCase> testCases, Path outputDir) {
    int passed = 0;
    int failed = 0;
    List<TestFailure> failures = new ArrayList<>();

    for (TestCase testCase : testCases) {
      TestResult result = runSingleTest(testCase, outputDir);
      if (result.passed()) {
        passed++;
        System.out.println("PASS: " + testCase.sourceFile().getFileName());
      } else {
        failed++;
        System.out.println("FAIL: " + testCase.sourceFile().getFileName());
        failures.add(new TestFailure(testCase, result.diffHint()));
      }
    }

    System.out.println();
    System.out.println("============================================");
    System.out.printf("Results: %d passed, %d failed, %d total%n", passed, failed, testCases.size());
    System.out.println("============================================");

    if (!failures.isEmpty()) {
      System.out.println();
      System.out.println("FAILURES:");
      System.out.println();
      for (TestFailure failure : failures) {
        System.out.println("--- " + failure.testCase().sourceFile().getFileName() + " ---");
        System.out.println(failure.diffHint());
        System.out.println();
      }
    }

    return failed > 0 ? 1 : 0;
  }

  private TestResult runSingleTest(TestCase testCase, Path outputDir) {
    try {
      // Compile the source file to IR
      var result = pipeline.compile(testCase.sourceFile());
      String generatedIr = result.irString();

      // Write generated IR to output directory for inspection
      String basename = testCase.sourceFile().getFileName().toString();
      basename = basename.substring(0, basename.length() - 2);
      Path generatedIrPath = outputDir.resolve(basename + ".generated.ir");
      Files.writeString(generatedIrPath, generatedIr);

      // Read golden IR
      String goldenIr = Files.readString(testCase.goldenIrFile());

      // Compare using normalized comparison
      if (IrNormalizer.equalsNormalized(goldenIr, generatedIr)) {
        return new TestResult(true, null);
      } else {
        String diffHint = IrNormalizer.generateDiffHint(goldenIr, generatedIr);
        return new TestResult(false, diffHint != null ? diffHint : "Unknown difference");
      }

    } catch (hr.fer.ppj.semantics.errors.SemanticException e) {
      return new TestResult(false, "Semantic error: " + e.getMessage());
    } catch (Exception e) {
      return new TestResult(false, "Compilation error: " + e.getMessage());
    }
  }

  private void ensureOutputDirectory(Path outputDir) throws IOException {
    if (!Files.exists(outputDir)) {
      Files.createDirectories(outputDir);
    }
  }

  private record TestCase(Path sourceFile, Path goldenIrFile) {}
  private record TestResult(boolean passed, String diffHint) {}
  private record TestFailure(TestCase testCase, String diffHint) {}
}
