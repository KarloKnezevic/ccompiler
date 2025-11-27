package hr.fer.ppj.codegen;

import hr.fer.ppj.codegen.func.FunctionCodeGenerator;
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
 * <p>The generated FRISC program follows the standard structure:
 * <ul>
 *   <li>Program initialization (stack setup, main call, halt)</li>
 *   <li>Function definitions (subroutines)</li>
 *   <li>Global variable declarations</li>
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
            processTranslationUnit(context, parseTree);
            
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
     * <p>This generates:
     * <pre>
     * ; Program entry point
     * MOVE 10000, R7      ; init stack pointer
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
