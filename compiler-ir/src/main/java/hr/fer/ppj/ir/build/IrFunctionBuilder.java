package hr.fer.ppj.ir.build;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrSlot;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Builder for constructing IR functions incrementally.
 *
 * <p>This builder manages:
 * <ul>
 *   <li>Function parameters</li>
 *   <li>Frame layout (locals, alignment)</li>
 *   <li>Slot declarations (params, locals, spills)</li>
 *   <li>Blocks and instructions</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class IrFunctionBuilder {

  private final String functionName;
  private final IrType returnType;
  private final List<IrFunction.Parameter> parameters = new ArrayList<>();
  private final Set<String> parameterNames = new HashSet<>();
  private final TempFactory tempFactory = new TempFactory();
  private final LabelFactory labelFactory = new LabelFactory();
  private final BlockManager blockManager;
  private final SlotManager slotManager;
  private Runnable onBlockStartCallback;

  // Frame layout
  private int localsBytes = 0;
  private int alignBytes = 4;

  public IrFunctionBuilder(String functionName, IrType returnType) {
    this.functionName = Objects.requireNonNull(functionName, "functionName must not be null");
    this.returnType = returnType;
    this.blockManager = new BlockManager(labelFactory, returnType);
    this.slotManager = new SlotManager();
  }

  public TempFactory tempFactory() {
    return tempFactory;
  }

  public LabelFactory labelFactory() {
    return labelFactory;
  }

  public void addParameter(String paramName, IrType paramType) {
    parameters.add(new IrFunction.Parameter(paramName, paramType));
    parameterNames.add(paramName);
    // Parameter slots are added later when we compute frame layout
  }

  /**
   * Checks if a name is a parameter name.
   */
  public boolean isParameter(String name) {
    return parameterNames.contains(name);
  }

  public void setFrame(int localsBytes, int alignBytes) {
    this.localsBytes = localsBytes;
    this.alignBytes = alignBytes;
  }

  public void addSlot(IrSlot slot) {
    slotManager.addSlot(slot);
  }

  /**
   * Gets all slots (for frame size computation).
   */
  public List<IrSlot> getSlots() {
    return slotManager.getSlots();
  }

  /**
   * Sets a callback to be invoked when a new block starts.
   */
  public void setOnBlockStartCallback(Runnable callback) {
    this.onBlockStartCallback = callback;
  }

  /**
   * Starts a new block with the given label.
   */
  public void startBlock(String label) {
    blockManager.startBlock(label);
    if (onBlockStartCallback != null) {
      onBlockStartCallback.run();
    }
  }

  /**
   * Starts a new block with an auto-generated label.
   */
  public String startNewBlock() {
    String label = blockManager.startNewBlock();
    if (onBlockStartCallback != null) {
      onBlockStartCallback.run();
    }
    return label;
  }

  /**
   * Adds an instruction to the current block.
   */
  public void addInstruction(IrInstruction instruction) {
    Objects.requireNonNull(instruction, "instruction must not be null");
    IrBlock.Builder builder = blockManager.getCurrentBlockBuilder();
    if (builder == null) {
      throw new IllegalStateException("No current block - call startBlock() first");
    }
    builder.addInstruction(instruction);
  }

  /**
   * Sets the terminator for the current block and finishes it.
   */
  public void setTerminator(IrTerminator terminator) {
    Objects.requireNonNull(terminator, "terminator must not be null");
    blockManager.setTerminator(terminator);
  }

  /**
   * Gets the current block label, or null if no block is active.
   */
  public String getCurrentBlockLabel() {
    return blockManager.getCurrentBlockLabel();
  }

  /**
   * Gets all blocks that have been finished so far.
   */
  public List<IrBlock> getBlocks() {
    return blockManager.getBlocks();
  }

  /**
   * Builds the function.
   *
   * <p>Ensures all blocks have terminators before building.
   */
  public IrFunction build() {
    List<IrBlock> blocks = blockManager.finishAllBlocks();
    List<IrSlot> orderedSlots = slotManager.getOrderedSlots();

    return new IrFunction(
        functionName, parameters, returnType, localsBytes, alignBytes, orderedSlots, blocks);
  }
}

