package hr.fer.ppj.codegen;

import hr.fer.ppj.codegen.emitter.FriscEmitter;
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
 * file {@code a.frisc}.
 * 
 * <p><b>Grammar Rule:</b> This class processes the root {@code <prijevodna_jedinica>}
 * (translation unit) nonterminal, which contains all external declarations (function
 * definitions and global variable declarations).
 * 
 * <p>The generated FRISC program follows the standard structure:
 * <ul>
 *   <li>Program initialization (stack setup, main call, halt)</li>
 *   <li>Helper functions (F_MUL, F_DIV) if needed</li>
 *   <li>Function definitions (subroutines)</li>
 *   <li>Global variable declarations</li>
 * </ul>
 * 
 * <p><b>FRISC Program Structure:</b>
 * <pre>
 * ; Program entry point
 * MOVE 40000, R7          ; Initialize stack pointer (SP)
 * CALL F_MAIN             ; Call main function
 * HALT                    ; End program, R6 holds return value
 * 
 * ; Helper functions (if needed)
 * F_MUL                   ; Multiplication helper
 * F_DIV                   ; Division helper
 * 
 * ; Function definitions
 * F_MAIN                  ; Main function
 * F_FUNCTION1             ; Other functions...
 * 
 * ; Global variables
 * G_VAR1 DW %D 42         ; Initialized global
 * G_ARRAY `DS %D 20       ; Uninitialized array
 * </pre>
 * 
 * <p>The code generator implements the PPJ-C to FRISC mapping according to the specification
 * in ppj-labos-upute chapter 5, ensuring compatibility with the FRISC simulator.
 * 
 * <p><b>FRISC Calling Convention:</b>
 * <ul>
 *   <li>R7 - Stack Pointer (SP), initialized to 40000</li>
 *   <li>R5 - Frame Pointer (FP), set in function prologue</li>
 *   <li>R6 - Return Value Register</li>
 *   <li>Arguments passed on stack (right-to-left)</li>
 *   <li>Caller cleans up arguments</li>
 * </ul>
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
     * @param globalScope the global symbol table from semantic analysis
     * @param parseTree the annotated parse tree from semantic analysis
     * @param outputPath the path where to write the generated FRISC code
     * @throws CodeGenerationException if code generation fails
     */
    public void generate(SymbolTable globalScope, NonTerminalNode parseTree, Path outputPath) {
        Objects.requireNonNull(globalScope, "globalScope must not be null");
        Objects.requireNonNull(parseTree, "parseTree must not be null");
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        
        try {
            FriscEmitter emitter = new FriscEmitter();
            LabelGenerator labelGen = new LabelGenerator();
            
            // Create the main code generation context (no activation record for global scope)
            CodeGenContext context = new CodeGenContext(globalScope, emitter, labelGen, null, null, null, null);
            
            // Generate program initialization
            generateProgramInit(context);
            
            // Process the translation unit (functions and global declarations)
            // This will mark which helper functions are needed
            processTranslationUnit(context, parseTree);
            
            // Generate helper functions (F_MUL, F_DIV) only if needed, before user functions
            if (emitter.needsMulHelper() || emitter.needsDivHelper()) {
                HelperFunctionGenerator helperGen = new HelperFunctionGenerator();
                helperGen.generateHelperFunctions(context, emitter.needsMulHelper(), emitter.needsDivHelper());
            }
            
            // Write the generated code to file
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
     * </pre>
     * 
     * <p>This method:
     * <ol>
     *   <li>Processes all function definitions ({@code <definicija_funkcije>})</li>
     *   <li>Generates helper functions (F_MUL, F_DIV) if needed</li>
     *   <li>Generates global variable declarations ({@code <deklaracija>})</li>
     * </ol>
     * 
     * <p><b>Processing Order:</b>
     * <ul>
     *   <li>Functions are generated first (they may reference globals)</li>
     *   <li>Helper functions (F_MUL, F_DIV) are generated before user functions</li>
     *   <li>Global variables are generated last (data section)</li>
     * </ul>
     */
    private void processTranslationUnit(CodeGenContext context, NonTerminalNode translationUnit) {
        // This will be implemented by delegating to specialized generators
        // for functions, expressions, and statements
        
        // Create generators
        FunctionCodeGenerator funcGen = new FunctionCodeGenerator(context);
        GlobalVariableGenerator globalGen = new GlobalVariableGenerator(context);
        
        // Set parse tree for global variable initializers
        globalGen.setParseTree(translationUnit);
        
        // Process all external declarations in the translation unit
        funcGen.processTranslationUnit(translationUnit);
        
        // Generate global variable declarations at the end
        globalGen.generateGlobalVariables();
    }
    
}
