package hr.fer.ppj.semantics.symbols;

/**
 * Sealed interface representing entries in the hierarchical symbol table.
 *
 * <p>This interface serves as the base type for all symbols stored in the symbol table.
 * The sealed interface pattern ensures that only the permitted implementations
 * ({@link VariableSymbol} and {@link FunctionSymbol}) can exist, providing compile-time
 * type safety and enabling exhaustive pattern matching.
 *
 * <p>Symbols represent named entities in the program:
 * <ul>
 *   <li><strong>Variables</strong>: Represented by {@link VariableSymbol}, stores type
 *       and const-qualification</li>
 *   <li><strong>Functions</strong>: Represented by {@link FunctionSymbol}, stores function
 *       signature and definition status</li>
 * </ul>
 *
 * <p>All symbols have a name (identifier) that is used for lookup in the symbol table.
 * The name must be unique within a scope (shadowing is handled by lexical scoping rules).
 *
 * @see VariableSymbol for variable and constant declarations
 * @see FunctionSymbol for function declarations and definitions
 * @see SymbolTable for the symbol table implementation
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface Symbol permits VariableSymbol, FunctionSymbol {

  /**
   * Returns the declared identifier name of this symbol.
   *
   * <p>The name is the identifier used in the source code (e.g., variable name, function name).
   * It is used for symbol lookup in the symbol table.
   *
   * @return the declared identifier name (never null)
   */
  String name();
}

