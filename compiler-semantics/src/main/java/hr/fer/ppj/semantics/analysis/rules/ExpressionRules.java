package hr.fer.ppj.semantics.analysis.rules;
import hr.fer.ppj.semantics.analysis.SemanticChecker;

/**
 * Semantic rule implementations for expression-related productions.
 * 
 * <p>This class orchestrates expression analysis and delegates to specialized
 * rule classes for:
 * <ul>
 *   <li>Primary expressions ({@link PrimaryExpressionRules})</li>
 *   <li>Postfix expressions ({@link PostfixExpressionRules})</li>
 *   <li>Unary expressions ({@link UnaryExpressionRules})</li>
 *   <li>Binary expressions ({@link BinaryExpressionRules})</li>
 * </ul>
 * 
 * <p>This class serves as the entry point for registering all expression-related
 * semantic rule handlers.
 * 
 * @see hr.fer.ppj.semantics.analysis.SemanticChecker for the main semantic analysis coordinator
 * @see DeclarationRules for declaration-related semantic rules
 * @see StatementRules for statement-related semantic rules
 */
public final class ExpressionRules {

  public ExpressionRules(SemanticChecker checker) {
    // Initialize specialized expression rule handlers
    new PrimaryExpressionRules(checker);
    new PostfixExpressionRules(checker);
    new UnaryExpressionRules(checker);
    new BinaryExpressionRules(checker);
  }
}
