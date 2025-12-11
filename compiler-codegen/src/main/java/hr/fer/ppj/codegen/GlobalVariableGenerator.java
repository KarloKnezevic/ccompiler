package hr.fer.ppj.codegen;

import hr.fer.ppj.codegen.global.ArraySizeExtractor;
import hr.fer.ppj.codegen.global.InitializerExtractor;
import hr.fer.ppj.codegen.structs.StructSizeCalculator;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly data declarations for global variables.
 *
 * <p>This class processes the global symbol table and generates appropriate FRISC data declarations
 * (DW, `DS) for global variables and constants. It implements the <b>global variable code
 * generation algorithm</b> that translates C global variable declarations into FRISC data section
 * declarations.
 *
 * <p><b>Algorithm: Global Variable Code Generation</b>
 *
 * <p>The algorithm works as follows:
 *
 * <ol>
 *   <li><b>Symbol Table Traversal:</b> Iterate through all symbols in the global scope
 *   <li><b>Variable Identification:</b> Filter for VariableSymbol entries (skip functions, types,
 *       etc.)
 *   <li><b>Type Analysis:</b> Determine if variable is simple (int, char, float) or array
 *   <li><b>Initializer Extraction:</b> Extract initializer values from the parse tree (if present)
 *   <li><b>Data Declaration Generation:</b> Generate appropriate FRISC data directive:
 *       <ul>
 *         <li>Initialized simple variables: {@code DW %D value}
 *         <li>Uninitialized simple variables: {@code DW %D 0}
 *         <li>Initialized arrays: {@code DW value1, value2, ...}
 *         <li>Uninitialized arrays: {@code `DS %D total_bytes}
 *       </ul>
 * </ol>
 *
 * <p><b>FRISC Data Directives:</b>
 *
 * <p>FRISC uses the following data directives:
 *
 * <ul>
 *   <li><b>DW (Define Word):</b> Declares a 32-bit word (4 bytes) with an initial value. Used for
 *       initialized simple variables and arrays.
 *   <li><b>`DS (Define Storage):</b> Reserves space for uninitialized data (backtick prefix). Used
 *       for uninitialized arrays. Format: {@code `DS %D N} where N is the number of bytes.
 * </ul>
 *
 * <p><b>Label Generation:</b>
 *
 * <p>Each global variable gets a unique label following the pattern:
 *
 * <pre>
 * G_<VARIABLE_NAME>
 * </pre>
 *
 * <p>For example:
 *
 * <ul>
 *   <li>Variable {@code x} → label {@code G_X}
 *   <li>Variable {@code counter} → label {@code G_COUNTER}
 * </ul>
 *
 * <p><b>Initializer Extraction Algorithm:</b>
 *
 * <p>Initializers are extracted from the parse tree using {@link InitializerExtractor}:
 *
 * <ol>
 *   <li><b>Parse Tree Search:</b> Search the parse tree for the variable declaration
 *   <li><b>Initializer Node Location:</b> Find the initializer node associated with the variable
 *   <li><b>Value Extraction:</b> Extract constant values from the initializer expression
 *   <li><b>Array Handling:</b> For arrays, extract all initializer values recursively
 * </ol>
 *
 * <p><b>Simple Variable Generation:</b>
 *
 * <p>For simple variables (int, char, float):
 *
 * <ul>
 *   <li><b>With Initializer:</b> {@code G_VAR DW %D 42} (initialized to 42)
 *   <li><b>Without Initializer:</b> {@code G_VAR DW %D 0} (initialized to 0)
 * </ul>
 *
 * <p><b>Array Variable Generation:</b>
 *
 * <p>For array variables:
 *
 * <ul>
 *   <li><b>With Initializer:</b> {@code G_ARRAY DW %D 1, %D 2, %D 3} (initialized array)
 *   <li><b>Without Initializer:</b> {@code G_ARRAY `DS %D 20} (uninitialized array, 20 bytes = 5
 *       ints)
 * </ul>
 *
 * <p><b>Array Size Calculation:</b>
 *
 * <p>For uninitialized arrays, the size is calculated as:
 *
 * <pre>
 * total_bytes = array_size × element_size
 * </pre>
 *
 * <p>For this project, both int and char arrays use element size 4 bytes (32 bits).
 *
 * <p><b>Placement in Generated Code:</b>
 *
 * <p>Global variables are placed at the end of the generated FRISC program, after all function
 * definitions. This follows the conventional assembly layout:
 *
 * <pre>
 * ; Program entry point
 * MOVE 40000, R7
 * CALL F_MAIN
 * HALT
 *
 * ; Helper functions
 * F_MUL ...
 * F_DIV ...
 *
 * ; Function definitions
 * F_MAIN ...
 * F_FOO ...
 *
 * ; Global variables (data section)
 * G_X DW %D 42
 * G_ARRAY `DS %D 20
 * </pre>
 *
 * <p><b>Why This Ordering?</b>
 *
 * <ul>
 *   <li><b>Conventional Layout:</b> Code section before data section is standard
 *   <li><b>Forward References:</b> Functions can reference global variables (labels can be used
 *       before definition in assembly)
 *   <li><b>Linker Compatibility:</b> Matches standard linker expectations
 * </ul>
 *
 * <p><b>Complexity Analysis:</b>
 *
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the number of global variables
 *   <li><b>Space Complexity:</b> O(1) - processes one variable at a time
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class GlobalVariableGenerator {

  private final CodeGenContext context;
  private InitializerExtractor initializerExtractor;
  private ArraySizeExtractor arraySizeExtractor;

  /**
   * Creates a new global variable generator.
   *
   * @param context the code generation context
   */
  public GlobalVariableGenerator(CodeGenContext context) {
    this.context = Objects.requireNonNull(context, "context must not be null");
  }

  /**
   * Sets the parse tree for extracting initializer values and array sizes.
   *
   * @param parseTree the parse tree from semantic analysis
   */
  public void setParseTree(NonTerminalNode parseTree) {
    this.initializerExtractor = new InitializerExtractor(parseTree);
    this.arraySizeExtractor = new ArraySizeExtractor(parseTree);
  }

  /**
   * Generates FRISC data declarations for all global variables.
   *
   * <p>This method examines the global symbol table and generates appropriate data declarations for
   * each global variable found.
   */
  public void generateGlobalVariables() {
    context.emitter().emitComment("Global variables");

    // Process all symbols in the global scope
    for (Symbol symbol : context.globalScope().entries().values()) {
      if (symbol instanceof VariableSymbol varSymbol) {
        generateGlobalVariable(varSymbol);
      }
    }

    context.emitter().emitNewline();
  }

  /**
   * Generates a FRISC data declaration for a single global variable.
   *
   * @param variable the variable symbol to generate code for
   */
  private void generateGlobalVariable(VariableSymbol variable) {
    String label = context.labelGenerator().getGlobalVariableLabel(variable.name());
    Type varType = TypeSystem.stripConst(variable.type());

    // Check if it's an array
    if (varType instanceof ArrayType arrayType) {
      generateArrayVariable(label, variable, arrayType);
      return;
    }

    // Check if it's a struct type
    if (varType instanceof StructType structType) {
      generateStructVariable(label, variable, structType);
      return;
    }

    // Check if it's a char type
    boolean isChar = varType == PrimitiveType.CHAR;

    // Try to find initializer value from parse tree
    String initValue = null;
    if (initializerExtractor != null) {
      initValue = initializerExtractor.findInitializerValue(variable.name(), isChar);
    }
    if (initValue == null) {
      initValue = "0"; // Default initialization
    }

    String comment =
        "global "
            + (variable.isConst() ? "const " : "")
            + variable.type()
            + " "
            + variable.name()
            + (initValue.equals("0") ? "" : " = " + initValue);

    // Always use %D prefix for C integer constants (including char values)
    // According to FRISC rules: C integer constants must use %D prefix
    String dataValue = "%D " + initValue;

    context.emitter().emitData(label, "DW", dataValue, comment);
  }

  /**
   * Generates FRISC data declaration for a struct variable.
   *
   * <p>For struct variables, we allocate space equal to the struct size. Uninitialized structs are
   * zero-initialized (allocated with `DS directive).
   *
   * @param label the global variable label
   * @param variable the variable symbol
   * @param structType the struct type
   */
  private void generateStructVariable(
      String label, VariableSymbol variable, StructType structType) {
    int structSize = StructSizeCalculator.calculateStructSize(structType);
    String comment = "global " + variable.type() + " " + variable.name();

    // For now, treat all struct globals as uninitialized (zero-initialized)
    // Full aggregate initialization would require parsing struct initializers
    context.emitter().emitData(label, "`DS", "%D " + structSize, comment);
  }

  /**
   * Generates FRISC data declaration for an array variable.
   *
   * <p>For this project, both int and char arrays use element size 4 bytes. Uninitialized arrays
   * use the `DS directive (backtick DS) to reserve space. Initialized arrays use DW with
   * comma-separated values.
   */
  private void generateArrayVariable(String label, VariableSymbol variable, ArrayType arrayType) {
    // Try to find array initializer from parse tree
    List<String> initValues = null;
    if (initializerExtractor != null) {
      initValues = initializerExtractor.findArrayInitializer(variable.name(), arrayType);
    }

    // For this project: both int and char arrays use element size 4 bytes
    // We treat chars as 4-byte elements and use LOAD instead of LOADB
    int elementSize = 4;

    if (initValues == null || initValues.isEmpty()) {
      // No initializer - use `DS directive (backtick DS) to allocate space
      // Extract array size from parse tree
      int arraySize = 0;
      if (arraySizeExtractor != null) {
        arraySize = arraySizeExtractor.extractArraySize(variable.name());
      }
      if (arraySize > 0) {
        int totalBytes = arraySize * elementSize;
        String comment = "global " + variable.type() + " " + variable.name();
        // Use backtick DS: `DS %D N
        context.emitter().emitData(label, "`DS", "%D " + totalBytes, comment);
      } else {
        // Fallback: allocate space for at least 1 element
        String comment = "global " + variable.type() + " " + variable.name();
        context.emitter().emitData(label, "`DS", "%D " + elementSize, comment);
      }
      return;
    }

    // Generate array with initializer values using DW
    StringBuilder dataValue = new StringBuilder();
    boolean first = true;
    for (String value : initValues) {
      if (!first) {
        dataValue.append(", ");
      }
      dataValue.append(value);
      first = false;
    }

    String comment = "global " + variable.type() + " " + variable.name();
    context.emitter().emitData(label, "DW", dataValue.toString(), comment);
  }
}
