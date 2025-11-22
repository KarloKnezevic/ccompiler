package hr.fer.ppj.parser.ast;

import java.util.List;

/**
 * Function declaration/definition.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record FunctionDeclaration(
    Type returnType,
    String name,
    List<VariableDeclaration> parameters,
    BlockStatement body, // null if just declaration
    int line,
    int column
) implements Declaration {}

