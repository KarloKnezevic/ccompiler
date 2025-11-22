package hr.fer.ppj.parser.ast;

/**
 * Assignment expression.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record AssignmentExpression(
    Expression left,
    Expression right,
    int line,
    int column
) implements Expression {}

