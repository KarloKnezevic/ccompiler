package hr.fer.ppj.codegen.util;

import java.util.Objects;

/**
 * Utility class for float code generation helpers.
 * 
 * <p>This class provides utilities for converting float values to Q16.16
 * fixed-point representation used by FRISC code generation.
 * 
 * <p><b>Q16.16 Fixed-Point Format:</b>
 * <ul>
 *   <li>32-bit signed integer</li>
 *   <li>Bits 31-16: Integer part (signed 16-bit)</li>
 *   <li>Bits 15-0: Fractional part (unsigned 16-bit)</li>
 *   <li>Actual float value = stored_integer / 65536.0</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FloatCodegenHelper {
    
    /**
     * Scaling factor for Q16.16 fixed-point representation.
     * Value: 2^16 = 65536
     */
    public static final int Q16_16_SCALE = 65536;
    
    /**
     * Converts a float value to Q16.16 fixed-point integer representation.
     * 
     * <p>The conversion multiplies the float by 65536 and rounds to the nearest integer.
     * 
     * <p>Examples:
     * <ul>
     *   <li>{@code floatToQ16_16(1.0f)} → {@code 65536}</li>
     *   <li>{@code floatToQ16_16(1.5f)} → {@code 98304}</li>
     *   <li>{@code floatToQ16_16(0.5f)} → {@code 32768}</li>
     *   <li>{@code floatToQ16_16(-1.0f)} → {@code -65536}</li>
     * </ul>
     * 
     * @param value the float value to convert
     * @return the Q16.16 fixed-point integer representation
     */
    public static int floatToQ16_16(float value) {
        // Multiply by scale factor and round to nearest integer
        return Math.round(value * Q16_16_SCALE);
    }
    
    /**
     * Converts a Q16.16 fixed-point integer back to a float value.
     * 
     * <p>The conversion divides the integer by 65536.0.
     * 
     * @param q16_16 the Q16.16 fixed-point integer
     * @return the float value
     */
    public static float q16_16ToFloat(int q16_16) {
        return q16_16 / (float) Q16_16_SCALE;
    }
    
    /**
     * Converts an integer to Q16.16 fixed-point representation.
     * 
     * <p>This is equivalent to converting the integer to float first,
     * then to Q16.16: {@code intToQ16_16(i) = floatToQ16_16((float)i)}.
     * 
     * <p>Since the integer has no fractional part, this is simply:
     * {@code i * 65536}.
     * 
     * @param value the integer value
     * @return the Q16.16 representation (value * 65536)
     */
    public static int intToQ16_16(int value) {
        return value * Q16_16_SCALE;
    }
    
    /**
     * Parses a float literal string and converts it to Q16.16 format.
     * 
     * <p>This method handles float literals like:
     * <ul>
     *   <li>{@code "1.0"}</li>
     *   <li>{@code "2.5"}</li>
     *   <li>{@code "3.14"}</li>
     *   <li>{@code "1.5e2"}</li>
     * </ul>
     * 
     * @param literal the float literal string (may include 'f' suffix)
     * @return the Q16.16 fixed-point representation
     * @throws NumberFormatException if the literal cannot be parsed
     */
    public static int parseFloatLiteral(String literal) {
        Objects.requireNonNull(literal, "literal must not be null");
        
        // Remove 'f' or 'F' suffix if present
        String cleaned = literal.trim();
        if (cleaned.endsWith("f") || cleaned.endsWith("F")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        
        // Parse as double, then convert to float
        double doubleValue = Double.parseDouble(cleaned);
        float floatValue = (float) doubleValue;
        
        return floatToQ16_16(floatValue);
    }
    
    /**
     * Checks if a string represents a float literal.
     * 
     * <p>A float literal contains either:
     * <ul>
     *   <li>A decimal point ({@code .})</li>
     *   <li>An exponent notation ({@code e} or {@code E})</li>
     * </ul>
     * 
     * @param literal the string to check
     * @return true if the string appears to be a float literal
     */
    public static boolean isFloatLiteral(String literal) {
        if (literal == null) {
            return false;
        }
        String cleaned = literal.trim().toLowerCase();
        return cleaned.contains(".") || cleaned.contains("e");
    }
}

