package hr.fer.ppj.codegen.frisc.helpers;

import hr.fer.ppj.codegen.frisc.emitter.FriscEmitter;

/**
 * Emits helper routines used by FRISC codegen when required.
 */
public final class HelperLibrary {

  /**
   * Emits all helper routines required by the current program.
   */
  public void emit(FriscEmitter emitter) {
    HelperLabeler labels = new HelperLabeler();
    FloatHelpers floatHelpers = new FloatHelpers(emitter, labels);
    IntMathHelpers intMathHelpers = new IntMathHelpers(emitter, labels);
    BoundsHelper boundsHelper = new BoundsHelper(emitter);

    if (emitter.needsFmul()) {
      floatHelpers.emitFloatMul();
    }
    if (emitter.needsFdiv()) {
      floatHelpers.emitFloatDiv();
    }
    if (emitter.needsF2i()) {
      floatHelpers.emitFloatToInt();
    }
    if (emitter.needsI2f()) {
      floatHelpers.emitIntToFloat();
    }
    if (emitter.needsMul()) {
      intMathHelpers.emitMul();
    }
    if (emitter.needsDiv()) {
      intMathHelpers.emitDiv();
    }
    if (emitter.needsMod()) {
      intMathHelpers.emitMod();
    }
    if (emitter.needsBoundsCheck()) {
      boundsHelper.emitBoundsError();
    }
  }
}
