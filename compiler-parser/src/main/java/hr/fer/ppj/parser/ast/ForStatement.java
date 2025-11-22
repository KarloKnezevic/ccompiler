package hr.fer.ppj.parser.ast;

/**
 * For loop statement.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record ForStatement(
    Expression init, // null if omitted
    Expression condition, // null if omitted
    Expression update, // null if omitted
    Statement body,
    int line,
    int column
) implements Statement {}

