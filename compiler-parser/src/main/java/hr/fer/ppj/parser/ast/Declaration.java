package hr.fer.ppj.parser.ast;

/**
 * Base sealed interface for all declarations.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface Declaration extends ASTNode
    permits VariableDeclaration, FunctionDeclaration, StructDeclaration {}

