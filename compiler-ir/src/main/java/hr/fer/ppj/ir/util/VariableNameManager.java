package hr.fer.ppj.ir.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages variable name mapping for shadowing detection.
 *
 * <p>Maps original variable names to actual IR names (which may be renamed
 * with _1, _2, etc. to handle shadowing).
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class VariableNameManager {

  private final Map<String, String> nameMap = new LinkedHashMap<>();

  /**
   * Gets the actual IR name for a variable, creating a unique name if needed.
   *
   * @param originalName the original variable name
   * @return the actual IR name (may be renamed)
   */
  public String getActualName(String originalName) {
    Objects.requireNonNull(originalName, "originalName must not be null");
    return nameMap.getOrDefault(originalName, originalName);
  }

  /**
   * Maps an original name to an actual IR name.
   *
   * @param originalName the original variable name
   * @param actualName the actual IR name
   */
  public void mapName(String originalName, String actualName) {
    Objects.requireNonNull(originalName, "originalName must not be null");
    Objects.requireNonNull(actualName, "actualName must not be null");
    nameMap.put(originalName, actualName);
  }

  /**
   * Checks if a name is already mapped.
   */
  public boolean isMapped(String originalName) {
    return nameMap.containsKey(originalName);
  }

  /**
   * Creates a snapshot of the current name mappings.
   */
  public VariableNameManager snapshot() {
    VariableNameManager snapshot = new VariableNameManager();
    snapshot.nameMap.putAll(this.nameMap);
    return snapshot;
  }

  /**
   * Restores name mappings from a snapshot.
   */
  public void restore(VariableNameManager snapshot) {
    this.nameMap.clear();
    this.nameMap.putAll(snapshot.nameMap);
  }

  /**
   * Clears all name mappings.
   */
  public void clear() {
    nameMap.clear();
  }
}
