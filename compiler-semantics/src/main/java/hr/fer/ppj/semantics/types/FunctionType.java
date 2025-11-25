package hr.fer.ppj.semantics.types;

import java.util.List;
import java.util.Objects;

/**
 * Represents a function prototype consisting of return type and ordered parameter types.
 *
 * @param returnType     the return type
 * @param parameterTypes ordered parameter types
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record FunctionType(Type returnType, List<Type> parameterTypes) implements Type {

  public FunctionType {
    Objects.requireNonNull(returnType, "returnType must not be null");
    Objects.requireNonNull(parameterTypes, "parameterTypes must not be null");
    parameterTypes = List.copyOf(parameterTypes);
  }

  @Override
  public boolean isScalar() {
    return false;
  }

  public boolean isVoidReturn() {
    return returnType.isVoid();
  }
}

