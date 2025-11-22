package hr.fer.ppj.parser.ast;

/**
 * Expression statement.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record ExpressionStatement(
    Expression expression,
    int line,
    int column
) implements Statement {}

