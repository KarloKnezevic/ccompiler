package hr.fer.ppj.common.diagnostic;

/**
 * Exception thrown when a fatal compilation error occurs.
 * Wraps a Diagnostic to provide context.
 */
public class CompilationException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final Diagnostic diagnostic;

    public CompilationException(Diagnostic diagnostic) {
        super(diagnostic.message());
        this.diagnostic = diagnostic;
    }

    public Diagnostic getDiagnostic() {
        return diagnostic;
    }
}
