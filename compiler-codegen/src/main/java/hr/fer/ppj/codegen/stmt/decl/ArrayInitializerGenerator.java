package hr.fer.ppj.codegen.stmt.decl;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.utils.ConstantValueExtractor;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC code for initializing local arrays with initializer lists.
 *
 * <p>This class handles the generation of code for array initializers in local variable
 * declarations, such as {@code int x[3] = { 51, 52, 53 };}.
 *
 * <p><b>Purpose:</b>
 *
 * <p>When a local array is declared with an initializer list, we need to:
 *
 * <ol>
 *   <li>Extract initializer values from the parse tree
 *   <li>Generate code to store each value at the appropriate array element address
 *   <li>Zero-initialize any remaining elements (C standard: partial initializers zero-fill)
 * </ol>
 *
 * <p><b>FRISC Code Pattern:</b>
 *
 * <p>For {@code int x[3] = { 51, 52, 53 };}:
 *
 * <pre>
 * MOVE %D 51, R0
 * STORE R0, (R5-0C)    ; x[0]
 * MOVE %D 52, R0
 * STORE R0, (R5-08)    ; x[1]
 * MOVE %D 53, R0
 * STORE R0, (R5-04)    ; x[2]
 * </pre>
 *
 * <p>For partial initializers (e.g., {@code int x[3] = { 51 };}):
 *
 * <pre>
 * MOVE %D 51, R0
 * STORE R0, (R5-0C)    ; x[0]
 * MOVE %D 0, R0        ; zero-initialize remaining elements
 * STORE R0, (R5-08)    ; x[1]
 * STORE R0, (R5-04)    ; x[2]
 * </pre>
 *
 * <p><b>Array Memory Layout:</b>
 *
 * <p>Arrays are allocated downward on the stack:
 *
 * <ul>
 *   <li>Base offset is the lowest address (furthest from R5)
 *   <li>Element i is at: {@code baseOffset + (i * elementSize)}
 *   <li>For example, {@code int x[3]} at R5-0C (-12): x[0] at R5-0C, x[1] at R5-08 (-8), x[2] at
 *       R5-04 (-4)
 * </ul>
 *
 * <p><b>Grammar Rule:</b>
 *
 * <p>Handles {@code <inicijalizator>} with array initializer list:
 *
 * <pre>
 * &lt;inicijalizator&gt; ::= L_VIT_ZAGRADA &lt;lista_izraza_pridruzivanja&gt; D_VIT_ZAGRADA
 *                    | L_VIT_ZAGRADA &lt;lista_izraza_pridruzivanja&gt; ZAREZ D_VIT_ZAGRADA
 * </pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ArrayInitializerGenerator {

  private final CodeGenContext context;

  /**
   * Creates a new array initializer generator.
   *
   * @param context the code generation context
   * @throws NullPointerException if context is null
   */
  public ArrayInitializerGenerator(CodeGenContext context) {
    this.context = Objects.requireNonNull(context, "context must not be null");
  }

  /**
   * Generates code to initialize a local array with an initializer list.
   *
   * <p>For code like {@code int x[3] = { 51, 52, 53 };}, generates:
   *
   * <pre>
   * MOVE %D 51, R0
   * STORE R0, (R5-0C)    ; x[0]
   * MOVE %D 52, R0
   * STORE R0, (R5-08)    ; x[1]
   * MOVE %D 53, R0
   * STORE R0, (R5-04)    ; x[2]
   * </pre>
   *
   * <p>For partial initializers (e.g., {@code int x[3] = { 51 };}), after storing the explicit
   * values, zero-initializes the remaining elements.
   *
   * @param varName the variable name
   * @param baseAddress the base address of the array (e.g., "(R5-0C)")
   * @param initializer the initializer node ({@code <inicijalizator>})
   * @param arraySize the array size (number of elements)
   * @param elementSize the element size in bytes (4 for int, 4 for char in this project)
   * @throws IllegalStateException if the base address format is invalid
   */
  public void generate(
      String varName,
      String baseAddress,
      NonTerminalNode initializer,
      int arraySize,
      int elementSize) {
    Objects.requireNonNull(varName, "varName must not be null");
    Objects.requireNonNull(baseAddress, "baseAddress must not be null");
    Objects.requireNonNull(initializer, "initializer must not be null");

    // Extract initializer values from the initializer list
    List<String> initValues = extractInitializerValues(initializer);

    // Get base offset from address (e.g., extract "-0C" from "(R5-0C)")
    String baseOffsetStr = extractOffsetFromAddress(baseAddress);
    if (baseOffsetStr == null) {
      throw new IllegalStateException("Invalid array base address: " + baseAddress);
    }

    // Parse base offset (hex format, e.g., "-0C" = -12 decimal)
    int baseOffset = parseOffset(baseOffsetStr);

    // Initialize each element
    // Arrays are allocated downward: baseOffset is the lowest address (furthest from R5)
    // Element i is at: baseOffset + (i * elementSize)
    // For example, int x[3] at R5-0C (-12): x[0] at R5-0C, x[1] at R5-08 (-8), x[2] at R5-04 (-4)
    int numInitialized = initValues != null ? initValues.size() : 0;
    for (int i = 0; i < arraySize; i++) {
      // Calculate element offset: baseOffset + (i * elementSize)
      // baseOffset is negative (e.g., -12), adding positive values moves toward R5
      int elementOffset = baseOffset + (i * elementSize);
      String elementAddress = formatAddress(elementOffset);

      if (i < numInitialized) {
        // Store explicit initializer value
        String value = initValues.get(i);
        // Remove "%D " prefix if present (from extractInitializerValues)
        String cleanValue = value.startsWith("%D ") ? value.substring(3) : value;
        context
            .emitter()
            .emitInstruction(
                "MOVE", "%D " + cleanValue, "R0", "initialize " + varName + "[" + i + "]");
        context
            .emitter()
            .emitInstruction("STORE", "R0", elementAddress, "store " + varName + "[" + i + "]");
      } else {
        // Zero-initialize remaining elements (C standard: partial initializers zero-fill)
        context
            .emitter()
            .emitInstruction("MOVE", "%D 0", "R0", "zero-initialize " + varName + "[" + i + "]");
        context
            .emitter()
            .emitInstruction("STORE", "R0", elementAddress, "store " + varName + "[" + i + "]");
      }
    }
  }

  /**
   * Extracts initializer values from an array initializer list.
   *
   * <p>Handles the grammar:
   *
   * <pre>
   * &lt;inicijalizator&gt; ::= L_VIT_ZAGRADA &lt;lista_izraza_pridruzivanja&gt; D_VIT_ZAGRADA
   *                    | L_VIT_ZAGRADA &lt;lista_izraza_pridruzivanja&gt; ZAREZ D_VIT_ZAGRADA
   * </pre>
   *
   * @param initializer the initializer node ({@code <inicijalizator>})
   * @return list of initializer values (as strings, without %D prefix), or empty list if none found
   */
  private List<String> extractInitializerValues(NonTerminalNode initializer) {
    List<String> values = new ArrayList<>();
    List<ParseNode> children = initializer.children();

    // Find <lista_izraza_pridruzivanja> node
    NonTerminalNode listNode = null;
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nonTerminal
          && "<lista_izraza_pridruzivanja>".equals(nonTerminal.symbol())) {
        listNode = nonTerminal;
        break;
      }
    }

    if (listNode != null) {
      extractValuesFromList(listNode, values);
    }

    return values;
  }

  /**
   * Recursively extracts values from a list of assignment expressions.
   *
   * <p>Grammar:
   *
   * <pre>
   * &lt;lista_izraza_pridruzivanja&gt; ::= &lt;izraz_pridruzivanja&gt;
   *                                | &lt;lista_izraza_pridruzivanja&gt; ZAREZ &lt;izraz_pridruzivanja&gt;
   * </pre>
   *
   * @param listNode the list node ({@code <lista_izraza_pridruzivanja>})
   * @param values the list to add values to
   */
  private void extractValuesFromList(NonTerminalNode listNode, List<String> values) {
    List<ParseNode> children = listNode.children();

    if (children.size() == 1) {
      // Single expression
      String value = extractConstantValue((NonTerminalNode) children.get(0));
      if (value != null) {
        values.add(value);
      }
    } else if (children.size() == 3) {
      // Multiple expressions: <lista_izraza_pridruzivanja> ZAREZ <izraz_pridruzivanja>
      extractValuesFromList((NonTerminalNode) children.get(0), values);
      String value = extractConstantValue((NonTerminalNode) children.get(2));
      if (value != null) {
        values.add(value);
      }
    }
  }

  /**
   * Extracts a constant value from an assignment expression.
   *
   * <p>For now, only supports simple constant literals (integers). More complex expressions would
   * need to be evaluated at runtime.
   *
   * @param expr the expression node ({@code <izraz_pridruzivanja>})
   * @return the constant value as a string, or null if not a constant
   */
  private String extractConstantValue(NonTerminalNode expr) {
    // Use ConstantValueExtractor to find constant values
    // For int arrays, isChar = false; for char arrays, isChar = true
    // For now, assume int (isChar = false) - can be enhanced later
    return ConstantValueExtractor.findConstantValue(expr, false);
  }

  /**
   * Extracts the offset part from an address expression.
   *
   * @param address the address expression (e.g., "(R5-0C)" or "(R5+08)")
   * @return the offset string (e.g., "-0C" or "+08"), or null if not found
   */
  private String extractOffsetFromAddress(String address) {
    int r5Index = address.indexOf("R5");
    if (r5Index == -1) {
      return null;
    }

    int offsetStart = r5Index + 2; // After "R5"
    if (offsetStart >= address.length()) {
      return null;
    }

    char firstChar = address.charAt(offsetStart);
    if (firstChar != '+' && firstChar != '-') {
      return null;
    }

    int offsetEnd = address.indexOf(')', offsetStart);
    if (offsetEnd == -1) {
      return null;
    }

    return address.substring(offsetStart, offsetEnd);
  }

  /**
   * Parses an offset string (hex format) to an integer.
   *
   * @param offsetStr the offset string (e.g., "-0C", "+08", "08")
   * @return the offset value as an integer
   * @throws IllegalStateException if the offset format is invalid
   */
  private int parseOffset(String offsetStr) {
    try {
      if (offsetStr.startsWith("-")) {
        String hexValue = offsetStr.substring(1);
        return -Integer.parseInt(hexValue, 16);
      } else if (offsetStr.startsWith("+")) {
        String hexValue = offsetStr.substring(1);
        return Integer.parseInt(hexValue, 16);
      } else {
        return Integer.parseInt(offsetStr, 16);
      }
    } catch (NumberFormatException e) {
      throw new IllegalStateException("Invalid offset format: " + offsetStr);
    }
  }

  /**
   * Formats an offset as a FRISC address expression.
   *
   * @param offset the offset value (can be positive or negative)
   * @return formatted address (e.g., "(R5-0C)" for -12, "(R5+08)" for +8)
   */
  private String formatAddress(int offset) {
    if (offset >= 0) {
      String hex = Integer.toHexString(offset).toUpperCase();
      if (hex.length() == 1) {
        hex = "0" + hex;
      }
      return "(R5+" + hex + ")";
    } else {
      String hex = Integer.toHexString(-offset).toUpperCase();
      if (hex.length() == 1) {
        hex = "0" + hex;
      }
      return "(R5-" + hex + ")";
    }
  }
}
