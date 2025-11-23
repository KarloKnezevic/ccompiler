package hr.fer.ppj.parser;

import hr.fer.ppj.parser.grammar.GrammarParser;
import java.io.StringReader;
import org.junit.jupiter.api.Test;

/**
 * Detailed test to check grammar parsing with real format.
 */
public final class GrammarParserDetailedTest {
  
  @Test
  void testRealGrammarFormat() throws Exception {
    // Simulate real grammar format (alternatives have leading space)
    String grammarText = """
        %V <prijevodna_jedinica> <vanjska_deklaracija>
        %T IDN
        <prijevodna_jedinica>
         <vanjska_deklaracija>
         <prijevodna_jedinica> <vanjska_deklaracija>
        <vanjska_deklaracija>
         IDN
        """;
    
    GrammarParser parser = new GrammarParser();
    parser.parse(new StringReader(grammarText));
    
    System.out.println("=== PARSED GRAMMAR ===");
    for (var entry : parser.getProductions().entrySet()) {
      System.out.println(entry.getKey() + ":");
      for (var prod : entry.getValue()) {
        System.out.println("  -> " + prod.rhs());
      }
    }
    
    // Check specific productions
    var prijevodnaProds = parser.getProductions().get("<prijevodna_jedinica>");
    System.out.println("\n<prijevodna_jedinica> productions: " + prijevodnaProds.size());
    assert prijevodnaProds != null;
    assert prijevodnaProds.size() == 2 : "Should have 2 productions, got " + prijevodnaProds.size();
    
    boolean hasVanjska = prijevodnaProds.stream().anyMatch(p -> p.rhs().equals(java.util.List.of("<vanjska_deklaracija>")));
    boolean hasBoth = prijevodnaProds.stream().anyMatch(p -> p.rhs().equals(java.util.List.of("<prijevodna_jedinica>", "<vanjska_deklaracija>")));
    
    assert hasVanjska : "Should have production <prijevodna_jedinica> -> <vanjska_deklaracija>";
    assert hasBoth : "Should have production <prijevodna_jedinica> -> <prijevodna_jedinica> <vanjska_deklaracija>";
  }
}

