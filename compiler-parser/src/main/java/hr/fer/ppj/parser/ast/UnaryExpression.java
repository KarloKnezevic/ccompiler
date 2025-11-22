package hr.fer.ppj.parser.ast;

/**
 * Unary operator expression.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record UnaryExpression(
    String operator,
    Expression operand,
    int line,
    int column
) implements Expression {}

