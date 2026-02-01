package hr.fer.ppj.common.diagnostic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A DiagnosticReporter that collects diagnostics into a list.
 */
public class ListDiagnosticReporter implements DiagnosticReporter {
    private final List<Diagnostic> diagnostics = new ArrayList<>();
    private boolean hasErrors = false;

    @Override
    public void report(Diagnostic diagnostic) {
        diagnostics.add(diagnostic);
        if (diagnostic.severity() == Severity.ERROR) {
            hasErrors = true;
        }
    }

    @Override
    public boolean hasErrors() {
        return hasErrors;
    }

    @Override
    public List<Diagnostic> getDiagnostics() {
        return Collections.unmodifiableList(diagnostics);
    }
}
