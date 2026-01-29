package hr.fer.ppj.ir.build;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Registry for assigning deterministic names to struct types.
 *
 * <p>Assigns:
 * <ul>
 *   <li>Tagged structs -> tag name</li>
 *   <li>Anonymous structs -> Anonymous$1, Anonymous$2, ...</li>
 * </ul>
 *
 * <p>Ensures the same struct type always gets the same name throughout IR generation.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StructNameRegistry {

  private final Map<Object, String> structNames = new HashMap<>();
  private int anonymousCounter = 1;

  /**
   * Gets or assigns a name for a struct type.
   *
   * @param structTag the struct tag (can be null for anonymous structs)
   * @param structIdentity an object that uniquely identifies the struct type
   *                       (used to ensure same anonymous struct gets same name)
   * @return the assigned struct name
   */
  public String getStructName(String structTag, Object structIdentity) {
    Objects.requireNonNull(structIdentity, "structIdentity must not be null");

    if (structTag != null && !structTag.isEmpty()) {
      // Tagged struct - use tag as name
      return structTag;
    }

    // Anonymous struct - assign deterministic name
    return structNames.computeIfAbsent(structIdentity, k -> "Anonymous$" + anonymousCounter++);
  }

  /**
   * Gets a struct name for a tagged struct (simpler API when tag is known).
   */
  public String getStructName(String structTag) {
    if (structTag != null && !structTag.isEmpty()) {
      return structTag;
    }
    // For anonymous structs without identity, we can't assign deterministically
    // This should not be called - use getStructName(String, Object) instead
    throw new IllegalArgumentException("Cannot assign name to anonymous struct without identity");
  }
}
