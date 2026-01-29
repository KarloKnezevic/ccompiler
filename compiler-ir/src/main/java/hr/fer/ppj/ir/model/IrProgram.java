package hr.fer.ppj.ir.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Complete IR program: .program ... .endprogram.
 *
 * <p>Programs contain:
 * <ul>
 *   <li>Global variable declarations</li>
 *   <li>Struct type definitions</li>
 *   <li>Function definitions</li>
 * </ul>
 *
 * @param globals the global variable declarations
 * @param structDefs the struct type definitions (by name)
 * @param functions the function definitions
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IrProgram(
    List<IrGlobalVar> globals, Map<String, IrStructDef> structDefs, List<IrFunction> functions) {

  public IrProgram {
    Objects.requireNonNull(globals, "globals must not be null");
    Objects.requireNonNull(structDefs, "structDefs must not be null");
    Objects.requireNonNull(functions, "functions must not be null");
    globals = List.copyOf(globals);
    structDefs = Map.copyOf(structDefs);
    functions = List.copyOf(functions);
  }

  /**
   * Creates a new program builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for constructing programs incrementally.
   */
  public static final class Builder {
    private final List<IrGlobalVar> globals = new ArrayList<>();
    private final Map<String, IrStructDef> structDefs = new LinkedHashMap<>();
    private final List<IrFunction> functions = new ArrayList<>();

    public Builder addGlobal(IrGlobalVar global) {
      globals.add(Objects.requireNonNull(global, "global must not be null"));
      return this;
    }

    public Builder addStructDef(IrStructDef structDef) {
      String name = Objects.requireNonNull(structDef, "structDef must not be null").name();
      if (structDefs.containsKey(name)) {
        throw new IllegalArgumentException("Struct " + name + " already defined");
      }
      structDefs.put(name, structDef);
      return this;
    }

    public Builder addFunction(IrFunction function) {
      functions.add(Objects.requireNonNull(function, "function must not be null"));
      return this;
    }

    public IrProgram build() {
      return new IrProgram(globals, structDefs, functions);
    }
  }
}

