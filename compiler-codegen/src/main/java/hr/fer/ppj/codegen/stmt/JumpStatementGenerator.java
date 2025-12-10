package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Dispatches jump statement code generation to specialized generators.
 *
 * <p>This class coordinates generation of jump statements by delegating to specialized generators:
 *
 * <ul>
 *   <li>{@link ReturnStatementGenerator} - generates return statements
 *   <li>Break and continue statements - handled directly (simple cases)
 * </ul>
 *
 * <p><b>Grammar Rules Handled:</b>
 *
 * <pre>
 * &lt;naredba_skoka&gt; ::= KR_RETURN &lt;izraz&gt; TOCKAZAREZ
 *                    | KR_RETURN TOCKAZAREZ
 *                    | KR_BREAK TOCKAZAREZ
 *                    | KR_CONTINUE TOCKAZAREZ
 * </pre>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class JumpStatementGenerator {

  private final CodeGenContext context;
  private final ReturnStatementGenerator returnGenerator;

  /**
   * Creates a new jump statement generator.
   *
   * @param context the code generation context
   * @param exprGen the expression generator for return expressions
   */
  public JumpStatementGenerator(CodeGenContext context, ExpressionCodeGenerator exprGen) {
    this.context = Objects.requireNonNull(context, "context must not be null");
    this.returnGenerator = new ReturnStatementGenerator(context, exprGen);
  }

  /**
   * Generates code for a jump statement (return, break, or continue).
   *
   * <p><b>Grammar Rule:</b> Processes {@code <naredba_skoka>}
   *
   * @param node the jump statement node ({@code <naredba_skoka>})
   */
  public void generateJumpStatement(NonTerminalNode node) {
    Objects.requireNonNull(node, "node must not be null");

    List<ParseNode> children = node.children();
    TerminalNode keyword = (TerminalNode) children.get(0);

    String jumpType = keyword.symbol();

    switch (jumpType) {
      case "KR_RETURN" -> returnGenerator.generateReturnStatement(node);
      case "KR_BREAK" -> generateBreakStatement();
      case "KR_CONTINUE" -> generateContinueStatement();
    }
  }

  /**
   * Generates code for a break statement.
   *
   * @throws IllegalStateException if break is used outside of a loop
   */
  private void generateBreakStatement() {
    if (context.loopBreakLabel() != null) {
      context.emitter().emitInstruction("JP", context.loopBreakLabel(), "break from loop");
    } else {
      throw new IllegalStateException("Break statement outside of loop");
    }
  }

  /**
   * Generates code for a continue statement.
   *
   * @throws IllegalStateException if continue is used outside of a loop
   */
  private void generateContinueStatement() {
    if (context.loopContinueLabel() != null) {
      context.emitter().emitInstruction("JP", context.loopContinueLabel(), "continue loop");
    } else {
      throw new IllegalStateException("Continue statement outside of loop");
    }
  }
}
