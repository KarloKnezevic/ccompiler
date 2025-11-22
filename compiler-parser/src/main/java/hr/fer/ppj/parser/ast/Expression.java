package hr.fer.ppj.parser.ast;

/**
 * Base sealed interface for all expressions.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface Expression extends ASTNode
    permits BinaryExpression, UnaryExpression, PrimaryExpression, AssignmentExpression {}

