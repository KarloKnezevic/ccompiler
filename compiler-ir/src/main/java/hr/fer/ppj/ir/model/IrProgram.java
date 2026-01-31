package hr.fer.ppj.ir.model;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents a complete IR program.
 *
 * <p>This record corresponds to the Program production in the IR grammar
 * ({@code config/ir_definition.txt}):
 *
 * <pre>
 * Program
 *   ::= ".program" NL
 *       { TopLevel }
 *       ".endprogram" NL? ;
 *
 * TopLevel
 *   ::= GlobalDecl | TypeDef | FuncDef ;
 * </pre>
 *
 * <h3>Structure Invariants</h3>
 * <ul>
 *   <li>All lists are immutable after construction</li>
 *   <li>Struct definitions are keyed by name (no duplicates)</li>
 *   <li>Functions may have any number of entries (including zero)</li>
 *   <li>Globals may be empty if no global variables exist</li>
 * </ul>
 *
 * <h3>Ordering</h3>
 * <p>The output ordering is:
 * <ol>
 *   <li>Struct type definitions ({@code .type struct ...})</li>
 *   <li>Global variable declarations ({@code .globals})</li>
 *   <li>Function definitions ({@code .func ... .endfunc})</li>
 * </ol>
 *
 * @param globals the global variable declarations
 * @param structDefs the struct type definitions indexed by name
 * @param functions the function definitions
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @see IrGlobalVar
 * @see IrStructDef
 * @see IrFunction
 */
public record IrProgram(
    List<IrGlobalVar> globals,
    Map<String, IrStructDef> structDefs,
    List<IrFunction> functions) {

  /**
   * Creates an IR program with defensive copying of collections.
   *
   * @throws NullPointerException if any argument is null
   */
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
   *
   * @return a new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Builder for constructing IR programs incrementally.
   *
   * <p>Usage:
   * <pre>{@code
   * IrProgram program = IrProgram.builder()
   *     .addStructDef(pointStruct)
   *     .addGlobal(globalVar)
   *     .addFunction(mainFunc)
   *     .build();
   * }</pre>
   */
  public static final class Builder {
    private final List<IrGlobalVar> globals = new ArrayList<>();
    private final Map<String, IrStructDef> structDefs = new LinkedHashMap<>();
    private final List<IrFunction> functions = new ArrayList<>();

    /**
     * Adds a global variable declaration.
     *
     * @param global the global variable
     * @return this builder
     * @throws NullPointerException if global is null
     */
    public Builder addGlobal(IrGlobalVar global) {
      globals.add(Objects.requireNonNull(global, "global must not be null"));
      return this;
    }

    /**
     * Adds a struct type definition.
     *
     * @param structDef the struct definition
     * @return this builder
     * @throws NullPointerException if structDef is null
     * @throws IllegalArgumentException if a struct with the same name already exists
     */
    public Builder addStructDef(IrStructDef structDef) {
      String name = Objects.requireNonNull(structDef, "structDef must not be null").name();
      if (structDefs.containsKey(name)) {
        throw new IllegalArgumentException("Struct " + name + " already defined");
      }
      structDefs.put(name, structDef);
      return this;
    }

    /**
     * Adds a function definition.
     *
     * @param function the function
     * @return this builder
     * @throws NullPointerException if function is null
     */
    public Builder addFunction(IrFunction function) {
      functions.add(Objects.requireNonNull(function, "function must not be null"));
      return this;
    }

    /**
     * Builds the immutable IR program.
     *
     * @return the constructed program
     */
    public IrProgram build() {
      return new IrProgram(globals, structDefs, functions);
    }
  }
}
