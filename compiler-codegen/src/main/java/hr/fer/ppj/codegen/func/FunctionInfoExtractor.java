package hr.fer.ppj.codegen.func;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Facade for extracting function information from parse tree nodes.
 *
 * <p>This class coordinates extraction of function information by delegating to specialized
 * extractors:
 *
 * <ul>
 *   <li>{@link FunctionNameExtractor} - extracts function names
 *   <li>{@link ParameterExtractor} - extracts parameter lists and names
 *   <li>{@link LocalVariableExtractor} - extracts local variable information
 * </ul>
 *
 * <p><b>Information Extracted:</b>
 *
 * <ul>
 *   <li>Function names from {@code <deklarator>} nodes
 *   <li>Parameter lists from {@code <lista_parametara>} nodes
 *   <li>Local variable information from {@code <slozena_naredba>} nodes
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionInfoExtractor {

  /**
   * Information about a local variable including its name and size.
   *
   * <p>This is a type alias for {@link LocalVariableExtractor.VariableInfo}.
   */
  public record VariableInfo(String name, int sizeInBytes) {}

  private final FunctionNameExtractor nameExtractor = new FunctionNameExtractor();
  private final ParameterExtractor parameterExtractor = new ParameterExtractor();
  private LocalVariableExtractor variableExtractor;
  private final hr.fer.ppj.codegen.structs.StructArraySizeExtractor arraySizeExtractor;

  /**
   * Creates a new function info extractor.
   *
   * @param parseTree the parse tree from semantic analysis (for extracting struct array sizes)
   */
  public FunctionInfoExtractor(NonTerminalNode parseTree) {
    this.variableExtractor = new LocalVariableExtractor(parseTree);
    this.arraySizeExtractor = new hr.fer.ppj.codegen.structs.StructArraySizeExtractor(parseTree);
  }

  /**
   * Gets the array size extractor for extracting struct array sizes.
   *
   * @return the array size extractor
   */
  public hr.fer.ppj.codegen.structs.StructArraySizeExtractor getArraySizeExtractor() {
    return arraySizeExtractor;
  }

  /**
   * Extracts the function name from a deklarator node.
   *
   * @param deklarator the deklarator node ({@code <deklarator>})
   * @return the function name (IDN lexeme), or null if not found
   */
  public String extractFunctionName(NonTerminalNode deklarator) {
    Objects.requireNonNull(deklarator, "deklarator must not be null");
    return nameExtractor.extractFunctionName(deklarator);
  }

  /**
   * Extracts function parameters from a deklarator node.
   *
   * @param deklarator the deklarator node ({@code <deklarator>})
   * @return the parameter list node ({@code <lista_parametara>}), or null if not found
   */
  public NonTerminalNode extractFunctionParameters(NonTerminalNode deklarator) {
    Objects.requireNonNull(deklarator, "deklarator must not be null");
    return parameterExtractor.extractFunctionParameters(deklarator);
  }

  /**
   * Extracts parameter names from the parameter list.
   *
   * @param parameters the parameter list node ({@code <lista_parametara>})
   * @return list of parameter names (IDN lexemes)
   */
  public List<String> extractParameterNames(NonTerminalNode parameters) {
    Objects.requireNonNull(parameters, "parameters must not be null");
    return parameterExtractor.extractParameterNames(parameters);
  }

  /**
   * Extracts local variable information from a function body.
   *
   * @param body the function body node ({@code <slozena_naredba>})
   * @param elementSize the element size in bytes (always 4 for this project)
   * @return list of variable information (name and size in bytes)
   */
  public List<VariableInfo> extractLocalVariables(NonTerminalNode body, int elementSize) {
    Objects.requireNonNull(body, "body must not be null");
    List<LocalVariableExtractor.VariableInfo> extracted =
        variableExtractor.extractLocalVariables(body, elementSize);
    // Convert to FunctionInfoExtractor.VariableInfo (they have the same structure)
    return extracted.stream().map(v -> new VariableInfo(v.name(), v.sizeInBytes())).toList();
  }
}
