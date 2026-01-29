package hr.fer.ppj.ir.util;

import hr.fer.ppj.ir.model.IrTemp;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Cache for block-local symbol addresses.
 *
 * <p>This cache tracks addresses of named symbols (locals, params, globals)
 * within a single basic block. Once an address temp is created for a symbol
 * in a block, subsequent uses of that symbol in the same block can reuse
 * the cached address temp.
 *
 * <p>The cache is cleared when a new basic block starts, ensuring addresses
 * are only reused within the same block.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BlockLocalSymbolAddressCache {

  private final Map<String, IrTemp> cache = new HashMap<>();

  /**
   * Gets a cached address temp for a symbol, if available.
   *
   * @param symbolRefKey the symbol reference key (e.g., "local:a", "param:x", "global:g")
   * @return the cached address temp, or null if not cached
   */
  public IrTemp get(String symbolRefKey) {
    return cache.get(symbolRefKey);
  }

  /**
   * Caches an address temp for a symbol.
   *
   * @param symbolRefKey the symbol reference key (e.g., "local:a", "param:x", "global:g")
   * @param addrTemp the address temp to cache
   */
  public void put(String symbolRefKey, IrTemp addrTemp) {
    Objects.requireNonNull(symbolRefKey, "symbolRefKey must not be null");
    Objects.requireNonNull(addrTemp, "addrTemp must not be null");
    cache.put(symbolRefKey, addrTemp);
  }

  /**
   * Clears the cache for a new basic block.
   *
   * <p>This should be called whenever a new basic block starts.
   */
  public void clearForNewBlock() {
    cache.clear();
  }

  /**
   * Removes a cached address for a specific symbol.
   *
   * <p>This should be called after a store to the symbol, to ensure
   * subsequent loads use a fresh addr_of_symbol instruction.
   *
   * @param symbolRefKey the symbol reference key (e.g., "local:a", "param:x", "global:g")
   */
  public void remove(String symbolRefKey) {
    cache.remove(symbolRefKey);
  }

  /**
   * Creates a symbol reference key from symbol kind and name.
   *
   * @param kind the symbol kind (LOCAL, PARAM, GLOBAL)
   * @param name the symbol name
   * @return the symbol reference key
   */
  public static String createSymbolRefKey(hr.fer.ppj.ir.model.IrSymbolRef.Kind kind, String name) {
    Objects.requireNonNull(kind, "kind must not be null");
    Objects.requireNonNull(name, "name must not be null");
    return kind.name().toLowerCase() + ":" + name;
  }
}
