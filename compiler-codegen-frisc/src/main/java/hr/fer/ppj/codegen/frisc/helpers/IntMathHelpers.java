package hr.fer.ppj.codegen.frisc.helpers;

import hr.fer.ppj.codegen.frisc.emitter.FriscEmitter;
import hr.fer.ppj.codegen.frisc.lowering.LoweringSupport;
import java.util.List;

/**
 * Emits integer arithmetic helpers (mul/div/mod).
 */
final class IntMathHelpers {
  private final FriscEmitter emitter;
  private final HelperLabeler labels;

  IntMathHelpers(FriscEmitter emitter, HelperLabeler labels) {
    this.emitter = emitter;
    this.labels = labels;
  }

  void emitMul() {
    emitter.emitLabel("F_MUL", "int32 multiplication");
    emitter.emitInstruction("PUSH", List.of("R5"), null);
    emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
    emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + LoweringSupport.formatImmediate(8) + ")"), "a");
    emitter.emitInstruction("LOAD", List.of("R1", "(R5+" + LoweringSupport.formatImmediate(12) + ")"), "b");
    emitter.emitInstruction("MOVE", List.of("0", "R4"), "zero");
    emitter.emitInstruction("MOVE", List.of("0", "R2"), "sign");
    emitter.emitInstruction("CMP", List.of("R0", "0"), null);
    String aPos = labels.next("L_MUL_A_POS");
    String bPos = labels.next("L_MUL_B_POS");
    String done = labels.next("L_MUL_DONE");
    String skipAdd = labels.next("L_MUL_SKIP");
    emitter.emitInstruction("JP_SGE", List.of(aPos), null);
    emitter.emitInstruction("SUB", List.of("R4", "R0", "R0"), null);
    emitter.emitInstruction("XOR", List.of("R2", "1", "R2"), null);
    emitter.emitLabel(aPos, null);
    emitter.emitInstruction("CMP", List.of("R1", "0"), null);
    emitter.emitInstruction("JP_SGE", List.of(bPos), null);
    emitter.emitInstruction("SUB", List.of("R4", "R1", "R1"), null);
    emitter.emitInstruction("XOR", List.of("R2", "1", "R2"), null);
    emitter.emitLabel(bPos, null);
    emitter.emitInstruction("MOVE", List.of("0", "R3"), "result");
    String loop = labels.next("L_MUL_LOOP");
    emitter.emitLabel(loop, null);
    emitter.emitInstruction("CMP", List.of("R1", "0"), null);
    emitter.emitInstruction("JP_EQ", List.of(done), null);
    emitter.emitInstruction("AND", List.of("R1", "1", "R4"), null);
    emitter.emitInstruction("CMP", List.of("R4", "0"), null);
    emitter.emitInstruction("JP_EQ", List.of(skipAdd), null);
    emitter.emitInstruction("ADD", List.of("R3", "R0", "R3"), null);
    emitter.emitLabel(skipAdd, null);
    emitter.emitInstruction("SHL", List.of("R0", "1", "R0"), null);
    emitter.emitInstruction("SHR", List.of("R1", "1", "R1"), null);
    emitter.emitInstruction("JP", List.of(loop), null);
    emitter.emitLabel(done, null);
    String signDone = labels.next("L_MUL_SIGN_DONE");
    emitter.emitInstruction("CMP", List.of("R2", "0"), null);
    emitter.emitInstruction("JP_EQ", List.of(signDone), null);
    emitter.emitInstruction("MOVE", List.of("0", "R4"), "zero");
    emitter.emitInstruction("SUB", List.of("R4", "R3", "R3"), null);
    emitter.emitLabel(signDone, null);
    emitter.emitInstruction("MOVE", List.of("R3", "R6"), null);
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);
  }

  void emitDiv() {
    emitter.emitLabel("F_DIV", "int32 division");
    emitter.emitInstruction("PUSH", List.of("R5"), null);
    emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
    emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + LoweringSupport.formatImmediate(8) + ")"), "dividend");
    emitter.emitInstruction("LOAD", List.of("R1", "(R5+" + LoweringSupport.formatImmediate(12) + ")"), "divisor");
    emitter.emitInstruction("MOVE", List.of("0", "R4"), "zero");
    emitter.emitInstruction("CMP", List.of("R1", "0"), null);
    String divByZero = labels.next("L_DIV_ZERO");
    emitter.emitInstruction("JP_EQ", List.of(divByZero), null);
    String notNegOneDivisor = labels.next("L_DIV_NOT_NEG_ONE");
    String negOneDone = labels.next("L_DIV_NEG_ONE_DONE");
    String negOneNotMin = labels.next("L_DIV_NEG_ONE_NOT_MIN");
    emitter.emitInstruction("CMP", List.of("R1", "-1"), null);
    emitter.emitInstruction("JP_NE", List.of(notNegOneDivisor), null);
    emitter.emitInstruction("MOVE", List.of("80000000", "R3"), "INT_MIN");
    emitter.emitInstruction("CMP", List.of("R0", "R3"), null);
    emitter.emitInstruction("JP_NE", List.of(negOneNotMin), null);
    emitter.emitInstruction("MOVE", List.of("R3", "R6"), "INT_MIN / -1 = INT_MIN");
    emitter.emitInstruction("JP", List.of(negOneDone), null);
    emitter.emitLabel(negOneNotMin, null);
    emitter.emitInstruction("SUB", List.of("R4", "R0", "R6"), "x / -1 = -x");
    emitter.emitLabel(negOneDone, null);
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);
    emitter.emitLabel(notNegOneDivisor, null);
    emitter.emitInstruction("MOVE", List.of("0", "R6"), "sign");
    String aPos = labels.next("L_DIV_A_POS");
    emitter.emitInstruction("CMP", List.of("R0", "0"), null);
    emitter.emitInstruction("JP_SGE", List.of(aPos), null);
    emitter.emitInstruction("SUB", List.of("R4", "R0", "R0"), null);
    emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
    emitter.emitLabel(aPos, null);
    String bPos = labels.next("L_DIV_B_POS");
    emitter.emitInstruction("CMP", List.of("R1", "0"), null);
    emitter.emitInstruction("JP_SGE", List.of(bPos), null);
    emitter.emitInstruction("SUB", List.of("R4", "R1", "R1"), null);
    emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
    emitter.emitLabel(bPos, null);

    emitter.emitInstruction("MOVE", List.of("0", "R2"), "remainder");
    emitter.emitInstruction("MOVE", List.of("0", "R3"), "quotient");
    emitter.emitInstruction("MOVE", List.of("20", "R4"), "bit count (32)");
    String loop = labels.next("L_DIV_LOOP");
    String noCarry = labels.next("L_DIV_NO_CARRY");
    String afterCarry = labels.next("L_DIV_AFTER_CARRY");
    String skipSub = labels.next("L_DIV_SKIP_SUB");
    emitter.emitLabel(loop, null);
    emitter.emitInstruction("SHL", List.of("R0", "1", "R0"), null);
    emitter.emitInstruction("JP_NC", List.of(noCarry), null);
    emitter.emitInstruction("SHL", List.of("R2", "1", "R2"), null);
    emitter.emitInstruction("OR", List.of("R2", "1", "R2"), null);
    emitter.emitInstruction("JP", List.of(afterCarry), null);
    emitter.emitLabel(noCarry, null);
    emitter.emitInstruction("SHL", List.of("R2", "1", "R2"), null);
    emitter.emitLabel(afterCarry, null);
    emitter.emitInstruction("SHL", List.of("R3", "1", "R3"), null);
    emitter.emitInstruction("CMP", List.of("R2", "R1"), null);
    emitter.emitInstruction("JP_SLT", List.of(skipSub), null);
    emitter.emitInstruction("SUB", List.of("R2", "R1", "R2"), null);
    emitter.emitInstruction("OR", List.of("R3", "1", "R3"), null);
    emitter.emitLabel(skipSub, null);
    emitter.emitInstruction("SUB", List.of("R4", "1", "R4"), null);
    emitter.emitInstruction("JP_NE", List.of(loop), null);

    String signDone = labels.next("L_DIV_SIGN_DONE");
    emitter.emitInstruction("CMP", List.of("R6", "0"), null);
    emitter.emitInstruction("JP_EQ", List.of(signDone), null);
    emitter.emitInstruction("SUB", List.of("R4", "R3", "R3"), null);
    emitter.emitLabel(signDone, null);
    emitter.emitInstruction("MOVE", List.of("R3", "R6"), null);
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);
    emitter.emitLabel(divByZero, null);
    emitter.emitInstruction("MOVE", List.of("0", "R6"), null);
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);
  }

  void emitMod() {
    emitter.emitLabel("F_MOD", "int32 modulo");
    emitter.emitInstruction("PUSH", List.of("R5"), null);
    emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
    emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + LoweringSupport.formatImmediate(8) + ")"), "dividend");
    emitter.emitInstruction("LOAD", List.of("R1", "(R5+" + LoweringSupport.formatImmediate(12) + ")"), "divisor");
    emitter.emitInstruction("MOVE", List.of("0", "R4"), "zero");
    emitter.emitInstruction("CMP", List.of("R1", "0"), null);
    String modByZero = labels.next("L_MOD_ZERO");
    emitter.emitInstruction("JP_EQ", List.of(modByZero), null);
    String notNegOneDivisor = labels.next("L_MOD_NOT_NEG_ONE");
    emitter.emitInstruction("CMP", List.of("R1", "-1"), null);
    emitter.emitInstruction("JP_NE", List.of(notNegOneDivisor), null);
    emitter.emitInstruction("MOVE", List.of("0", "R6"), "x % -1 = 0");
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);
    emitter.emitLabel(notNegOneDivisor, null);
    emitter.emitInstruction("MOVE", List.of("0", "R6"), "sign");
    String aPos = labels.next("L_MOD_A_POS");
    emitter.emitInstruction("CMP", List.of("R0", "0"), null);
    emitter.emitInstruction("JP_SGE", List.of(aPos), null);
    emitter.emitInstruction("SUB", List.of("R4", "R0", "R0"), null);
    emitter.emitInstruction("MOVE", List.of("1", "R6"), null);
    emitter.emitLabel(aPos, null);
    String bPos = labels.next("L_MOD_B_POS");
    emitter.emitInstruction("CMP", List.of("R1", "0"), null);
    emitter.emitInstruction("JP_SGE", List.of(bPos), null);
    emitter.emitInstruction("SUB", List.of("R4", "R1", "R1"), null);
    emitter.emitLabel(bPos, null);

    emitter.emitInstruction("MOVE", List.of("0", "R2"), "remainder");
    emitter.emitInstruction("MOVE", List.of("20", "R4"), "bit count (32)");
    String loop = labels.next("L_MOD_LOOP");
    String noCarry = labels.next("L_MOD_NO_CARRY");
    String afterCarry = labels.next("L_MOD_AFTER_CARRY");
    String skipSub = labels.next("L_MOD_SKIP_SUB");
    emitter.emitLabel(loop, null);
    emitter.emitInstruction("SHL", List.of("R0", "1", "R0"), null);
    emitter.emitInstruction("JP_NC", List.of(noCarry), null);
    emitter.emitInstruction("SHL", List.of("R2", "1", "R2"), null);
    emitter.emitInstruction("OR", List.of("R2", "1", "R2"), null);
    emitter.emitInstruction("JP", List.of(afterCarry), null);
    emitter.emitLabel(noCarry, null);
    emitter.emitInstruction("SHL", List.of("R2", "1", "R2"), null);
    emitter.emitLabel(afterCarry, null);
    emitter.emitInstruction("CMP", List.of("R2", "R1"), null);
    emitter.emitInstruction("JP_SLT", List.of(skipSub), null);
    emitter.emitInstruction("SUB", List.of("R2", "R1", "R2"), null);
    emitter.emitLabel(skipSub, null);
    emitter.emitInstruction("SUB", List.of("R4", "1", "R4"), null);
    emitter.emitInstruction("JP_NE", List.of(loop), null);

    String signDone = labels.next("L_MOD_SIGN_DONE");
    emitter.emitInstruction("CMP", List.of("R6", "0"), null);
    emitter.emitInstruction("JP_EQ", List.of(signDone), null);
    emitter.emitInstruction("SUB", List.of("R4", "R2", "R2"), null);
    emitter.emitLabel(signDone, null);
    emitter.emitInstruction("MOVE", List.of("R2", "R6"), null);
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);
    emitter.emitLabel(modByZero, null);
    emitter.emitInstruction("MOVE", List.of("0", "R6"), null);
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);
  }
}
