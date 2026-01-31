package hr.fer.ppj.ir.model;

import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents an IR function definition.
 *
 * <p>This record corresponds to the FuncDef production in the IR grammar
 * ({@code config/ir_definition.txt}):
 *
 * <pre>
 * FuncDef
 *   ::= ".func" Ident "(" [ ParamList ] ")" ":" Type NL
 *       FrameDecl
 *       SlotsDecl
 *       BlocksDecl
 *       ".endfunc" NL ;
 *
 * FrameDecl
 *   ::= ".frame" "locals" "=" Int "bytes" "align" "=" Int NL ;
 *
 * SlotsDecl
 *   ::= ".slots" NL { SlotEntry } ;
 *
 * BlocksDecl
 *   ::= ".blocks" NL { Block } ;
 * </pre>
 *
 * <h3>Structure Invariants</h3>
 * <ul>
 *   <li>Function name must be non-null and non-empty</li>
 *   <li>Parameters are ordered as declared in the source</li>
 *   <li>Return type is null for void functions</li>
 *   <li>localsBytes must be non-negative</li>
 *   <li>alignBytes must be positive (typically 4)</li>
 *   <li>Each function must have at least one block</li>
 *   <li>Each block must end with exactly one terminator</li>
 * </ul>
 *
 * <h3>Slot Namespace Convention</h3>
 * <p>Parameters and locals use independent offset spaces. The same offset
 * value can appear for both a param and a local without conflict.
 *
 * @param name the function name
 * @param parameters the function parameters (ordered)
 * @param returnType the return type (null for void)
 * @param localsBytes the size of local storage in bytes
 * @param alignBytes the alignment requirement in bytes
 * @param slots the slot declarations (params, locals, spills)
 * @param blocks the basic blocks (ordered)
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 * @see IrSlot
 * @see IrBlock
 */
public record IrFunction(
    String name,
    List<Parameter> parameters,
    IrType returnType,
    int localsBytes,
    int alignBytes,
    List<IrSlot> slots,
    List<IrBlock> blocks) {

  /**
   * Creates an IR function with validation.
   *
   * @throws NullPointerException if name, parameters, slots, or blocks is null
   * @throws IllegalArgumentException if localsBytes is negative or alignBytes is non-positive
   */
  public IrFunction {
    Objects.requireNonNull(name, "name must not be null");
    Objects.requireNonNull(parameters, "parameters must not be null");
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
   * Represents a function parameter.
   *
   * <p>Corresponds to the Param production:
   * <pre>
   * Param ::= Ident ":" Type ;
   * </pre>
   *
   * @param name the parameter name
   * @param type the parameter type
   */
  public record Parameter(String name, IrType type) {
    public Parameter {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(type, "type must not be null");
    }
  }

  /**
   * Creates a new function builder.
   *
   * @param name the function name
   * @param returnType the return type (null for void)
   * @return a new builder instance
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
    private int alignBytes = 4;
    private final List<IrSlot> slots = new ArrayList<>();
    private final List<IrBlock> blocks = new ArrayList<>();

    private Builder(String name, IrType returnType) {
      this.name = Objects.requireNonNull(name, "name must not be null");
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
