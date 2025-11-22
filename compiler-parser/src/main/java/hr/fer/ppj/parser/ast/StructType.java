package hr.fer.ppj.parser.ast;

/**
 * Struct type.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record StructType(
    String name, // struct tag name
    int line,
    int column
) implements Type {}

