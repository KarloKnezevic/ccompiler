package hr.fer.ppj.parser.ast;

import java.util.List;

/**
 * Block statement (compound statement).
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record BlockStatement(
    List<Declaration> declarations,
    List<Statement> statements,
    int line,
    int column
) implements Statement {}

