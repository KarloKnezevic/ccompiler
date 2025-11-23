package hr.fer.ppj.parser.table;

import hr.fer.ppj.parser.grammar.Grammar;
import hr.fer.ppj.parser.grammar.FirstSetComputer;
import hr.fer.ppj.parser.lr.LRTableBuilder;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Cache for LR parsing tables to avoid regenerating them on every test run.
 * 
 * <p>Tables are serialized to disk and can be reused across test runs.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LRTableCache {
  
  private static final Logger LOG = Logger.getLogger(LRTableCache.class.getName());
  private static final String CACHE_DIR = "target/parser-cache";
  private static final String TABLE_FILE = "lr_table.ser";
  
  /**
   * Gets or builds the LR table for the given grammar.
   * 
   * <p>If a cached table exists and is valid, it is loaded.
   * Otherwise, a new table is built and cached.
   * 
   * @param grammar The grammar
   * @param firstComputer The FIRST set computer
   * @return The LR parsing table
   */
  public static LRTable getOrBuild(Grammar grammar, FirstSetComputer firstComputer) {
    Path cachePath = getCachePath();
    
    // Try to load from cache
    LRTable cached = loadFromCache(cachePath);
    if (cached != null) {
      LOG.info("Loaded LR table from cache");
      return cached;
    }
    
    // Build new table
    LOG.info("Building new LR table (this may take a while)...");
    long startTime = System.currentTimeMillis();
    LRTableBuilder builder = new LRTableBuilder(grammar, firstComputer);
    LRTable table = builder.build();
    long endTime = System.currentTimeMillis();
    
    LOG.info("Built LR table with " + builder.getStateCount() + " states in " + (endTime - startTime) + " ms");
    
    // Save to cache
    saveToCache(table, cachePath);
    
    return table;
  }
  
  /**
   * Clears the cache.
   */
  public static void clearCache() {
    Path cachePath = getCachePath();
    try {
      Files.deleteIfExists(cachePath);
      LOG.info("Cache cleared");
    } catch (IOException e) {
      LOG.warning("Failed to clear cache: " + e.getMessage());
    }
  }
  
  private static Path getCachePath() {
    Path cacheDir = Paths.get(CACHE_DIR);
    try {
      Files.createDirectories(cacheDir);
    } catch (IOException e) {
      // Ignore
    }
    return cacheDir.resolve(TABLE_FILE);
  }
  
  private static LRTable loadFromCache(Path cachePath) {
    if (!Files.exists(cachePath)) {
      return null;
    }
    
    try (ObjectInputStream ois = new ObjectInputStream(Files.newInputStream(cachePath))) {
      return (LRTable) ois.readObject();
    } catch (Exception e) {
      LOG.warning("Failed to load cache: " + e.getMessage());
      return null;
    }
  }
  
  private static void saveToCache(LRTable table, Path cachePath) {
    try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(cachePath))) {
      oos.writeObject(table);
      LOG.info("Saved LR table to cache");
    } catch (IOException e) {
      LOG.warning("Failed to save cache: " + e.getMessage());
    }
  }
}

