package hr.fer.ppj.codegen.frisc.ir;

/**
 * Public entry point for parsing typed IR text into a structured model.
 */
public final class IrTextParser {
  private final IrProgramParser programParser = new IrProgramParser();

  public IrProgramModel parse(String text) {
    return programParser.parse(text);
  }
}
