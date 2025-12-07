package hr.fer.ppj.codegen;

import hr.fer.ppj.codegen.emitter.FriscEmitter;
import hr.fer.ppj.codegen.frisc.FloatHelperGenerator;
import hr.fer.ppj.codegen.frisc.HelperFunctionGenerator;
import hr.fer.ppj.codegen.func.FunctionCodeGenerator;
import hr.fer.ppj.codegen.util.LabelGenerator;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;

/**
 * Main entry point for FRISC assembly code generation.
 * 
 * <p>This class orchestrates the entire code generation process, taking the semantic analysis
 * results (symbol table and annotated parse tree) and producing FRISC assembly code in the
 * file {@code a.frisc}. It implements a <b>syntax-directed translation</b> approach, where
 * the structure of the generated code mirrors the structure of the source parse tree.
 * 
 * <p><b>Code Generation Pipeline Overview:</b>
 * 
 * <p>The code generation process follows these phases in order:
 * 
 * <ol>
 *   <li><b>Initialization Phase:</b>
 *       <ul>
 *         <li>Create shared infrastructure ({@link FriscEmitter}, {@link LabelGenerator})</li>
 *         <li>Initialize code generation context ({@link CodeGenContext})</li>
 *         <li>Generate program entry point (stack initialization, main call, halt)</li>
 *       </ul>
 *   </li>
 *   
 *   <li><b>Translation Unit Processing Phase:</b>
 *       <ul>
 *         <li>Traverse the parse tree to identify all functions and global declarations</li>
 *         <li>Generate code for each function definition ({@link FunctionCodeGenerator})</li>
 *         <li>Track which helper functions are needed (F_MUL, F_DIV, float helpers)</li>
 *       </ul>
 *   </li>
 *   
 *   <li><b>Helper Function Generation Phase:</b>
 *       <ul>
 *         <li>Generate float helper functions first ({@link FloatHelperGenerator})</li>
 *         <li>Float helpers may internally require integer helpers (F_MUL, F_DIV)</li>
 *         <li>Generate integer helper functions if needed ({@link HelperFunctionGenerator})</li>
 *         <li><b>Critical ordering:</b> Float helpers must be generated before integer helpers
 *             because float multiplication/division internally call integer helpers, and the
 *             emitter flags are set during float helper generation.</li>
 *       </ul>
 *   </li>
 *   
 *   <li><b>Global Variable Generation Phase:</b>
 *       <ul>
 *         <li>Generate global variable declarations ({@link GlobalVariableGenerator})</li>
 *         <li>Extract and emit initializer values for initialized globals</li>
 *         <li>Allocate uninitialized arrays with appropriate size directives</li>
 *       </ul>
 *   </li>
 *   
 *   <li><b>Code Emission Phase:</b>
 *       <ul>
 *         <li>Write all buffered assembly code to the output file</li>
 *         <li>Format instructions, labels, and comments consistently</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><b>Grammar Rule:</b> This class processes the root {@code <prijevodna_jedinica>}
 * (translation unit) nonterminal, which contains all external declarations (function
 * definitions and global variable declarations).
 * 
 * <p><b>FRISC Program Structure:</b>
 * 
 * <p>The generated FRISC program follows this standard structure:
 * <pre>
 * ; ============================================
 * ; Program entry point
 * ; ============================================
 * MOVE 40000, R7          ; Initialize stack pointer (SP) to high memory
 * CALL F_MAIN             ; Call main function
 * HALT                    ; End program, R6 holds return value
 * 
 * ; ============================================
 * ; Helper functions (generated only if needed)
 * ; ============================================
 * F_MUL                   ; 32-bit integer multiplication helper
 * F_DIV                   ; 32-bit integer division helper
 * F_FADD                  ; Q16.16 float addition
 * F_FSUB                  ; Q16.16 float subtraction
 * F_FMUL                  ; Q16.16 float multiplication
 * F_FDIV                  ; Q16.16 float division
 * F_FCMP                  ; Q16.16 float comparison
 * F_I2F                   ; Integer to float conversion
 * F_F2I                   ; Float to integer conversion
 * 
 * ; ============================================
 * ; Function definitions
 * ; ============================================
 * F_MAIN                  ; Main function
 *     ; Function prologue (save R5, set new R5, allocate locals)
 *     ; Function body (statements, expressions)
 *     ; Function epilogue (restore R5, return)
 * F_FUNCTION1             ; Other user-defined functions...
 * 
 * ; ============================================
 * ; Global variables (data section)
 * ; ============================================
 * G_VAR1 DW %D 42         ; Initialized global variable
 * G_ARRAY `DS %D 20       ; Uninitialized array (20 bytes = 5 ints)
 * </pre>
 * 
 * <p><b>FRISC Calling Convention:</b>
 * 
 * <p>This code generator implements the standard FRISC calling convention:
 * <ul>
 *   <li><b>R7 (Stack Pointer):</b> Initialized to 40000, grows downward (decreasing addresses)</li>
 *   <li><b>R5 (Frame Pointer):</b> Points to the saved old R5 in the current stack frame</li>
 *   <li><b>R6 (Return Value):</b> Used to return function results</li>
 *   <li><b>R0-R4:</b> General-purpose registers (caller-saved, may be clobbered)</li>
 *   <li><b>Parameter Passing:</b> Arguments pushed on stack right-to-left (C convention)</li>
 *   <li><b>Stack Frame Layout:</b>
 *       <ul>
 *         <li>Parameters: R5+8, R5+12, R5+16, ... (positive offsets)</li>
 *         <li>Return address: R5+4 (saved by CALL instruction)</li>
 *         <li>Old R5: R5+0 (saved by callee prologue)</li>
 *         <li>Local variables: R5-4, R5-8, R5-12, ... (negative offsets)</li>
 *       </ul>
 *   </li>
 *   <li><b>Caller Responsibilities:</b> Push arguments, call function, clean up arguments</li>
 *   <li><b>Callee Responsibilities:</b> Save R5, allocate locals, restore R5, return</li>
 * </ul>
 * 
 * <p><b>Algorithm: Syntax-Directed Translation</b>
 * 
 * <p>This code generator uses a <b>syntax-directed translation</b> approach, which is a
 * form of tree traversal where code generation actions are associated with grammar rules.
 * The algorithm works as follows:
 * 
 * <ol>
 *   <li><b>Tree Traversal:</b> Recursively traverse the parse tree in depth-first order</li>
 *   <li><b>Rule Matching:</b> For each nonterminal node, identify the grammar rule that produced it</li>
 *   <li><b>Code Generation:</b> Emit FRISC instructions according to the semantic action
 *       associated with that grammar rule</li>
 *   <li><b>Context Propagation:</b> Pass code generation context (symbol table, activation record,
 *       labels) down the tree</li>
 *   <li><b>Result Assembly:</b> Combine code fragments from child nodes into complete instructions</li>
 * </ol>
 * 
 * <p><b>Key Design Decisions:</b>
 * 
 * <ul>
 *   <li><b>Deferred Helper Generation:</b> Helper functions are generated <i>after</i> processing
 *       the translation unit because we need to know which operations are used before generating
 *       helpers. This avoids generating unused helper code.</li>
 *   <li><b>Float Helpers Before Integer Helpers:</b> Float helpers may mark integer
 *       helpers as needed during their generation, so float helpers must be generated
 *       first to properly mark integer helpers as needed.</li>
 *   <li><b>Global Variables Last:</b> Global variables are generated at the end because they
 *       form the data section, which is conventionally placed after code sections.</li>
 *   <li><b>Immutable Context:</b> The {@link CodeGenContext} is immutable, allowing safe
 *       recursive traversal without side effects on parent contexts.</li>
 * </ul>
 * 
 * <p><b>Complexity:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the number of nodes in the parse tree</li>
 *   <li><b>Space Complexity:</b> O(n) for the parse tree + O(m) for generated code buffer,
 *       where m is the number of generated instructions</li>
 * </ul>
 * 
 * <p>The code generator implements the PPJ-C to FRISC mapping according to the specification
 * in ppj-labos-upute chapter 5, ensuring compatibility with the FRISC simulator.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class CodeGenerator {
    
    /**
     * Standard output filename for generated FRISC assembly code.
     */
    public static final String OUTPUT_FILENAME = "a.frisc";
    
    /**
     * Generates FRISC assembly code from semantic analysis results.
     * 
     * <p>This method performs the complete code generation process:
     * <ol>
     *   <li>Initializes the FRISC emitter and label generator</li>
     *   <li>Generates program initialization code</li>
     *   <li>Processes all function definitions</li>
     *   <li>Declares global variables and constants</li>
     *   <li>Writes the final assembly to {@code a.frisc}</li>
     * </ol>
     * 
     * @param globalScope the global symbol table from semantic analysis
     * @param parseTree the annotated parse tree from semantic analysis
     * @throws CodeGenerationException if code generation fails
     */
    public void generate(SymbolTable globalScope, NonTerminalNode parseTree) {
        generate(globalScope, parseTree, Paths.get(OUTPUT_FILENAME));
    }
    
    /**
     * Generates FRISC assembly code to a specific output file.
     * 
     * <p>This is the main code generation method that orchestrates the entire pipeline.
     * It performs the following steps in order:
     * 
     * <ol>
     *   <li><b>Initialization:</b> Creates the emitter, label generator, and initial context</li>
     *   <li><b>Program Entry:</b> Generates the program initialization sequence</li>
     *   <li><b>Translation Unit Processing:</b> Traverses the parse tree to generate function
     *       code and identify needed helper functions</li>
     *   <li><b>Helper Function Generation:</b> Generates helper functions in the correct order
     *       (float helpers first, then integer helpers)</li>
     *   <li><b>Code Emission:</b> Writes all generated code to the output file</li>
     * </ol>
     * 
     * <p><b>Preconditions:</b>
     * <ul>
     *   <li>{@code globalScope} must be a valid symbol table from semantic analysis</li>
     *   <li>{@code parseTree} must be a valid parse tree with semantic annotations</li>
     *   <li>{@code outputPath} must be a valid file path (parent directory must exist)</li>
     * </ul>
     * 
     * <p><b>Postconditions:</b>
     * <ul>
     *   <li>A complete FRISC assembly file is written to {@code outputPath}</li>
     *   <li>The file contains valid FRISC instructions that can be executed by the simulator</li>
     *   <li>All helper functions needed by the program are included</li>
     * </ul>
     * 
     * <p><b>Side Effects:</b>
     * <ul>
     *   <li>Creates or overwrites the file at {@code outputPath}</li>
     *   <li>Modifies the emitter's internal buffer with generated instructions</li>
     *   <li>Updates label generator counters for unique label generation</li>
     * </ul>
     * 
     * @param globalScope the global symbol table from semantic analysis (must not be null)
     * @param parseTree the annotated parse tree from semantic analysis (must not be null)
     * @param outputPath the path where to write the generated FRISC code (must not be null)
     * @throws CodeGenerationException if code generation fails (I/O errors, unsupported constructs, etc.)
     */
    public void generate(SymbolTable globalScope, NonTerminalNode parseTree, Path outputPath) {
        Objects.requireNonNull(globalScope, "globalScope must not be null");
        Objects.requireNonNull(parseTree, "parseTree must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        
        try {
            // Phase 1: Initialize code generation infrastructure
            // The emitter buffers all generated code in memory until writeToFile() is called.
            // This allows us to generate code in any order and write it atomically.
            FriscEmitter emitter = new FriscEmitter();
            LabelGenerator labelGen = new LabelGenerator();
            
            // Create the main code generation context (no activation record for global scope)
            // The context is immutable, so we create new instances when entering functions.
            CodeGenContext context = new CodeGenContext(globalScope, emitter, labelGen, null, null, null, null);
            
            // Phase 2: Generate program initialization sequence
            // This must come first because it's the entry point of the program.
            generateProgramInit(context);
            
            // Phase 3: Process the translation unit (functions and global declarations)
            // This phase:
            // - Traverses the parse tree to find all function definitions
            // - Generates code for each function (prologue, body, epilogue)
            // - Tracks which helper functions are needed (via emitter flags)
            // - Prepares global variable information (but doesn't emit them yet)
            // 
            // IMPORTANT: We process functions BEFORE globals because functions may reference
            // global variables, but the linker/simulator can resolve forward references.
            processTranslationUnit(context, parseTree);
            
            // Phase 4: Generate helper functions
            // 
            // CRITICAL ORDERING CONSTRAINT: Float helpers must be generated BEFORE integer helpers
            // because:
            // 1. Float helpers may mark integer helpers as needed DURING their generation
            // 2. Therefore, we must generate float helpers first, then check if integer helpers
            //    are needed, then generate integer helpers.
            //
            // The emitter flags are set during float helper generation, so we check them AFTER.
            if (emitter.needsAnyFloatHelper()) {
                FloatHelperGenerator floatHelperGen = new FloatHelperGenerator();
                boolean[] flags = emitter.getFloatHelperFlags();
                // Generate only the float helpers that are actually needed
                floatHelperGen.generateFloatHelpers(context, 
                    flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], flags[6]);
                // After generating float helpers, check if they marked integer helpers as needed.
                // This must be done AFTER generateFloatHelpers() because markMulNeeded()/markDivNeeded()
                // are called DURING float helper generation (inside generateFloatMul/generateFloatDiv).
            }
            
            // Generate integer helper functions (F_MUL, F_DIV) only if needed.
            // These are used for integer multiplication/division operations in user code.
            // IMPORTANT: This check must happen AFTER generateFloatHelpers() completes,
            // because markMulNeeded()/markDivNeeded() are called DURING float helper generation.
            boolean needsMul = emitter.needsMulHelper();
            boolean needsDiv = emitter.needsDivHelper();
            
            // Generate 32-bit integer helpers (F_MUL, F_DIV) if needed
            // These are used for integer multiplication/division operations in user code.
            if (needsMul || needsDiv) {
                HelperFunctionGenerator helperGen = new HelperFunctionGenerator();
                helperGen.generateHelperFunctions(context, needsMul, needsDiv);
            }
            
            // Phase 5: Write the generated code to file
            // All code has been buffered in the emitter, so we write it atomically.
            // The emitter handles formatting, so the output is properly formatted FRISC assembly.
            emitter.writeToFile(outputPath);
            
        } catch (IOException e) {
            throw new CodeGenerationException("Failed to write output file: " + outputPath, e);
        } catch (Exception e) {
            throw new CodeGenerationException("Code generation failed", e);
        }
    }
    
    /**
     * Generates the standard program initialization sequence.
     * 
     * <p>This method generates the FRISC program entry point, which:
     * <ol>
     *   <li>Initializes the stack pointer (R7) to 40000</li>
     *   <li>Calls the main function (F_MAIN)</li>
     *   <li>Halts execution (R6 contains the return value)</li>
     * </ol>
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>Stack pointer (R7) initialized to 40000 (high memory address)</li>
     *   <li>Stack grows downward (decreasing addresses)</li>
     *   <li>CALL instruction saves return address on stack</li>
     *   <li>HALT terminates execution; R6 value is the program result</li>
     * </ul>
     * 
     * <p>Generated code:
     * <pre>
     * ; Program entry point
     * MOVE 40000, R7      ; init stack pointer (SP)
     * CALL F_MAIN         ; call main
     * HALT                ; end of program, R6 holds return value
     * </pre>
     */
    private void generateProgramInit(CodeGenContext context) {
        FriscEmitter emitter = context.emitter();
        
        emitter.emitComment("Program entry point");
        emitter.emitInstruction("MOVE", "40000", "R7", "init stack pointer");
        emitter.emitInstruction("CALL", "F_MAIN", null, "call main");
        emitter.emitInstruction("HALT", null, null, "end of program, R6 holds return value");
        emitter.emitNewline();
    }
    
    /**
     * Processes the translation unit, generating code for all functions and global declarations.
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <prijevodna_jedinica>} (translation unit):
     * <pre>
     * &lt;prijevodna_jedinica&gt; ::= &lt;vanjska_deklaracija&gt;
     *                            | &lt;prijevodna_jedinica&gt; &lt;vanjska_deklaracija&gt;
     * 
     * &lt;vanjska_deklaracija&gt; ::= &lt;definicija_funkcije&gt;
     *                            | &lt;deklaracija&gt;
     * </pre>
     * 
     * <p>This method implements a <b>two-pass approach</b>:
     * <ol>
     *   <li><b>First Pass - Function Generation:</b>
     *       <ul>
     *         <li>Traverses the parse tree to find all function definitions</li>
     *         <li>For each function, generates prologue, body, and epilogue</li>
     *         <li>During function code generation, tracks which helper functions are needed
     *             (via emitter flags set by expression generators)</li>
     *       </ul>
     *   </li>
     *   <li><b>Second Pass - Global Variable Generation:</b>
     *       <ul>
     *         <li>Extracts all global variable declarations from the parse tree</li>
     *         <li>Generates data declarations (DW for initialized, `DS for uninitialized)</li>
     *         <li>Extracts and emits initializer values for initialized globals</li>
     *       </ul>
     *   </li>
     * </ol>
     * 
     * <p><b>Processing Order Rationale:</b>
     * <ul>
     *   <li><b>Functions First:</b> Functions are generated before globals because:
     *       <ul>
     *         <li>Functions may reference global variables, but forward references are allowed
     *             in FRISC assembly (labels can be used before definition)</li>
     *         <li>This ordering matches conventional assembly layout (code section before data section)</li>
     *       </ul>
     *   </li>
     *   <li><b>Globals Last:</b> Global variables form the data section, which is conventionally
     *       placed at the end of the assembly file</li>
     * </ul>
     * 
     * <p><b>Side Effects:</b>
     * <ul>
     *   <li>Emits function code to the emitter buffer</li>
     *   <li>Sets emitter flags indicating which helper functions are needed</li>
     *   <li>Emits global variable declarations to the emitter buffer</li>
     * </ul>
     * 
     * @param context the code generation context (must not be null)
     * @param translationUnit the root parse tree node representing the translation unit
     */
    private void processTranslationUnit(CodeGenContext context, NonTerminalNode translationUnit) {
        // Create specialized generators for functions and global variables.
        // These generators encapsulate the logic for traversing the parse tree and
        // generating appropriate FRISC code for each construct.
        FunctionCodeGenerator funcGen = new FunctionCodeGenerator(context);
        GlobalVariableGenerator globalGen = new GlobalVariableGenerator(context);
        
        // Set the parse tree for global variable initializers.
        // The global generator needs the full parse tree to extract initializer values
        // from the AST, as initializers can be complex expressions.
        globalGen.setParseTree(translationUnit);
        
        // First pass: Process all external declarations in the translation unit.
        // This will:
        // - Find all function definitions (<definicija_funkcije>)
        // - Generate code for each function (prologue, body, epilogue)
        // - Track which helper functions are needed (via emitter flags)
        // - Skip global variable declarations (handled in second pass)
        funcGen.processTranslationUnit(translationUnit);
        
        // Second pass: Generate global variable declarations at the end.
        // This ensures the data section comes after the code section, which is
        // the conventional assembly layout. Global variables are generated here
        // because they don't depend on function code generation order.
        globalGen.generateGlobalVariables();
    }
    
}
