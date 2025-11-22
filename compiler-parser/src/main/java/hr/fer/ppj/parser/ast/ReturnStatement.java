package hr.fer.ppj.parser.ast;

/**
 * Return statement.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record ReturnStatement(
    Expression value, // null if no return value
    int line,
    int column
) implements Statement {}

