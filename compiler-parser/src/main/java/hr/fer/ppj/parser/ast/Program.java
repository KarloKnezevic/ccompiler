package hr.fer.ppj.parser.ast;

import java.util.List;

/**
 * Root AST node representing a translation unit.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record Program(
    List<Declaration> declarations,
    int line,
    int column
) implements ASTNode {}

