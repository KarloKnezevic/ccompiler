package hr.fer.ppj.cli;

import hr.fer.ppj.cli.support.TestCompilationPipeline;
import hr.fer.ppj.codegen.frisc.FriscCodeGenerator;
import hr.fer.ppj.codegen.frisc.ir.IrTextParser;
import hr.fer.ppj.cli.ir.IrInterpreter;
import hr.fer.ppj.cli.ir.IrInterpreterOptions;
import hr.fer.ppj.ir.IrPipeline;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.opt.api.IrOptimizer;
import hr.fer.ppj.opt.api.OptimizationOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizationRegressionTest {

  @Test
  void optimizedAndUnoptimizedFriscMatchForRealWorldTargets() throws Exception {
    Path root = findProjectRoot();
    List<String> targets = List.of(
        "real_prime_sieve",
        "real_perceptron_sigmoid",
        "real_checksum_crc",
        "real_bfs_shortest_path");

    TestCompilationPipeline pipeline = new TestCompilationPipeline();
    FriscCodeGenerator codeGenerator = new FriscCodeGenerator();
    IrOptimizer optimizer = new IrOptimizer();
    IrTextParser irTextParser = new IrTextParser();
    FriscRunner runner = new FriscRunner(root);

    Path tempDir = Files.createTempDirectory("ppj-opt-regression-");

    for (String target : targets) {
      Path sourceFile = root.resolve("examples/real_world").resolve(target).resolve("program.c");
      TestCompilationPipeline.CompilationResult compilation = pipeline.compile(sourceFile);

      IrProgram unoptimizedProgram = compilation.irProgram();
      IrProgram optimizedProgram = optimizer.optimize(unoptimizedProgram, OptimizationOptions.O1);

      String unoptimizedIr = IrPipeline.print(unoptimizedProgram);
      String optimizedIr = IrPipeline.print(optimizedProgram);
      int unoptimizedIrResult = new IrInterpreter(
          irTextParser.parse(unoptimizedIr),
          IrInterpreterOptions.defaults()).executeMain().returnValue();
      int optimizedIrResult = new IrInterpreter(
          irTextParser.parse(optimizedIr),
          IrInterpreterOptions.defaults()).executeMain().returnValue();
      assertEquals(unoptimizedIrResult, optimizedIrResult, "Optimized IR result differs for " + target);

      Path unoptimizedFrisc = tempDir.resolve(target + ".unoptimized.a.out");
      Path optimizedFrisc = tempDir.resolve(target + ".optimized.a.out");

      codeGenerator.generate(unoptimizedIr, unoptimizedFrisc, sourceFile.getFileName().toString());
      codeGenerator.generate(optimizedIr, optimizedFrisc, sourceFile.getFileName().toString());

      FriscRunner.Result unoptimizedRun = runner.run(unoptimizedFrisc, Duration.ofSeconds(30));
      FriscRunner.Result optimizedRun = runner.run(optimizedFrisc, Duration.ofSeconds(30));

      if (unoptimizedRun.success() && optimizedRun.success()) {
        assertEquals(
            unoptimizedRun.r6Value().trim(),
            optimizedRun.r6Value().trim(),
            "Optimized FRISC result differs for " + target);
      } else {
        String unoptimizedError = unoptimizedRun.errorMessage();
        String optimizedError = optimizedRun.errorMessage();
        boolean bothTimeout =
            unoptimizedError != null
                && optimizedError != null
                && unoptimizedError.startsWith("Execution timed out")
                && optimizedError.startsWith("Execution timed out");
        assertTrue(
            bothTimeout,
            "FRISC parity failure for " + target
                + " (unoptimized: " + unoptimizedError
                + ", optimized: " + optimizedError + ")");
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
