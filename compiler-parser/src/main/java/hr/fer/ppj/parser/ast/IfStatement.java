package hr.fer.ppj.parser.ast;

/**
 * If statement (with optional else).
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IfStatement(
    Expression condition,
    Statement thenBranch,
    Statement elseBranch, // null if no else
    int line,
    int column
) implements Statement {}

