package hr.fer.ppj.parser;

import hr.fer.ppj.parser.grammar.GrammarParser;
import java.io.StringReader;
import java.util.Map;

/**
 * Debug test to see what GrammarParser produces.
 */
public final class GrammarParserDebugTest {
  
  public static void main(String[] args) throws Exception {
    String grammarText = """
        %V <S> <A>
        %T a b
        <S>
        <A> a
        <A> b
        """;
    
    GrammarParser parser = new GrammarParser();
    parser.parse(new StringReader(grammarText));
    
    System.out.println("Non-terminals: " + parser.getNonTerminals());
    System.out.println("Terminals: " + parser.getTerminals());
    System.out.println("\nProductions:");
    for (Map.Entry<String, java.util.List<GrammarParser.Production>> entry : parser.getProductions().entrySet()) {
      System.out.println("  " + entry.getKey() + ":");
      for (GrammarParser.Production prod : entry.getValue()) {
        System.out.println("    -> " + prod.rhs());
      }
    }
  }
}

