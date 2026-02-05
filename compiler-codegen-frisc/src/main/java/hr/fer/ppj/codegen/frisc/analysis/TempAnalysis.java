package hr.fer.ppj.codegen.frisc.analysis;

import hr.fer.ppj.ir.types.IrType;
import java.util.Map;

/**
 * Result of temporary usage analysis for a single function.
 *
 * <p>This data is computed once per function and then used to size the temp
 * stack area and argument scratch space.
 */
public final class TempAnalysis {

  private final Map<Integer, IrType> tempTypes;
  private final int maxTempIndex;
  private final int maxCallArgs;

  public TempAnalysis(Map<Integer, IrType> tempTypes, int maxTempIndex, int maxCallArgs) {
    this.tempTypes = tempTypes;
    this.maxTempIndex = maxTempIndex;
    this.maxCallArgs = maxCallArgs;
  }

  public Map<Integer, IrType> tempTypes() {
    return tempTypes;
  }

  public int maxTempIndex() {
    return maxTempIndex;
  }

  public int maxCallArgs() {
    return maxCallArgs;
  }
}
