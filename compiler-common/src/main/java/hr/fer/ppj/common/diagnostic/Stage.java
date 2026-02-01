package hr.fer.ppj.common.diagnostic;

/**
 * The compilation stage where a diagnostic was produced.
 */
public enum Stage {
    LEXER,
    PARSER,
    SEMANTICS,
    IR,
    CODEGEN
}
