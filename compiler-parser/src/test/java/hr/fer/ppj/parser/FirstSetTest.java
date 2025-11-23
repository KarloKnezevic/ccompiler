package hr.fer.ppj.parser;

import hr.fer.ppj.parser.grammar.FirstSetComputer;
import hr.fer.ppj.parser.grammar.Grammar;
import hr.fer.ppj.parser.grammar.GrammarParser;
import java.io.StringReader;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for FIRST set computation.
 */
public final class FirstSetTest {
  
  @Test
  void testFirstForTerminal() throws Exception {
    String grammarText = """
        %V <S>
        %T a b
        <S>
        a
        """;
    
    GrammarParser parser = new GrammarParser();
    parser.parse(new StringReader(grammarText));
    Grammar grammar = new Grammar(parser);
    
    FirstSetComputer first = new FirstSetComputer(grammar);
    
    // FIRST(a) = {a}
    Set<String> firstA = first.computeFirst("a");
    assertEquals(Set.of("a"), firstA);
  }
  
  @Test
  void testFirstForNonTerminal() throws Exception {
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
    
    FirstSetComputer first = new FirstSetComputer(grammar);
    
    // FIRST(<A>) = {a, b}
    Set<String> firstA = first.computeFirst("<A>");
    assertTrue(firstA.contains("a"));
    assertTrue(firstA.contains("b"));
  }
  
  @Test
  void testFirstWithEpsilon() throws Exception {
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
    
    FirstSetComputer first = new FirstSetComputer(grammar);
    
    // FIRST(<A>) should contain both 'a' and epsilon ($)
    Set<String> firstA = first.computeFirst("<A>");
    assertTrue(firstA.contains("a"));
    assertTrue(firstA.contains("$"));
  }
  
  @Test
  void testFirstForSequence() throws Exception {
    String grammarText = """
        %V <S> <A> <B>
        %T a b
        <S>
        <A> a
        <A> $
        <B> b
        """;
    
    GrammarParser parser = new GrammarParser();
    parser.parse(new StringReader(grammarText));
    Grammar grammar = new Grammar(parser);
    
    FirstSetComputer first = new FirstSetComputer(grammar);
    
    // FIRST(<A><B>) = FIRST(<A>) if epsilon not in FIRST(<A>), 
    // else FIRST(<A>) ∪ FIRST(<B>) = {a, $} ∪ {b} = {a, b}
    // Epsilon is in FIRST(<A><B>) only if both can derive epsilon
    // Since <B> cannot derive epsilon, epsilon is NOT in FIRST(<A><B>)
    Set<String> firstAB = first.computeFirst(List.of("<A>", "<B>"));
    assertTrue(firstAB.contains("a"), "Should contain 'a' from FIRST(<A>)");
    assertTrue(firstAB.contains("b"), "Should contain 'b' from FIRST(<B>) since <A> can derive epsilon");
    assertFalse(firstAB.contains("$"), "Should NOT contain epsilon since <B> cannot derive epsilon");
  }
}

