package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticConstants;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.util.NodeUtils;

/**
 * Semantic rules for jump statements.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Break statements</li>
 *   <li>Continue statements</li>
 *   <li>Return statements</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class JumpStatementRules {
  
  private final SemanticChecker checker;
  
  JumpStatementRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<naredba_skoka>", this::visitNaredbaSkoka);
  }
  
  /**
   * Performs semantic analysis for jump statements (break, continue, return).
   * 
   * <p>Grammar:
   *   <naredba_skoka> ::= KR_BREAK TOCKAZAREZ
   *                    | KR_CONTINUE TOCKAZAREZ
   *                    | KR_RETURN TOCKAZAREZ
   *                    | KR_RETURN <izraz> TOCKAZAREZ
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>break and continue must appear inside a loop</li>
   *   <li>return must appear inside a function</li>
   *   <li>void functions cannot return expressions</li>
   *   <li>non-void functions must return expressions</li>
   *   <li>return expression type must match function return type</li>
   * </ul>
   * 
   * @param node the jump statement node
   */
  private void visitNaredbaSkoka(NonTerminalNode node) {
    TerminalNode keyword = (TerminalNode) node.children().get(0);
    switch (keyword.symbol()) {
      case SemanticConstants.KR_BREAK, SemanticConstants.KR_CONTINUE -> {
        // break and continue must appear inside a loop
        if (checker.loopDepth() == 0) {
          checker.fail(node);
        }
      }
      case SemanticConstants.KR_RETURN -> handleReturn(node);
      default -> checker.fail(node);
    }
  }
  
  /**
   * Handles return statement semantic analysis.
   * 
   * <p>Validates:
   * <ul>
   *   <li>Return statement must appear inside a function</li>
   *   <li>Void functions: return; is valid, return expr; is invalid</li>
   *   <li>Non-void functions: return; is invalid, return expr; is valid</li>
   *   <li>Return expression type must be assignable to function return type</li>
   * </ul>
   * 
   * @param node the return statement node
   */
  private void handleReturn(NonTerminalNode node) {
    FunctionType current = checker.currentFunction();
    if (current == null) {
      checker.fail(node);
    }
    
    // Handle bare return: return;
    if (node.children().size() == 2) {
      // Only void functions can have bare return
      if (!current.isVoidReturn()) {
        checker.fail(node);
      }
      return;
    }
    
    // Handle return with expression: return <izraz>;
    // Non-void functions must return an expression
    NonTerminalNode expr = NodeUtils.asNonTerminal(node.children().get(1));
    checker.visitNonTerminal(expr);
    // Validate that return expression type is assignable to function return type
    checker.ensureAssignable(expr.attributes().type(), current.returnType(), node);
  }
}

