package hr.fer.ppj.parser.ast;

/**
 * Pointer type.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record PointerType(
    Type baseType,
    boolean isConst,
    int line,
    int column
) implements Type {}

