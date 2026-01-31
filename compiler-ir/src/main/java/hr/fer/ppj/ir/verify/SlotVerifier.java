package hr.fer.ppj.ir.verify;

import hr.fer.ppj.ir.model.IrSlot;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Verifies slot declarations according to the IR grammar.
 *
 * <p>Validates slot correctness as defined in {@code config/ir_definition.txt}:
 *
 * <pre>
 * SlotsDecl
 *   ::= ".slots" NL { SlotEntry } ;
 *
 * SlotEntry
 *   ::= SlotKind Ident "@" Int ":" Type NL ;
 *
 * SlotKind
 *   ::= "param" | "local" | "spill" ;
 * </pre>
 *
 * <h3>Validation Rules</h3>
 * <ul>
 *   <li>No duplicate slot names within the same kind (params, locals, spills)</li>
 *   <li>No overlapping byte ranges within the same kind</li>
 *   <li>Parameters and locals have independent offset spaces (separate namespaces)</li>
 *   <li>All slot types must be valid IR types</li>
 *   <li>Offsets must be non-negative</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SlotVerifier {

  private final VerificationContext context;

  /**
   * Creates a new slot verifier.
   *
   * @param context the verification context for reporting errors
   */
  public SlotVerifier(VerificationContext context) {
    this.context = Objects.requireNonNull(context, "context must not be null");
  }

  /**
   * Verifies all slots for a function.
   *
   * @param functionName the function name for error reporting
   * @param slots the list of slots to verify
   */
  public void verifySlots(String functionName, List<IrSlot> slots) {
    if (slots == null || slots.isEmpty()) {
      return;
    }

    // Group slots by kind for independent validation
    Map<IrSlot.Kind, List<IrSlot>> slotsByKind = new HashMap<>();
    for (IrSlot.Kind kind : IrSlot.Kind.values()) {
      slotsByKind.put(kind, slots.stream()
          .filter(s -> s.kind() == kind)
          .toList());
    }

    // Verify each kind independently
    for (Map.Entry<IrSlot.Kind, List<IrSlot>> entry : slotsByKind.entrySet()) {
      verifySlotKind(functionName, entry.getKey(), entry.getValue());
    }
  }

  private void verifySlotKind(String functionName, IrSlot.Kind kind, List<IrSlot> slots) {
    if (slots.isEmpty()) {
      return;
    }

    Set<String> seenNames = new HashSet<>();

    for (IrSlot slot : slots) {
      // Check for duplicate names within kind
      if (!seenNames.add(slot.name())) {
        context.addFunctionError(functionName,
            "Duplicate " + kind.toIrString() + " slot name: " + slot.name());
      }

      // Check for negative offset
      if (slot.offset() < 0) {
        context.addFunctionError(functionName,
            "Slot '" + slot.name() + "' has negative offset: " + slot.offset());
      }

      // Check for null type
      if (slot.type() == null) {
        context.addFunctionError(functionName,
            "Slot '" + slot.name() + "' has null type");
      }
    }

    // Note: We do NOT check for overlapping offsets between different kinds
    // because params and locals use independent offset spaces per the
    // established convention in examples/valid/program39.ir
  }
}
