package hr.fer.ppj.codegen.frisc;

import hr.fer.ppj.codegen.frisc.emitter.FriscEmitter;
import hr.fer.ppj.codegen.frisc.helpers.HelperLibrary;

/**
 * Emits helper routines (mul/div/mod and conversions) when required.
 */
final class HelperEmitter {

  private final HelperLibrary helperLibrary;

  HelperEmitter(HelperLibrary helperLibrary) {
    this.helperLibrary = helperLibrary;
  }

  void emit(FriscEmitter emitter) {
    helperLibrary.emit(emitter);
  }
}
