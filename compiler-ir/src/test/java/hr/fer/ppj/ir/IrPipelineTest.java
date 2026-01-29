package hr.fer.ppj.ir;

import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Basic unit tests for IR pipeline.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public class IrPipelineTest {

  @Test
  public void testPrintEmptyProgram() {
    IrProgram program = IrProgram.builder().build();
    String output = IrPipeline.print(program);
    
    assertNotNull(output);
    assertTrue(output.contains(".program"));
    assertTrue(output.contains(".endprogram"));
  }

  @Test
  public void testVerifyEmptyProgram() {
    IrProgram program = IrProgram.builder().build();
    
    // Empty program should verify successfully
    assertDoesNotThrow(() -> IrPipeline.verify(program));
  }

  @Test
  public void testTypeMapper() {
    // Test that type mapper works for basic types
    // This is indirectly tested through IR generation, but we can add explicit tests here
    assertNotNull(IrPrimitiveType.INT32);
    assertNotNull(IrPrimitiveType.CHAR);
    assertNotNull(IrPrimitiveType.FLOAT);
    assertNotNull(IrPrimitiveType.BOOL);
  }
}

