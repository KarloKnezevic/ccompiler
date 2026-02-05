package hr.fer.ppj.codegen.frisc.analysis;

import java.util.List;
import java.util.Map;

/**
 * Scratch storage allocation for pointer locals that need materialized addresses.
 */
public record PointerScratch(
    Map<String, Map<String, String>> labelsByFunction,
    List<Scratch> scratches) {
}
