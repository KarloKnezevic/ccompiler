package hr.fer.ppj.ir.build;

import hr.fer.ppj.ir.model.IrSlot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages slot declarations (params, locals, spills) for a function.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SlotManager {

  private final Map<String, IrSlot> slots = new LinkedHashMap<>(); // Preserve insertion order

  /**
   * Adds a slot.
   */
  public void addSlot(IrSlot slot) {
    Objects.requireNonNull(slot, "slot must not be null");
    String key = slot.kind() + ":" + slot.name();
    if (slots.containsKey(key)) {
      throw new IllegalArgumentException("Slot " + key + " already exists");
    }
    slots.put(key, slot);
  }

  /**
   * Gets all slots in order (params first, then locals, then spills).
   */
  public List<IrSlot> getOrderedSlots() {
    List<IrSlot> orderedSlots = new ArrayList<>();
    for (IrSlot slot : slots.values()) {
      if (slot.kind() == IrSlot.Kind.PARAM) {
        orderedSlots.add(slot);
      }
    }
    for (IrSlot slot : slots.values()) {
      if (slot.kind() == IrSlot.Kind.LOCAL) {
        orderedSlots.add(slot);
      }
    }
    for (IrSlot slot : slots.values()) {
      if (slot.kind() == IrSlot.Kind.SPILL) {
        orderedSlots.add(slot);
      }
    }
    return orderedSlots;
  }

  /**
   * Gets all slots (for frame size computation).
   */
  public List<IrSlot> getSlots() {
    return new ArrayList<>(slots.values());
  }
}
