package hr.fer.ppj.semantics.symbols;

import hr.fer.ppj.semantics.types.FunctionType;
import java.util.Objects;

/**
 * Represents a function declaration or definition in the symbol table.
 *
 * <p>This record stores information about a function:
 * <ul>
 *   <li><strong>Name</strong>: The identifier used to call the function</li>
 *   <li><strong>Type</strong>: The function signature (return type and parameter types)</li>
 *   <li><strong>Definition status</strong>: Whether the function body has been seen</li>
 * </ul>
 *
 * <p>Function declarations vs definitions:
 * <ul>
 *   <li><strong>Declaration</strong>: {@code defined = false} - function signature only,
 *       no body provided. Multiple declarations are allowed if they match.</li>
 *   <li><strong>Definition</strong>: {@code defined = true} - function signature with body.
 *       Only one definition is allowed per function.</li>
 * </ul>
 *
 * <p>Function symbols are created during semantic analysis when processing:
 * <ul>
 *   <li>Function declarations: {@code int f(int x);}</li>
 *   <li>Function definitions: {@code int f(int x) { return x; }}</li>
 * </ul>
 *
 * <p>Function type information:
 * <ul>
 *   <li>The function type includes return type and parameter types</li>
 *   <li>Arrays decay to pointers in function parameters</li>
 *   <li>Void parameter list means no parameters</li>
 *   <li>Return type can be any type including void</li>
 * </ul>
 *
 * <p>Global constraint verification:
 * <ul>
 *   <li>All declared functions must be defined (checked after semantic analysis)</li>
 *   <li>The {@code main} function must exist and have signature {@code int main(void)}</li>
 * </ul>
 *
 * @param name the identifier name of the function (must not be null)
 * @param type the function signature including return type and parameters (must not be null)
 * @param defined {@code true} if the function body has been seen (definition),
 *                {@code false} if only the signature has been seen (declaration)
 *
 * @see Symbol for the base symbol interface
 * @see VariableSymbol for variable symbols
 * @see FunctionType for function type representation
 * @see SymbolTable for symbol table operations
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record FunctionSymbol(String name, FunctionType type, boolean defined) implements Symbol {

  /**
   * Constructs a function symbol.
   *
   * @param name the identifier name (must not be null)
   * @param type the function signature (must not be null)
   * @param defined whether the function body has been seen
   * @throws NullPointerException if name or type is null
   */
  public FunctionSymbol {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
  }

  /**
   * Creates a new function symbol with the definition status set to {@code true}.
   *
   * <p>This method is used when a function definition is processed after a declaration
   * has already been registered. It creates a new symbol with the same name and type
   * but with {@code defined = true}.
   *
   * @return a new function symbol with {@code defined = true}
   */
  public FunctionSymbol markDefined() {
    return new FunctionSymbol(name, type, true);
  }
}

