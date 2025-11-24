package hr.fer.ppj.parser;

import hr.fer.ppj.parser.config.ParserConfig;
import hr.fer.ppj.parser.grammar.FirstSetComputer;
import hr.fer.ppj.parser.grammar.Grammar;
import hr.fer.ppj.parser.grammar.GrammarParser;
import hr.fer.ppj.parser.io.TokenReader;
import hr.fer.ppj.parser.io.TokenReader.Token;
import hr.fer.ppj.parser.lr.LRParser;
import hr.fer.ppj.parser.lr.LRTableBuilder;
import hr.fer.ppj.parser.table.LRTable;
import hr.fer.ppj.parser.tree.ParseTree;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

/**
 * Main parser class that coordinates grammar parsing, table generation, and parsing.
 * 
 * <p>This class orchestrates the entire parsing process:
 * <ol>
 *   <li>Parses the grammar definition from {@code parser_definition.txt}</li>
 *   <li>Computes FIRST sets for the grammar</li>
 *   <li>Builds or loads cached LR(1) parsing tables</li>
 *   <li>Reads input tokens from lexer output</li>
 *   <li>Parses tokens into a parse tree</li>
 *   <li>Generates output files: {@code generativno_stablo.txt} and {@code sintaksno_stablo.txt}</li>
 * </ol>
 * 
 * <p>Usage:
 * <pre>
 * ParserConfig.Config config = ParserConfig.Config.createDefault(inputTokens, outputDir);
 * Parser parser = new Parser();
 * parser.parse(config);
 * </pre>
 * 
 * <p>The parser uses canonical LR(1) parsing algorithm and generates approximately 823 states
 * for the PPJ grammar. Parsing tables are cached to avoid regeneration on every run.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class Parser {
  
  private static final Logger LOG = Logger.getLogger(Parser.class.getName());
  
  /**
   * Parses input tokens according to the grammar and generates output files.
   * 
   * @param config Parser configuration
   * @throws ParserException If parsing fails
   */
  public void parse(ParserConfig.Config config) throws ParserException {
    try {
      // Step 1: Parse grammar
      LOG.info("Parsing grammar from " + config.grammarDefinition());
      Grammar grammar = parseGrammar(config.grammarDefinition());
      
      // Step 2: Build FIRST sets
      LOG.info("Computing FIRST sets");
      FirstSetComputer firstComputer = new FirstSetComputer(grammar);
      
      // Step 3: Build LR(1) parsing table (use cache to avoid regenerating)
      LOG.info("Building LR(1) parsing table");
      LRTable table = hr.fer.ppj.parser.table.LRTableCache.getOrBuild(grammar, firstComputer);
      LOG.info("Using LR(1) table");
      
      // Step 4: Read input tokens
      LOG.info("Reading tokens from " + config.inputTokens());
      List<Token> tokens = readTokens(config.inputTokens());
      
      // Step 5: Parse tokens
      LOG.info("Parsing " + tokens.size() + " tokens");
      LRParser parser = new LRParser(table, grammar);
      ParseTree parseTree = parser.parse(tokens);
      
      // Step 6: Generate output files
      LOG.info("Generating output files");
      writeGenerativeTree(parseTree, config.outputGenerativeTree());
      writeSyntaxTree(parseTree, config.outputSyntaxTree());
      
      LOG.info("Parsing completed successfully");
    } catch (IOException e) {
      throw new ParserException("I/O error: " + e.getMessage(), e);
    } catch (LRParser.ParseException e) {
      // Error message is already in Croatian, just pass it through
      throw new ParserException(e.getMessage(), e);
    }
  }
  
  /**
   * Parses the grammar definition file.
   */
  private Grammar parseGrammar(Path grammarPath) throws IOException {
    GrammarParser parser = new GrammarParser();
    try (Reader reader = Files.newBufferedReader(grammarPath)) {
      parser.parse(reader);
    }
    return new Grammar(parser);
  }
  
  /**
   * Reads tokens from the lexer output file.
   */
  private List<Token> readTokens(Path tokenPath) throws IOException {
    TokenReader reader = new TokenReader();
    try (Reader fileReader = Files.newBufferedReader(tokenPath)) {
      return reader.readTokens(fileReader);
    }
  }
  
  /**
   * Writes the generative tree to file.
   */
  private void writeGenerativeTree(ParseTree tree, Path outputPath) throws IOException {
    String output = tree.toGenerativeTreeString();
    Files.writeString(outputPath, output);
  }
  
  /**
   * Writes the syntax tree to file.
   */
  private void writeSyntaxTree(ParseTree tree, Path outputPath) throws IOException {
    String output = tree.toSyntaxTreeString();
    Files.writeString(outputPath, output);
  }
  
  /**
   * Exception thrown by the parser.
   */
  public static final class ParserException extends Exception {
    public ParserException(String message) {
      super(message);
    }
    
    public ParserException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}

