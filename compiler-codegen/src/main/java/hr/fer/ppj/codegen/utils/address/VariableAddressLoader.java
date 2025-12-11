package hr.fer.ppj.codegen.utils.address;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.Objects;

/**
 * Generates FRISC code to load variable addresses into registers.
 *
 * <p>This class handles the low-level code generation for loading variable addresses, including
 * handling different address formats (global labels vs. frame pointer offsets) and parameter
 * dereferencing logic.
 *
 * <p><b>Purpose:</b>
 *
 * <p>When computing addresses for L-values, we need to generate FRISC instructions that:
 *
 * <ul>
 *   <li>Load global variable labels (e.g., {@code MOVE G_X, R0})
 *   <li>Compute frame pointer offsets for local variables (e.g., {@code MOVE R5, R0; ADD R0, -08,
 *       R0})
 *   <li>Handle parameter dereferencing for array/pointer parameters (e.g., {@code LOAD R0, (R0)})
 * </ul>
 *
 * <p><b>Address Formats:</b>
 *
 * <p>This class handles two main address formats:
 *
 * <ul>
 *   <li><b>Global Variables:</b> {@code (G_LABEL)} → generates {@code MOVE G_LABEL, targetRegister}
 *   <li><b>Local Variables/Parameters:</b> {@code (R5+offset)} or {@code (R5-offset)} → generates
 *       frame pointer computation and optional offset addition
 * </ul>
 *
 * <p><b>Parameter Dereferencing:</b>
 *
 * <p>For array/pointer parameters, the parameter slot contains a pointer value that must be loaded.
 * This class uses {@link ParameterTypeChecker} to determine if dereferencing is needed and emits
 * the appropriate LOAD instruction.
 *
 * <p><b>FRISC Code Patterns:</b>
 *
 * <p><b>Global Variable:</b>
 *
 * <pre>
 * MOVE G_X, R0                    ; load global variable address
 * </pre>
 *
 * <p><b>Local Variable:</b>
 *
 * <pre>
 * MOVE R5, R0                     ; load frame pointer
 * SUB R0, %D 8, R0                ; add variable offset (-8)
 * </pre>
 *
 * <p><b>Array Parameter:</b>
 *
 * <pre>
 * MOVE R5, R0                     ; load frame pointer
 * ADD R0, %D 8, R0                ; add parameter offset (+8)
 * LOAD R0, (R0)                   ; load pointer value (array base address)
 * </pre>
 *
 * <p><b>Struct Parameter:</b>
 *
 * <pre>
 * MOVE R5, R0                     ; load frame pointer
 * ADD R0, %D 8, R0                ; add parameter offset (+8)
 * ; No LOAD needed - struct stored directly
 * </pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class VariableAddressLoader {

  private final CodeGenContext context;
  private final ParameterTypeChecker typeChecker;

  /**
   * Creates a new variable address loader.
   *
   * @param context the code generation context
   * @throws NullPointerException if context is null
   */
  public VariableAddressLoader(CodeGenContext context) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.typeChecker = new ParameterTypeChecker(context);
  }

  /**
   * Loads a variable address into a register.
   *
   * <p>For local variables: computes R5 + offset For global variables: loads global label
   *
   * <p><b>Key:</b> This computes the ADDRESS, not the value. Never uses LOAD to get the address of
   * a struct variable.
   *
   * <p><b>Array Parameters:</b> In C, array parameters decay to pointers. When the variable is an
   * array parameter (declared as {@code int a[]} or {@code int *a}), the parameter slot contains a
   * pointer value (the base address of the array). To get the base address for array indexing, we
   * need to LOAD the pointer value from the parameter slot.
   *
   * <p><b>Struct Parameters:</b> Struct parameters passed by value are stored DIRECTLY on the
   * stack. They do NOT decay to pointers like arrays do. For struct parameters, we should NOT emit
   * the extra LOAD.
   *
   * @param address the address expression (e.g., "(R5-08)" or "(G_X)")
   * @param targetRegister the register to load the address into
   * @param variableName the variable name (used to check if it's an array parameter)
   * @param expressionNode optional expression node for type extraction (if null, falls back to
   *     symbol table lookup)
   */
  public void loadAddress(
      String address, String targetRegister, String variableName, NonTerminalNode expressionNode) {
    Objects.requireNonNull(address, "address must not be null");
    Objects.requireNonNull(targetRegister, "targetRegister must not be null");
    Objects.requireNonNull(variableName, "variableName must not be null");

    if (address.startsWith("(G_")) {
      // Global variable: extract label from "(G_LABEL)" format
      String label = address.substring(1, address.length() - 1); // Remove parentheses
      context
          .emitter()
          .emitInstruction("MOVE", label, targetRegister, "load global variable address");
    } else if (address.startsWith("(R5")) {
      // Local variable or parameter: compute address from frame pointer
      loadFramePointerAddress(address, targetRegister, variableName, expressionNode);
    } else {
      // Fallback: assume it's a label
      context.emitter().emitInstruction("MOVE", address, targetRegister, "load variable address");
    }
  }

  /**
   * Loads a frame pointer-based address (local variable or parameter).
   *
   * <p>Generates code to compute an address relative to the frame pointer (R5):
   *
   * <ul>
   *   <li>Loads R5 into target register
   *   <li>Adds/subtracts the offset to get the final address
   *   <li>For array/pointer parameters, emits LOAD to dereference the pointer
   * </ul>
   *
   * @param address the address expression (e.g., "(R5-08)" or "(R5+08)")
   * @param targetRegister the register to load the address into
   * @param variableName the variable name (for parameter type checking)
   * @param expressionNode optional expression node for type extraction
   */
  private void loadFramePointerAddress(
      String address, String targetRegister, String variableName, NonTerminalNode expressionNode) {
    // Load frame pointer
    context.emitter().emitInstruction("MOVE", "R5", targetRegister, "load frame pointer");

    // Extract and apply offset
    String offsetStr = extractOffsetFromAddress(address);
    if (offsetStr != null) {
      applyOffset(offsetStr, targetRegister);

      // Check if this is a parameter that needs dereferencing
      // Parameters have positive offsets (R5+8, R5+12, etc.), locals have negative offsets
      // Only emit LOAD for array/pointer parameters, NOT for struct-by-value parameters
      if (offsetStr.startsWith("+")) {
        // This is a parameter - check if it's an array/pointer type (needs dereferencing)
        // vs a struct type (stored directly, doesn't need dereferencing)
        if (typeChecker.needsDereferencing(variableName, expressionNode)) {
          // Parameter slot address is now in targetRegister
          // LOAD the pointer value (base address of the array/pointer) from the parameter slot
          // In C, array parameters always decay to pointers, so we need to LOAD
          context
              .emitter()
              .emitInstruction(
                  "LOAD",
                  targetRegister,
                  "(" + targetRegister + ")",
                  "load array/pointer from parameter");
        }
        // For struct-by-value parameters: skip the LOAD - the struct is stored directly
        // at the parameter slot address, so targetRegister already contains the correct address
      }
    }
  }

  /**
   * Applies an offset to the target register.
   *
   * <p>Handles both positive and negative offsets, parsing hex or decimal format.
   *
   * @param offsetStr the offset string (e.g., "-08", "+0C", "08")
   * @param targetRegister the register to apply the offset to
   */
  private void applyOffset(String offsetStr, String targetRegister) {
    try {
      int offsetValue;
      if (offsetStr.startsWith("-")) {
        // Negative offset: parse hex value and negate
        String hexValue = offsetStr.substring(1); // Remove minus sign
        offsetValue = -Integer.parseInt(hexValue, 16);
        context
            .emitter()
            .emitInstruction(
                "SUB",
                targetRegister,
                "%D " + Math.abs(offsetValue),
                targetRegister,
                "add variable offset");
      } else if (offsetStr.startsWith("+")) {
        // Positive offset: parse hex value
        String hexValue = offsetStr.substring(1); // Remove plus sign
        offsetValue = Integer.parseInt(hexValue, 16);
        context
            .emitter()
            .emitInstruction(
                "ADD", targetRegister, "%D " + offsetValue, targetRegister, "add variable offset");
      } else {
        // No sign: assume positive hex
        offsetValue = Integer.parseInt(offsetStr, 16);
        context
            .emitter()
            .emitInstruction(
                "ADD", targetRegister, "%D " + offsetValue, targetRegister, "add variable offset");
      }
    } catch (NumberFormatException e) {
      // If parsing fails, try as decimal
      if (offsetStr.startsWith("-")) {
        context
            .emitter()
            .emitInstruction(
                "SUB",
                targetRegister,
                "%D " + offsetStr.substring(1),
                targetRegister,
                "add variable offset");
      } else if (offsetStr.startsWith("+")) {
        context
            .emitter()
            .emitInstruction(
                "ADD",
                targetRegister,
                "%D " + offsetStr.substring(1),
                targetRegister,
                "add variable offset");
      } else {
        context
            .emitter()
            .emitInstruction(
                "ADD", targetRegister, "%D " + offsetStr, targetRegister, "add variable offset");
      }
    }
  }

  /**
   * Extracts the offset part from an address expression.
   *
   * @param address the address expression (e.g., "(R5-04)" or "(R5+08)")
   * @return the offset string (e.g., "-04" or "+08"), or null if not found
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
}
