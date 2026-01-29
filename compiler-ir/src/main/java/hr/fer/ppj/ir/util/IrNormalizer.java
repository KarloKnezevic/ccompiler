package hr.fer.ppj.ir.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility for normalizing IR output for comparison.
 *
 * <p>Normalization removes blank lines and trims trailing whitespace,
 * allowing comparison to ignore formatting differences while preserving
 * semantic content.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IrNormalizer {

  private IrNormalizer() {}

  /**
   * Normalizes IR output by removing blank lines and trimming trailing whitespace.
   *
   * <p>This method:
   * <ul>
   *   <li>Splits input into lines</li>
   *   <li>Trims trailing whitespace from each line</li>
   *   <li>Removes empty lines and whitespace-only lines</li>
   *   <li>Preserves meaningful whitespace within lines</li>
   * </ul>
   *
   * @param irOutput the IR output string to normalize
   * @return normalized lines (without blank lines)
   */
  public static List<String> normalize(String irOutput) {
    Objects.requireNonNull(irOutput, "irOutput must not be null");

    List<String> normalized = new ArrayList<>();
    String[] lines = irOutput.split("\n", -1); // -1 to preserve trailing empty strings

    for (String line : lines) {
      // Trim trailing whitespace
      String trimmed = line.replaceFirst("\\s+$", "");
      
      // Skip empty lines and whitespace-only lines
      if (!trimmed.isEmpty()) {
        normalized.add(trimmed);
      }
    }

    return normalized;
  }

  /**
   * Compares two IR outputs using normalized comparison (ignores blank lines).
   *
   * @param expected the expected IR output
   * @param actual the actual IR output
   * @return true if the normalized outputs are equal, false otherwise
   */
  public static boolean equalsNormalized(String expected, String actual) {
    List<String> expectedLines = normalize(expected);
    List<String> actualLines = normalize(actual);

    if (expectedLines.size() != actualLines.size()) {
      return false;
    }

    for (int i = 0; i < expectedLines.size(); i++) {
      if (!expectedLines.get(i).equals(actualLines.get(i))) {
        return false;
      }
    }

    return true;
  }

  /**
   * Generates a diff hint showing the first difference between normalized outputs.
   *
   * @param expected the expected IR output
   * @param actual the actual IR output
   * @return a string describing the first difference, or null if they match
   */
  public static String generateDiffHint(String expected, String actual) {
    List<String> expectedLines = normalize(expected);
    List<String> actualLines = normalize(actual);

    int minLen = Math.min(expectedLines.size(), actualLines.size());
    for (int i = 0; i < minLen; i++) {
      if (!expectedLines.get(i).equals(actualLines.get(i))) {
        return String.format(
            "First difference at normalized line %d:\nExpected: %s\nActual:   %s",
            i + 1, expectedLines.get(i), actualLines.get(i));
      }
    }

    if (expectedLines.size() != actualLines.size()) {
      return String.format(
          "Line count mismatch: expected %d normalized lines, got %d normalized lines",
          expectedLines.size(), actualLines.size());
    }

    return null; // They match
  }
}
