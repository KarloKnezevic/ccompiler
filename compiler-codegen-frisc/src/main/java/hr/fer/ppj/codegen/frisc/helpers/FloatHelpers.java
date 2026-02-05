package hr.fer.ppj.codegen.frisc.helpers;

import hr.fer.ppj.codegen.frisc.emitter.FriscEmitter;
import hr.fer.ppj.codegen.frisc.lowering.LoweringSupport;
import java.util.List;

/**
 * Emits Q16.16 floating point helpers.
 */
final class FloatHelpers {
  private final FriscEmitter emitter;
  private final HelperLabeler labels;

  FloatHelpers(FriscEmitter emitter, HelperLabeler labels) {
    this.emitter = emitter;
    this.labels = labels;
  }

  void emitFloatMul() {
    emitter.emitLabel("F_FMUL", "Q16.16 float multiplication");
    emitter.emitInstruction("PUSH", List.of("R5"), null);
    emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
    emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + LoweringSupport.formatImmediate(8) + ")"), "a");
    emitter.emitInstruction("LOAD", List.of("R1", "(R5+" + LoweringSupport.formatImmediate(12) + ")"), "b");
    emitter.emitInstruction("MOVE", List.of("0", "R6"), "sign");
    emitter.emitInstruction("MOVE", List.of("0", "R3"), "zero");
    String aPos = labels.next("L_FMUL_A_POS");
    String bPos = labels.next("L_FMUL_B_POS");
    emitter.emitInstruction("CMP", List.of("R0", "0"), null);
    emitter.emitInstruction("JP_SGE", List.of(aPos), null);
    emitter.emitInstruction("SUB", List.of("R3", "R0", "R0"), null);
    emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
    emitter.emitLabel(aPos, null);
    emitter.emitInstruction("CMP", List.of("R1", "0"), null);
    emitter.emitInstruction("JP_SGE", List.of(bPos), null);
    emitter.emitInstruction("SUB", List.of("R3", "R1", "R1"), null);
    emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
    emitter.emitLabel(bPos, null);
    emitter.emitInstruction("MOVE", List.of("0", "R2"), "prod low");
    emitter.emitInstruction("MOVE", List.of("0", "R3"), "prod high");
    emitter.emitInstruction("MOVE", List.of("0", "R4"), "multiplicand high");

    String loop = labels.next("L_FMUL_LOOP");
    String done = labels.next("L_FMUL_DONE");
    String skipAdd = labels.next("L_FMUL_SKIP");
    emitter.emitLabel(loop, null);
    emitter.emitInstruction("CMP", List.of("R1", "0"), null);
    emitter.emitInstruction("JP_EQ", List.of(done), null);
    emitter.emitInstruction("SHR", List.of("R1", "1", "R1"), null);
    emitter.emitInstruction("JP_NC", List.of(skipAdd), null);
    emitter.emitInstruction("ADD", List.of("R2", "R0", "R2"), null);
    emitter.emitInstruction("ADC", List.of("R3", "R4", "R3"), null);
    emitter.emitLabel(skipAdd, null);
    emitter.emitInstruction("SHL", List.of("R0", "1", "R0"), null);
    emitter.emitInstruction("ADC", List.of("R4", "R4", "R4"), null);
    emitter.emitInstruction("JP", List.of(loop), null);
    emitter.emitLabel(done, null);
    emitter.emitInstruction("SHR", List.of("R2", "10", "R2"), "lo >> 16");
    emitter.emitInstruction("SHL", List.of("R3", "10", "R3"), "hi << 16");
    emitter.emitInstruction("OR", List.of("R3", "R2", "R2"), "combine");
    String signDone = labels.next("L_FMUL_SIGN_DONE");
    emitter.emitInstruction("CMP", List.of("R6", "0"), null);
    emitter.emitInstruction("JP_EQ", List.of(signDone), null);
    emitter.emitInstruction("MOVE", List.of("0", "R1"), null);
    emitter.emitInstruction("SUB", List.of("R1", "R2", "R2"), null);
    emitter.emitLabel(signDone, null);
    emitter.emitInstruction("MOVE", List.of("R2", "R6"), null);
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);
  }

  void emitFloatDiv() {
    emitter.emitLabel("F_FDIV", "Q16.16 float division");
    emitter.emitInstruction("PUSH", List.of("R5"), null);
    emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
    emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + LoweringSupport.formatImmediate(8) + ")"), "a");
    emitter.emitInstruction("LOAD", List.of("R1", "(R5+" + LoweringSupport.formatImmediate(12) + ")"), "b");
    emitter.emitInstruction("MOVE", List.of("0", "R6"), "sign");
    emitter.emitInstruction("MOVE", List.of("0", "R4"), "zero");
    String divByZero = labels.next("L_FDIV_ZERO");
    emitter.emitInstruction("CMP", List.of("R1", "0"), null);
    emitter.emitInstruction("JP_EQ", List.of(divByZero), null);

    String aPos = labels.next("L_FDIV_A_POS");
    String bPos = labels.next("L_FDIV_B_POS");
    emitter.emitInstruction("CMP", List.of("R0", "0"), null);
    emitter.emitInstruction("JP_SGE", List.of(aPos), null);
    emitter.emitInstruction("SUB", List.of("R4", "R0", "R0"), null);
    emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
    emitter.emitLabel(aPos, null);
    emitter.emitInstruction("CMP", List.of("R1", "0"), null);
    emitter.emitInstruction("JP_SGE", List.of(bPos), null);
    emitter.emitInstruction("SUB", List.of("R4", "R1", "R1"), null);
    emitter.emitInstruction("XOR", List.of("R6", "1", "R6"), null);
    emitter.emitLabel(bPos, null);

    emitter.emitInstruction("PUSH", List.of("R6"), "Save sign");
    emitter.emitInstruction("PUSH", List.of("R0"), "Save a");
    emitter.emitInstruction("PUSH", List.of("R1"), "Save b");

    emitter.emitInstruction("PUSH", List.of("R1"), "Arg right");
    emitter.emitInstruction("PUSH", List.of("R0"), "Arg left");
    emitter.emitInstruction("CALL", List.of("F_DIV"), null);
    emitter.emitInstruction("ADD", List.of("R7", "8", "R7"), "Clean args");
    emitter.emitInstruction("MOVE", List.of("R6", "R3"), "Integer part");

    emitter.emitInstruction("POP", List.of("R1"), "Restore b");
    emitter.emitInstruction("POP", List.of("R0"), "Restore a");
    emitter.emitInstruction("PUSH", List.of("R1"), "Save b");

    emitter.emitInstruction("PUSH", List.of("R1"), "Arg right");
    emitter.emitInstruction("PUSH", List.of("R0"), "Arg left");
    emitter.emitInstruction("CALL", List.of("F_MOD"), null);
    emitter.emitInstruction("ADD", List.of("R7", "8", "R7"), "Clean args");
    emitter.emitInstruction("POP", List.of("R4"), "Restore b");
    emitter.emitInstruction("MOVE", List.of("R6", "R2"), "Remainder");

    emitter.emitInstruction("POP", List.of("R6"), "Restore sign");

    emitter.emitInstruction("MOVE", List.of("0", "R1"), "Fraction");
    emitter.emitInstruction("MOVE", List.of("10", "R0"), "Loop count (16)");
    String fracLoop = labels.next("L_FDIV_FRAC_LOOP");
    String fracDone = labels.next("L_FDIV_FRAC_DONE");
    String fracSkip = labels.next("L_FDIV_FRAC_SKIP");
    emitter.emitLabel(fracLoop, null);
    emitter.emitInstruction("CMP", List.of("R0", "0"), null);
    emitter.emitInstruction("JP_EQ", List.of(fracDone), null);
    emitter.emitInstruction("SHL", List.of("R2", "1", "R2"), null);
    emitter.emitInstruction("SHL", List.of("R1", "1", "R1"), null);
    emitter.emitInstruction("CMP", List.of("R2", "R4"), null);
    emitter.emitInstruction("JP_SLT", List.of(fracSkip), null);
    emitter.emitInstruction("SUB", List.of("R2", "R4", "R2"), null);
    emitter.emitInstruction("OR", List.of("R1", "1", "R1"), null);
    emitter.emitLabel(fracSkip, null);
    emitter.emitInstruction("SUB", List.of("R0", "1", "R0"), null);
    emitter.emitInstruction("JP", List.of(fracLoop), null);
    emitter.emitLabel(fracDone, null);

    emitter.emitInstruction("SHL", List.of("R3", "10", "R3"), "int << 16");
    emitter.emitInstruction("OR", List.of("R3", "R1", "R3"), "combine");
    String signDone = labels.next("L_FDIV_SIGN_DONE");
    emitter.emitInstruction("CMP", List.of("R6", "0"), null);
    emitter.emitInstruction("JP_EQ", List.of(signDone), null);
    emitter.emitInstruction("MOVE", List.of("0", "R0"), null);
    emitter.emitInstruction("SUB", List.of("R0", "R3", "R3"), null);
    emitter.emitLabel(signDone, null);
    emitter.emitInstruction("MOVE", List.of("R3", "R6"), null);
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);

    emitter.emitLabel(divByZero, null);
    emitter.emitInstruction("MOVE", List.of("0", "R6"), null);
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);
  }

  void emitFloatToInt() {
    emitter.emitLabel("F_F2I", "Q16.16 to int32");
    emitter.emitInstruction("PUSH", List.of("R5"), null);
    emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
    emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + LoweringSupport.formatImmediate(8) + ")"), null);
    emitter.emitInstruction("SHR", List.of("R0", "10", "R0"), null);
    emitter.emitInstruction("MOVE", List.of("R0", "R6"), null);
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);
  }

  void emitIntToFloat() {
    emitter.emitLabel("F_I2F", "int32 to Q16.16");
    emitter.emitInstruction("PUSH", List.of("R5"), null);
    emitter.emitInstruction("MOVE", List.of("R7", "R5"), null);
    emitter.emitInstruction("LOAD", List.of("R0", "(R5+" + LoweringSupport.formatImmediate(8) + ")"), null);
    emitter.emitInstruction("SHL", List.of("R0", "10", "R0"), null);
    emitter.emitInstruction("MOVE", List.of("R0", "R6"), null);
    emitter.emitInstruction("POP", List.of("R5"), null);
    emitter.emitInstruction("RET", List.of(), null);
  }
}
