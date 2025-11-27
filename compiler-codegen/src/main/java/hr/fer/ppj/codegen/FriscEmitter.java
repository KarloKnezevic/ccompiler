package hr.fer.ppj.codegen;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility class for generating and emitting FRISC assembly instructions.
 * 
 * <p>This class provides a high-level interface for generating FRISC assembly code,
 * handling instruction formatting, label management, and comment generation.
 * The generated code is buffered in memory and can be written to a file when
 * code generation is complete.
 * 
 * <p>The emitter ensures proper formatting of FRISC assembly:
 * <ul>
 *   <li>Labels are placed at the beginning of lines</li>
 *   <li>Instructions are properly indented</li>
 *   <li>Comments are aligned and prefixed with semicolons</li>
 *   <li>Operands are formatted according to FRISC syntax</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FriscEmitter {
    
    /**
     * Buffer for storing generated assembly lines.
     */
    private final List<String> lines = new ArrayList<>();
    
    /**
     * Emits a FRISC instruction with optional operands and comment.
     * 
     * <p>Examples:
     * <ul>
     *   <li>{@code emitInstruction("MOVE", "10000", "R7", "init stack")} → {@code MOVE 10000, R7      ; init stack}</li>
     *   <li>{@code emitInstruction("HALT", null, null, "end program")} → {@code HALT                ; end program}</li>
     *   <li>{@code emitInstruction("ADD", "R0", "R1", "R2", null)} → {@code ADD R0, R1, R2}</li>
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
        sb.append("        ").append(mnemonic); // 8 spaces for indentation
        
        // Add operands
        if (operand1 != null) {
            sb.append(" ").append(operand1);
            if (operand2 != null) {
                sb.append(", ").append(operand2);
            }
        }
        
        // Add comment if provided
        if (comment != null && !comment.isEmpty()) {
            // Pad to consistent column for comments
            while (sb.length() < 32) {
                sb.append(" ");
            }
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
        sb.append("        ").append(mnemonic); // 8 spaces for indentation
        
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
            // Pad to consistent column for comments
            while (sb.length() < 32) {
                sb.append(" ");
            }
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
            while (sb.length() < 32) {
                sb.append(" ");
            }
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
     * Emits a data declaration (DW, DH, DB).
     * 
     * @param label optional label for the data
     * @param directive the data directive ("DW", "DH", "DB")
     * @param value the data value
     * @param comment optional comment
     */
    public void emitData(String label, String directive, String value, String comment) {
        Objects.requireNonNull(directive, "directive must not be null");
        Objects.requireNonNull(value, "value must not be null");
        
        StringBuilder sb = new StringBuilder();
        
        if (label != null) {
            sb.append(label);
            while (sb.length() < 8) {
                sb.append(" ");
            }
        } else {
            sb.append("        "); // 8 spaces for indentation
        }
        
        sb.append(directive).append(" ").append(value);
        
        if (comment != null && !comment.isEmpty()) {
            while (sb.length() < 32) {
                sb.append(" ");
            }
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
}
