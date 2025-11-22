package hr.fer.ppj.lexer.gen;

import hr.fer.ppj.lexer.dfa.DFA;
import java.util.List;
import java.util.Map;

/**
 * Result of lexer generation containing states, tokens, DFAs, and rules.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record LexerGeneratorResult(
    List<String> states,
    List<String> tokens,
    Map<String, DFA> stateDFAs,
    List<LexerSpecParser.LexerRule> rules
) {}

