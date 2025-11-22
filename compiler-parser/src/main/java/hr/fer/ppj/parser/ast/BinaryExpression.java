package hr.fer.ppj.parser.ast;

/**
 * Binary operator expression.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record BinaryExpression(
    Expression left,
    String operator,
    Expression right,
    int line,
    int column
) implements Expression {}

