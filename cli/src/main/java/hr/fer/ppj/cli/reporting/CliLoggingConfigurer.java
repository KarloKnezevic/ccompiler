package hr.fer.ppj.cli.reporting;

import java.util.Locale;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Applies logging defaults suitable for normal CLI usage.
 */
public final class CliLoggingConfigurer {

  private static final String PARSER_LOG_LEVEL_PROPERTY = "ccompiler.parser.log.level";
  private static final String GLOBAL_LOG_LEVEL_PROPERTY = "ccompiler.log.level";
  private static final Level DEFAULT_PARSER_LOG_LEVEL = Level.WARNING;
  private static final Level DEFAULT_GLOBAL_LOG_LEVEL = Level.WARNING;

  private CliLoggingConfigurer() {
  }

  /**
   * Configures package-level logger filters for the CLI process.
   */
  public static void configure() {
    configureGlobalLogging();
    configureParserLogs();
  }

  private static void configureGlobalLogging() {
    Level level = resolveConfiguredLevel(GLOBAL_LOG_LEVEL_PROPERTY, DEFAULT_GLOBAL_LOG_LEVEL);
    Logger rootLogger = LogManager.getLogManager().getLogger("");
    if (rootLogger != null) {
      rootLogger.setLevel(level);
      for (Handler handler : rootLogger.getHandlers()) {
        handler.setLevel(level);
      }
    }
  }

  private static void configureParserLogs() {
    Level level = resolveConfiguredLevel(PARSER_LOG_LEVEL_PROPERTY, DEFAULT_PARSER_LOG_LEVEL);
    Logger.getLogger("hr.fer.ppj.parser").setLevel(level);
    Logger.getLogger("hr.fer.ppj.parser.Parser").setLevel(level);
    Logger.getLogger("hr.fer.ppj.parser.table.LRTableCache").setLevel(level);
    Logger.getLogger("hr.fer.ppj.parser.lr.LRTableBuilder").setLevel(level);
    Logger.getLogger("hr.fer.ppj.parser.lr.LRParser").setLevel(level);
  }

  private static Level resolveConfiguredLevel(String propertyName, Level fallback) {
    String configured = System.getProperty(propertyName);
    if (configured == null || configured.isBlank()) {
      return fallback;
    }

    try {
      return Level.parse(configured.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ignored) {
      return fallback;
    }
  }
}
