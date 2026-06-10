package hr.fer.ppj.cli.vm;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.codegen.frisc.ir.IrTextParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Parses a standalone {@code .ir} file, lowers it to bytecode, and either runs it on the
 * {@link BytecodeVm} or disassembles it.
 */
public final class VmCommandRunner {

  public VmExecutionResult run(Path irFile, VmExecutionOptions options) throws Exception {
    IrProgramModel model = parse(irFile);
    Bytecode.Program bytecode = new IrToBytecodeCompiler(model).compile();
    return new BytecodeVm(model, bytecode, options).execute();
  }

  public String disassemble(Path irFile) throws Exception {
    IrProgramModel model = parse(irFile);
    Bytecode.Program bytecode = new IrToBytecodeCompiler(model).compile();
    return new BytecodeDisassembler().disassemble(bytecode);
  }

  private IrProgramModel parse(Path irFile) throws Exception {
    Objects.requireNonNull(irFile, "irFile must not be null");
    String irText = Files.readString(irFile, StandardCharsets.UTF_8);
    return new IrTextParser().parse(irText);
  }
}
