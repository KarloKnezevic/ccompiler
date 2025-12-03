package hr.fer.ppj.codegen.emitter;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Emits FRISC assembly code with proper formatting and structure.
 * 
 * <p>This class is responsible for generating well-formatted FRISC assembly instructions,
 * managing the output buffer, and writing the final assembly code to files. It handles
 * instruction formatting, label placement, comment alignment, and tracks which helper
 * functions (F_MUL, F_DIV) are needed by the generated code.
 * 
 * <p>The emitter ensures consistent formatting:
 * <ul>
 *   <li>Labels are placed at the beginning of lines</li>
 *   <li>Instructions are indented with 8 spaces</li>
 *   <li>Comments are aligned to column 32 and prefixed with semicolons</li>
 *   <li>Operands are formatted according to FRISC syntax</li>
 * </ul>
 * 
 * <p>All generated code is buffered in memory until {@link #writeToFile(Path)} is called,
 * allowing the code generator to emit instructions in any order and then write them
 * atomically to the output file.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FriscEmitter {
    
    /**
     * Standard indentation for instructions (8 spaces).
     */
    private static final String INDENT = "        ";
    
    /**
     * Target column for comment alignment.
     */
    private static final int COMMENT_COLUMN = 32;
    
    /**
     * Buffer for storing generated assembly lines.
     */
    private final List<String> lines = new ArrayList<>();
    
    /**
     * Flags to track if helper functions are needed.
     */
    private boolean needsMulHelper = false;
    private boolean needsDivHelper = false;
    
    /**
     * Marks that F_MUL helper function is needed.
     * 
     * <p>This should be called whenever multiplication is encountered in the code
     * being generated, as FRISC architecture doesn't have a native MUL instruction.
     */
    public void markMulNeeded() {
        needsMulHelper = true;
    }
    
    /**
     * Marks that F_DIV helper function is needed.
     * 
     * <p>This should be called whenever division is encountered in the code
     * being generated, as FRISC architecture doesn't have a native DIV instruction.
     */
    public void markDivNeeded() {
        needsDivHelper = true;
    }
    
    /**
     * Checks if F_MUL helper function is needed.
     * 
     * @return true if multiplication helper is needed
     */
    public boolean needsMulHelper() {
        return needsMulHelper;
    }
    
    /**
     * Checks if F_DIV helper function is needed.
     * 
     * @return true if division helper is needed
     */
    public boolean needsDivHelper() {
        return needsDivHelper;
    }
    
    /**
     * Emits a FRISC instruction with optional operands and comment.
     * 
     * <p>Examples:
     * <ul>
     *   <li>{@code emitInstruction("MOVE", "40000", "R7", "init stack")} → 
     *       {@code MOVE 40000, R7      ; init stack}</li>
     *   <li>{@code emitInstruction("HALT", null, null, "end program")} → 
     *       {@code HALT                ; end program}</li>
     *   <li>{@code emitInstruction("ADD", "R0", "R1", "R2", null)} → 
     *       {@code ADD R0, R1, R2}</li>
     * </ul>
     * 
     * @param mnemonic the instruction mnemonic (e.g., "MOVE", "ADD", "CALL")
     * @param operand1 first operand (may be null)
     * @param operand2 second operand (may be null)
     * @param comment optional comment (may be null)
     */
    public void emitInstruction(String mnemonic, String operand1, String operand2, String comment) {
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
        
        lines.add(sb.toString());
    }
    
    /**
     * Emits a three-operand FRISC instruction.
     * 
     * @param mnemonic the instruction mnemonic
     * @param operand1 first operand
     * @param operand2 second operand  
     * @param operand3 third operand
     * @param comment optional comment
     */
    public void emitInstruction(String mnemonic, String operand1, String operand2, String operand3, String comment) {
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
        
        lines.add(sb.toString());
    }
    
    /**
     * Emits a label at the beginning of a line.
     * 
     * @param label the label name (without colon)
     */
    public void emitLabel(String label) {
        Objects.requireNonNull(label, "label must not be null");
        lines.add(label);
    }
    
    /**
     * Emits a label with an optional comment on the same line.
     * 
     * @param label the label name
     * @param comment optional comment describing the label
     */
    public void emitLabel(String label, String comment) {
        Objects.requireNonNull(label, "label must not be null");
        
        if (comment != null && !comment.isEmpty()) {
            StringBuilder sb = new StringBuilder(label);
            padToColumn(sb, COMMENT_COLUMN);
            sb.append("; ").append(comment);
            lines.add(sb.toString());
        } else {
            lines.add(label);
        }
    }
    
    /**
     * Emits a comment line.
     * 
     * @param comment the comment text (semicolon will be added automatically)
     */
    public void emitComment(String comment) {
        Objects.requireNonNull(comment, "comment must not be null");
        lines.add("; " + comment);
    }
    
    /**
     * Emits a data declaration (DW, DH, DB, `DS).
     * 
     * <p>For the `DS directive (backtick DS), the directive parameter should be "`DS".
     * This directive reserves uninitialized memory space.
     * 
     * @param label optional label for the data
     * @param directive the data directive ("DW", "DH", "DB", "`DS")
     * @param value the data value
     * @param comment optional comment
     */
    public void emitData(String label, String directive, String value, String comment) {
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
        
        lines.add(sb.toString());
    }
    
    /**
     * Emits an empty line for better readability.
     */
    public void emitNewline() {
        lines.add("");
    }
    
    /**
     * Minimum value for a 20-bit signed immediate (-2^19 = -524288).
     */
    private static final int IMMEDIATE_MIN = -524288;
    
    /**
     * Maximum value for a 20-bit signed immediate (2^19 - 1 = 524287).
     */
    private static final int IMMEDIATE_MAX = 524287;
    
    /**
     * Emits code to load a 32-bit integer constant into a register.
     * 
     * <p>If the value fits into a 20-bit signed immediate (-524288 to 524287),
     * emits a single MOVE instruction. Otherwise, constructs the value from
     * high and low 16-bit parts using SHL and ADD.
     * 
     * <p>Example for small value (fits in 20 bits):
     * <pre>
     * MOVE %D 12345, R0
     * </pre>
     * 
     * <p>Example for large value (doesn't fit):
     * <pre>
     * MOVE %D 18838, R0    ; hi part (1234567890 >> 16)
     * SHL  R0, %D 16, R0   ; shift left by 16
     * ADD  R0, %D 722, R0  ; add lo part (1234567890 & 0xFFFF)
     * </pre>
     * 
     * @param value the 32-bit integer value to load
     * @param targetRegister the target register (e.g., "R0", "R6")
     * @param comment optional comment (may be null)
     */
    public void emitLoadIntConstant(int value, String targetRegister, String comment) {
        Objects.requireNonNull(targetRegister, "targetRegister must not be null");
        
        if (value >= IMMEDIATE_MIN && value <= IMMEDIATE_MAX) {
            // Value fits in 20-bit signed immediate - use single MOVE
            String commentText = comment != null ? comment : "load constant " + value;
            emitInstruction("MOVE", "%D " + value, targetRegister, commentText);
        } else {
            // Value doesn't fit - construct from hi and lo parts
            // Split into 16-bit parts: value = hi * 2^16 + lo
            int hi = (value >> 16) & 0xFFFF;
            int lo = value & 0xFFFF;
            
            // Sign-extend hi as signed 16-bit for proper representation
            short hiShort = (short) hi;
            int hiSigned = hiShort; // This will sign-extend to 32 bits
            
            // Emit construction sequence
            String baseComment = comment != null ? comment : "load constant " + value;
            emitInstruction("MOVE", "%D " + hiSigned, targetRegister, baseComment + " (hi part)");
            emitInstruction("SHL", targetRegister, "%D 16", targetRegister, "shift hi part left by 16");
            // Only emit ADD if lo part is non-zero (optimization)
            if (lo != 0) {
                emitInstruction("ADD", targetRegister, "%D " + lo, targetRegister, "add lo part");
            }
        }
    }
    
    /**
     * Checks if a value fits in a 20-bit signed immediate.
     * 
     * @param value the value to check
     * @return true if the value fits in [-524288, 524287]
     */
    public static boolean fitsInImmediate(int value) {
        return value >= IMMEDIATE_MIN && value <= IMMEDIATE_MAX;
    }
    
    /**
     * Emits a section header comment for better code organization.
     * 
     * @param title the section title
     */
    public void emitSectionHeader(String title) {
        Objects.requireNonNull(title, "title must not be null");
        emitComment(title);
    }
    
    /**
     * Convenience method for emitting simple instructions without operands.
     * 
     * @param mnemonic the instruction mnemonic
     * @param comment optional comment
     */
    public void emitInstruction(String mnemonic, String comment) {
        emitInstruction(mnemonic, null, null, comment);
    }
    
    /**
     * Convenience method for emitting instructions with one operand.
     * 
     * @param mnemonic the instruction mnemonic
     * @param operand the single operand
     * @param comment optional comment
     */
    public void emitInstruction(String mnemonic, String operand, String comment) {
        emitInstruction(mnemonic, operand, null, comment);
    }
    
    /**
     * Writes the generated assembly code to a file.
     * 
     * @param outputPath the path where to write the assembly code
     * @throws IOException if writing fails
     */
    public void writeToFile(Path outputPath) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (String line : lines) {
                writer.println(line);
            }
        }
    }
    
    /**
     * Returns the generated assembly code as a string.
     * 
     * @return the complete assembly code
     */
    public String getGeneratedCode() {
        return String.join("\n", lines);
    }
    
    /**
     * Formats a number as hexadecimal for FRISC assembly.
     * 
     * <p>This is used for addresses, offsets, and bit masks which should be
     * represented in hexadecimal format (without %D prefix).
     * 
     * @param value the numeric value
     * @return formatted hex string (e.g., "04", "0C")
     */
    public static String formatHex(int value) {
        return String.format("%02X", value);
    }
    
    /**
     * Returns the number of generated lines.
     * 
     * @return the line count
     */
    public int getLineCount() {
        return lines.size();
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
}

