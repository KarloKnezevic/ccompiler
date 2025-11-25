package hr.fer.ppj.semantics.io;

/**
 * Represents a lexer token enriched with line information that is required by the
 * semantic analyser to produce precise diagnostics.
 *
 * @param type   uniform token name (e.g. {@code IDN}, {@code KR_INT})
 * @param line   line number from the original source (1-based)
 * @param lexeme lexeme string as emitted by the lexer
 */
public record SemanticToken(String type, int line, String lexeme) {}

