package hr.fer.ppj.ir.print;

import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrTemp;
import java.util.List;

/**
 * Prints IR blocks with formatting logic for blank lines.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BlockPrinter {

  private final StringBuilder out;
  private final InstructionPrinter instructionPrinter;
  private final TerminatorPrinter terminatorPrinter;

  public BlockPrinter(
      StringBuilder out, InstructionPrinter instructionPrinter, TerminatorPrinter terminatorPrinter) {
    this.out = out;
    this.instructionPrinter = instructionPrinter;
    this.terminatorPrinter = terminatorPrinter;
  }

  /**
   * Prints a block with formatting.
   */
  public void printBlock(IrBlock block) {
    out.append("  ").append(block.label()).append(":\n");

    List<IrInstruction> instructions = block.instructions();
    for (int i = 0; i < instructions.size(); i++) {
      IrInstruction instr = instructions.get(i);
      boolean isStore = instr instanceof IrInstruction.IrStoreInstr;
      boolean isIncDec =
          instr instanceof IrInstruction.IrAssignInstr assign
              && assign.rhs() instanceof IrRhs.IncDecOp;
      boolean isBinOp =
          instr instanceof IrInstruction.IrAssignInstr assign2
              && assign2.rhs() instanceof IrRhs.BinOp;

      out.append("    ");
      instructionPrinter.printInstruction(instr);
      out.append("\n");

      if ((isStore || isIncDec || isBinOp) && i + 1 < instructions.size()) {
        if (shouldAddBlankLine(instructions, i, isStore, isIncDec, isBinOp, instr)) {
          out.append("\n");
        }
      }
    }

    out.append("    ");
    terminatorPrinter.printTerminator(block.terminator());
    out.append("\n");
  }

  private boolean shouldAddBlankLine(
      List<IrInstruction> instructions,
      int currentIdx,
      boolean isStore,
      boolean isIncDec,
      boolean isBinOp,
      IrInstruction instr) {
    int nextIdx = currentIdx + 1;
    while (nextIdx < instructions.size()) {
      IrInstruction nextInstr = instructions.get(nextIdx);

      if (nextInstr instanceof IrInstruction.IrAssignInstr assign) {
        if (assign.rhs() instanceof IrRhs.AddrOfSymbol) {
          if (nextIdx + 1 < instructions.size()
              && instructions.get(nextIdx + 1) instanceof IrInstruction.IrStoreInstr) {
            if (isBinOp) {
              return true;
            }
            nextIdx++;
            continue;
          }
          if (nextIdx + 1 < instructions.size()) {
            IrInstruction afterAddr = instructions.get(nextIdx + 1);
            if (afterAddr instanceof IrInstruction.IrAssignInstr afterAssign) {
              if (afterAssign.rhs() instanceof IrRhs.Load) {
                if (nextIdx + 2 >= instructions.size()) {
                  return false;
                }
              } else if (afterAssign.rhs() instanceof IrRhs.IncDecOp) {
                if (nextIdx + 2 >= instructions.size()) {
                  return false;
                }
              }
            }
          }
          return true;
        }
      }

      boolean nextIsStore = nextInstr instanceof IrInstruction.IrStoreInstr;
      boolean nextIsIncDec =
          nextInstr instanceof IrInstruction.IrAssignInstr assign2
              && assign2.rhs() instanceof IrRhs.IncDecOp;

      if (nextIsStore) {
        boolean hasComputationBetween = false;
        for (int k = currentIdx + 1; k < nextIdx; k++) {
          IrInstruction betweenInstr = instructions.get(k);
          if (betweenInstr instanceof IrInstruction.IrAssignInstr assign) {
            if (assign.rhs() instanceof IrRhs.Load
                || assign.rhs() instanceof IrRhs.BinOp
                || assign.rhs() instanceof IrRhs.CmpOp
                || assign.rhs() instanceof IrRhs.IncDecOp) {
              hasComputationBetween = true;
              break;
            }
          }
        }
        return hasComputationBetween;
      } else if (nextIsIncDec) {
        if (isStore && nextIdx + 1 < instructions.size()) {
          return false;
        }
        return true;
      } else if (nextInstr instanceof IrInstruction.IrAssignInstr assign3) {
        if (assign3.rhs() instanceof IrRhs.Load) {
          if (nextIdx + 2 >= instructions.size()) {
            return false;
          }
          if (isIncDec) {
            return false;
          }
          return true;
        } else if (assign3.rhs() instanceof IrRhs.BinOp || assign3.rhs() instanceof IrRhs.CmpOp) {
          if (isIncDec) {
            return false;
          }
          if (isBinOp && assign3.rhs() instanceof IrRhs.BinOp) {
            IrTemp currentDest = ((IrInstruction.IrAssignInstr) instr).dest();
            IrRhs.BinOp nextBinOp = (IrRhs.BinOp) assign3.rhs();
            boolean usesCurrentResult =
                nextBinOp.left().equals(currentDest) || nextBinOp.right().equals(currentDest);
            if (!usesCurrentResult) {
              return true;
            }
            if (nextIdx + 1 < instructions.size()) {
              IrInstruction afterNextBinOp = instructions.get(nextIdx + 1);
              if (afterNextBinOp instanceof IrInstruction.IrAssignInstr afterAssign
                  && afterAssign.rhs() instanceof IrRhs.AddrOfSymbol) {
                return true;
              }
            }
            return false;
          }
          return true;
        } else if (assign3.rhs() instanceof IrRhs.Call) {
          if (isBinOp && nextIdx + 1 < instructions.size()) {
            IrInstruction afterCall = instructions.get(nextIdx + 1);
            if (afterCall instanceof IrInstruction.IrAssignInstr afterCallAssign
                && afterCallAssign.rhs() instanceof IrRhs.BinOp binOp) {
              IrTemp callResult = assign3.dest();
              boolean usesCallResult =
                  binOp.left().equals(callResult) || binOp.right().equals(callResult);
              return !usesCallResult;
            }
            return true;
          }
        }
      }
      return false;
    }
    return false;
  }
}
