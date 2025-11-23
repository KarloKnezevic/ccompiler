package hr.fer.ppj.parser;

import hr.fer.ppj.parser.grammar.Grammar;
import hr.fer.ppj.parser.grammar.GrammarParser;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

/**
 * Debug test to check grammar parsing.
 */
public final class GrammarDebugTest {
  
  @Test
  void debugGrammarParsing() throws Exception {
    String grammarText = """
        %V <prijevodna_jedinica> <vanjska_deklaracija>
        %T IDN
        <prijevodna_jedinica>
         <vanjska_deklaracija>
        <vanjska_deklaracija>
         IDN
        """;
    
    GrammarParser parser = new GrammarParser();
    parser.parse(new StringReader(grammarText));
    
    System.out.println("=== PARSED GRAMMAR ===");
    System.out.println("Non-terminals: " + parser.getNonTerminals());
    System.out.println("Terminals: " + parser.getTerminals());
    System.out.println("\nProductions:");
    for (var entry : parser.getProductions().entrySet()) {
      System.out.println("  " + entry.getKey() + ":");
      for (var prod : entry.getValue()) {
        System.out.println("    -> " + prod.rhs());
      }
    }
    
    Grammar grammar = new Grammar(parser);
    System.out.println("\n=== AUGMENTED GRAMMAR ===");
    System.out.println("Augmented start: " + grammar.getAugmentedStartSymbol());
    System.out.println("All productions:");
    for (var prod : grammar.getAllProductions()) {
      System.out.println("  " + prod.lhs() + " -> " + prod.rhs());
    }
  }
}

