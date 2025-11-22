package hr.fer.ppj.parser.ast;

/**
 * Primitive type (int, char, float, void).
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record PrimitiveType(
    String name, // KR_INT, KR_CHAR, KR_FLOAT, KR_VOID
    int line,
    int column
) implements Type {}

