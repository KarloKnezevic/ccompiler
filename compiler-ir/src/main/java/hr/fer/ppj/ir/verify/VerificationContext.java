package hr.fer.ppj.ir.verify;

import java.util.ArrayList;
import java.util.List;

/**
 * Context for collecting verification errors.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class VerificationContext {

  private final List<String> errors = new ArrayList<>();

  /**
   * Adds a verification error.
   */
  public void addError(String functionName, String blockLabel, String message) {
    errors.add("Function " + functionName + ", Block " + blockLabel + ": " + message);
  }

  /**
   * Gets all errors.
   */
  public List<String> getErrors() {
    return new ArrayList<>(errors);
  }

  /**
   * Checks if there are any errors.
   */
  public boolean hasErrors() {
    return !errors.isEmpty();
  }
}
