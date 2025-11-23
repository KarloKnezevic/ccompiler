package hr.fer.ppj.parser;

import hr.fer.ppj.parser.grammar.GrammarParser;
import hr.fer.ppj.parser.lr.LRItem;
import hr.fer.ppj.parser.lr.LRItemSet;
import java.io.StringReader;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify LRItemSet equality and merging.
 */
public final class LRItemSetTest {
  
  @Test
  void testItemSetEquality() throws Exception {
    String grammarText = """
        %V <S> <A>
        %T a b
        <S>
        <A> a
        <A> b
        """;
    
    GrammarParser parser = new GrammarParser();
    parser.parse(new StringReader(grammarText));
    
    var prods = parser.getProductions().get("<A>");
    assertNotNull(prods);
    assertFalse(prods.isEmpty());
    
    var prodA = prods.get(0);
    
    // Create two item sets with same production but different lookaheads
    LRItemSet set1 = new LRItemSet();
    set1.addItem(new LRItem(prodA, 0, Set.of("a")));
    
    LRItemSet set2 = new LRItemSet();
    set2.addItem(new LRItem(prodA, 0, Set.of("b")));
    
    // They should be different (different lookaheads)
    assertNotEquals(set1, set2, "Item sets with different lookaheads should be different");
    
    // Create item set with same production and same lookahead
    LRItemSet set3 = new LRItemSet();
    set3.addItem(new LRItem(prodA, 0, Set.of("a")));
    
    // They should be equal
    assertEquals(set1, set3, "Item sets with same items should be equal");
    
    // Test merging
    LRItemSet set4 = new LRItemSet();
    set4.addItem(new LRItem(prodA, 0, Set.of("a")));
    set4.addItem(new LRItem(prodA, 0, Set.of("b")));
    
    // set4 should have merged lookaheads {a, b}
    var items = set4.getItems();
    assertEquals(1, items.size(), "Merged set should have one item");
    var item = items.iterator().next();
    assertTrue(item.getLookahead().contains("a"));
    assertTrue(item.getLookahead().contains("b"));
  }
}

