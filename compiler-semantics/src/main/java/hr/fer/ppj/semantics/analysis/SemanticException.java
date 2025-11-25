package hr.fer.ppj.semantics.analysis;

/**
 * Exception used internally to short-circuit semantic analysis upon the first error. The CLI layer
 * intentionally catches this exception and suppresses the stack trace so that the output matches
 * PPJ's strict grading format.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SemanticException extends RuntimeException {

  public SemanticException(String message) {
    super(message);
  }
}

