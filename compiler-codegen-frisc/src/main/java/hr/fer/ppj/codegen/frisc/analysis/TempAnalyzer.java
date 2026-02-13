package hr.fer.ppj.codegen.frisc.analysis;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.ir.types.IrType;
import java.util.Map;
import java.util.Set;

/**
 * Performs a lightweight scan of a function body to determine temp usage and bounds-check needs.
 */
public final class TempAnalyzer {
  private final TempUsageAnalyzer tempUsageAnalyzer = new TempUsageAnalyzer();
  private final AddrIndexAnalyzer addrIndexAnalyzer = new AddrIndexAnalyzer();

  /**
   * Bundles temp analysis results for a single function.
   */
  public record TempAnalysisResult(TempAnalysis tempAnalysis, Set<Integer> addrIndexNeedsCheck) {
    public TempAnalysisResult {
      if (tempAnalysis == null) {
        throw new IllegalArgumentException("tempAnalysis must not be null");
      }
      if (addrIndexNeedsCheck == null) {
        throw new IllegalArgumentException("addrIndexNeedsCheck must not be null");
      }
    }
  }

  /**
   * Scans a function to determine temp usage and address-index checks.
   */
  public TempAnalysisResult analyze(
      IrProgramModel.Function function,
      Map<String, IrProgramModel.Slot> localSlots,
      Map<String, IrProgramModel.Slot> paramSlots,
      Map<String, IrType> globalTypes) {
    TempAnalysis tempAnalysis = tempUsageAnalyzer.analyze(function, localSlots, paramSlots, globalTypes);
    Set<Integer> addrIndexNeedsCheck =
        addrIndexAnalyzer.analyze(function, tempAnalysis.tempTypes());
    return new TempAnalysisResult(tempAnalysis, addrIndexNeedsCheck);
  }
}
