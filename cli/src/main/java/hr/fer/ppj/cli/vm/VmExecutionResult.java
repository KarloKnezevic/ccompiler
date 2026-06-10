package hr.fer.ppj.cli.vm;

/**
 * Result of bytecode VM execution.
 *
 * @param returnValue value returned by {@code main}
 * @param dispatched total number of bytecode instructions dispatched
 * @param trace textual trace (empty when tracing disabled)
 */
public record VmExecutionResult(int returnValue, long dispatched, String trace) {
}
