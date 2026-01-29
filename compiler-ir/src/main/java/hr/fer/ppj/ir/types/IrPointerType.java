package hr.fer.ppj.ir.types;

import java.util.Objects;

/**
 * Pointer type: ptr<T>.
 *
 * @param baseType the type being pointed to
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IrPointerType(IrType baseType) implements IrType {

  public IrPointerType {
    Objects.requireNonNull(baseType, "baseType must not be null");
  }

  @Override
  public String toIrString() {
    return "ptr<" + baseType.toIrString() + ">";
  }
}

