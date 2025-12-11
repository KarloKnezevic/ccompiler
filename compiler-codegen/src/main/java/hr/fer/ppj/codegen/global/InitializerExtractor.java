package hr.fer.ppj.codegen.global;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import java.util.List;
import java.util.Objects;

/**
 * Facade for extracting initializer values for global variables from the parse tree.
 *
 * <p>This class coordinates extraction of initializer values by delegating to specialized
 * extractors:
 *
 * <ul>
 *   <li>{@link SimpleInitializerExtractor} - extracts simple variable initializers
 *   <li>{@link ArrayInitializerExtractor} - extracts array initializers
 * </ul>
 *
 * <p><b>Handles:</b>
 *
 * <ul>
 *   <li>Integer and character constants
 *   <li>Negative literals (unary minus)
 *   <li>Array initializer lists
 *   <li>Escape sequences in character literals
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class InitializerExtractor {

  private final SimpleInitializerExtractor simpleExtractor;
  private final ArrayInitializerExtractor arrayExtractor;

  /**
   * Creates a new initializer extractor.
   *
   * @param parseTree the parse tree from semantic analysis
   */
  public InitializerExtractor(NonTerminalNode parseTree) {
    Objects.requireNonNull(parseTree, "parseTree must not be null");
    this.simpleExtractor = new SimpleInitializerExtractor(parseTree);
    this.arrayExtractor = new ArrayInitializerExtractor(parseTree);
  }

  /**
   * Finds the initializer value for a global variable from the parse tree.
   *
   * @param variableName the name of the variable to find
   * @param isChar whether the variable is of char type
   * @return the initializer value as string, or null if not found
   */
  public String findInitializerValue(String variableName, boolean isChar) {
    return simpleExtractor.findInitializerValue(variableName, isChar);
  }

  /**
   * Finds array initializer values for a global array variable.
   *
   * @param variableName the name of the array variable
   * @param arrayType the array type
   * @return list of initializer values (with %D prefix), or null if not found
   */
  public List<String> findArrayInitializer(String variableName, ArrayType arrayType) {
    return arrayExtractor.findArrayInitializer(variableName, arrayType);
  }
}
