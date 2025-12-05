package hr.fer.ppj.semantics.symbols;

import hr.fer.ppj.semantics.types.Type;
import java.util.Objects;

/**
 * Represents a variable or constant declaration in the symbol table.
 *
 * <p>This record stores information about a variable or constant declaration:
 * <ul>
 *   <li><strong>Name</strong>: The identifier used to reference the variable</li>
 *   <li><strong>Type</strong>: The declared type (can be any type including arrays, pointers, structs)</li>
 *   <li><strong>Const qualification</strong>: Whether the variable is const-qualified</li>
 * </ul>
 *
 * <p>Variable symbols are created during semantic analysis when processing:
 * <ul>
 *   <li>Global variable declarations: {@code int global_var;}</li>
 *   <li>Local variable declarations: {@code int local_var;}</li>
 *   <li>Function parameters: {@code void f(int param);}</li>
 *   <li>Const declarations: {@code const int x = 5;}</li>
 * </ul>
 *
 * <p>Type information:
 * <ul>
 *   <li>The type can be any PPJ-C type: primitive, array, pointer, struct, or const-qualified</li>
 *   <li>Arrays are stored as {@code ArrayType}</li>
 *   <li>Pointers are stored as {@code PointerType}</li>
 *   <li>Const-qualified types are wrapped with {@code ConstType}</li>
 * </ul>
 *
 * <p>Const qualification:
 * <ul>
 *   <li>{@code isConst = true}: The variable is const-qualified and cannot be modified</li>
 *   <li>{@code isConst = false}: The variable is mutable</li>
 * </ul>
 *
 * @param name the identifier name of the variable (must not be null)
 * @param type the declared type of the variable (must not be null, cannot be void)
 * @param isConst {@code true} if the variable is const-qualified, {@code false} otherwise
 *
 * @see Symbol for the base symbol interface
 * @see FunctionSymbol for function symbols
 * @see SymbolTable for symbol table operations
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record VariableSymbol(String name, Type type, boolean isConst) implements Symbol {

  /**
   * Constructs a variable symbol.
   *
   * @param name the identifier name (must not be null)
   * @param type the declared type (must not be null)
   * @param isConst whether the variable is const-qualified
   * @throws NullPointerException if name or type is null
   */
  public VariableSymbol {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(type, "type must not be null");
  }
}

