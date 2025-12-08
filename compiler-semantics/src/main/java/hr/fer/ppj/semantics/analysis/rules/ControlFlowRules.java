package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticConstants;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.util.NodeUtils;

/**
 * Semantic rules for control flow statements.
 * 
 * <p>Handles:
 * <ul>
 *   <li>Compound statements (blocks)</li>
 *   <li>Statement lists</li>
 *   <li>Expression statements</li>
 *   <li>Conditional statements (if-else)</li>
 *   <li>Loop statements (while, for)</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class ControlFlowRules {
  
  private final SemanticChecker checker;
  
  ControlFlowRules(SemanticChecker checker) {
    this.checker = checker;
    checker.registerRule("<slozena_naredba>", this::visitSlozenaNaredba);
    checker.registerRule("<lista_naredbi>", this::visitListaNaredbi);
    checker.registerRule("<naredba>", this::visitNaredba);
    checker.registerRule("<izraz_naredba>", this::visitIzrazNaredba);
    checker.registerRule("<naredba_grananja>", this::visitNaredbaGrananja);
    checker.registerRule("<naredba_petlje>", this::visitNaredbaPetlje);
  }
  
  private void visitSlozenaNaredba(NonTerminalNode node) {
    checker.withNewScope(() -> processBlock(node));
  }
  
  void processBlock(NonTerminalNode node) {
    var children = node.children();
    if (children.size() < 2) {
      checker.fail(node);
    }
    if (children.size() == 2) {
      return;
    }
    if (children.size() == 3) {
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(1)));
      return;
    }
    if (children.size() == 4) {
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(1)));
      checker.visitNonTerminal(NodeUtils.asNonTerminal(children.get(2)));
      return;
    }
    checker.fail(node);
  }
  
  private void visitListaNaredbi(NonTerminalNode node) {
    for (ParseNode child : node.children()) {
      if (child instanceof NonTerminalNode nt) {
        checker.visitNonTerminal(nt);
      }
    }
  }
  
  private void visitNaredba(NonTerminalNode node) {
    checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(0)));
  }
  
  private void visitIzrazNaredba(NonTerminalNode node) {
    if (node.children().size() == 1) {
      node.attributes().type(PrimitiveType.INT);
      return;
    }
    NonTerminalNode expr = NodeUtils.asNonTerminal(node.children().get(0));
    checker.visitNonTerminal(expr);
    node.attributes().type(expr.attributes().type());
  }
  
  /**
   * Performs semantic analysis for if-else statements.
   * 
   * <p>Grammar:
   *   <naredba_grananja> ::= KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba>
   *                       | KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba> KR_ELSE <naredba>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Condition must be a scalar type (int, char, float, or pointer)</li>
   *   <li>Both branches are processed (if and optional else)</li>
   * </ul>
   * 
   * @param node the if-else statement node
   */
  private void visitNaredbaGrananja(NonTerminalNode node) {
    // Process condition: <izraz>
    NonTerminalNode condition = NodeUtils.asNonTerminal(node.children().get(2));
    checker.visitNonTerminal(condition);
    Type conditionType = condition.attributes().type();
    // Condition must be scalar (can be used in boolean context)
    if (conditionType == null || !conditionType.isScalar()) {
      checker.fail(node);
      return;
    }
    // Process if branch: <naredba>
    checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(4)));
    // Process else branch if present
    if (node.children().size() == 7) {
      checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(6)));
    }
  }
  
  /**
   * Performs semantic analysis for loop statements (while, for).
   * 
   * <p>Grammar:
   *   <naredba_petlje> ::= KR_WHILE L_ZAGRADA <izraz> D_ZAGRADA <naredba>
   *                    | KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> D_ZAGRADA <naredba>
   *                    | KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> <izraz> D_ZAGRADA <naredba>
   * 
   * <p>Semantic constraints:
   * <ul>
   *   <li>Loop condition must be a scalar type</li>
   *   <li>Loop body is processed within loop context (for break/continue validation)</li>
   * </ul>
   * 
   * @param node the loop statement node
   */
  private void visitNaredbaPetlje(NonTerminalNode node) {
    TerminalNode keyword = (TerminalNode) node.children().get(0);
    if (SemanticConstants.KR_WHILE.equals(keyword.symbol())) {
      handleWhileLoop(node);
      return;
    }
    if (SemanticConstants.KR_FOR.equals(keyword.symbol())) {
      handleForLoop(node);
      return;
    }
    checker.fail(node);
  }
  
  /**
   * Handles while loop semantic analysis.
   * 
   * <p>Grammar:
   *   KR_WHILE L_ZAGRADA <izraz> D_ZAGRADA <naredba>
   * 
   * <p>Validates condition is scalar and processes body within loop context.
   * 
   * @param node the while loop node
   */
  private void handleWhileLoop(NonTerminalNode node) {
    // Process condition: <izraz>
    NonTerminalNode condition = NodeUtils.asNonTerminal(node.children().get(2));
    checker.visitNonTerminal(condition);
    Type conditionType = condition.attributes().type();
    // Condition must be scalar (can be used in boolean context)
    if (conditionType == null || !conditionType.isScalar()) {
      checker.fail(node);
      return;
    }
    // Process body within loop context (enables break/continue validation)
    checker.withinLoop(() -> checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(4))));
  }
  
  /**
   * Handles for loop semantic analysis.
   * 
   * <p>Grammar:
   *   KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> D_ZAGRADA <naredba>
   *   KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> <izraz> D_ZAGRADA <naredba>
   * 
   * <p>Validates:
   * <ul>
   *   <li>Initialization expression (optional)</li>
   *   <li>Condition expression (must be scalar if present)</li>
   *   <li>Step expression (optional)</li>
   *   <li>Body (processed within loop context)</li>
   * </ul>
   * 
   * @param node the for loop node
   */
  private void handleForLoop(NonTerminalNode node) {
    // Process initialization: <izraz_naredba>
    NonTerminalNode init = NodeUtils.asNonTerminal(node.children().get(2));
    checker.visitNonTerminal(init);
    // Process condition: <izraz_naredba>
    NonTerminalNode condition = NodeUtils.asNonTerminal(node.children().get(3));
    checker.visitNonTerminal(condition);
    Type conditionType = condition.attributes().type();
    // Condition must be scalar if present (empty condition is also valid)
    if (conditionType == null || !conditionType.isScalar()) {
      checker.fail(node);
      return;
    }
    // Determine body index based on whether step expression is present
    int bodyIndex;
    if (node.children().size() == 7) {
      // Step expression present: KR_FOR L_ZAGRADA <init> <condition> <step> D_ZAGRADA <body>
      NonTerminalNode step = NodeUtils.asNonTerminal(node.children().get(4));
      checker.visitNonTerminal(step);
      bodyIndex = 6;
    } else {
      // No step expression: KR_FOR L_ZAGRADA <init> <condition> D_ZAGRADA <body>
      bodyIndex = 5;
    }
    // Process body within loop context (enables break/continue validation)
    int finalBodyIndex = bodyIndex;
    checker.withinLoop(() -> checker.visitNonTerminal(NodeUtils.asNonTerminal(node.children().get(finalBodyIndex))));
  }
}

