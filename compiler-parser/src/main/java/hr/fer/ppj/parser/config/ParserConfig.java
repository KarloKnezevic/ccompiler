package hr.fer.ppj.parser.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Central configuration for the parser.
 * 
 * <p>This class provides configuration paths for:
 * <ul>
 *   <li>Grammar definition file (parser_definition.txt)</li>
 *   <li>Input tokens (lexer output)</li>
 *   <li>Output files (generativno_stablo.txt, sintaksno_stablo.txt)</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @version 1.0
 */
public final class ParserConfig {
  
  /**
   * Default filename for the parser definition file.
   */
  private static final String PARSER_DEFINITION_FILENAME = "parser_definition.txt";
  
  /**
   * Default directory name for configuration files.
   */
  private static final String CONFIG_DIRECTORY = "config";
  
  /**
   * Private constructor to prevent instantiation.
   */
  private ParserConfig() {
    throw new AssertionError("ParserConfig should not be instantiated");
  }
  
  /**
   * Gets the path to the parser definition file.
   * 
   * <p>The path is resolved relative to the project root directory.
   * 
   * @return Path to the parser definition file
   */
  public static Path getParserDefinitionPath() {
    String customPath = System.getenv("PARSER_DEFINITION_PATH");
    if (customPath != null && !customPath.isEmpty()) {
      Path path = Paths.get(customPath);
      if (java.nio.file.Files.exists(path)) {
        return path;
      }
      throw new IllegalStateException(
          "PARSER_DEFINITION_PATH environment variable points to non-existent file: " + customPath);
    }
    
    // Default: config/parser_definition.txt relative to project root
    Path currentDir = Paths.get(System.getProperty("user.dir"));
    Path searchDir = currentDir;
    
    while (searchDir != null) {
      boolean hasPom = java.nio.file.Files.exists(searchDir.resolve("pom.xml"));
      boolean hasConfig = java.nio.file.Files.isDirectory(searchDir.resolve(CONFIG_DIRECTORY));
      
      if (hasPom && hasConfig) {
        Path configPath = searchDir.resolve(CONFIG_DIRECTORY).resolve(PARSER_DEFINITION_FILENAME);
        if (java.nio.file.Files.exists(configPath)) {
          return configPath;
        }
        return configPath;
      }
      
      searchDir = searchDir.getParent();
    }
    
    // Fallback: current directory
    Path configPath = currentDir.resolve(CONFIG_DIRECTORY).resolve(PARSER_DEFINITION_FILENAME);
    if (java.nio.file.Files.exists(configPath)) {
      return configPath;
    }
    return configPath;
  }
  
  /**
   * Configuration record for parser execution.
   * 
   * @param grammarDefinition Path to grammar definition file
   * @param inputTokens Path to lexer output (tokens)
   * @param outputGenerativeTree Path to output generative tree file
   * @param outputSyntaxTree Path to output syntax tree file
   */
  public record Config(
      Path grammarDefinition,
      Path inputTokens,
      Path outputGenerativeTree,
      Path outputSyntaxTree
  ) {
    /**
     * Creates a default configuration using standard paths.
     * 
     * @param inputTokens Path to lexer output
     * @param outputDir Directory for output files
     * @return Config instance
     */
    public static Config createDefault(Path inputTokens, Path outputDir) {
      return new Config(
          getParserDefinitionPath(),
          inputTokens,
          outputDir.resolve("generativno_stablo.txt"),
          outputDir.resolve("sintaksno_stablo.txt")
      );
    }
  }
}

