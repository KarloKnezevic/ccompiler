package hr.fer.ppj.parser;

import hr.fer.ppj.lexer.gen.LexerGenerator;
import hr.fer.ppj.lexer.gen.LexerGeneratorResult;
import hr.fer.ppj.lexer.io.Lexer;
import hr.fer.ppj.lexer.io.Token;
import hr.fer.ppj.parser.config.ParserConfig;
import hr.fer.ppj.parser.grammar.FirstSetComputer;
import hr.fer.ppj.parser.grammar.Grammar;
import hr.fer.ppj.parser.grammar.GrammarParser;
import hr.fer.ppj.parser.io.TokenReader;
import hr.fer.ppj.parser.lr.LRParser;
import hr.fer.ppj.parser.lr.LRTableBuilder;
import hr.fer.ppj.parser.table.LRTable;
import hr.fer.ppj.parser.tree.ParseTree;
import java.io.FileReader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple parser tests to verify basic functionality.
 */
public final class ParserSimpleTest {
  
  private static LexerGeneratorResult lexerGeneratorResult;
  
  @BeforeAll
  static void setup() throws Exception {
    LexerGenerator generator = new LexerGenerator();
    Path lexerDefinitionPath = hr.fer.ppj.lexer.config.LexerConfig.getLexerDefinitionPath();
    try (FileReader reader = new FileReader(lexerDefinitionPath.toFile())) {
      lexerGeneratorResult = generator.generate(reader);
    }
  }
  
  @Test
  void testSimpleProgram(@TempDir Path tempDir) throws Exception {
    // Very simple program
    String program = "int main(void) { return 0; }";
    
    // Tokenize
    Lexer lexer = new Lexer(lexerGeneratorResult);
    List<Token> tokens;
    try (StringReader reader = new StringReader(program)) {
      tokens = lexer.tokenize(reader);
    }
    
    assertFalse(tokens.isEmpty(), "Should have tokens");
    
    // Format lexer output
    String lexerOutput = formatLexerOutput(lexer, tokens);
    Path tokenFile = tempDir.resolve("tokens.txt");
    Files.writeString(tokenFile, lexerOutput);
    
    // Parse grammar
    Path grammarPath = ParserConfig.getParserDefinitionPath();
    GrammarParser parser = new GrammarParser();
    try (var reader = Files.newBufferedReader(grammarPath)) {
      parser.parse(reader);
    }
    Grammar grammar = new Grammar(parser);
    
    // Build FIRST sets
    FirstSetComputer firstComputer = new FirstSetComputer(grammar);
    
    // Build LR table
    System.out.println("Building LR table...");
    long startTime = System.currentTimeMillis();
    LRTableBuilder tableBuilder = new LRTableBuilder(grammar, firstComputer);
    LRTable table = tableBuilder.build();
    long endTime = System.currentTimeMillis();
    System.out.println("Built LR table with " + tableBuilder.getStateCount() + " states in " + (endTime - startTime) + " ms");
    
    assertTrue(tableBuilder.getStateCount() > 0, "Should have at least one state");
    assertTrue(tableBuilder.getStateCount() < 10000, "Should not have too many states");
    
    // Read tokens
    TokenReader tokenReader = new TokenReader();
    List<TokenReader.Token> parserTokens;
    try (var reader = Files.newBufferedReader(tokenFile)) {
      parserTokens = tokenReader.readTokens(reader);
    }
    
    // Parse
    System.out.println("Parsing " + parserTokens.size() + " tokens...");
    LRParser lrParser = new LRParser(table, grammar);
    ParseTree parseTree = lrParser.parse(parserTokens);
    
    assertNotNull(parseTree, "Should produce parse tree");
    assertEquals("<prijevodna_jedinica>", parseTree.getSymbol(), "Root should be start symbol");
  }
  
  private String formatLexerOutput(Lexer lexer, List<Token> tokens) {
    StringBuilder sb = new StringBuilder();
    
    // Symbol table
    sb.append("tablica znakova:\n");
    sb.append("indeks   uniformni znak   izvorni tekst\n");
    List<Lexer.SymbolTableEntry> symbolTable = lexer.getSymbolTable();
    for (int i = 0; i < symbolTable.size(); i++) {
      Lexer.SymbolTableEntry entry = symbolTable.get(i);
      sb.append(String.format("     %d   %-18s %s%n", i, entry.token(), entry.text()));
    }
    
    // Token stream
    sb.append("\nniz uniformnih znakova:\n");
    sb.append("uniformni znak    redak    indeks u tablicu znakova\n");
    for (Token token : tokens) {
      sb.append(String.format("%-18s %5d       %d%n", 
          token.type(), 
          token.line(), 
          token.symbolTableIndex()));
    }
    
    return sb.toString();
  }
}

