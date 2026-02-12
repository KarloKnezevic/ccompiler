package hr.fer.ppj.opt;

import hr.fer.ppj.ir.IrPipeline;
import hr.fer.ppj.ir.model.IrBlock;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.ir.model.IrGlobalVar;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrSlot;
import hr.fer.ppj.ir.model.IrSymbolRef;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.model.IrTerminator;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.opt.api.IrOptimizer;
import hr.fer.ppj.opt.api.OptimizationOptions;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrOptimizerRulesTest {

  private final IrOptimizer optimizer = new IrOptimizer();

  @Test
  void removesAddZeroThroughCopyPropagationAndDce() {
    IrTemp t0 = new IrTemp(0, IrPrimitiveType.INT32);
    IrTemp t1 = new IrTemp(1, IrPrimitiveType.INT32);

    IrBlock block = new IrBlock(
        "L0",
        List.of(
            new IrInstruction.IrAssignInstr(t0, new IrRhs.ConstRhs(new IrConst.IntConst(7, IrPrimitiveType.INT32))),
            new IrInstruction.IrAssignInstr(
                t1,
                new IrRhs.BinOp(
                    IrRhs.BinOpName.ADD,
                    t0,
                    new IrConst.IntConst(0, IrPrimitiveType.INT32),
                    IrPrimitiveType.INT32))),
        new IrTerminator.IrRetTerm(t1));

    IrProgram optimized = optimizer.optimize(programOf(block), OptimizationOptions.O1);
    IrPipeline.verify(optimized);
    String text = IrPipeline.print(optimized);

    assertFalse(text.contains("add t0, #0:int32"));
    assertTrue(text.contains("ret t0"));
  }

  @Test
  void rewritesMulByPowerOfTwoToShiftLeft() {
    IrTemp t0 = new IrTemp(0, IrPrimitiveType.INT32);
    IrTemp t1 = new IrTemp(1, IrPrimitiveType.INT32);

    IrBlock block = new IrBlock(
        "L0",
        List.of(
            new IrInstruction.IrAssignInstr(t0, new IrRhs.ConstRhs(new IrConst.IntConst(3, IrPrimitiveType.INT32))),
            new IrInstruction.IrAssignInstr(
                t1,
                new IrRhs.BinOp(
                    IrRhs.BinOpName.MUL,
                    t0,
                    new IrConst.IntConst(8, IrPrimitiveType.INT32),
                    IrPrimitiveType.INT32))),
        new IrTerminator.IrRetTerm(t1));

    IrProgram optimized = optimizer.optimize(programOf(block), OptimizationOptions.O1);
    IrPipeline.verify(optimized);
    String text = IrPipeline.print(optimized);

    assertTrue(text.contains("shl t0, #3:int32 : int32"));
  }

  @Test
  void foldsConstantBranchToJump() {
    IrBlock entry = new IrBlock(
        "L0",
        List.of(),
        new IrTerminator.IrBrTerm(new IrConst.IntConst(1, IrPrimitiveType.BOOL), "L1", "L2"));
    IrBlock l1 = new IrBlock(
        "L1",
        List.of(),
        new IrTerminator.IrRetTerm(new IrConst.IntConst(11, IrPrimitiveType.INT32)));
    IrBlock l2 = new IrBlock(
        "L2",
        List.of(),
        new IrTerminator.IrRetTerm(new IrConst.IntConst(22, IrPrimitiveType.INT32)));

    IrProgram optimized = optimizer.optimize(programOf(entry, l1, l2), OptimizationOptions.O1);
    IrPipeline.verify(optimized);
    IrTerminator term = optimized.functions().getFirst().blocks().getFirst().terminator();

    assertFalse(term instanceof IrTerminator.IrBrTerm);
  }

  @Test
  void rewritesModuloByMinusOneToZeroConstant() {
    IrTemp t0 = new IrTemp(0, IrPrimitiveType.INT32);

    IrBlock block = new IrBlock(
        "L0",
        List.of(new IrInstruction.IrAssignInstr(
            t0,
            new IrRhs.BinOp(
                IrRhs.BinOpName.MOD,
                new IrConst.IntConst(Integer.MIN_VALUE, IrPrimitiveType.INT32),
                new IrConst.IntConst(-1, IrPrimitiveType.INT32),
                IrPrimitiveType.INT32))),
        new IrTerminator.IrRetTerm(t0));

    IrProgram optimized = optimizer.optimize(programOf(block), OptimizationOptions.O1);
    IrPipeline.verify(optimized);
    String text = IrPipeline.print(optimized);

    assertTrue(text.contains("#0:int32"));
  }

  @Test
  void keepsFrameAndSlotsUnchanged() {
    IrTemp t0 = new IrTemp(0, IrPrimitiveType.INT32);

    IrBlock block = new IrBlock(
        "L0",
        List.of(new IrInstruction.IrAssignInstr(
            t0,
            new IrRhs.BinOp(
                IrRhs.BinOpName.ADD,
                new IrConst.IntConst(1, IrPrimitiveType.INT32),
                new IrConst.IntConst(2, IrPrimitiveType.INT32),
                IrPrimitiveType.INT32))),
        new IrTerminator.IrRetTerm(t0));

    IrFunction function = new IrFunction(
        "main",
        List.of(),
        IrPrimitiveType.INT32,
        16,
        4,
        List.of(
            new hr.fer.ppj.ir.model.IrSlot(hr.fer.ppj.ir.model.IrSlot.Kind.LOCAL, "x", 0, IrPrimitiveType.INT32),
            new hr.fer.ppj.ir.model.IrSlot(hr.fer.ppj.ir.model.IrSlot.Kind.PARAM, "p", 0, IrPrimitiveType.INT32)),
        List.of(block));

    IrProgram optimized = optimizer.optimize(new IrProgram(List.of(), java.util.Map.of(), List.of(function)), OptimizationOptions.O1);
    IrFunction optimizedFunction = optimized.functions().getFirst();

    assertEquals(16, optimizedFunction.localsBytes());
    assertEquals(4, optimizedFunction.alignBytes());
    assertEquals(function.slots(), optimizedFunction.slots());
  }

  @Test
  void propagatesKnownSlotConstantAcrossBlocks() {
    IrSlot slot = new IrSlot(IrSlot.Kind.LOCAL, "x", 0, IrPrimitiveType.INT32);
    IrTemp entryAddr = new IrTemp(0, new IrPointerType(IrPrimitiveType.INT32));
    IrTemp loadAddr = new IrTemp(2, new IrPointerType(IrPrimitiveType.INT32));
    IrTemp load = new IrTemp(1, IrPrimitiveType.INT32);

    IrBlock entry = new IrBlock(
        "L0",
        List.of(
            new IrInstruction.IrAssignInstr(
                entryAddr,
                new IrRhs.AddrOfSymbol(new IrSymbolRef(IrSymbolRef.Kind.LOCAL, "x"), entryAddr.type())),
            new IrInstruction.IrStoreInstr(entryAddr, new IrConst.IntConst(7, IrPrimitiveType.INT32), IrPrimitiveType.INT32)),
        new IrTerminator.IrJmpTerm("L1"));

    IrBlock body = new IrBlock(
        "L1",
        List.of(
            new IrInstruction.IrAssignInstr(
                loadAddr,
                new IrRhs.AddrOfSymbol(new IrSymbolRef(IrSymbolRef.Kind.LOCAL, "x"), loadAddr.type())),
            new IrInstruction.IrAssignInstr(load, new IrRhs.Load(loadAddr, IrPrimitiveType.INT32))),
        new IrTerminator.IrRetTerm(load));

    IrFunction function = new IrFunction(
        "main",
        List.of(),
        IrPrimitiveType.INT32,
        4,
        4,
        List.of(slot),
        List.of(entry, body));

    IrProgram optimized = optimizer.optimize(new IrProgram(List.<IrGlobalVar>of(), java.util.Map.of(), List.of(function)), OptimizationOptions.O1);
    IrPipeline.verify(optimized);
    String text = IrPipeline.print(optimized);

    assertTrue(text.contains("ret #7:int32"));
  }

  @Test
  void removesUnreachableBlocks() {
    IrBlock entry = new IrBlock(
        "L0",
        List.of(),
        new IrTerminator.IrRetTerm(new IrConst.IntConst(1, IrPrimitiveType.INT32)));
    IrBlock dead = new IrBlock(
        "L_dead",
        List.of(),
        new IrTerminator.IrRetTerm(new IrConst.IntConst(2, IrPrimitiveType.INT32)));

    IrProgram optimized = optimizer.optimize(programOf(entry, dead), OptimizationOptions.O1);
    IrPipeline.verify(optimized);
    String text = IrPipeline.print(optimized);

    assertFalse(text.contains("L_dead:"));
  }

  @Test
  void simplifiesBranchUsingRangeInformation() {
    IrSlot slot = new IrSlot(IrSlot.Kind.LOCAL, "i", 0, IrPrimitiveType.INT32);
    IrTemp entryAddr = new IrTemp(0, new IrPointerType(IrPrimitiveType.INT32));
    IrTemp load = new IrTemp(1, IrPrimitiveType.INT32);
    IrTemp cmp = new IrTemp(2, IrPrimitiveType.BOOL);
    IrTemp checkAddr = new IrTemp(3, new IrPointerType(IrPrimitiveType.INT32));

    IrBlock entry = new IrBlock(
        "L0",
        List.of(
            new IrInstruction.IrAssignInstr(
                entryAddr,
                new IrRhs.AddrOfSymbol(new IrSymbolRef(IrSymbolRef.Kind.LOCAL, "i"), entryAddr.type())),
            new IrInstruction.IrStoreInstr(entryAddr, new IrConst.IntConst(0, IrPrimitiveType.INT32), IrPrimitiveType.INT32)),
        new IrTerminator.IrJmpTerm("L1"));

    IrBlock check = new IrBlock(
        "L1",
        List.of(
            new IrInstruction.IrAssignInstr(
                checkAddr,
                new IrRhs.AddrOfSymbol(new IrSymbolRef(IrSymbolRef.Kind.LOCAL, "i"), checkAddr.type())),
            new IrInstruction.IrAssignInstr(load, new IrRhs.Load(checkAddr, IrPrimitiveType.INT32)),
            new IrInstruction.IrAssignInstr(
                cmp,
                new IrRhs.CmpOp(IrRhs.CmpOpName.LT, load, new IrConst.IntConst(10, IrPrimitiveType.INT32)))),
        new IrTerminator.IrBrTerm(cmp, "L2", "L3"));

    IrBlock l2 = new IrBlock(
        "L2",
        List.of(),
        new IrTerminator.IrRetTerm(new IrConst.IntConst(1, IrPrimitiveType.INT32)));
    IrBlock l3 = new IrBlock(
        "L3",
        List.of(),
        new IrTerminator.IrRetTerm(new IrConst.IntConst(0, IrPrimitiveType.INT32)));

    IrFunction function = new IrFunction(
        "main",
        List.of(),
        IrPrimitiveType.INT32,
        4,
        4,
        List.of(slot),
        List.of(entry, check, l2, l3));

    IrProgram optimized = optimizer.optimize(new IrProgram(List.<IrGlobalVar>of(), java.util.Map.of(), List.of(function)), OptimizationOptions.O1);
    IrPipeline.verify(optimized);
    String text = IrPipeline.print(optimized);

    assertFalse(text.contains("br t2"));
    assertFalse(text.contains("L3:"));
    assertTrue(text.contains("ret #1:int32"));
  }

  @Test
  void appliesLoopStrengthReductionForInductionMultiplyByFive() {
    IrSlot iSlot = new IrSlot(IrSlot.Kind.LOCAL, "i", 0, IrPrimitiveType.INT32);
    IrSlot outSlot = new IrSlot(IrSlot.Kind.LOCAL, "out", 4, IrPrimitiveType.INT32);

    IrTemp iAddrEntry = new IrTemp(0, new IrPointerType(IrPrimitiveType.INT32));
    IrTemp outAddrEntry = new IrTemp(1, new IrPointerType(IrPrimitiveType.INT32));
    IrTemp iLoad = new IrTemp(2, IrPrimitiveType.INT32);
    IrTemp mul = new IrTemp(3, IrPrimitiveType.INT32);
    IrTemp inc = new IrTemp(4, IrPrimitiveType.INT32);
    IrTemp cmp = new IrTemp(5, IrPrimitiveType.BOOL);
    IrTemp outLoad = new IrTemp(6, IrPrimitiveType.INT32);
    IrTemp iAddrLoop = new IrTemp(7, new IrPointerType(IrPrimitiveType.INT32));
    IrTemp outAddrLoop = new IrTemp(8, new IrPointerType(IrPrimitiveType.INT32));
    IrTemp outAddrExit = new IrTemp(9, new IrPointerType(IrPrimitiveType.INT32));

    IrBlock entry = new IrBlock(
        "L0",
        List.of(
            new IrInstruction.IrAssignInstr(
                iAddrEntry,
                new IrRhs.AddrOfSymbol(new IrSymbolRef(IrSymbolRef.Kind.LOCAL, "i"), iAddrEntry.type())),
            new IrInstruction.IrAssignInstr(
                outAddrEntry,
                new IrRhs.AddrOfSymbol(new IrSymbolRef(IrSymbolRef.Kind.LOCAL, "out"), outAddrEntry.type())),
            new IrInstruction.IrStoreInstr(iAddrEntry, new IrConst.IntConst(0, IrPrimitiveType.INT32), IrPrimitiveType.INT32),
            new IrInstruction.IrStoreInstr(outAddrEntry, new IrConst.IntConst(0, IrPrimitiveType.INT32), IrPrimitiveType.INT32)),
        new IrTerminator.IrJmpTerm("L1"));

    IrBlock loop = new IrBlock(
        "L1",
        List.of(
            new IrInstruction.IrAssignInstr(
                iAddrLoop,
                new IrRhs.AddrOfSymbol(new IrSymbolRef(IrSymbolRef.Kind.LOCAL, "i"), iAddrLoop.type())),
            new IrInstruction.IrAssignInstr(
                outAddrLoop,
                new IrRhs.AddrOfSymbol(new IrSymbolRef(IrSymbolRef.Kind.LOCAL, "out"), outAddrLoop.type())),
            new IrInstruction.IrAssignInstr(iLoad, new IrRhs.Load(iAddrLoop, IrPrimitiveType.INT32)),
            new IrInstruction.IrAssignInstr(
                mul,
                new IrRhs.BinOp(IrRhs.BinOpName.MUL, iLoad, new IrConst.IntConst(5, IrPrimitiveType.INT32), IrPrimitiveType.INT32)),
            new IrInstruction.IrStoreInstr(outAddrLoop, mul, IrPrimitiveType.INT32),
            new IrInstruction.IrAssignInstr(
                inc,
                new IrRhs.BinOp(IrRhs.BinOpName.ADD, iLoad, new IrConst.IntConst(1, IrPrimitiveType.INT32), IrPrimitiveType.INT32)),
            new IrInstruction.IrStoreInstr(iAddrLoop, inc, IrPrimitiveType.INT32),
            new IrInstruction.IrAssignInstr(
                cmp,
                new IrRhs.CmpOp(IrRhs.CmpOpName.LT, inc, new IrConst.IntConst(4, IrPrimitiveType.INT32)))),
        new IrTerminator.IrBrTerm(cmp, "L1", "L2"));

    IrBlock exit = new IrBlock(
        "L2",
        List.of(
            new IrInstruction.IrAssignInstr(
                outAddrExit,
                new IrRhs.AddrOfSymbol(new IrSymbolRef(IrSymbolRef.Kind.LOCAL, "out"), outAddrExit.type())),
            new IrInstruction.IrAssignInstr(outLoad, new IrRhs.Load(outAddrExit, IrPrimitiveType.INT32))),
        new IrTerminator.IrRetTerm(outLoad));

    IrFunction function = new IrFunction(
        "main",
        List.of(),
        IrPrimitiveType.INT32,
        8,
        4,
        List.of(iSlot, outSlot),
        List.of(entry, loop, exit));

    IrProgram optimized = optimizer.optimize(new IrProgram(List.<IrGlobalVar>of(), java.util.Map.of(), List.of(function)), OptimizationOptions.O1);
    IrPipeline.verify(optimized);
    String text = IrPipeline.print(optimized);

    assertTrue(text.contains("shl t2, #2:int32 : int32"));
    assertFalse(text.contains("mul t2, #5:int32 : int32"));
  }

  private static IrProgram programOf(IrBlock... blocks) {
    IrFunction function = new IrFunction(
        "main",
        List.of(),
        IrPrimitiveType.INT32,
        0,
        4,
        List.of(),
        List.of(blocks));
    return new IrProgram(List.of(), java.util.Map.of(), List.of(function));
  }
}
