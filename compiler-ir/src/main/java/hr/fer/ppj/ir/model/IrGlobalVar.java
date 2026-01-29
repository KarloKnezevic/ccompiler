package hr.fer.ppj.ir.model;

import hr.fer.ppj.ir.types.IrType;
import java.util.Objects;

/**
 * Global variable declaration: global name:type [= const].
 *
 * @param name the global variable name
 * @param type the variable type
 * @param initializer optional constant initializer (null if uninitialized)
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IrGlobalVar(String name, IrType type, IrConst initializer) {

  public IrGlobalVar {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
    // initializer can be null (uninitialized global)
  }
}

