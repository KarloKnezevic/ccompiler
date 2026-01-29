package hr.fer.ppj.ir.build;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Manages IR blocks during function construction.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BlockManager {

  private final LabelFactory labelFactory;
  private final IrType returnType;
  private final List<IrBlock> blocks = new ArrayList<>();
  private final Map<String, IrBlock.Builder> unfinishedBlocks = new LinkedHashMap<>();

  // Current block being built
  private IrBlock.Builder currentBlockBuilder;
  private String currentBlockLabel;

  public BlockManager(LabelFactory labelFactory, IrType returnType) {
    this.labelFactory = Objects.requireNonNull(labelFactory, "labelFactory must not be null");
    this.returnType = returnType;
  }

  /**
   * Starts a new block with the given label.
   */
  public void startBlock(String label) {
    finishCurrentBlock();
    currentBlockLabel = Objects.requireNonNull(label, "label must not be null");
    currentBlockBuilder = IrBlock.builder(label);
  }

  /**
   * Starts a new block with an auto-generated label.
   */
  public String startNewBlock() {
    String label = labelFactory.newLabel();
    startBlock(label);
    return label;
  }

  /**
   * Gets the current block builder.
   */
  public IrBlock.Builder getCurrentBlockBuilder() {
    return currentBlockBuilder;
  }

  /**
   * Gets the current block label, or null if no block is active.
   */
  public String getCurrentBlockLabel() {
    return currentBlockLabel;
  }

  /**
   * Sets the terminator for the current block and finishes it.
   */
  public void setTerminator(IrTerminator terminator) {
    if (currentBlockBuilder == null) {
      throw new IllegalStateException("No current block - call startBlock() first");
    }
    currentBlockBuilder.setTerminator(terminator);
    finishCurrentBlock();
  }

  /**
   * Finishes the current block if one is being built.
   */
  private void finishCurrentBlock() {
    if (currentBlockBuilder != null) {
      try {
        IrBlock block = currentBlockBuilder.build();
        blocks.add(block);
      } catch (IllegalStateException e) {
        // Block doesn't have terminator yet - save it for later
        unfinishedBlocks.put(currentBlockLabel, currentBlockBuilder);
      }
      currentBlockBuilder = null;
      currentBlockLabel = null;
    }
  }

  /**
   * Gets all blocks that have been finished so far.
   */
  public List<IrBlock> getBlocks() {
    return new ArrayList<>(blocks);
  }

  /**
   * Finishes all blocks and returns the complete list.
   *
   * @throws IllegalStateException if any block is missing a terminator
   */
  public List<IrBlock> finishAllBlocks() {
    // Finish current block if active
    if (currentBlockBuilder != null) {
      finishBlockOrThrow(currentBlockBuilder, currentBlockLabel);
      currentBlockBuilder = null;
      currentBlockLabel = null;
    }

    // Finish all unfinished blocks
    for (Map.Entry<String, IrBlock.Builder> entry : unfinishedBlocks.entrySet()) {
      finishBlockOrThrow(entry.getValue(), entry.getKey());
    }
    unfinishedBlocks.clear();

    return blocks;
  }

  /**
   * Finishes a block, throwing an exception if it doesn't have a terminator.
   *
   * @param builder the block builder
   * @param label the block label (for error messages)
   * @throws IllegalStateException if the block doesn't have a terminator
   */
  private void finishBlockOrThrow(IrBlock.Builder builder, String label) {
    try {
      IrBlock block = builder.build();
      blocks.add(block);
    } catch (IllegalStateException e) {
      // Block doesn't have terminator - throw exception with function context
      // Note: We don't have function name here, but the label helps identify the block
      throw new IllegalStateException(
          "Block " + label + " is missing a terminator (br/jmp/ret). "
          + "Every basic block must end with exactly one terminator.", e);
    }
  }
}
