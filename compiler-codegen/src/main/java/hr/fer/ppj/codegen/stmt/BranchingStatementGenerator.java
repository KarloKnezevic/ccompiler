package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for branching statements (if-else).
 *
 * <p>This class handles the generation of conditional control flow, implementing the <b>conditional
 * branch code generation algorithm</b> that translates C if-else constructs into FRISC assembly
 * with proper label management and control flow.
 *
 * <p><b>Algorithm: Conditional Branch Code Generation</b>
 *
 * <p>Conditional branches are translated using a <b>structured control flow pattern</b>:
 *
 * <ol>
 *   <li><b>Condition Evaluation:</b> Evaluate the condition expression, result in R0
 *   <li><b>Boolean Check:</b> Compare R0 with 0 (C convention: 0 = false, non-zero = true)
 *   <li><b>Conditional Jump:</b> Jump to else/end label if condition is false
 *   <li><b>Then Branch:</b> Generate code for the then statement
 *   <li><b>Else Branch:</b> Generate code for the else statement (if present)
 *   <li><b>End Label:</b> Marks the end of the if-else construct
 * </ol>
 *
 * <p><b>Branch Types Handled:</b>
 *
 * <ul>
 *   <li><b>If Statements:</b> {@code if (condition) statement}
 *   <li><b>If-Else Statements:</b> {@code if (condition) statement1 else statement2}
 * </ul>
 *
 * <p><b>Grammar Rules Handled:</b>
 *
 * <pre>
 * &lt;naredba_grananja&gt; ::= KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
 *                        | KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt; KR_ELSE &lt;naredba&gt;
 * </pre>
 *
 * <p><b>If Statement Algorithm:</b>
 *
 * <p>The if statement (without else) is translated as follows:
 *
 * <ol>
 *   <li><b>Condition Evaluation:</b> Evaluate condition, result in R0
 *   <li><b>Boolean Check:</b> Compare R0 with 0
 *   <li><b>Skip Then:</b> If condition is false (R0 == 0), jump to end label
 *   <li><b>Then Execution:</b> Generate code for then statement
 *   <li><b>End Label:</b> Marks the end of the if statement
 * </ol>
 *
 * <p><b>If-Else Statement Algorithm:</b>
 *
 * <p>The if-else statement is translated as follows:
 *
 * <ol>
 *   <li><b>Condition Evaluation:</b> Evaluate condition, result in R0
 *   <li><b>Boolean Check:</b> Compare R0 with 0
 *   <li><b>Jump to Else:</b> If condition is false (R0 == 0), jump to else label
 *   <li><b>Then Execution:</b> Generate code for then statement
 *   <li><b>Skip Else:</b> Jump to end label (to skip else branch)
 *   <li><b>Else Label:</b> Marks the start of the else branch
 *   <li><b>Else Execution:</b> Generate code for else statement
 *   <li><b>End Label:</b> Marks the end of the if-else statement
 * </ol>
 *
 * <p><b>Boolean Evaluation Convention:</b>
 *
 * <p>C uses the following convention for boolean evaluation:
 *
 * <ul>
 *   <li><b>False:</b> 0 (zero value)
 *   <li><b>True:</b> Any non-zero value
 * </ul>
 *
 * <p>This is implemented by comparing the condition result with 0:
 *
 * <ul>
 *   <li>If R0 == 0, condition is false → skip then branch
 *   <li>If R0 != 0, condition is true → execute then branch
 * </ul>
 *
 * <p><b>FRISC Code Pattern (If Statement):</b>
 *
 * <pre>
 * ; If statement
 * &lt;condition evaluation&gt;     ; result in R0
 * CMP R0, %D 0              ; compare with 0
 * JP_EQ L_END1               ; jump to end if false
 * &lt;then statement&gt;
 * L_END1:                    ; end label
 * </pre>
 *
 * <p><b>FRISC Code Pattern (If-Else Statement):</b>
 *
 * <pre>
 * ; If-else statement
 * &lt;condition evaluation&gt;     ; result in R0
 * CMP R0, %D 0              ; compare with 0
 * JP_EQ L_ELSE1               ; jump to else if false
 * &lt;then statement&gt;
 * JP L_END1                   ; skip else
 * L_ELSE1:                    ; else label
 * &lt;else statement&gt;
 * L_END1:                     ; end label
 * </pre>
 *
 * <p><b>Nested Conditionals:</b>
 *
 * <p>Nested if-else statements are handled correctly because:
 *
 * <ul>
 *   <li>Each if-else creates its own set of labels (if, else, end)
 *   <li>Labels are generated with unique numbers (L_IF1, L_ELSE1, L_END1, etc.)
 *   <li>Inner conditionals use different labels than outer conditionals
 * </ul>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for code generation (constant number of instructions), but
 *       actual runtime depends on condition and branch execution
 *   <li><b>Space Complexity:</b> O(1) - uses only a few labels and registers
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BranchingStatementGenerator {

  private final CodeGenContext context;
  private final ExpressionCodeGenerator exprGen;
  private final StatementCodeGenerator stmtGen;

  /**
   * Creates a new branching statement generator.
   *
   * @param context the code generation context
   * @param exprGen the expression generator for condition evaluation
   * @param stmtGen the statement generator for then/else branches
   */
  public BranchingStatementGenerator(
      CodeGenContext context, ExpressionCodeGenerator exprGen, StatementCodeGenerator stmtGen) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.exprGen = Objects.requireNonNull(exprGen, "exprGen must not be null");
    this.stmtGen = Objects.requireNonNull(stmtGen, "stmtGen must not be null");
  }

  /**
   * Generates code for a branching statement (if or if-else).
   *
   * <p><b>Grammar Rule:</b> Processes {@code <naredba_grananja>}
   *
   * @param node the branching statement node ({@code <naredba_grananja>})
   */
  public void generateBranchingStatement(NonTerminalNode node) {
    Objects.requireNonNull(node, "node must not be null");

    List<ParseNode> children = node.children();

    if (children.size() == 5) {
      // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba>
      generateIfStatement(node);
    } else if (children.size() == 7) {
      // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba> KR_ELSE <naredba>
      generateIfElseStatement(node);
    }
  }

  /**
   * Generates code for an if statement without else clause.
   *
   * <p><b>Parse Tree Structure:</b>
   *
   * <pre>
   * KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt;
   * </pre>
   *
   * <p><b>FRISC Code:</b>
   *
   * <pre>
   * ; If statement
   * &lt;condition evaluation&gt;     ; result in R0
   * CMP R0, %D 0              ; compare with 0
   * JP_EQ L_END                ; jump to end if false
   * &lt;then statement&gt;
   * L_END                      ; end label
   * </pre>
   *
   * @param node the if statement node
   */
  private void generateIfStatement(NonTerminalNode node) {
    List<ParseNode> children = node.children();

    // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba>
    NonTerminalNode condition = (NonTerminalNode) children.get(2);
    NonTerminalNode thenStmt = (NonTerminalNode) children.get(4);

    var labels = context.labelGenerator().generateIfLabels();

    context.emitter().emitComment("If statement");

    // Generate condition
    exprGen.generateExpression(condition);

    // Jump to end if condition is false
    context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
    context.emitter().emitInstruction("JP_EQ", labels.endLabel(), null, "if condition is false");

    // Generate then statement
    stmtGen.generateStatement(thenStmt);

    context.emitter().emitLabel(labels.endLabel(), "end if");
  }

  /**
   * Generates code for an if-else statement.
   *
   * <p><b>Parse Tree Structure:</b>
   *
   * <pre>
   * KR_IF L_ZAGRADA &lt;izraz&gt; D_ZAGRADA &lt;naredba&gt; KR_ELSE &lt;naredba&gt;
   * </pre>
   *
   * <p><b>FRISC Code:</b>
   *
   * <pre>
   * ; If-else statement
   * &lt;condition evaluation&gt;     ; result in R0
   * CMP R0, %D 0              ; compare with 0
   * JP_EQ L_ELSE               ; jump to else if false
   * &lt;then statement&gt;
   * JP L_END                   ; skip else
   * L_ELSE                     ; else label
   * &lt;else statement&gt;
   * L_END                      ; end label
   * </pre>
   *
   * @param node the if-else statement node
   */
  private void generateIfElseStatement(NonTerminalNode node) {
    List<ParseNode> children = node.children();

    // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba> KR_ELSE <naredba>
    NonTerminalNode condition = (NonTerminalNode) children.get(2);
    NonTerminalNode thenStmt = (NonTerminalNode) children.get(4);
    NonTerminalNode elseStmt = (NonTerminalNode) children.get(6);

    var labels = context.labelGenerator().generateIfLabels();

    context.emitter().emitComment("If-else statement");

    // Generate condition
    exprGen.generateExpression(condition);

    // Jump to else if condition is false
    context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
    context.emitter().emitInstruction("JP_EQ", labels.elseLabel(), null, "if condition is false");

    // Generate then statement
    stmtGen.generateStatement(thenStmt);
    context.emitter().emitInstruction("JP", labels.endLabel(), null, "skip else");

    // Generate else statement
    context.emitter().emitLabel(labels.elseLabel(), "else clause");
    stmtGen.generateStatement(elseStmt);

    context.emitter().emitLabel(labels.endLabel(), "end if-else");
  }
}
