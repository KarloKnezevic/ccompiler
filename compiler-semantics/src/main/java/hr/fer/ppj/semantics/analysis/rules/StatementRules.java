package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

import hr.fer.ppj.semantics.tree.NonTerminalNode;

/**
 * Semantic rule implementations for statement-related productions.
 * 
 * <p>This class orchestrates statement analysis and delegates to specialized
 * rule classes for:
 * <ul>
 *   <li>Control flow ({@link ControlFlowRules})</li>
 *   <li>Jump statements ({@link JumpStatementRules})</li>
 * </ul>
 * 
 * <p>This class serves as the entry point for registering all statement-related
 * semantic rule handlers.
 * 
 * @see hr.fer.ppj.semantics.analysis.SemanticChecker for the main semantic analysis coordinator
 * @see ExpressionRules for expression-related semantic rules
 * @see DeclarationRules for declaration-related semantic rules
 */
public final class StatementRules {

  private final SemanticChecker checker;
  private final ControlFlowRules controlFlowRules;

  public StatementRules(SemanticChecker checker) {
    this.checker = checker;
    
    // Initialize specialized statement rule handlers
    this.controlFlowRules = new ControlFlowRules(checker);
    new JumpStatementRules(checker);
  }
  
  public void processBlock(NonTerminalNode node) {
    controlFlowRules.processBlock(node);
  }
}
