package hr.fer.ppj.cli.reporting;

import hr.fer.ppj.common.diagnostic.Diagnostic;
import hr.fer.ppj.common.diagnostic.DiagnosticReporter;
import hr.fer.ppj.common.diagnostic.Severity;
import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostic reporter that collects diagnostics for later rendering.
 */
public final class CollectingReporter implements DiagnosticReporter {

  private final List<Diagnostic> diagnostics = new ArrayList<>();
  private boolean hasErrors;

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
    return List.copyOf(diagnostics);
  }
}
