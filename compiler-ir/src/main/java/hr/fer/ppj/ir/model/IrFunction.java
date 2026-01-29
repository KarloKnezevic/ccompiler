package hr.fer.ppj.ir.model;

import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * IR function definition.
 *
 * <p>Functions contain:
 * <ul>
 *   <li>Name and signature (parameters and return type)</li>
 *   <li>Frame information (locals size, alignment)</li>
 *   <li>Slot declarations (params, locals, spills)</li>
 *   <li>Blocks (labeled basic blocks)</li>
 * </ul>
 *
 * @param name the function name
 * @param parameters the function parameters (ordered)
 * @param returnType the return type
 * @param localsBytes the size of local variables in bytes
 * @param alignBytes the alignment requirement in bytes
 * @param slots the slot declarations (params, locals, spills)
 * @param blocks the function blocks (ordered)
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public record IrFunction(
    String name,
    List<Parameter> parameters,
    IrType returnType,
    int localsBytes,
    int alignBytes,
    List<IrSlot> slots,
    List<IrBlock> blocks) {

  public IrFunction {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(parameters, "parameters must not be null");
    // returnType can be null for void functions
    Objects.requireNonNull(slots, "slots must not be null");
    Objects.requireNonNull(blocks, "blocks must not be null");
    parameters = List.copyOf(parameters);
    slots = List.copyOf(slots);
    blocks = List.copyOf(blocks);
    if (localsBytes < 0) {
      throw new IllegalArgumentException("localsBytes must be non-negative");
    }
    if (alignBytes <= 0) {
      throw new IllegalArgumentException("alignBytes must be positive");
    }
  }

  /**
   * Function parameter.
   */
  public record Parameter(String name, IrType type) {
    public Parameter {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(type, "type must not be null");
    }
  }

  /**
   * Creates a new function builder.
   */
  public static Builder builder(String name, IrType returnType) {
    return new Builder(name, returnType);
  }

  /**
   * Builder for constructing functions incrementally.
   */
  public static final class Builder {
    private final String name;
    private final List<Parameter> parameters = new ArrayList<>();
    private final IrType returnType;
    private int localsBytes = 0;
    private int alignBytes = 4; // Default alignment
    private final List<IrSlot> slots = new ArrayList<>();
    private final List<IrBlock> blocks = new ArrayList<>();

    private Builder(String name, IrType returnType) {
      this.name = Objects.requireNonNull(name, "name must not be null");
      // returnType can be null for void functions
      this.returnType = returnType;
    }

    public Builder addParameter(String paramName, IrType paramType) {
      parameters.add(new Parameter(paramName, paramType));
      return this;
    }

    public Builder setFrame(int localsBytes, int alignBytes) {
      this.localsBytes = localsBytes;
      this.alignBytes = alignBytes;
      return this;
    }

    public Builder addSlot(IrSlot slot) {
      slots.add(Objects.requireNonNull(slot, "slot must not be null"));
      return this;
    }

    public Builder addBlock(IrBlock block) {
      blocks.add(Objects.requireNonNull(block, "block must not be null"));
      return this;
    }

    public IrFunction build() {
      return new IrFunction(name, parameters, returnType, localsBytes, alignBytes, slots, blocks);
    }
  }
}

