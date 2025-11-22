package hr.fer.ppj.parser.ast;

/**
 * Break statement.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record BreakStatement(
    int line,
    int column
) implements Statement {}

