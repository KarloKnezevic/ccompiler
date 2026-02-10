package hr.fer.ppj.cli.ir;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.ir.IrTextParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Executes a standalone IR file via interpreter.
 */
public final class IrCommandRunner {

  public IrExecutionResult run(Path irFile, IrInterpreterOptions options) throws Exception {
    Objects.requireNonNull(irFile, "irFile must not be null");
    Objects.requireNonNull(options, "options must not be null");

    String irText = Files.readString(irFile, StandardCharsets.UTF_8);
    IrProgramModel model = new IrTextParser().parse(irText);
    IrInterpreter interpreter = new IrInterpreter(model, options);
    return interpreter.executeMain();
  }
}
