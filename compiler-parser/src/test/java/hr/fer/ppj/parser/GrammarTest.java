package hr.fer.ppj.parser;

import hr.fer.ppj.parser.grammar.Grammar;
import hr.fer.ppj.parser.grammar.GrammarParser;
import java.io.StringReader;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Grammar class.
 */
public final class GrammarTest {
  
  @Test
  void testGrammarAugmentation() throws Exception {
    String grammarText = """
        %V <S> <A>
        %T a b
        <S>
        <A> a
        <A> b
        """;
    
    GrammarParser parser = new GrammarParser();
    parser.parse(new StringReader(grammarText));
    
    Grammar grammar = new Grammar(parser);
    
    // Check augmented start symbol
    assertEquals("<pocetni_nezavrsni_znak>", grammar.getAugmentedStartSymbol());
    assertEquals("<S>", grammar.getStartSymbol());
    
    // Check that augmented production exists
    var allProds = grammar.getAllProductions();
    assertFalse(allProds.isEmpty());
    assertEquals("<pocetni_nezavrsni_znak>", allProds.get(0).lhs());
    assertEquals("<S>", allProds.get(0).rhs().get(0));
  }
  
  @Test
  void testGrammarProductions() throws Exception {
    String grammarText = """
        %V <S> <A>
        %T a b
        <S>
        <A> a
        <A> b
        """;
    
    GrammarParser parser = new GrammarParser();
    parser.parse(new StringReader(grammarText));
    
    Grammar grammar = new Grammar(parser);
    
    // Check productions for <A>
    var aProds = grammar.getProductions("<A>");
    assertEquals(2, aProds.size());
    
    // Check that productions have correct RHS
    boolean hasA = aProds.stream().anyMatch(p -> p.rhs().equals(List.of("a")));
    boolean hasB = aProds.stream().anyMatch(p -> p.rhs().equals(List.of("b")));
    assertTrue(hasA, "Should have production A -> a");
    assertTrue(hasB, "Should have production A -> b");
  }
  
  @Test
  void testEpsilonProduction() throws Exception {
    String grammarText = """
        %V <S> <A>
        %T a
        <S>
        <A> a
        <A> $
        """;
    
    GrammarParser parser = new GrammarParser();
    parser.parse(new StringReader(grammarText));
    
    Grammar grammar = new Grammar(parser);
    
    // Check that epsilon production is parsed correctly
    var aProds = grammar.getProductions("<A>");
    assertEquals(2, aProds.size());
    
    // One production should have empty RHS (epsilon)
    boolean hasEpsilon = aProds.stream().anyMatch(p -> p.rhs().isEmpty());
    assertTrue(hasEpsilon, "Should have epsilon production");
  }
}

