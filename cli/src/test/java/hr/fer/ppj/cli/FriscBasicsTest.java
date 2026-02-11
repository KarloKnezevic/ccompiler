package hr.fer.ppj.cli;

import hr.fer.ppj.cli.support.TestCompilationPipeline;
import hr.fer.ppj.codegen.frisc.FriscCodeGenerator;
import hr.fer.ppj.codegen.frisc.ir.IrTextParser;
import hr.fer.ppj.cli.ir.IrInterpreter;
import hr.fer.ppj.cli.ir.IrInterpreterOptions;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class FriscBasicsTest {

  @Test
  public void runBasicsPrograms() throws Exception {
    String dir = System.getProperty("ppj.testDir", "basics");
    runDirectory(dir);
  }

  public void runDirectory(String directoryName) throws Exception {
    Path root = findProjectRoot();
    Path directory = root.resolve("examples/valid").resolve(directoryName);
    Path outputDir = root.resolve("compiler-bin");

    if (!Files.exists(directory)) {
      fail("Directory not found: " + directory);
    }

    List<Path> programDirs;
    try (Stream<Path> stream = Files.list(directory)) {
      programDirs = stream
          .filter(Files::isDirectory)
          .sorted(Comparator.comparing(Path::toString))
          .collect(Collectors.toList());
    }

    TestCompilationPipeline pipeline = new TestCompilationPipeline();
    FriscCodeGenerator codegen = new FriscCodeGenerator();
    FriscRunner runner = new FriscRunner(root);

    boolean unsignedExit = "basics".equals(directoryName);
    boolean q16Expected = "arithmetic_float".equals(directoryName);

    for (Path dir : programDirs) {
      Path sourceFile = dir.resolve("program.c");
      Path expectedFile = dir.resolve("expected.txt");
      if (!Files.exists(sourceFile)) {
        fail("Missing program.c in " + dir);
      }
      cleanOutputDirectory(outputDir);
      Files.createDirectories(outputDir);

      if (!Files.exists(expectedFile)) {
        boolean failed = false;
        try {
          pipeline.compile(sourceFile);
        } catch (Exception ex) {
          failed = true;
        }
        if (!failed) {
          fail("Expected compilation to fail for " + dir + " (missing expected.txt)");
        }
        continue;
      }

      TestCompilationPipeline.CompilationResult result = pipeline.compile(sourceFile);
      Files.writeString(outputDir.resolve("intermediate.ir"), result.irString(), StandardCharsets.UTF_8);

      Path friscFile = outputDir.resolve("a.out");
      codegen.generate(result.irString(), friscFile, sourceFile.getFileName().toString());

      FriscRunner.Result run = runner.run(friscFile);
      if (!run.success()) {
        System.err.println("FRISC simulator failed for " + dir.getFileName());
        System.err.println("a.out: " + friscFile.toAbsolutePath());
        System.err.println("Simulator output:\n" + run.output());
        fail("Simulator failed: " + run.errorMessage());
      }

      String expectedRaw = Files.readString(expectedFile, StandardCharsets.UTF_8).trim();
      String expected = expectedRaw;
      if (q16Expected) {
        IrTextParser parser = new IrTextParser();
        IrInterpreter interpreter =
            new IrInterpreter(parser.parse(result.irString()), IrInterpreterOptions.defaults());
        expected = String.valueOf(interpreter.executeMain().returnValue());
      }
      String actual = run.r6Value().trim();
      String comparedActual = actual;
      if (unsignedExit) {
        try {
          int actualValue = Integer.parseInt(actual);
          comparedActual = String.valueOf(actualValue & 0xFF);
        } catch (NumberFormatException ignored) {
          // Leave as-is if parsing fails; mismatch will be reported.
        }
      }
      if (!expected.equals(comparedActual)) {
        System.err.println("Output mismatch for " + dir.getFileName());
        System.err.println("Expected: " + expected);
        System.err.println("Actual: " + actual);
        if (unsignedExit && !actual.equals(comparedActual)) {
          System.err.println("Actual (unsigned): " + comparedActual);
        }
        System.err.println("Simulator output:\n" + run.output());
      }
      assertEquals(expected, comparedActual, "Unexpected output for " + dir.getFileName());
    }
  }

  private static void cleanOutputDirectory(Path outputDir) throws IOException {
    if (!Files.exists(outputDir)) {
      return;
    }
    try (Stream<Path> walk = Files.walk(outputDir)) {
      List<Path> paths = walk.sorted(Comparator.reverseOrder()).collect(Collectors.toList());
      for (Path path : paths) {
        if (Files.isDirectory(path)) {
          if (!path.equals(outputDir)) {
            Files.deleteIfExists(path);
          }
        } else {
          Files.deleteIfExists(path);
        }
      }
    }
  }

  private static Path findProjectRoot() {
    Path current = Paths.get("").toAbsolutePath().normalize();
    Path testPath = current;
    for (int i = 0; i < 10; i++) {
      if (Files.exists(testPath.resolve("pom.xml")) && Files.exists(testPath.resolve("examples"))) {
        return testPath;
      }
      Path parent = testPath.getParent();
      if (parent == null) {
        break;
      }
      testPath = parent;
    }
    return current;
  }
}
