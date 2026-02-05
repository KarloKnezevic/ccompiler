package hr.fer.ppj.codegen.frisc.analysis;

/**
 * Scratch data declaration description.
 */
public record Scratch(
    String label,
    int size,
    int alignment,
    String comment) {
}
