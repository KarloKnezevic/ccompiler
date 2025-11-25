package hr.fer.ppj.semantics.symbols;

/**
 * Marker interface for entries in the hierarchical symbol table.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface Symbol permits VariableSymbol, FunctionSymbol {

  /**
   * @return declared identifier name.
   */
  String name();
}

