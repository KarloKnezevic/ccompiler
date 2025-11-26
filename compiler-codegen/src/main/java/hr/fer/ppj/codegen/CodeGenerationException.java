package hr.fer.ppj.codegen;

/**
 * Exception thrown when FRISC code generation fails.
 * 
 * <p>This exception indicates an error during the code generation phase,
 * such as unsupported language constructs, internal errors, or I/O failures
 * when writing the output file.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class CodeGenerationException extends RuntimeException {
    
    /**
     * Constructs a new code generation exception with the specified detail message.
     * 
     * @param message the detail message
     */
    public CodeGenerationException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new code generation exception with the specified detail message and cause.
     * 
     * @param message the detail message
     * @param cause the cause of this exception
     */
    public CodeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
