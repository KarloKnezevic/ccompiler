package hr.fer.ppj.codegen.expr.array;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.utils.LValueAddressGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for array indexing operations.
 *
 * <p>This class handles the generation of code for array element access and assignment,
 * implementing the <b>array indexing code generation algorithm</b> that translates C array
 * operations into FRISC assembly with proper address calculation.
 *
 * <p><b>Grammar Rule:</b> Handles array indexing from {@code <postfiks_izraz>}:
 *
 * <pre>
 * &lt;postfiks_izraz&gt; ::= &lt;postfiks_izraz&gt; L_UGL_ZAGRADA &lt;izraz&gt; D_UGL_ZAGRADA
 * </pre>
 *
 * <p><b>Algorithm: Array Indexing Code Generation</b>
 *
 * <p>The algorithm works as follows:
 *
 * <ol>
 *   <li><b>Base Address Resolution:</b> Resolve the base address of the array:
 *       <ul>
 *         <li>Global arrays: Use global label (e.g., {@code G_A})
 *         <li>Local arrays: Use frame pointer with offset (e.g., {@code R5 - 20})
 *         <li>Array parameters: Load pointer from parameter slot (array decay to pointer)
 *       </ul>
 *   <li><b>Index Evaluation:</b> Evaluate the index expression (result in R0)
 *   <li><b>Offset Calculation:</b> Multiply index by element size (4 bytes) using left shift:
 *       {@code SHL R0, %D 2, R0} (shift left by 2 = multiply by 4)
 *   <li><b>Address Computation:</b> Add offset to base address: {@code ADD R1, R0, R1}
 *   <li><b>Memory Access:</b> Load or store element value using computed address
 * </ol>
 *
 * <p><b>Array Element Size:</b>
 *
 * <p>For this project, both {@code int} and {@code char} arrays use 4-byte elements:
 *
 * <ul>
 *   <li>This simplifies the implementation (no need for different element sizes)
 *   <li>Chars are stored as 32-bit words (not bytes)
 *   <li>Uses {@code LOAD} and {@code STORE} instructions (not {@code LOADB} and {@code STOREB})
 * </ul>
 *
 * <p><b>Address Calculation Formula:</b>
 *
 * <pre>
 * element_address = base_address + (index × element_size)
 *                 = base_address + (index × 4)
 * </pre>
 *
 * <p><b>Index Multiplication Optimization:</b>
 *
 * <p>Multiplying by 4 is optimized using left shift (more efficient than calling F_MUL):
 *
 * <pre>
 * index × 4 = index << 2
 * </pre>
 *
 * <p>This is implemented as: {@code SHL R0, %D 2, R0}
 *
 * <p><b>Array Parameter Handling:</b>
 *
 * <p>When an array is passed as a parameter, C's array decay to pointer semantics apply:
 *
 * <ul>
 *   <li>The parameter slot contains a pointer to the array (not the array itself)
 *   <li>We must first load the pointer value: {@code LOAD R1, (R5+offset)}
 *   <li>Then index from that pointer: {@code ADD R1, R0, R1}
 * </ul>
 *
 * <p><b>FRISC Code Pattern (Array Access):</b>
 *
 * <pre>
 * ; Array access: a[i]
 *
 * ; Evaluate index
 * ... (evaluate i, result in R0) ...
 *
 * ; Multiply index by element size (4 bytes)
 * SHL R0, %D 2, R0              ; index * 4
 *
 * ; Load base address
 * MOVE G_A, R1                   ; global array
 * ; OR
 * MOVE R5, R1                    ; local array
 * ADD R1, -20, R1                ; add base offset
 * ; OR
 * LOAD R1, (R5+08)               ; array parameter (load pointer)
 *
 * ; Compute element address
 * ADD R1, R0, R1                 ; R1 = base + (index * 4)
 *
 * ; Load element value
 * LOAD R0, (R1)                  ; load a[i]
 * </pre>
 *
 * <p><b>FRISC Code Pattern (Array Assignment):</b>
 *
 * <pre>
 * ; Array assignment: a[i] = value
 *
 * ; Save value (already in sourceRegister, typically R0)
 * MOVE R0, R2                    ; save value
 *
 * ; Evaluate index
 * ... (evaluate i, result in R0) ...
 * SHL R0, %D 2, R0               ; index * 4
 *
 * ; Compute element address (same as access)
 * MOVE G_A, R1                   ; base address
 * ADD R1, R0, R1                 ; element address
 *
 * ; Store value
 * STORE R2, (R1)                 ; store to a[i]
 * </pre>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for code generation (constant number of instructions)
 *   <li><b>Space Complexity:</b> O(1) - uses only registers
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ArrayExpressionGenerator {

  private final CodeGenContext context;
  private final LValueAddressGenerator addressGenerator;

  /**
   * Creates a new array expression generator.
   *
   * @param context the code generation context
   * @param expressionGenerator the main expression generator for recursive calls
   */
  public ArrayExpressionGenerator(
      CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    this.addressGenerator = new LValueAddressGenerator(context, expressionGenerator);
  }

  /**
   * Sets the parse tree for extracting struct array sizes.
   *
   * <p>This propagates the parse tree to the LValueAddressGenerator so it can extract array sizes
   * for nested structs with arrays.
   *
   * @param parseTree the parse tree from semantic analysis
   */
  public void setParseTree(NonTerminalNode parseTree) {
    if (addressGenerator != null) {
      addressGenerator.setParseTree(parseTree);
    }
  }

  /**
   * Generates code for array element access: a[i] or m.arr[i].
   *
   * <p><b>Grammar Rule:</b> Implements {@code <postfiks_izraz> ::= <postfiks_izraz> L_UGL_ZAGRADA
   * <izraz> D_UGL_ZAGRADA}
   *
   * <p>This method handles:
   *
   * <ul>
   *   <li>Simple arrays: {@code a[i]}
   *   <li>Array fields: {@code m.arr[i]}
   *   <li>Nested struct arrays: {@code o.inner.arr[i]}
   *   <li>Arrays of structs: {@code points[i]}
   * </ul>
   *
   * <p>The result is loaded into register R0.
   *
   * @param arrayAccessNode the full array access node ({@code <postfiks_izraz> L_UGL_ZAGRADA
   *     <izraz> D_UGL_ZAGRADA})
   */
  public void generateArrayIndexing(NonTerminalNode arrayAccessNode) {
    // Pattern: <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
    List<ParseNode> children = arrayAccessNode.children();
    if (children.size() != 4) {
      throw new IllegalStateException("Invalid array access node structure");
    }

    NonTerminalNode base = (NonTerminalNode) children.get(0);

    // 1) Use address generator to compute element address (handles all base types recursively)
    // This handles: a[i], a.arr[i], o.inner.arr[i], etc.
    addressGenerator.generateAddress(arrayAccessNode, "R0");

    // 2) Get element type from the base expression (not the array access node)
    Type baseType = base.attributes() != null ? base.attributes().type() : null;
    if (baseType == null) {
      throw new IllegalStateException("Base expression has no type annotation");
    }

    Type strippedBaseType = TypeSystem.stripConst(baseType);
    if (!(strippedBaseType instanceof ArrayType arrayType)) {
      throw new IllegalStateException(
          "Array access base is not an array type: " + strippedBaseType);
    }

    // 3) Get element type
    Type elementType = arrayType.elementType();
    Type strippedElementType = TypeSystem.stripConst(elementType);

    // 4) Load element value based on element type
    if (strippedElementType == hr.fer.ppj.semantics.types.PrimitiveType.CHAR) {
      // Char elements use LOADB (though chars are 4 bytes, we use LOADB for consistency)
      context.emitter().emitInstruction("LOAD", "R0", "(R0)", "load array element (char)");
    } else {
      // All other scalar types (int, float, pointer) are 4 bytes - use LOAD
      context.emitter().emitInstruction("LOAD", "R0", "(R0)", "load array element");
    }
  }

  /**
   * Generates code for array element assignment: a[i] = value.
   *
   * <p>The value to assign should be in the specified source register.
   *
   * @param lvalue the array indexing expression
   * @param sourceRegister the register containing the value to assign
   */
  public void generateArrayAssignment(NonTerminalNode lvalue, String sourceRegister) {
    // Use address generator to compute element address (handles field access bases)
    // Save source value
    context.emitter().emitInstruction("PUSH", sourceRegister, null, "save value to assign");

    // Compute address of array element using address generator
    addressGenerator.generateAddress(lvalue, "R0");

    // Restore value and store
    context.emitter().emitInstruction("POP", "R1", null, "restore value");
    context.emitter().emitInstruction("STORE", "R1", "(R0)", "store array element");
    context.emitter().emitInstruction("MOVE", "R1", "R0", "assignment result");
  }
}
