package hr.fer.ppj.semantics.symbols;

import hr.fer.ppj.semantics.types.FunctionType;
import java.util.Objects;

/**
 * Represents a function declaration/definition entry.
 *
 * @param name    identifier
 * @param type    function signature
 * @param defined whether the function body has been seen
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record FunctionSymbol(String name, FunctionType type, boolean defined) implements Symbol {

  public FunctionSymbol {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
  }

  public FunctionSymbol markDefined() {
    return new FunctionSymbol(name, type, true);
  }
}

