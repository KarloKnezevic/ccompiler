package hr.fer.ppj.cli;

import hr.fer.ppj.cli.args.ArgumentParser;
import hr.fer.ppj.cli.args.CliOptions;
import hr.fer.ppj.cli.pipeline.PipelineStage;
import hr.fer.ppj.opt.api.OptimizationLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArgumentParserTest {

  @Test
  void parsesRunIrCommand() {
    ArgumentParser parser = new ArgumentParser();
    ArgumentParser.ParseResult result =
        parser.parse(new String[] {"run-ir", "--ir-step-limit", "1000", "--trace-ir", "file.ir"});

    assertTrue(result.success());
    CliOptions options = result.options();
    assertNotNull(options);
    assertTrue(options.runIrCommand());
    assertEquals("file.ir", options.irFile().toString());
    assertEquals(1000, options.irStepLimit());
    assertTrue(options.irTrace());
  }

  @Test
  void parsesCompilationFlags() {
    ArgumentParser parser = new ArgumentParser();
    ArgumentParser.ParseResult result =
        parser.parse(new String[] {"--frisc", "--O1", "--dump-ir", "program.c"});

    assertTrue(result.success());
    CliOptions options = result.options();
    assertNotNull(options);
    assertFalse(options.runIrCommand());
    assertTrue(options.requestedStages().contains(PipelineStage.FRISC));
    assertEquals("program.c", options.sourceFile().toString());
    assertEquals(OptimizationLevel.O1, options.optimizationLevel());
    assertTrue(options.dumpIr());
  }
}
