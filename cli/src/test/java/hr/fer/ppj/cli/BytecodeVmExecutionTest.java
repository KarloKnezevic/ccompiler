package hr.fer.ppj.cli;

import hr.fer.ppj.cli.ir.IrInterpreter;
import hr.fer.ppj.cli.ir.IrInterpreterOptions;
import hr.fer.ppj.cli.vm.Bytecode;
import hr.fer.ppj.cli.vm.BytecodeVm;
import hr.fer.ppj.cli.vm.IrToBytecodeCompiler;
import hr.fer.ppj.cli.vm.VmExecutionOptions;
import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.ir.IrTextParser;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates the bytecode VM as a second back end for the typed IR.
 *
 * <p>The decisive test is differential: the VM and the IR interpreter consume the same
 * {@code IrProgramModel} and must return the same value for every program. Any disagreement points
 * at a bug in exactly one of the two back ends.
 */
class BytecodeVmExecutionTest {

  @Test
  void matchesExpectedReturnValuesOnAnchorPrograms() throws Exception {
    Path root = findProjectRoot();
    Map<String, Integer> expected = Map.of(
        "real_prime_sieve", 46,
        "math_fibonacci_iter", 6765,
        "real_checksum_crc", 142,
        "real_bfs_shortest_path", 14);

    for (Map.Entry<String, Integer> entry : expected.entrySet()) {
      Path irFile = root.resolve("examples/real_world").resolve(entry.getKey()).resolve("program.ir");
      int produced = runOnVm(irFile);
      assertEquals(entry.getValue().intValue(), produced, "VM mismatch for " + entry.getKey());
    }
  }

  @Test
  void agreesWithInterpreterAcrossTheRealWorldSuite() throws Exception {
    Path root = findProjectRoot();
    Path realWorld = root.resolve("examples/real_world");

    List<Path> irFiles = new ArrayList<>();
    try (Stream<Path> stream = Files.walk(realWorld)) {
      stream.filter(p -> p.getFileName().toString().equals("program.ir")).forEach(irFiles::add);
    }
    assertTrue(irFiles.size() >= 20, "Expected a substantial real_world IR corpus");

    IrTextParser parser = new IrTextParser();
    for (Path irFile : irFiles) {
      String irText = Files.readString(irFile, StandardCharsets.UTF_8);
      IrProgramModel model = parser.parse(irText);

      int interpreted = new IrInterpreter(model, new IrInterpreterOptions(5_000_000, false))
          .executeMain().returnValue();

      Bytecode.Program bytecode = new IrToBytecodeCompiler(model).compile();
      int onVm = new BytecodeVm(model, bytecode, new VmExecutionOptions(50_000_000L, false))
          .execute().returnValue();

      assertEquals(interpreted, onVm,
          "Interpreter and VM disagree for " + realWorld.relativize(irFile));
    }
  }

  private int runOnVm(Path irFile) throws IOException {
    String irText = Files.readString(irFile, StandardCharsets.UTF_8);
    IrProgramModel model = new IrTextParser().parse(irText);
    Bytecode.Program bytecode = new IrToBytecodeCompiler(model).compile();
    return new BytecodeVm(model, bytecode, VmExecutionOptions.defaults()).execute().returnValue();
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
