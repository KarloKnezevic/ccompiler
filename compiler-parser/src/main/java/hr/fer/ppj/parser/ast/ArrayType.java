package hr.fer.ppj.parser.ast;

/**
 * Array type.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record ArrayType(
    Type elementType,
    Expression size, // null if unspecified
    int line,
    int column
) implements Type {}

