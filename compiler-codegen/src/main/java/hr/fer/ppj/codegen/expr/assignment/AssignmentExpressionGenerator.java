package hr.fer.ppj.codegen.expr.assignment;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.utils.LValueAddressGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for assignment expressions and increment/decrement operations.
 *
 * <p>This class handles the generation of code for assignments and increment/decrement operations,
 * implementing the <b>assignment code generation algorithm</b> that translates C assignment
 * operations into FRISC assembly.
 *
 * <p><b>Algorithm: Assignment Code Generation</b>
 *
 * <p>The algorithm works as follows:
 *
 * <ol>
 *   <li><b>Right-Hand Side Evaluation:</b> Evaluate the right-hand side expression first (result in
 *       R0)
 *   <li><b>Left-Hand Side Resolution:</b> Resolve the left-hand side (lvalue):
 *       <ul>
 *         <li>Simple variable: Get address from activation record or global scope
 *         <li>Array element: Delegate to array generator for address calculation
 *       </ul>
 *   <li><b>Value Storage:</b> Store the value from R0 to the resolved address
 * </ol>
 *
 * <p><b>Assignment Types Handled:</b>
 *
 * <ul>
 *   <li><b>Simple Variable Assignment:</b> {@code x = value} - direct STORE to variable address
 *   <li><b>Array Element Assignment:</b> {@code a[i] = value} - delegate to array generator
 * </ul>
 *
 * <p><b>Increment/Decrement Operations:</b>
 *
 * <p>Increment and decrement operations are delegated to {@link IncrementDecrementGenerator}:
 *
 * <ul>
 *   <li><b>Pre-increment (++var):</b> Increment variable, return new value
 *   <li><b>Pre-decrement (--var):</b> Decrement variable, return new value
 *   <li><b>Post-increment (var++):</b> Save old value, increment variable, return old value
 *   <li><b>Post-decrement (var--):</b> Save old value, decrement variable, return old value
 * </ul>
 *
 * <p><b>FRISC Code Pattern (Simple Assignment):</b>
 *
 * <pre>
 * ; Assignment: x = y + z
 *
 * ; Evaluate right-hand side
 * ... (evaluate y + z, result in R0) ...
 *
 * ; Store to left-hand side
 * STORE R0, (R5-04)            ; local variable
 * ; OR
 * STORE R0, (G_X)               ; global variable
 * </pre>
 *
 * <p><b>FRISC Code Pattern (Array Assignment):</b>
 *
 * <pre>
 * ; Assignment: a[i] = value
 *
 * ; Evaluate value (already in R0)
 * MOVE R0, R2                   ; save value
 *
 * ; Evaluate index
 * ... (evaluate i, result in R0) ...
 * SHL R0, %D 2, R0              ; index * 4
 *
 * ; Compute element address
 * MOVE G_A, R1                  ; base address
 * ADD R1, R0, R1                ; element address
 *
 * ; Store value
 * STORE R2, (R1)                ; store to array element
 * </pre>
 *
 * <p><b>Lvalue Resolution:</b>
 *
 * <p>Lvalues (left-hand sides of assignments) must be:
 *
 * <ul>
 *   <li><b>Modifiable:</b> Must be a variable or array element (not a constant)
 *   <li><b>Addressable:</b> Must have a memory address (not a temporary value)
 * </ul>
 *
 * <p>This implementation supports:
 *
 * <ul>
 *   <li>Simple variables (local and global)
 *   <li>Array elements (via array generator)
 * </ul>
 *
 * <p>Complex lvalues (e.g., pointer dereferences, structure members) are not supported in this
 * subset.
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for simple assignments, O(1) for array assignments (constant
 *       number of instructions)
 *   <li><b>Space Complexity:</b> O(1) - uses only registers
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class AssignmentExpressionGenerator {

  private final CodeGenContext context;
  private final ExpressionCodeGenerator expressionGenerator;
  private final IncrementDecrementGenerator incDecGenerator;
  private final LValueAddressGenerator addressGenerator;
  private final StructAssignmentGenerator structAssignmentGenerator;
  private final FunctionCallExtractor functionCallExtractor;

  /**
   * Creates a new assignment expression generator.
   *
   * @param context the code generation context
   * @param expressionGenerator the main expression generator for recursive calls
   */
  public AssignmentExpressionGenerator(
      CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.expressionGenerator =
        Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    this.incDecGenerator = new IncrementDecrementGenerator(context, expressionGenerator);
    this.addressGenerator = new LValueAddressGenerator(context, expressionGenerator);
    this.structAssignmentGenerator =
        new StructAssignmentGenerator(context, expressionGenerator, addressGenerator);
    this.functionCallExtractor = new FunctionCallExtractor(context);
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
   * Generates code for assignment expressions (=).
   *
   * @param node the assignment expression node
   */
  public void generateAssignmentExpression(NonTerminalNode node) {
    List<ParseNode> children = node.children();

    if (children.size() == 1) {
      // Single child - delegate to next level
      expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
    } else if (children.size() == 3) {
      // Assignment: <postfiks_izraz> OP_PRIDRUZI <izraz_pridruzivanja>
      NonTerminalNode lvalue = (NonTerminalNode) children.get(0);
      NonTerminalNode rvalue = (NonTerminalNode) children.get(2);

      // Check if this is a struct assignment (both sides are structs)
      Type lvalueType = lvalue.attributes() != null ? lvalue.attributes().type() : null;
      Type rvalueType = rvalue.attributes() != null ? rvalue.attributes().type() : null;
      Type strippedLvalueType = lvalueType != null ? TypeSystem.stripConst(lvalueType) : null;
      Type strippedRvalueType = rvalueType != null ? TypeSystem.stripConst(rvalueType) : null;

      if (strippedLvalueType instanceof StructType) {
        // LHS is a struct - check if RHS is a struct-returning function call
        if (functionCallExtractor.isStructReturningFunctionCall(rvalue)) {
          // Optimized path: p = makePoint(...) - pass &p as hidden return pointer
          FunctionCallExtractor.FunctionCallInfo callInfo =
              functionCallExtractor.extractFunctionCallInfo(rvalue);
          if (callInfo == null) {
            throw new IllegalStateException("RHS is not a function call");
          }
          structAssignmentGenerator.generateFromFunctionCall(
              lvalue, rvalue, (StructType) strippedLvalueType, callInfo);
        } else if (strippedRvalueType instanceof StructType) {
          // Struct assignment: p = q (byte-wise copy)
          structAssignmentGenerator.generateStructCopy(
              lvalue, rvalue, (StructType) strippedLvalueType);
        } else {
          throw new IllegalStateException(
              "Cannot assign non-struct to struct: " + strippedRvalueType);
        }
      } else {
        // Regular assignment: evaluate rvalue, then assign
        expressionGenerator.generateExpression(rvalue);

        // Generate assignment code
        generateAssignment(lvalue, "R0");
      }
    }
  }

  /**
   * Generates code for pre-increment (++var). Returns the new value.
   *
   * @param operand the operand expression
   */
  public void generatePreIncrement(NonTerminalNode operand) {
    incDecGenerator.generatePreIncrement(operand);
  }

  /**
   * Generates code for pre-decrement (--var). Returns the new value.
   *
   * @param operand the operand expression
   */
  public void generatePreDecrement(NonTerminalNode operand) {
    incDecGenerator.generatePreDecrement(operand);
  }

  /**
   * Generates code for post-increment (var++). Returns the old value.
   *
   * @param operand the operand expression
   */
  public void generatePostIncrement(NonTerminalNode operand) {
    incDecGenerator.generatePostIncrement(operand);
  }

  /**
   * Generates code for post-decrement (var--). Returns the old value.
   *
   * @param operand the operand expression
   */
  public void generatePostDecrement(NonTerminalNode operand) {
    incDecGenerator.generatePostDecrement(operand);
  }

  /**
   * Generates code to assign a value to an lvalue.
   *
   * <p>This method handles:
   *
   * <ul>
   *   <li>Simple variable assignments: {@code x = value}
   *   <li>Array element assignments: {@code a[i] = value}
   *   <li>Struct field assignments: {@code p.x = value}
   *   <li>Struct assignments: {@code p = q} (byte-wise copy)
   * </ul>
   *
   * @param lvalue the left-hand side expression
   * @param sourceRegister the register containing the value to assign
   */
  public void generateAssignment(NonTerminalNode lvalue, String sourceRegister) {
    // Save source value on stack (safe - won't be overwritten by address computation)
    context.emitter().emitInstruction("PUSH", sourceRegister, null, "save RHS value");

    // Use the address generator to compute the L-value address
    // This handles: simple variables, field access (including nested), and array indexing
    // Address computation may use R0, R1, R2 freely, but won't touch the stack
    addressGenerator.generateAddress(lvalue, "R0");

    // Restore RHS value from stack
    context.emitter().emitInstruction("POP", "R1", null, "restore RHS value");

    // Get lvalue type to determine store instruction
    Type lvalueType = lvalue.attributes() != null ? lvalue.attributes().type() : null;
    Type strippedLvalueType = lvalueType != null ? TypeSystem.stripConst(lvalueType) : null;

    // Store the value to the computed address
    // Note: char, int, and float are all 4 bytes, so use word STORE for all
    context.emitter().emitInstruction("STORE", "R1", "(R0)", "assign value");

    // Assignment result is the value assigned (for expressions like x = y = z)
    context.emitter().emitInstruction("MOVE", "R1", "R0", "assignment result");
  }

  /**
   * Checks if an expression is an array indexing expression.
   *
   * @param expr the expression to check
   * @return true if the expression is array indexing (a[i])
   */
  public boolean isArrayIndexing(NonTerminalNode expr) {
    return extractArrayIndexingInfo(expr) != null;
  }

  /** Information about an array indexing expression. */
  public record ArrayIndexingInfo(NonTerminalNode base, NonTerminalNode indexExpr) {}

  /**
   * Extracts array indexing information from an expression.
   *
   * @param expr the expression to extract from
   * @return ArrayIndexingInfo with base and indexExpr, or null if not array indexing
   */
  public ArrayIndexingInfo extractArrayIndexingInfo(NonTerminalNode expr) {
    String symbol = expr.symbol();
    List<ParseNode> children = expr.children();

    // Check if this node itself is array indexing: <postfiks_izraz> L_UGL_ZAGRADA <izraz>
    // D_UGL_ZAGRADA
    if ("<postfiks_izraz>".equals(symbol) && children.size() == 4) {
      ParseNode first = children.get(1);
      ParseNode third = children.get(3);
      if (first instanceof TerminalNode leftBracket
          && "L_UGL_ZAGRADA".equals(leftBracket.symbol())
          && third instanceof TerminalNode rightBracket
          && "D_UGL_ZAGRADA".equals(rightBracket.symbol())) {
        NonTerminalNode base = (NonTerminalNode) children.get(0);
        NonTerminalNode indexExpr = (NonTerminalNode) children.get(2);
        return new ArrayIndexingInfo(base, indexExpr);
      }
    }

    // Recursively check children
    for (ParseNode child : children) {
      if (child instanceof NonTerminalNode nonTerminal) {
        ArrayIndexingInfo info = extractArrayIndexingInfo(nonTerminal);
        if (info != null) {
          return info;
        }
      }
    }

    return null;
  }
}
