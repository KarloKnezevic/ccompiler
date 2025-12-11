package hr.fer.ppj.codegen.structs;

import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.Map;
import java.util.Objects;

/**
 * Utility for recursively extracting array sizes for nested structs.
 *
 * <p>This class provides a centralized method for extracting array sizes for all nested structs at
 * any depth within a struct type. It ensures that when processing a struct like {@code Outer} that
 * contains {@code Middle}, which contains {@code Inner}, all array sizes are extracted recursively.
 *
 * <p><b>Algorithm: Nested Struct Array Size Extraction</b>
 *
 * <p>The algorithm works as follows:
 *
 * <ol>
 *   <li><b>Current Struct Extraction:</b> Extract array sizes for the current struct (if it has a
 *       tag)
 *   <li><b>Field Traversal:</b> For each field that is a struct type, recursively extract array
 *       sizes
 *   <li><b>Depth-First Traversal:</b> Process nested structs before processing deeper nested
 *       structs
 * </ol>
 *
 * <p><b>Critical Design Decision:</b>
 *
 * <p>Array sizes are always extracted and stored in the map, even if empty. This is important
 * because:
 *
 * <ul>
 *   <li>An empty map indicates that we've tried to extract array sizes for this struct
 *   <li>This prevents infinite recursion when the same struct appears multiple times
 *   <li>It allows {@link StructSizeCalculator} to know that array sizes were attempted
 * </ul>
 *
 * <p><b>Example:</b>
 *
 * <pre>
 * struct Inner {
 *     int arr[2];
 * };
 *
 * struct Middle {
 *     struct Inner inner;
 * };
 *
 * struct Outer {
 *     struct Middle middle;
 * };
 *
 * // When processing Outer, this will extract:
 * // - Outer: {} (no arrays)
 * // - Middle: {} (no arrays)
 * // - Inner: {"arr": 2}
 * </pre>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the total number of nested struct fields
 *   <li><b>Space Complexity:</b> O(n) for storing array sizes in the map
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class NestedStructArraySizeExtractor {

  /** Private constructor to prevent instantiation. */
  private NestedStructArraySizeExtractor() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Recursively extracts array sizes for all nested structs in a struct type.
   *
   * <p>This method traverses all fields of the struct and extracts array sizes for any nested
   * struct fields, recursively handling deeply nested structs.
   *
   * <p><b>Critical:</b> Always extracts and stores array sizes in the map, even if empty. This
   * ensures that we know we've attempted extraction for each struct, preventing infinite recursion
   * and allowing proper size calculation.
   *
   * @param structType the struct type to extract nested array sizes for
   * @param arraySizeExtractor the extractor to use for extracting array sizes
   * @param nestedStructArraySizes the map to populate with nested struct array sizes (struct tag ->
   *     array sizes map)
   */
  public static void extractNestedStructArraySizes(
      StructType structType,
      StructArraySizeExtractor arraySizeExtractor,
      Map<String, Map<String, Integer>> nestedStructArraySizes) {
    Objects.requireNonNull(nestedStructArraySizes, "nestedStructArraySizes must not be null");

    // Early return if inputs are invalid
    if (structType == null || arraySizeExtractor == null) {
      return;
    }

    // Phase 1: Extract array sizes for the current struct (if it has a tag)
    // This handles the case where the struct itself has array fields
    String structTag = structType.tag();
    if (structTag != null && !nestedStructArraySizes.containsKey(structTag)) {
      // Extract array sizes for this struct's fields
      Map<String, Integer> currentArraySizes = arraySizeExtractor.extractArraySizes(structTag);

      // CRITICAL: Always put in map, even if empty
      // This serves two purposes:
      // 1. Prevents infinite recursion (we know we've tried to extract for this struct)
      // 2. Allows StructSizeCalculator to know that array sizes were attempted
      //    (empty map means "no arrays" vs null means "not tried")
      nestedStructArraySizes.put(structTag, currentArraySizes);
    }

    // Phase 2: Extract array sizes for all nested struct fields (recursively)
    // This handles deeply nested structs like: Outer -> Middle -> Inner
    for (Map.Entry<String, Type> field : structType.fields().entrySet()) {
      Type fieldType = TypeSystem.stripConst(field.getValue());

      // Check if this field is a struct type (nested struct)
      if (fieldType instanceof StructType nestedStructType) {
        String nestedTag = nestedStructType.tag();

        // Handle anonymous structs (structs without tags)
        // Anonymous structs can't be extracted by tag, but they might contain
        // nested structs that do have tags, so we recursively extract
        if (nestedTag == null) {
          // For anonymous structs, still try to recursively extract
          // They might have nested structs with tags that we can extract
          extractNestedStructArraySizes(
              nestedStructType, arraySizeExtractor, nestedStructArraySizes);
          continue;
        }

        // Skip if we've already extracted array sizes for this struct tag
        // This prevents duplicate work and infinite recursion
        if (nestedStructArraySizes.containsKey(nestedTag)) {
          continue;
        }

        // Extract array sizes for this nested struct
        // CRITICAL: Always extract, even if empty, so we know we've tried
        // If the struct has array fields, this will populate the map with array sizes
        Map<String, Integer> nestedArraySizes = arraySizeExtractor.extractArraySizes(nestedTag);

        // Always put in map, even if empty (needed for calculateStructSize to know we've tried)
        // Empty map means "no arrays in this struct"
        // Null/absent means "haven't tried to extract yet"
        nestedStructArraySizes.put(nestedTag, nestedArraySizes);

        // CRITICAL: Recursively extract array sizes for even deeper nested structs
        // This ensures we get array sizes for Inner when processing Outer
        // Example: Outer contains Middle, Middle contains Inner
        // When processing Outer, we extract Middle's array sizes, then recursively
        // extract Inner's array sizes
        // This must be done AFTER extracting array sizes for the current nested struct,
        // so that deeper nested structs can also be found
        extractNestedStructArraySizes(nestedStructType, arraySizeExtractor, nestedStructArraySizes);
      }
    }
  }
}
