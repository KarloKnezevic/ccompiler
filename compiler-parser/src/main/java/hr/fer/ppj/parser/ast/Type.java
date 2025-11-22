package hr.fer.ppj.parser.ast;

/**
 * Base sealed interface for types.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface Type extends ASTNode
    permits PrimitiveType, PointerType, ArrayType, StructType {}

