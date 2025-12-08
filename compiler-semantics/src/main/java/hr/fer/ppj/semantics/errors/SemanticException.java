package hr.fer.ppj.semantics.errors;

/**
 * Exception used internally to short-circuit semantic analysis upon the first error.
 * 
 * <p>The CLI layer intentionally catches this exception and suppresses the stack trace
 * so that the output matches PPJ's strict grading format.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SemanticException extends RuntimeException {

  /**
   * Creates a new semantic exception with the specified message.
   * 
   * @param message the error message
   */
  public SemanticException(String message) {
    super(message);
  }
}

