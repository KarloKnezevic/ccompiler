package hr.fer.ppj.cli;

import hr.fer.ppj.codegen.frisc.FriscCodeGenerator;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntMathEdgeCaseTest {

  @Test
  void divisionIntMinByMinusOneReturnsIntMin() throws Exception {
    runAndAssertR6(
        String.join(System.lineSeparator(),
            ".program",
            "",
            ".func main():int32",
            "  .frame locals=0 bytes align=4",
            "  .slots",
            "  .blocks",
            "  L0:",
            "    t0 = div #-2147483648:int32, #-1:int32 : int32",
            "    ret t0",
            ".endfunc",
            "",
            ".endprogram",
            ""),
        Integer.toString(Integer.MIN_VALUE));
  }

  @Test
  void moduloIntMinByMinusOneReturnsZero() throws Exception {
    runAndAssertR6(
        String.join(System.lineSeparator(),
            ".program",
            "",
            ".func main():int32",
            "  .frame locals=0 bytes align=4",
            "  .slots",
            "  .blocks",
            "  L0:",
            "    t0 = mod #-2147483648:int32, #-1:int32 : int32",
            "    ret t0",
            ".endfunc",
            "",
            ".endprogram",
            ""),
        "0");
  }

  private static void runAndAssertR6(String irText, String expectedR6) throws Exception {
    Path root = findProjectRoot();
    Path tempDir = Files.createTempDirectory("ppj-intmath-edge-");
    Path friscFile = tempDir.resolve("a.out");
    Files.writeString(tempDir.resolve("program.ir"), irText, StandardCharsets.UTF_8);

    new FriscCodeGenerator().generate(irText, friscFile, "edge_case.ir");
    FriscRunner.Result result = new FriscRunner(root).run(friscFile, Duration.ofSeconds(20));

    assertTrue(result.success(), "FRISC run failed: " + result.errorMessage() + "\n" + result.output());
    assertEquals(expectedR6, result.r6Value().trim());
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
