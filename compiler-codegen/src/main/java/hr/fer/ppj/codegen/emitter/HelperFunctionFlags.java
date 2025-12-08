package hr.fer.ppj.codegen.emitter;

/**
 * Tracks which helper functions are needed by the generated code.
 * 
 * <p>This class encapsulates all the boolean flags that track whether various
 * helper functions (F_MUL, F_DIV, F_FADD, etc.) need to be generated.
 * It eliminates the need for multiple boolean fields in FriscEmitter.
 * 
 * <p><b>Helper Functions Tracked:</b>
 * <ul>
 *   <li>Integer operations: F_MUL, F_DIV, F_MUL64</li>
 *   <li>Float operations: F_FADD, F_FSUB, F_FMUL, F_FDIV, F_FCMP, F_I2F, F_F2I</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class HelperFunctionFlags {
    
    private boolean needsMulHelper = false;
    private boolean needsDivHelper = false;
    private boolean needsFloatAdd = false;
    private boolean needsFloatSub = false;
    private boolean needsFloatMul = false;
    private boolean needsFloatDiv = false;
    private boolean needsFloatCmp = false;
    private boolean needsIntToFloat = false;
    private boolean needsFloatToInt = false;
    
    /**
     * Marks that F_MUL helper function is needed.
     */
    public void markMulNeeded() {
        needsMulHelper = true;
    }
    
    /**
     * Marks that F_DIV helper function is needed.
     */
    public void markDivNeeded() {
        needsDivHelper = true;
    }
    
    /**
     * Marks that F_FADD helper function is needed.
     */
    public void markFloatAddNeeded() {
        needsFloatAdd = true;
    }
    
    /**
     * Marks that F_FSUB helper function is needed.
     */
    public void markFloatSubNeeded() {
        needsFloatSub = true;
    }
    
    /**
     * Marks that F_FMUL helper function is needed.
     */
    public void markFloatMulNeeded() {
        needsFloatMul = true;
    }
    
    /**
     * Marks that F_FDIV helper function is needed.
     */
    public void markFloatDivNeeded() {
        needsFloatDiv = true;
    }
    
    /**
     * Marks that F_FCMP helper function is needed.
     */
    public void markFloatCmpNeeded() {
        needsFloatCmp = true;
    }
    
    /**
     * Marks that F_I2F helper function is needed.
     */
    public void markIntToFloatNeeded() {
        needsIntToFloat = true;
    }
    
    /**
     * Marks that F_F2I helper function is needed.
     */
    public void markFloatToIntNeeded() {
        needsFloatToInt = true;
    }
    
    /**
     * Checks if F_MUL helper function is needed.
     * 
     * @return true if multiplication helper is needed
     */
    public boolean needsMulHelper() {
        return needsMulHelper;
    }
    
    /**
     * Checks if F_DIV helper function is needed.
     * 
     * @return true if division helper is needed
     */
    public boolean needsDivHelper() {
        return needsDivHelper;
    }
    
    /**
     * Checks if any float helper functions are needed.
     * 
     * @return true if any float helper is needed
     */
    public boolean needsAnyFloatHelper() {
        return needsFloatAdd || needsFloatSub || needsFloatMul || needsFloatDiv 
            || needsFloatCmp || needsIntToFloat || needsFloatToInt;
    }
    
    /**
     * Checks if F_FMUL helper function is needed.
     * 
     * @return true if F_FMUL is needed
     */
    public boolean needsFloatMulHelper() {
        return needsFloatMul;
    }
    
    /**
     * Gets flags for float helper generation.
     * 
     * @return array of 7 booleans: [add, sub, mul, div, cmp, i2f, f2i]
     */
    public boolean[] getFloatHelperFlags() {
        return new boolean[] {
            needsFloatAdd, needsFloatSub, needsFloatMul, needsFloatDiv,
            needsFloatCmp, needsIntToFloat, needsFloatToInt
        };
    }
}

