package hr.fer.ppj.common.source;

/**
 * Represents a specific location in a source file.
 *
 * @param line   The line number (1-based).
 * @param column The column number (1-based).
 */
public record SourceLocation(int line, int column) {
    public static final SourceLocation UNKNOWN = new SourceLocation(-1, -1);

    @Override
    public String toString() {
        if (this == UNKNOWN) {
            return "unknown location";
        }
        return "line " + line + ", column " + column;
    }
}
