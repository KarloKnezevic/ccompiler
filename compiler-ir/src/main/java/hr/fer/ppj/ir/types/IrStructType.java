package hr.fer.ppj.ir.types;

import java.util.Objects;

/**
 * Struct type: struct Name.
 *
 * <p>Struct types are referenced by name. The actual struct definition (with fields)
 * is stored separately in the IR program.
 *
 * @param name the struct tag name
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IrStructType(String name) implements IrType {

  public IrStructType {
    Objects.requireNonNull(name, "name must not be null");
  }

  @Override
  public String toIrString() {
    return "struct " + name;
  }
}

