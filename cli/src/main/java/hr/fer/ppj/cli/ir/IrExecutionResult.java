package hr.fer.ppj.cli.ir;

/**
 * Result of IR program execution.
 *
 * @param returnValue value returned by {@code main}
 * @param steps total executed instructions/terminators
 * @param trace textual trace (empty when tracing disabled)
 */
public record IrExecutionResult(int returnValue, int steps, String trace) {
}
