package hr.fer.ppj.codegen.func;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.model.ActivationRecord;
import hr.fer.ppj.codegen.stmt.StatementCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import java.util.List;
import java.util.Objects;

/**
 * Orchestrates FRISC assembly code generation for function definitions.
 * 
 * <p>This class serves as the main coordinator for function code generation,
 * delegating to specialized classes for:
 * <ul>
 *   <li>Function information extraction ({@link FunctionInfoExtractor})</li>
 *   <li>Prologue/epilogue generation ({@link FunctionPrologueEpilogueGenerator})</li>
 *   <li>Statement generation ({@link StatementCodeGenerator})</li>
 * </ul>
 * 
 * <p><b>Grammar Rule:</b> Processes {@code <prijevodna_jedinica>} to find
 * {@code <definicija_funkcije>} nodes:
 * <pre>
 * &lt;prijevodna_jedinica&gt; ::= &lt;vanjska_deklaracija&gt;
 *                            | &lt;prijevodna_jedinica&gt; &lt;vanjska_deklaracija&gt;
 * 
 * &lt;vanjska_deklaracija&gt; ::= &lt;definicija_funkcije&gt;
 *                           | &lt;deklaracija&gt;
 * </pre>
 * 
 * <p><b>FRISC Calling Convention:</b>
 * <ul>
 *   <li>Arguments are passed on the stack (right-to-left)</li>
 *   <li>Return values are placed in register R6</li>
 *   <li>Caller cleans up arguments from the stack</li>
 *   <li>Local variables are allocated on the stack</li>
 * </ul>
 * 
 * <p>Each function is translated to a FRISC subroutine with a label following
 * the pattern {@code F_<FUNCTION_NAME>}. The subroutine manages its own
 * activation record (stack frame) for parameters and local variables.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionCodeGenerator {
    
    private final CodeGenContext context;
    private final FunctionInfoExtractor infoExtractor;
    private final FunctionPrologueEpilogueGenerator prologueEpilogueGenerator;
    
    /**
     * Creates a new function code generator.
     * 
     * <p>Initializes specialized extractors and generators for function code generation.
     * 
     * @param context the code generation context
     */
    public FunctionCodeGenerator(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.infoExtractor = new FunctionInfoExtractor();
        this.prologueEpilogueGenerator = new FunctionPrologueEpilogueGenerator();
    }
    
    /**
     * Processes the translation unit, generating code for all function definitions.
     * 
     * <p>This method traverses the parse tree looking for function definitions
     * and generates the corresponding FRISC subroutines.
     * 
     * @param translationUnit the root node of the translation unit
     */
    public void processTranslationUnit(NonTerminalNode translationUnit) {
        Objects.requireNonNull(translationUnit, "translationUnit must not be null");
        
        context.emitter().emitComment("Function definitions");
        
        // Process all external declarations
        processExternalDeclarations(translationUnit);
    }
    
    /**
     * Processes external declarations, looking for function definitions.
     */
    private void processExternalDeclarations(NonTerminalNode node) {
        String symbol = node.symbol();
        
        if ("<prijevodna_jedinica>".equals(symbol)) {
            List<ParseNode> children = node.children();
            
            for (ParseNode child : children) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    processExternalDeclarations(nonTerminal);
                }
            }
        } else if ("<vanjska_deklaracija>".equals(symbol)) {
            processExternalDeclaration(node);
        }
    }
    
    /**
     * Processes a single external declaration.
     */
    private void processExternalDeclaration(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode child) {
            String symbol = child.symbol();
            
            if ("<definicija_funkcije>".equals(symbol)) {
                generateFunctionDefinition(child);
            }
            // Ignore other external declarations (global variables are handled separately)
        }
    }
    
    /**
     * Generates code for a function definition.
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <definicija_funkcije>}:
     * <pre>
     * &lt;definicija_funkcije&gt; ::= &lt;ime_tipa&gt; &lt;deklarator&gt; &lt;slozena_naredba&gt;
     * </pre>
     * 
     * <p>Extracts function name, parameters, and body from the parse tree structure,
     * then delegates to {@link #generateFunction(String, NonTerminalNode, NonTerminalNode)}.
     * 
     * @param node the function definition node ({@code <definicija_funkcije>})
     */
    private void generateFunctionDefinition(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        // Extract function name, parameters, and body from the function definition structure
        String functionName = null;
        NonTerminalNode parameters = null;
        NonTerminalNode body = null;
        
        // Look for the deklarator which contains the function name and parameters
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                if ("<deklarator>".equals(nonTerminal.symbol())) {
                    // Extract function name and parameters using FunctionInfoExtractor
                    functionName = infoExtractor.extractFunctionName(nonTerminal);
                    parameters = infoExtractor.extractFunctionParameters(nonTerminal);
                } else if ("<slozena_naredba>".equals(nonTerminal.symbol())) {
                    body = nonTerminal;
                }
            }
        }
        
        if (functionName != null && body != null) {
            generateFunction(functionName, parameters, body);
        }
    }
    
    /**
     * Generates FRISC code for a single function.
     * 
     * <p><b>Grammar Rule:</b> Implements {@code <definicija_funkcije>}:
     * <pre>
     * &lt;definicija_funkcije&gt; ::= &lt;ime_tipa&gt; &lt;deklarator&gt; &lt;slozena_naredba&gt;
     * </pre>
     * 
     * <p><b>FRISC Function Structure:</b>
     * <pre>
     * F_FUNCTION_NAME           ; Function label
     *     PUSH R5                ; Save old frame pointer
     *     MOVE R7, R5            ; R5 = current SP (new frame pointer)
     *     SUB R7, %D K, R7       ; Allocate K bytes for local variables
     *     ... (function body) ...
     * L_EXIT                    ; Exit label
     *     ADD R7, %D K, R7       ; Deallocate locals
     *     POP R5                 ; Restore old frame pointer
     *     RET                    ; Return to caller
     * </pre>
     * 
     * <p><b>FRISC Calling Convention:</b>
     * <ul>
     *   <li>Function label: {@code F_<function_name>}</li>
     *   <li>Prologue: Save R5, set R5 = R7, allocate locals</li>
     *   <li>Parameters: At positive offsets from R5 (R5+8, R5+12, ...)</li>
     *   <li>Locals: At negative offsets from R5 (R5-4, R5-8, ...)</li>
     *   <li>Return value: In R6</li>
     *   <li>Epilogue: Deallocate locals, restore R5, RET</li>
     * </ul>
     * 
     * @param functionName the function name (IDN terminal)
     * @param parameters the parameter list ({@code <lista_parametara>} or null)
     * @param body the function body ({@code <slozena_naredba>})
     */
    private void generateFunction(String functionName, NonTerminalNode parameters, NonTerminalNode body) {
        String functionLabel = context.labelGenerator().getFunctionLabel(functionName);
        
        context.emitter().emitLabel(functionLabel, "Function " + functionName);
        
        // Create activation record for this function
        ActivationRecord activationRecord = new ActivationRecord();
        
        // Process function parameters and add them to activation record
        if (parameters != null) {
            // Extract parameter names using FunctionInfoExtractor
            List<String> parameterNames = infoExtractor.extractParameterNames(parameters);
            for (String paramName : parameterNames) {
                activationRecord.addParameter(paramName);
                context.emitter().emitComment("Parameter " + paramName + " at " + 
                                            activationRecord.getVariableAddress(paramName));
            }
        }
        
        // Process local variable declarations in the function body
        // Extract local variables using FunctionInfoExtractor
        // For this project: both int and char arrays use element size 4 bytes
        int elementSize = 4;
        List<FunctionInfoExtractor.VariableInfo> localVars = infoExtractor.extractLocalVariables(body, elementSize);
        for (FunctionInfoExtractor.VariableInfo varInfo : localVars) {
            activationRecord.addLocalVariable(varInfo.name(), varInfo.sizeInBytes());
            context.emitter().emitComment("Local variable " + varInfo.name() + " at " + 
                                        activationRecord.getVariableAddress(varInfo.name()));
        }
        
        // Generate exit label for this function
        String exitLabel = context.labelGenerator().generateLabel();
        
        // Create function context with activation record and exit label
        CodeGenContext functionContext = context.withActivationRecord(activationRecord)
                                               .withFunctionExitLabel(exitLabel);
        
        // Generate function prologue using FunctionPrologueEpilogueGenerator
        prologueEpilogueGenerator.generatePrologue(functionContext, activationRecord);
        
        // Generate function body
        StatementCodeGenerator stmtGen = new StatementCodeGenerator(functionContext);
        stmtGen.generateStatement(body);
        
        // Generate function epilogue label (for functions that fall through without explicit return)
        // Return statements will jump to this label to avoid duplicate epilogues
        context.emitter().emitLabel(exitLabel, "function exit");
        
        // Generate function epilogue using FunctionPrologueEpilogueGenerator
        prologueEpilogueGenerator.generateEpilogue(functionContext, activationRecord);
        
        context.emitter().emitNewline();
    }
}
