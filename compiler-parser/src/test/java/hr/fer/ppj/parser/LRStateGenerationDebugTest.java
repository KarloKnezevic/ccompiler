package hr.fer.ppj.parser;

import hr.fer.ppj.parser.config.ParserConfig;
import hr.fer.ppj.parser.grammar.FirstSetComputer;
import hr.fer.ppj.parser.grammar.Grammar;
import hr.fer.ppj.parser.grammar.GrammarParser;
import hr.fer.ppj.parser.lr.LRItem;
import hr.fer.ppj.parser.lr.LRItemSet;
import hr.fer.ppj.parser.lr.LRClosure;
import hr.fer.ppj.parser.lr.LRGoto;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Debug test to understand why only 823 states are generated instead of ~39000.
 */
public final class LRStateGenerationDebugTest {
  
  @Test
  void debugStateGeneration() throws Exception {
    Path grammarPath = ParserConfig.getParserDefinitionPath();
    GrammarParser parser = new GrammarParser();
    try (var reader = Files.newBufferedReader(grammarPath)) {
      parser.parse(reader);
    }
    
    Grammar grammar = new Grammar(parser);
    FirstSetComputer firstComputer = new FirstSetComputer(grammar);
    LRClosure closure = new LRClosure(grammar, firstComputer);
    LRGoto gotoOp = new LRGoto(closure);
    
    // Create initial state
    var augmentedStart = grammar.getAllProductions().get(0);
    LRItem initialItem = new LRItem(augmentedStart, 0, Set.of("#"));
    LRItemSet initialSet = new LRItemSet();
    initialSet.addItem(initialItem);
    initialSet = closure.closure(initialSet);
    
    System.out.println("=== INITIAL STATE ===");
    System.out.println("Items: " + initialSet.getItems().size());
    for (var item : initialSet.getItems()) {
      System.out.println("  " + item);
    }
    
    // Test GOTO from initial state
    System.out.println("\n=== TESTING GOTO FROM INITIAL STATE ===");
    String[] testSymbols = {"<prijevodna_jedinica>", "<vanjska_deklaracija>", "KR_INT", "IDN"};
    for (String symbol : testSymbols) {
      LRItemSet nextSet = gotoOp.gotoSet(initialSet, symbol);
      if (nextSet != null) {
        System.out.println("GOTO(0, " + symbol + ") -> " + nextSet.getItems().size() + " items");
        if (nextSet.getItems().size() <= 5) {
          for (var item : nextSet.getItems()) {
            System.out.println("  " + item);
          }
        }
      } else {
        System.out.println("GOTO(0, " + symbol + ") -> null");
      }
    }
    
    // Check if item sets with different lookaheads are being merged incorrectly
    System.out.println("\n=== TESTING ITEM SET EQUALITY ===");
    LRItemSet set1 = new LRItemSet();
    set1.addItem(new LRItem(augmentedStart, 1, Set.of("#")));
    
    LRItemSet set2 = new LRItemSet();
    set2.addItem(new LRItem(augmentedStart, 1, Set.of("$")));
    
    System.out.println("set1.equals(set2): " + set1.equals(set2));
    System.out.println("set1.hashCode(): " + set1.hashCode());
    System.out.println("set2.hashCode(): " + set2.hashCode());
    
    // They should be different (different lookaheads)
    if (set1.equals(set2)) {
      System.out.println("ERROR: Item sets with different lookaheads are being treated as equal!");
    } else {
      System.out.println("OK: Item sets with different lookaheads are correctly treated as different");
    }
  }
}

