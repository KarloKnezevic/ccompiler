package hr.fer.ppj.parser.ast;

/**
 * Continue statement.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record ContinueStatement(
    int line,
    int column
) implements Statement {}

