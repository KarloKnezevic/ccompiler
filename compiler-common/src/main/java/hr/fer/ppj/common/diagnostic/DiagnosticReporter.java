package hr.fer.ppj.common.diagnostic;

import java.util.List;

/**
 * Interface for collecting or reporting diagnostics.
 */
public interface DiagnosticReporter {
    void report(Diagnostic diagnostic);

    default void error(Stage stage, hr.fer.ppj.common.source.SourceLocation location, String message) {
        report(Diagnostic.error(stage, location, message));
    }

    boolean hasErrors();
    
    List<Diagnostic> getDiagnostics();
}
