package hr.fer.ppj.parser.ast;

/**
 * While loop statement.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record WhileStatement(
    Expression condition,
    Statement body,
    int line,
    int column
) implements Statement {}

