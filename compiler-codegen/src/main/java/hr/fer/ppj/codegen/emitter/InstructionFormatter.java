package hr.fer.ppj.codegen.emitter;

import java.util.Objects;

/**
 * Formats FRISC assembly instructions, labels, and comments.
 *
 * <p>This class encapsulates the formatting logic for FRISC assembly code, ensuring consistent
 * formatting across all generated code. It implements the <b>assembly formatting algorithm</b> that
 * produces readable, well-aligned FRISC assembly output.
 *
 * <p><b>Design Pattern: Formatter</b>
 *
 * <p>This class implements the <b>formatter pattern</b>, separating formatting concerns from code
 * generation logic. This provides several benefits:
 *
 * <ul>
 *   <li><b>Consistency:</b> All generated code follows the same formatting rules
 *   <li><b>Readability:</b> Well-formatted assembly is easier to read and debug
 *   <li><b>Maintainability:</b> Formatting rules are centralized in one place
 *   <li><b>Flexibility:</b> Formatting can be changed without modifying code generation logic
 * </ul>
 *
 * <p><b>Formatting Rules:</b>
 *
 * <ul>
 *   <li><b>Labels:</b> Placed at the beginning of lines (no indentation). Labels may have optional
 *       comments aligned to column 32.
 *   <li><b>Instructions:</b> Indented with 8 spaces to distinguish them from labels. Operands are
 *       separated by commas and spaces.
 *   <li><b>Comments:</b> Aligned to column 32 and prefixed with semicolons (;). Comments provide
 *       documentation for the generated code.
 *   <li><b>Data Declarations:</b> Labels (if present) are padded to 8 characters for alignment,
 *       followed by the directive and value.
 * </ul>
 *
 * <p><b>FRISC Assembly Format:</b>
 *
 * <p>The generated assembly follows this format:
 *
 * <pre>
 * LABEL_NAME:                    ; Optional comment for label
 *         MNEMONIC OPERAND1, OPERAND2    ; Instruction comment
 *         MNEMONIC OPERAND1, OPERAND2, OPERAND3    ; Three-operand instruction
 *         ; Standalone comment line
 * </pre>
 *
 * <p><b>Alignment Algorithm:</b>
 *
 * <p>Comments are aligned to column 32 using a padding algorithm:
 *
 * <ol>
 *   <li>Build the instruction string (mnemonic + operands)
 *   <li>Calculate padding needed to reach column 32
 *   <li>Add spaces to pad to the target column
 *   <li>Append the comment with semicolon prefix
 * </ol>
 *
 * <p>This ensures that comments start at a consistent column, making the assembly code easier to
 * read and scan.
 *
 * <p><b>Example Output:</b>
 *
 * <pre>
 * F_MAIN:                        ; Function main
 *         MOVE 40000, R7         ; Initialize stack pointer
 *         CALL F_FOO              ; Call function foo
 *         ADD R0, R1, R2         ; Add operands
 *         HALT                    ; End program
 * </pre>
 *
 * <p><b>Complexity:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the length of the formatted string
 *   <li><b>Space Complexity:</b> O(n) for the formatted string
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class InstructionFormatter {

  /** Standard indentation for instructions (8 spaces). */
  private static final String INDENT = "        ";

  /** Target column for comment alignment. */
  private static final int COMMENT_COLUMN = 32;

  /**
   * Formats a FRISC instruction with optional operands and comment.
   *
   * @param mnemonic the instruction mnemonic (e.g., "MOVE", "ADD", "CALL")
   * @param operand1 first operand (may be null)
   * @param operand2 second operand (may be null)
   * @param comment optional comment (may be null)
   * @return formatted instruction line
   */
  public String formatInstruction(
      String mnemonic, String operand1, String operand2, String comment) {
    Objects.requireNonNull(mnemonic, "mnemonic must not be null");

    StringBuilder sb = new StringBuilder();
    sb.append(INDENT).append(mnemonic);

    // Add operands
    if (operand1 != null) {
      sb.append(" ").append(operand1);
      if (operand2 != null) {
        sb.append(", ").append(operand2);
      }
    }

    // Add comment if provided
    if (comment != null && !comment.isEmpty()) {
      padToColumn(sb, COMMENT_COLUMN);
      sb.append("; ").append(comment);
    }

    return sb.toString();
  }

  /**
   * Formats a three-operand FRISC instruction.
   *
   * @param mnemonic the instruction mnemonic
   * @param operand1 first operand
   * @param operand2 second operand
   * @param operand3 third operand
   * @param comment optional comment
   * @return formatted instruction line
   */
  public String formatInstruction(
      String mnemonic, String operand1, String operand2, String operand3, String comment) {
    Objects.requireNonNull(mnemonic, "mnemonic must not be null");

    StringBuilder sb = new StringBuilder();
    sb.append(INDENT).append(mnemonic);

    // Add operands
    if (operand1 != null) {
      sb.append(" ").append(operand1);
      if (operand2 != null) {
        sb.append(", ").append(operand2);
        if (operand3 != null) {
          sb.append(", ").append(operand3);
        }
      }
    }

    // Add comment if provided
    if (comment != null && !comment.isEmpty()) {
      padToColumn(sb, COMMENT_COLUMN);
      sb.append("; ").append(comment);
    }

    return sb.toString();
  }

  /**
   * Formats a label with an optional comment.
   *
   * @param label the label name
   * @param comment optional comment describing the label
   * @return formatted label line
   */
  public String formatLabel(String label, String comment) {
    Objects.requireNonNull(label, "label must not be null");

    if (comment != null && !comment.isEmpty()) {
      StringBuilder sb = new StringBuilder(label);
      padToColumn(sb, COMMENT_COLUMN);
      sb.append("; ").append(comment);
      return sb.toString();
    } else {
      return label;
    }
  }

  /**
   * Formats a comment line.
   *
   * @param comment the comment text
   * @return formatted comment line
   */
  public String formatComment(String comment) {
    Objects.requireNonNull(comment, "comment must not be null");
    return "; " + comment;
  }

  /**
   * Formats a data declaration (DW, DH, DB, `DS).
   *
   * @param label optional label for the data
   * @param directive the data directive ("DW", "DH", "DB", "`DS")
   * @param value the data value
   * @param comment optional comment
   * @return formatted data declaration line
   */
  public String formatData(String label, String directive, String value, String comment) {
    Objects.requireNonNull(directive, "directive must not be null");
    Objects.requireNonNull(value, "value must not be null");

    StringBuilder sb = new StringBuilder();

    if (label != null) {
      sb.append(label);
      // Pad label to at least 8 characters for alignment
      while (sb.length() < 8) {
        sb.append(" ");
      }
      // Always add a space after label (even if label is longer than 8)
      sb.append(" ");
    } else {
      sb.append(INDENT);
    }

    sb.append(directive).append(" ").append(value);

    if (comment != null && !comment.isEmpty()) {
      padToColumn(sb, COMMENT_COLUMN);
      sb.append("; ").append(comment);
    }

    return sb.toString();
  }

  /**
   * Pads a string builder to the specified column by adding spaces.
   *
   * @param sb the string builder to pad
   * @param targetColumn the target column number
   */
  private void padToColumn(StringBuilder sb, int targetColumn) {
    while (sb.length() < targetColumn) {
      sb.append(" ");
    }
  }

  /**
   * Formats a number as hexadecimal for FRISC assembly.
   *
   * @param value the numeric value
   * @return formatted hex string (e.g., "04", "0C")
   */
  public static String formatHex(int value) {
    return String.format("%02X", value);
  }
}
