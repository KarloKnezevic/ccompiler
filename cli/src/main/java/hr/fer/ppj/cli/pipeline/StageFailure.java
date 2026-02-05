package hr.fer.ppj.cli.pipeline;

import java.util.List;

/**
 * Exception representing a stage failure with contextual details.
 */
final class StageFailure extends Exception {

  private final List<String> details;
  private final String hint;

  StageFailure(String message, List<String> details, String hint) {
    super(message);
    this.details = details == null ? List.of() : List.copyOf(details);
    this.hint = hint;
  }

  StageFailure(String message, Throwable cause, List<String> details, String hint) {
    super(message, cause);
    this.details = details == null ? List.of() : List.copyOf(details);
    this.hint = hint;
  }

  List<String> details() {
    return details;
  }

  String hint() {
    return hint;
  }
}
