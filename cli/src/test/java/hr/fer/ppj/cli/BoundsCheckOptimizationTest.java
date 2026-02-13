package hr.fer.ppj.cli;

import hr.fer.ppj.codegen.frisc.FriscCodeGenerator;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundsCheckOptimizationTest {

  private final FriscCodeGenerator generator = new FriscCodeGenerator();

  @Test
  void skipsBoundsCheckForCompileTimeInBoundsConstantIndex() throws Exception {
    Path output = Files.createTempFile("frisc-bounds-const", ".a.out");
    generator.generate(constantIndexIr(), output, "program.c");

    String asm = Files.readString(output);
    assertFalse(asm.contains("L_BOUNDS_ERROR"));
    assertFalse(asm.contains("JP_SLT L_BOUNDS_ERROR"));
    assertFalse(asm.contains("JP_SGE L_BOUNDS_ERROR"));
  }

  @Test
  void keepsBoundsCheckForNonConstantIndex() throws Exception {
    Path output = Files.createTempFile("frisc-bounds-variable", ".a.out");
    generator.generate(variableIndexIr(), output, "program.c");

    String asm = Files.readString(output);
    assertTrue(asm.contains("L_BOUNDS_ERROR"));
    assertTrue(asm.contains("JP_SLT L_BOUNDS_ERROR") || asm.contains("JP_SGE L_BOUNDS_ERROR"));
  }

  private String constantIndexIr() {
    return String.join("\n",
        ".program",
        "",
        ".globals",
        "  global arr:array<int32,4> = { #3:int32, #7:int32, #11:int32, #13:int32 } : array<int32,4>",
        "",
        ".func main():int32",
        "  .frame locals=0 bytes align=4",
        "  .slots",
        "  .blocks",
        "  L0:",
        "    t0 = addr_of_symbol global:arr",
        "    t1 = addr_index t0, #2:int32, 4",
        "    t2 = load t1 : int32",
        "    ret t2",
        ".endfunc",
        "",
        ".endprogram",
        "");
  }

  private String variableIndexIr() {
    return String.join("\n",
        ".program",
        "",
        ".globals",
        "  global arr:array<int32,4> = { #3:int32, #7:int32, #11:int32, #13:int32 } : array<int32,4>",
        "",
        ".func main():int32",
        "  .frame locals=4 bytes align=4",
        "  .slots",
        "    local i@0:int32",
        "  .blocks",
        "  L0:",
        "    t0 = addr_of_symbol local:i",
        "    store t0, #2:int32 : int32",
        "    t1 = load t0 : int32",
        "    t2 = addr_of_symbol global:arr",
        "    t3 = addr_index t2, t1, 4",
        "    t4 = load t3 : int32",
        "    ret t4",
        ".endfunc",
        "",
        ".endprogram",
        "");
  }
}
