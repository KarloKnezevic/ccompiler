package hr.fer.ppj.codegen.expr.assignment;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.expr.call.FunctionCallGenerator;
import hr.fer.ppj.codegen.structs.NestedStructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructSizeCalculator;
import hr.fer.ppj.codegen.utils.LValueAddressGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.StructType;
import java.util.Map;
import java.util.Objects;

/**
 * Generates code for struct assignment operations.
 *
 * <p>This class handles the generation of code for struct assignments, including:
 *
 * <ul>
 *   <li><b>Struct-to-struct assignment:</b> {@code p = q} - byte-wise memory copy
 *   <li><b>Function call assignment:</b> {@code p = makePoint(...)} - optimized path using hidden
 *       return pointer
 * </ul>
 *
 * <p><b>Algorithm: Struct Assignment Code Generation</b>
 *
 * <p>For struct-to-struct assignment ({@code p = q}):
 *
 * <ol>
 *   <li><b>Size Calculation:</b> Calculate struct size (may require array size extraction)
 *   <li><b>Address Resolution:</b> Compute addresses of source and destination structs
 *   <li><b>Memory Copy:</b> Copy struct word-by-word using a loop
 * </ol>
 *
 * <p>For function call assignment ({@code p = makePoint(...)}):
 *
 * <ol>
 *   <li><b>Address Resolution:</b> Compute destination struct address
 *   <li><b>Function Call:</b> Call function with hidden return pointer in R2
 *   <li><b>No Copy Needed:</b> Function writes directly to destination
 * </ol>
 *
 * <p><b>FRISC Code Pattern (Struct-to-Struct):</b>
 *
 * <pre>
 * ; Struct assignment: p = q
 *
 * ; Compute source address (q)
 * MOVE R5, R2                    ; frame pointer
 * ADD R2, -20, R2                ; source struct offset
 *
 * ; Compute destination address (p)
 * MOVE R5, R3                    ; frame pointer
 * ADD R3, -12, R3                ; dest struct offset
 *
 * ; Copy loop
 * MOVE %D 24, R4                  ; struct size (24 bytes)
 * L_LOOP:
 *     CMP R4, %D 0
 *     JP_EQ L_END
 *     LOAD R0, (R2)               ; load word from source
 *     STORE R0, (R3)              ; store word to destination
 *     ADD R2, %D 4, R2            ; increment source pointer
 *     ADD R3, %D 4, R3            ; increment dest pointer
 *     SUB R4, %D 4, R4            ; decrement counter
 *     JP L_LOOP
 * L_END:
 * </pre>
 *
 * <p><b>FRISC Code Pattern (Function Call):</b>
 *
 * <pre>
 * ; Struct assignment: p = makePoint(1, 2)
 *
 * ; Compute destination address (p)
 * MOVE R5, R0                     ; frame pointer
 * ADD R0, -12, R0                 ; struct offset
 *
 * ; Save return pointer in R1 (temporary)
 * MOVE R0, R1
 *
 * ; Evaluate and push arguments
 * ... (push arguments) ...
 *
 * ; Set return pointer in R2
 * MOVE R1, R2
 *
 * ; Call function
 * CALL F_MAKEPOINT
 *
 * ; Struct already written to p, no copy needed
 * </pre>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is struct size in bytes (word-by-word copy)
 *   <li><b>Space Complexity:</b> O(1) - uses only registers
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StructAssignmentGenerator {

  private final CodeGenContext context;
  private final ExpressionCodeGenerator expressionGenerator;
  private final LValueAddressGenerator addressGenerator;

  /**
   * Creates a new struct assignment generator.
   *
   * @param context the code generation context
   * @param expressionGenerator the expression generator for evaluating expressions
   * @param addressGenerator the address generator for computing struct addresses
   */
  public StructAssignmentGenerator(
      CodeGenContext context,
      ExpressionCodeGenerator expressionGenerator,
      LValueAddressGenerator addressGenerator) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.expressionGenerator =
        Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    this.addressGenerator =
        Objects.requireNonNull(addressGenerator, "addressGenerator must not be null");
  }

  /**
   * Generates code for struct assignment from a struct-returning function call: p = makePoint(...).
   *
   * <p>This is an optimized path that passes the LHS address as a hidden return pointer, avoiding
   * an extra copy. The function writes directly to the destination struct.
   *
   * <p><b>Calling Convention:</b> The return pointer is passed in register R2 before the CALL
   * instruction. The callee writes the struct directly to this address.
   *
   * @param lvalue the left-hand side (destination struct)
   * @param rvalue the right-hand side (function call expression)
   * @param structType the struct type
   * @param callInfo the extracted function call information
   */
  public void generateFromFunctionCall(
      NonTerminalNode lvalue,
      NonTerminalNode rvalue,
      StructType structType,
      FunctionCallExtractor.FunctionCallInfo callInfo) {
    Objects.requireNonNull(lvalue, "lvalue must not be null");
    Objects.requireNonNull(rvalue, "rvalue must not be null");
    Objects.requireNonNull(structType, "structType must not be null");
    Objects.requireNonNull(callInfo, "callInfo must not be null");

    // Compute LHS address (destination for struct return)
    addressGenerator.generateAddress(lvalue, "R0");

    // Generate function call with hidden return pointer
    // The return pointer is passed in R2 before CALL
    FunctionCallGenerator callGen = new FunctionCallGenerator(context, expressionGenerator);
    callGen.generateFunctionCallWithReturnPointer(callInfo.function(), callInfo.arguments(), "R0");

    // Struct is already written to LHS by callee, no extra copy needed
    // Assignment result can be ignored (struct assignment returns void-like)
  }

  /**
   * Generates code for struct assignment: p = q (byte-wise copy).
   *
   * <p>This performs a memory copy of the entire struct from source to destination. Uses address
   * generator to handle complex expressions like nested field access.
   *
   * <p><b>Copy Algorithm:</b>
   *
   * <ol>
   *   <li>Compute source address (rvalue)
   *   <li>Compute destination address (lvalue)
   *   <li>Copy struct word-by-word in a loop
   * </ol>
   *
   * @param lvalue the left-hand side (destination struct)
   * @param rvalue the right-hand side (source struct)
   * @param structType the struct type
   */
  public void generateStructCopy(
      NonTerminalNode lvalue, NonTerminalNode rvalue, StructType structType) {
    Objects.requireNonNull(lvalue, "lvalue must not be null");
    Objects.requireNonNull(rvalue, "rvalue must not be null");
    Objects.requireNonNull(structType, "structType must not be null");

    // Calculate struct size - try without array sizes first, then with if needed
    int structSize;
    Map<String, Integer> arraySizes = null;
    Map<String, Map<String, Integer>> nestedStructArraySizes = null;

    try {
      structSize = StructSizeCalculator.calculateStructSize(structType);
    } catch (IllegalArgumentException e) {
      // Struct has array fields - need to extract array sizes
      // Extract array sizes for this struct and nested structs
      if (addressGenerator != null) {
        StructArraySizeExtractor arraySizeExtractor = addressGenerator.getArraySizeExtractor();
        if (arraySizeExtractor != null) {
          String structTag = structType.tag();
          arraySizes = arraySizeExtractor.extractArraySizes(structTag);

          // Extract array sizes for nested struct fields (recursively)
          nestedStructArraySizes = new java.util.HashMap<>();
          NestedStructArraySizeExtractor.extractNestedStructArraySizes(
              structType, arraySizeExtractor, nestedStructArraySizes);
        }
      }

      structSize =
          StructSizeCalculator.calculateStructSize(structType, arraySizes, nestedStructArraySizes);
    }

    context.emitter().emitComment("Struct assignment: copy " + structSize + " bytes");

    // Compute source address (rvalue)
    addressGenerator.generateAddress(rvalue, "R2");
    context.emitter().emitInstruction("MOVE", "R2", "R2", "source addr (struct rvalue)");

    // Compute destination address (lvalue)
    addressGenerator.generateAddress(lvalue, "R3");
    context.emitter().emitInstruction("MOVE", "R3", "R3", "dest addr (struct lvalue)");

    // Copy struct word-by-word from beginning (offset 0) forward
    String loopLabel = context.labelGenerator().generateLabel();
    String endLabel = context.labelGenerator().generateLabel();

    // Initialize: R4 = remaining bytes
    context.emitter().emitInstruction("MOVE", "%D " + structSize, "R4", "remaining bytes");

    context.emitter().emitLabel(loopLabel, "struct copy loop");

    // Check if counter is zero
    context.emitter().emitInstruction("CMP", "R4", "%D 0", null);
    context.emitter().emitInstruction("JP_EQ", endLabel, "done if counter == 0");

    // Load word from source: R0 = *R2 (starting from offset 0)
    context.emitter().emitInstruction("LOAD", "R0", "(R2)", "load word from source");

    // Store word to destination: *R3 = R0
    context.emitter().emitInstruction("STORE", "R0", "(R3)", "store word to destination");

    // Increment pointers: R2 += 4, R3 += 4 (move forward through struct)
    context.emitter().emitInstruction("ADD", "R2", "%D 4", "R2", "increment source pointer");
    context.emitter().emitInstruction("ADD", "R3", "%D 4", "R3", "increment dest pointer");

    // Decrement counter: R4 -= 4
    context.emitter().emitInstruction("SUB", "R4", "%D 4", "R4", "decrement remaining bytes");

    // Loop back
    context.emitter().emitInstruction("JP", loopLabel, "continue copy loop");

    context.emitter().emitLabel(endLabel, "end struct copy");

    // Assignment result: address of LHS (for consistency with C semantics)
    context.emitter().emitInstruction("MOVE", "R3", "R0", "assignment result (addr of LHS)");
  }
}
