package hr.fer.ppj.semantics.analysis;

import hr.fer.ppj.semantics.errors.SemanticErrorReporter;
import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.Map;

/**
 * Verifies global semantic constraints that must hold for a valid PPJ-C program.
 * 
 * <p>This class implements program-level semantic requirements:
 * <ul>
 *   <li>Main function requirement: Every program must contain exactly one
 *       function named "main" with signature {@code int main(void)} and a definition</li>
 *   <li>Function definition requirement: Every declared function must
 *       have exactly one definition in the program</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class GlobalConstraintVerifier {
  
  private final SemanticErrorReporter errorReporter;
  
  GlobalConstraintVerifier(SemanticErrorReporter errorReporter) {
    this.errorReporter = errorReporter;
  }
  
  /**
   * Verifies all global semantic constraints.
   * 
   * @param functions all functions declared or defined in the program
   * @throws hr.fer.ppj.semantics.errors.SemanticException if any constraint is violated
   */
  void verify(Map<String, FunctionSymbol> functions) {
    verifyMainFunction(functions);
    verifyAllFunctionsDefined(functions);
  }
  
  private void verifyMainFunction(Map<String, FunctionSymbol> functions) {
    FunctionSymbol main = functions.get(SemanticConstants.MAIN_FUNCTION_NAME);
    if (main == null
        || !main.defined()
        || TypeSystem.stripConst(main.type().returnType()) != PrimitiveType.INT
        || !main.type().parameterTypes().isEmpty()) {
      errorReporter.reportGlobalError(SemanticConstants.ERROR_MISSING_MAIN);
    }
  }
  
  private void verifyAllFunctionsDefined(Map<String, FunctionSymbol> functions) {
    for (FunctionSymbol function : functions.values()) {
      if (!function.defined()) {
        errorReporter.reportGlobalError(SemanticConstants.ERROR_UNDEFINED_FUNCTION);
      }
    }
  }
}

