package hr.fer.ppj.parser;

import hr.fer.ppj.parser.config.ParserConfig;
import hr.fer.ppj.parser.grammar.FirstSetComputer;
import hr.fer.ppj.parser.grammar.Grammar;
import hr.fer.ppj.parser.grammar.GrammarParser;
import hr.fer.ppj.parser.lr.LRTableBuilder;
import hr.fer.ppj.parser.table.LRTable;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Debug test to inspect LR table construction.
 */
public final class ParserDebugTest {
  
  @Test
  void debugLRTableConstruction() throws Exception {
    Path grammarPath = ParserConfig.getParserDefinitionPath();
    GrammarParser parser = new GrammarParser();
    try (var reader = Files.newBufferedReader(grammarPath)) {
      parser.parse(reader);
    }
    
    System.out.println("=== GRAMMAR INFO ===");
    System.out.println("Non-terminals: " + parser.getNonTerminals().size());
    System.out.println("Terminals: " + parser.getTerminals().size());
    System.out.println("Productions: " + parser.getProductions().size());
    
    Grammar grammar = new Grammar(parser);
    System.out.println("\nAugmented start: " + grammar.getAugmentedStartSymbol());
    System.out.println("Original start: " + grammar.getStartSymbol());
    System.out.println("All productions: " + grammar.getAllProductions().size());
    
    FirstSetComputer firstComputer = new FirstSetComputer(grammar);
    
    System.out.println("\n=== BUILDING LR TABLE ===");
    long startTime = System.currentTimeMillis();
    LRTableBuilder tableBuilder = new LRTableBuilder(grammar, firstComputer);
    LRTable table = tableBuilder.build();
    long endTime = System.currentTimeMillis();
    
    System.out.println("\n=== LR TABLE INFO ===");
    System.out.println("States: " + tableBuilder.getStateCount());
    System.out.println("Build time: " + (endTime - startTime) + " ms");
    
    // Check some actions
    System.out.println("\n=== STATE DETAILS ===");
    for (int state = 0; state < Math.min(5, tableBuilder.getStateCount()); state++) {
      System.out.println("\nState " + state + ":");
      var itemSet = tableBuilder.getItemSet(state);
      System.out.println("  Items: " + itemSet.getItems().size());
      for (var item : itemSet.getItems()) {
        if (item.isReduceItem()) {
          System.out.println("    REDUCE: " + item.getProduction().lhs() + " -> " + item.getProduction().rhs() + " , lookahead: " + item.getLookahead());
        } else {
          System.out.println("    SHIFT: " + item.getProduction().lhs() + " -> ... " + item.getNextSymbol() + " ... , lookahead: " + item.getLookahead());
        }
      }
      // Check for some common terminals
      String[] testTerminals = {"KR_INT", "IDN", "L_ZAGRADA", "D_ZAGRADA", "#"};
      System.out.println("  Actions:");
      for (String term : testTerminals) {
        String action = table.getAction(state, term);
        if (action != null) {
          System.out.println("    " + term + " -> " + action);
        }
      }
    }
    
    assertTrue(tableBuilder.getStateCount() > 0, "Should have states");
  }
}

