package hr.fer.ppj.codegen.utils.address;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.Objects;

/**
 * Utility class for checking parameter types to determine if dereferencing is needed.
 *
 * <p>This class handles the critical distinction between different parameter types in C's calling
 * convention:
 *
 * <ul>
 *   <li><b>Array/Pointer Parameters:</b> Decay to pointers, stored as 4-byte pointer values. Need
 *       LOAD to get the actual base address.
 *   <li><b>Struct-by-Value Parameters:</b> Stored directly on the stack (multiple words). Do NOT
 *       need LOAD - the address is already the struct address.
 *   <li><b>Scalar Parameters:</b> Stored directly on the stack (4 bytes). Do NOT need LOAD when
 *       computing address.
 * </ul>
 *
 * <p><b>Purpose:</b>
 *
 * <p>When computing addresses for array indexing (e.g., {@code a[i]} where {@code a} is a
 * parameter), we need to know whether to emit a LOAD instruction:
 *
 * <ul>
 *   <li><b>Array/Pointer Parameters:</b> Parameter slot contains pointer value → need LOAD
 *   <li><b>Struct Parameters:</b> Parameter slot contains struct bytes directly → no LOAD
 *   <li><b>Scalar Parameters:</b> Parameter slot contains value directly → no LOAD
 * </ul>
 *
 * <p><b>Algorithm:</b>
 *
 * <p>The type checking algorithm uses a multi-step approach:
 *
 * <ol>
 *   <li><b>Size Check:</b> If parameter size > 4 bytes, it's a struct-by-value → no LOAD
 *   <li><b>Type Attribute Check:</b> Extract type from expression node semantic attributes (most
 *       reliable for function parameters)
 *   <li><b>Symbol Table Check:</b> Fallback to symbol table lookup to determine if
 *       ArrayType/PointerType
 *   <li><b>Conservative Default:</b> If type cannot be determined, return false (no LOAD)
 * </ol>
 *
 * <p><b>Key Design Decision:</b>
 *
 * <p>This class uses a <b>conservative approach</b>: if the type cannot be determined, it returns
 * false (no LOAD). This is safe because:
 *
 * <ul>
 *   <li>Struct parameters don't need LOAD (correct behavior)
 *   <li>Scalar parameters don't need LOAD (correct behavior)
 *   <li>Array/pointer parameters that can't be identified will fail at runtime (better than
 *       incorrect code generation)
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * // Function: void foo(int arr[], struct Point p)
 *
 * // For arr[i]:
 * ParameterTypeChecker checker = new ParameterTypeChecker(context);
 * boolean needsLoad = checker.needsDereferencing("arr", expressionNode);
 * // Returns true → emit LOAD instruction
 *
 * // For p.x:
 * boolean needsLoad = checker.needsDereferencing("p", expressionNode);
 * // Returns false → no LOAD needed (struct stored directly)
 * </pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ParameterTypeChecker {

  private final CodeGenContext context;

  /**
   * Creates a new parameter type checker.
   *
   * @param context the code generation context
   * @throws NullPointerException if context is null
   */
  public ParameterTypeChecker(CodeGenContext context) {
    this.context = Objects.requireNonNull(context, "context must not be null");
  }

  /**
   * Checks if a parameter needs dereferencing (LOAD instruction) when computing its address.
   *
   * <p>In C, array parameters (int a[]) and pointer parameters (int *a) decay to pointers. The
   * parameter slot contains a pointer value that must be loaded before use.
   *
   * <p>Struct parameters passed by value (struct Point p) are stored DIRECTLY on the stack and do
   * NOT need dereferencing. This method returns false for struct parameters.
   *
   * <p><b>Algorithm:</b>
   *
   * <ol>
   *   <li>If parameter size > 4 bytes, it's a struct-by-value → return false (no LOAD needed)
   *   <li>If expressionNode is provided, extract type from semantic attributes (most reliable)
   *   <li>Otherwise, check symbol table to determine if it's ArrayType or PointerType
   *   <li>If type cannot be determined, return false (conservative approach)
   * </ol>
   *
   * @param variableName the variable name
   * @param expressionNode optional expression node for type extraction (may be null)
   * @return true if the variable is an array or pointer parameter (needs LOAD), false if
   *     struct-by-value, scalar, or type cannot be determined
   */
  public boolean needsDereferencing(String variableName, NonTerminalNode expressionNode) {
    Objects.requireNonNull(variableName, "variableName must not be null");

    // First check: if parameter size > 4 bytes, it's a struct-by-value parameter
    // Struct parameters are passed by value and take multiple words (e.g., 8 bytes for struct
    // Point)
    // Array/pointer parameters are always 4 bytes (they decay to pointers)
    if (context.isInFunction() && context.activationRecord().hasVariable(variableName)) {
      Integer paramSize = context.activationRecord().getVariableSize(variableName);
      if (paramSize != null && paramSize > 4) {
        // Parameter size > 4 bytes → struct-by-value parameter
        // Structs are stored directly on the stack, no LOAD needed
        return false;
      }
    }

    // Second check: if expressionNode is provided, extract type from semantic attributes
    // This is more reliable than symbol table lookup for function parameters
    if (expressionNode != null && expressionNode.attributes() != null) {
      Type exprType = expressionNode.attributes().type();
      if (exprType != null) {
        Type strippedType = TypeSystem.stripConst(exprType);
        // Check if it's a struct type (stored directly, no LOAD needed)
        if (strippedType instanceof StructType) {
          return false; // Struct-by-value parameter: no LOAD needed
        }
        // Check if it's an array or pointer type (needs LOAD)
        if (strippedType instanceof ArrayType || strippedType instanceof PointerType) {
          return true; // Array/pointer parameter: needs LOAD
        }
        // For scalar types (int, char, float), we don't need LOAD when computing address
        // Scalar parameters are stored directly on the stack, just like struct parameters
        // Only array/pointer parameters need the extra LOAD
        return false; // Scalar parameter: no LOAD needed
      }
    }

    // Third check: fallback to symbol table lookup
    // For 4-byte parameters, check if it's ArrayType or PointerType (needs LOAD)
    boolean isArrayParam = isArrayParameter(variableName);
    if (isArrayParam) {
      return true; // Found in symbol table as array/pointer
    }

    // Fourth check: conservative default
    // If we can't determine the type and it's a parameter, be conservative:
    // Don't emit LOAD unless we can prove it's needed (found in symbol table as array/pointer)
    return false; // Conservative: no LOAD if we can't determine type
  }

  /**
   * Checks if a variable is an array parameter.
   *
   * <p>In C, array parameters decay to pointers. This method checks if the variable is a parameter
   * and if its type is an array type (which means it's an array parameter that should be treated as
   * a pointer).
   *
   * <p><b>Note:</b> Function parameters are stored in function scopes (child scopes of global
   * scope). The standard {@code lookup()} method only traverses parent scopes, so we need to search
   * through child scopes recursively to find function parameters. However, in practice, parameters
   * should be findable via standard lookup if we're in the right context.
   *
   * @param variableName the variable name
   * @return true if the variable is an array parameter
   */
  private boolean isArrayParameter(String variableName) {
    // First try standard lookup (finds variables in current scope and parent scopes)
    var symbolOpt = context.globalScope().lookup(variableName);
    if (symbolOpt.isPresent() && symbolOpt.get() instanceof VariableSymbol varSymbol) {
      Type varType = varSymbol.type();
      Type strippedType = TypeSystem.stripConst(varType);
      // Array parameters are either ArrayType or PointerType (array decay)
      // In C, int a[] and int *a are equivalent in function parameters
      return strippedType instanceof ArrayType || strippedType instanceof PointerType;
    }

    // If not found, we can't determine the type
    // In this case, we'll be conservative and not treat it as an array parameter
    // This should be rare since semantic analysis should have validated all variables
    return false;
  }
}
