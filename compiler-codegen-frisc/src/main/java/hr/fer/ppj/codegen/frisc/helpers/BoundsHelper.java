package hr.fer.ppj.codegen.frisc.helpers;

import hr.fer.ppj.codegen.frisc.emitter.FriscEmitter;
import hr.fer.ppj.codegen.frisc.lowering.LoweringSupport;
import java.util.List;

/**
 * Emits array bounds error helper.
 */
final class BoundsHelper {
  private final FriscEmitter emitter;

  BoundsHelper(FriscEmitter emitter) {
    this.emitter = emitter;
  }

  void emitBoundsError() {
    emitter.emitLabel("L_BOUNDS_ERROR", "array bounds error");
    emitter.emitInstruction("MOVE", List.of(LoweringSupport.formatImmediate(-6), "R6"), "Error code");
    emitter.emitInstruction("HALT", List.of(), "Abort");
  }
}
