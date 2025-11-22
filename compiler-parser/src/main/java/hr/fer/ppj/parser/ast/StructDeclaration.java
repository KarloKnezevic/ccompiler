package hr.fer.ppj.parser.ast;

import java.util.List;

/**
 * Struct declaration/definition.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record StructDeclaration(
    String name, // null if anonymous
    List<VariableDeclaration> fields,
    int line,
    int column
) implements Declaration {}

