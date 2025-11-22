package hr.fer.ppj.lexer.config;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Central configuration for the lexer generator.
 * 
 * <p>This class provides a single point of configuration for the lexer definition file path.
 * All components that need to access the lexer definition should use this class to obtain
 * the correct path, ensuring consistency and easy maintenance.
 * 
 * <p>The lexer definition file is located at {@code config/lexer_definition.txt} relative
 * to the project root directory.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @version 1.0
 */
public final class LexerConfig {
  
  /**
   * Default filename for the lexer definition file.
   */
  private static final String LEXER_DEFINITION_FILENAME = "lexer_definition.txt";
  
  /**
   * Default directory name for configuration files.
   */
  private static final String CONFIG_DIRECTORY = "config";
  
  /**
   * Private constructor to prevent instantiation.
   */
  private LexerConfig() {
    throw new AssertionError("LexerConfig should not be instantiated");
  }
  
  /**
   * Gets the path to the lexer definition file.
   * 
   * <p>The path is resolved relative to the project root directory. The method first
   * checks if a custom path is set via the {@code LEXER_DEFINITION_PATH} environment
   * variable. If not set, it defaults to {@code config/lexer_definition.txt} relative
   * to the project root.
   * 
   * <p>The project root is determined by:
   * <ol>
   *   <li>Checking if the current working directory contains a {@code config} directory</li>
   *   <li>If not, going up one level (for Maven projects where we might be in a submodule)</li>
   *   <li>If still not found, using the current working directory as project root</li>
   * </ol>
   * 
   * @return Path to the lexer definition file
   * @throws IllegalStateException if the lexer definition file cannot be located
   */
  public static Path getLexerDefinitionPath() {
    // Check for custom path via environment variable (highest priority)
    String customPath = System.getenv("LEXER_DEFINITION_PATH");
    if (customPath != null && !customPath.isEmpty()) {
      Path path = Paths.get(customPath);
      if (java.nio.file.Files.exists(path)) {
        return path;
      }
      throw new IllegalStateException(
          "LEXER_DEFINITION_PATH environment variable points to non-existent file: " + customPath);
    }
    
    // Default: config/lexer_definition.txt relative to project root
    // Project root is determined by looking for a directory that contains both
    // pom.xml AND config/ directory (the actual project root)
    Path currentDir = Paths.get(System.getProperty("user.dir"));
    
    // Strategy: Go up from current directory until we find a directory that contains
    // both pom.xml AND config/ directory (this is the project root)
    Path searchDir = currentDir;
    while (searchDir != null) {
      // Check if this directory contains both pom.xml and config/ directory
      boolean hasPom = java.nio.file.Files.exists(searchDir.resolve("pom.xml"));
      boolean hasConfig = java.nio.file.Files.isDirectory(searchDir.resolve(CONFIG_DIRECTORY));
      
      if (hasPom && hasConfig) {
        // This is the project root (has both pom.xml and config/)
        Path configPath = searchDir.resolve(CONFIG_DIRECTORY).resolve(LEXER_DEFINITION_FILENAME);
        if (java.nio.file.Files.exists(configPath)) {
          return configPath;
        }
        // Even if file doesn't exist, return the expected path relative to project root
        return configPath;
      }
      
      // If we found pom.xml but no config/, continue searching up (might be a submodule)
      searchDir = searchDir.getParent();
    }
    
    // Fallback: if no project root found, try current directory
    Path configPath = currentDir.resolve(CONFIG_DIRECTORY).resolve(LEXER_DEFINITION_FILENAME);
    if (java.nio.file.Files.exists(configPath)) {
      return configPath;
    }
    
    // Last resort: return expected path relative to current directory
    return configPath;
  }
  
  /**
   * Gets the lexer definition filename.
   * 
   * @return The lexer definition filename
   */
  public static String getLexerDefinitionFilename() {
    return LEXER_DEFINITION_FILENAME;
  }
  
  /**
   * Gets the configuration directory name.
   * 
   * @return The configuration directory name
   */
  public static String getConfigDirectory() {
    return CONFIG_DIRECTORY;
  }
}

