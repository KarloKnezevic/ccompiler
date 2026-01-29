package hr.fer.ppj.ir.types;

/**
 * Primitive types in IR: int32, char, uchar, float, bool.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public enum IrPrimitiveType implements IrType {
  INT32("int32"),
  CHAR("char"),
  UCHAR("uchar"),
  FLOAT("float"),
  BOOL("bool");

  private final String irString;

  IrPrimitiveType(String irString) {
    this.irString = irString;
  }

  @Override
  public String toIrString() {
    return irString;
  }
}

