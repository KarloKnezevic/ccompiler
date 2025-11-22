package hr.fer.ppj.parser.ast;

/**
 * Variable declaration.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record VariableDeclaration(
    Type type,
    String name,
    Expression initializer, // null if no initializer
    int line,
    int column
) implements Declaration {}

