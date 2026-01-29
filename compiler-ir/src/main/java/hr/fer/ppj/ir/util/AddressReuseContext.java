package hr.fer.ppj.ir.util;

import hr.fer.ppj.ir.model.IrTemp;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Manages address reuse optimization context.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Assignment address reuse (for patterns like n = n - 1)</li>
 *   <li>Array base address reuse (for patterns like a[0], a[4], a[2])</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class AddressReuseContext {

  private IrTemp assignmentReuseAddr;
  private String assignmentReuseVarName;
  private IrTemp arrayBaseReuseAddr;
  private String arrayBaseReuseVarName;
  private IrTemp lastLoadAddress;
  private String lastLoadVarName;
  private final Map<String, IrTemp> lastLoadedValues = new HashMap<>();

  public IrTemp assignmentReuseAddr() {
    return assignmentReuseAddr;
  }

  public String assignmentReuseVarName() {
    return assignmentReuseVarName;
  }

  public void setAssignmentReuse(IrTemp addr, String varName) {
    this.assignmentReuseAddr = addr;
    this.assignmentReuseVarName = varName;
  }

  /**
   * Sets only the assignment reuse variable name, without an address.
   * Used to signal that we're in an assignment context before the address is known.
   */
  public void setAssignmentReuseVarName(String varName) {
    this.assignmentReuseVarName = varName;
    // Keep the existing address if any
  }

  public void clearAssignmentReuse() {
    this.assignmentReuseAddr = null;
    this.assignmentReuseVarName = null;
  }

  public IrTemp arrayBaseReuseAddr() {
    return arrayBaseReuseAddr;
  }

  public String arrayBaseReuseVarName() {
    return arrayBaseReuseVarName;
  }

  public void setArrayBaseReuse(IrTemp addr, String varName) {
    this.arrayBaseReuseAddr = addr;
    this.arrayBaseReuseVarName = varName;
  }

  public void clearArrayBaseReuse() {
    this.arrayBaseReuseAddr = null;
    this.arrayBaseReuseVarName = null;
  }

  /**
   * Clears all address reuse caches.
   *
   * <p>For correctness, we clear all caches including loaded values.
   * This ensures no invalid reuse across statements or blocks.
   */
  public void clearAll() {
    clearAssignmentReuse();
    clearArrayBaseReuse();
    clearLastLoadAddress();
    clearAllLastLoadedValues();
  }

  public boolean canReuse(String varName) {
    return assignmentReuseVarName != null && assignmentReuseVarName.equals(varName);
  }

  public IrTemp getReuseAddress(String varName) {
    if (canReuse(varName)) {
      return assignmentReuseAddr;
    }
    return null;
  }

  public boolean canReuseArrayBase(String varName) {
    return arrayBaseReuseVarName != null && arrayBaseReuseVarName.equals(varName);
  }

  public IrTemp getArrayBaseAddress(String varName) {
    if (canReuseArrayBase(varName)) {
      return arrayBaseReuseAddr;
    }
    return null;
  }

  public void setArrayBaseAddress(String varName, IrTemp addr) {
    setArrayBaseReuse(addr, varName);
  }

  /**
   * Records the last address used when loading a variable.
   * This allows reusing the address for subsequent operations (e.g., store).
   */
  public void setLastLoadAddress(String varName, IrTemp addr) {
    this.lastLoadAddress = addr;
    this.lastLoadVarName = varName;
  }

  /**
   * Gets the last address used when loading a variable, if it matches the given variable name.
   */
  public IrTemp getLastLoadAddress(String varName) {
    if (lastLoadVarName != null && lastLoadVarName.equals(varName)) {
      return lastLoadAddress;
    }
    return null;
  }

  /**
   * Clears the last load address tracking.
   */
  public void clearLastLoadAddress() {
    this.lastLoadAddress = null;
    this.lastLoadVarName = null;
  }

  /**
   * Records the last loaded value for a variable.
   */
  public void setLastLoadedValue(String varName, IrTemp value) {
    this.lastLoadedValues.put(varName, value);
  }

  /**
   * Gets the last loaded value for a variable, if available.
   */
  public IrTemp getLastLoadedValue(String varName) {
    return lastLoadedValues.get(varName);
  }

  /**
   * Clears the last loaded value for a specific variable.
   *
   * <p>Since cache keys are now fully-qualified (e.g., "param:m", "local:x", "global:g"),
   * this method clears all possible cache keys for the given variable name to ensure
   * proper invalidation regardless of scope.
   */
  public void clearLastLoadedValue(String varName) {
    if (varName != null) {
      // Clear all possible cache keys for this variable name
      // (param:name, local:name, global:name)
      this.lastLoadedValues.remove("param:" + varName);
      this.lastLoadedValues.remove("local:" + varName);
      this.lastLoadedValues.remove("global:" + varName);
      // Also clear the old-style key (for backward compatibility)
      this.lastLoadedValues.remove(varName);
    }
  }

  /**
   * Clears all last loaded values.
   */
  public void clearAllLastLoadedValues() {
    this.lastLoadedValues.clear();
  }

  /**
   * Begins a new statement scope.
   *
   * <p>This clears statement-local caches (loaded values, load addresses, assignment reuse)
   * to ensure values are only reused within a single statement/expression evaluation.
   */
  public void beginStatement() {
    clearAllLastLoadedValues();
    clearLastLoadAddress();
    clearAssignmentReuse();
  }

  /**
   * Ends the current statement scope.
   *
   * <p>This clears statement-local caches to ensure values are not reused across statements.
   */
  public void endStatement() {
    clearAllLastLoadedValues();
    clearLastLoadAddress();
    clearAssignmentReuse();
  }
}
