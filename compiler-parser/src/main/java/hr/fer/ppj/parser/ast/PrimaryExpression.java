package hr.fer.ppj.parser.ast;

/**
 * Primary expression (identifier, literal, etc.).
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record PrimaryExpression(
    String type, // IDN, BROJ, ZNAK, NIZ_ZNAKOVA
    String value,
    int line,
    int column
) implements Expression {}

