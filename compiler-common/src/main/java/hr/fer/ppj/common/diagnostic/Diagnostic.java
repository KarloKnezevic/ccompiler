package hr.fer.ppj.common.diagnostic;

import hr.fer.ppj.common.source.SourceLocation;
import java.util.Objects;

/**
 * A diagnostic message produced during compilation.
 *
 * @param stage    The compilation stage.
 * @param severity The severity of the diagnostic.
 * @param location The source location (or UNKNOWN).
 * @param message  The human-readable message.
 */
public record Diagnostic(Stage stage, Severity severity, SourceLocation location, String message) {
    public Diagnostic {
        Objects.requireNonNull(stage);
        Objects.requireNonNull(severity);
        Objects.requireNonNull(location);
        Objects.requireNonNull(message);
    }

    public static Diagnostic error(Stage stage, SourceLocation location, String message) {
        return new Diagnostic(stage, Severity.ERROR, location, message);
    }
    
    public static Diagnostic error(Stage stage, String message) {
        return new Diagnostic(stage, Severity.ERROR, SourceLocation.UNKNOWN, message);
    }

    public static Diagnostic warning(Stage stage, SourceLocation location, String message) {
        return new Diagnostic(stage, Severity.WARNING, location, message);
    }
    
    public static Diagnostic info(Stage stage, SourceLocation location, String message) {
        return new Diagnostic(stage, Severity.INFO, location, message);
    }
}
