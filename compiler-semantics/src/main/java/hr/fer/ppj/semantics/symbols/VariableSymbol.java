package hr.fer.ppj.semantics.symbols;

import hr.fer.ppj.semantics.types.Type;
import java.util.Objects;

/**
 * Represents a variable (or constant) declaration.
 *
 * @param name    identifier
 * @param type    declared type
 * @param isConst {@code true} for const-qualified entities
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record VariableSymbol(String name, Type type, boolean isConst) implements Symbol {

  public VariableSymbol {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
  }
}

