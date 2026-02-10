package hr.fer.ppj.cli;

import hr.fer.ppj.cli.ir.IrInterpreter;
import hr.fer.ppj.cli.ir.IrInterpreterOptions;
import hr.fer.ppj.cli.pipeline.CompilationPipeline;
import hr.fer.ppj.codegen.frisc.FriscCodeGenerator;
import hr.fer.ppj.codegen.frisc.ir.IrTextParser;
import hr.fer.ppj.ir.IrPipeline;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrInterpreterExecutionTest {

  @Test
  void executesTargetRealWorldProgramsFromIr() throws Exception {
    Path root = findProjectRoot();
    Map<String, Integer> expectedByProgram = Map.of(
        "real_prime_sieve", 46,
        "real_perceptron_sigmoid", 0,
        "real_checksum_crc", 142,
        "real_bfs_shortest_path", 14);

    IrTextParser parser = new IrTextParser();
    IrInterpreterOptions options = IrInterpreterOptions.defaults();

    for (Map.Entry<String, Integer> entry : expectedByProgram.entrySet()) {
      String programName = entry.getKey();
      Path programDir = root.resolve("examples/real_world").resolve(programName);
      Path irFile = programDir.resolve("program.ir");

      String irText = Files.readString(irFile, StandardCharsets.UTF_8);
      int interpreted = new IrInterpreter(parser.parse(irText), options).executeMain().returnValue();

      assertEquals(entry.getValue().intValue(), interpreted, "IR interpreter mismatch for " + programName);
    }
  }

  @Test
  void compilesHexLiteralToCanonicalIrAndRunsFrisc() throws Exception {
    Path root = findProjectRoot();
    Path tempDir = Files.createTempDirectory("ppj-hex-literal-");
    Path sourceFile = tempDir.resolve("hex_literal_program.c");

    String source = String.join(System.lineSeparator(),
        "int main(void)",
        "{",
        "    return 0x2A;",
        "}",
        "");
    Files.writeString(sourceFile, source, StandardCharsets.UTF_8);

    CompilationPipeline pipeline = new CompilationPipeline();
    CompilationPipeline.CompilationResult result = pipeline.compile(sourceFile);

    IrPipeline.verify(result.irProgram());
    assertTrue(result.irString().contains("#42:int32"), "IR should canonicalize hex literal to decimal");

    Path friscFile = tempDir.resolve("a.frisc");
    new FriscCodeGenerator().generate(result.irString(), friscFile, sourceFile.getFileName().toString());

    FriscRunner.Result runResult = new FriscRunner(root).run(friscFile);
    assertTrue(runResult.success(), "FRISC execution failed: " + runResult.output());
    assertEquals("42", runResult.r6Value().trim(), "Unexpected return value from FRISC");
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
