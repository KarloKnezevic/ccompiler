package hr.fer.ppj.codegen.func;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.model.ActivationRecord;
import hr.fer.ppj.codegen.stmt.StatementCodeGenerator;
import hr.fer.ppj.codegen.utils.StructLayoutCalculator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Map;
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
    private final NonTerminalNode parseTree;
    
    /**
     * Creates a new function code generator.
     * 
     * <p>Initializes specialized extractors and generators for function code generation.
     * 
     * @param context the code generation context
     * @param parseTree the parse tree from semantic analysis (for extracting struct array sizes)
     */
    public FunctionCodeGenerator(CodeGenContext context, NonTerminalNode parseTree) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.parseTree = parseTree; // Store parse tree for passing to StatementCodeGenerator
        // Pass the full translation unit parse tree to FunctionInfoExtractor
        // so it can extract struct array sizes from struct definitions
        this.infoExtractor = new FunctionInfoExtractor(parseTree);
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
        
        // Extract function name, parameters, body, and function type
        String functionName = null;
        NonTerminalNode parameters = null;
        NonTerminalNode body = null;
        FunctionType functionType = null;
        
        // Look for the deklarator which contains the function name and parameters
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                if ("<deklarator>".equals(nonTerminal.symbol())) {
                    // Extract function name and parameters using FunctionInfoExtractor
                    functionName = infoExtractor.extractFunctionName(nonTerminal);
                    parameters = infoExtractor.extractFunctionParameters(nonTerminal);
                    // Get function type from declarator attributes
                    if (nonTerminal.attributes() != null) {
                        functionType = nonTerminal.attributes().functionType();
                    }
                } else if ("<slozena_naredba>".equals(nonTerminal.symbol())) {
                    body = nonTerminal;
                }
            }
        }
        
        if (functionName != null && body != null) {
            generateFunction(functionName, parameters, body, functionType);
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
    private void generateFunction(String functionName, NonTerminalNode parameters, NonTerminalNode body, FunctionType functionType) {
        String functionLabel = context.labelGenerator().getFunctionLabel(functionName);
        
        context.emitter().emitLabel(functionLabel, "Function " + functionName);
        
        // Check if function returns a struct (needs hidden return pointer parameter)
        boolean returnsStruct = false;
        StructType returnStructType = null;
        if (functionType != null) {
            Type returnType = functionType.returnType();
            Type strippedReturnType = TypeSystem.stripConst(returnType);
            if (strippedReturnType instanceof StructType) {
                returnsStruct = true;
                returnStructType = (StructType) strippedReturnType;
            }
        }
        
        // Create activation record for this function
        ActivationRecord activationRecord = new ActivationRecord();
        
        // If function returns a struct, the return pointer is passed in R2 (not on stack)
        // No hidden parameter needed - R2 is set by caller before CALL instruction
        if (returnsStruct) {
            context.emitter().emitComment("Struct return: return pointer passed in R2 by caller");
        }
        
        // Process function parameters and add them to activation record
        if (parameters != null) {
            // Get parameter names and types from semantic attributes (set by semantic analysis)
            // This is more reliable than extracting from parse tree, especially for struct parameters
            List<String> parameterNames = null;
            List<Type> parameterTypes = null;
            
            if (parameters.attributes() != null) {
                parameterNames = parameters.attributes().parameterNames();
                parameterTypes = parameters.attributes().parameterTypes();
            }
            
            // Fallback: extract parameter names from parse tree if semantic attributes not available
            if (parameterNames == null || parameterNames.isEmpty()) {
                parameterNames = infoExtractor.extractParameterNames(parameters);
            }
            
            // If we have types, use them to calculate sizes; otherwise default to 4 bytes
            for (int i = 0; i < parameterNames.size(); i++) {
                String paramName = parameterNames.get(i);
                int paramSize = 4; // Default: 4 bytes for scalars
                
                if (parameterTypes != null && i < parameterTypes.size()) {
                    Type paramType = parameterTypes.get(i);
                    Type strippedParamType = TypeSystem.stripConst(paramType);
                    
                    // Calculate size based on parameter type
                    if (strippedParamType instanceof StructType structType) {
                        // Struct parameter: allocate sizeof(struct) bytes
                        // Extract array sizes if needed (for structs with array fields)
                        // Also extract array sizes for nested structs that contain arrays
                        Map<String, Integer> arraySizes = null;
                        Map<String, Map<String, Integer>> nestedStructArraySizes = null;
                        
                        if (infoExtractor.getArraySizeExtractor() != null) {
                            String structTag = structType.tag();
                            arraySizes = infoExtractor.getArraySizeExtractor().extractArraySizes(structTag);
                            
                            // Extract array sizes for nested struct fields (recursively)
                            // CRITICAL: Must recursively extract for ALL nested structs at ALL levels
                            // This ensures we get array sizes for Inner when processing Outer
                            nestedStructArraySizes = new java.util.HashMap<>();
                            extractNestedStructArraySizes(structType, infoExtractor.getArraySizeExtractor(), nestedStructArraySizes);
                        }
                        
                        paramSize = StructLayoutCalculator.calculateStructSize(structType, arraySizes, nestedStructArraySizes);
                    } else if (strippedParamType instanceof ArrayType) {
                        // Array parameters decay to pointers: 4 bytes
                        paramSize = 4;
                    } else {
                        // Scalar types (int, char, float, pointer): 4 bytes
                        paramSize = 4;
                    }
                }
                
                activationRecord.addParameter(paramName, paramSize);
                context.emitter().emitComment("Parameter " + paramName + " (" + paramSize + " bytes) at " + 
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
        // Set parse tree for extracting struct array sizes (needed for deeply nested struct access)
        // This propagates the parse tree to all LValueAddressGenerator instances so they can
        // extract array sizes for nested structs with arrays
        stmtGen.setParseTree(parseTree);
        stmtGen.generateStatement(body);
        
        // Generate function epilogue label (for functions that fall through without explicit return)
        // Return statements will jump to this label to avoid duplicate epilogues
        context.emitter().emitLabel(exitLabel, "function exit");
        
        // Generate function epilogue using FunctionPrologueEpilogueGenerator
        prologueEpilogueGenerator.generateEpilogue(functionContext, activationRecord);
        
        context.emitter().emitNewline();
    }
    
    /**
     * Recursively extracts array sizes for all nested struct fields.
     * 
     * <p>This method traverses the struct type hierarchy and extracts array sizes
     * for all nested structs at all levels, ensuring that deeply nested structs
     * with array fields have their array sizes available for size calculation.
     * 
     * @param structType the struct type to extract nested struct array sizes for
     * @param arraySizeExtractor the extractor to use for extracting array sizes
     * @param nestedStructArraySizes the map to populate with nested struct array sizes
     */
    private void extractNestedStructArraySizes(StructType structType, 
                                               hr.fer.ppj.codegen.utils.StructArraySizeExtractor arraySizeExtractor,
                                               Map<String, Map<String, Integer>> nestedStructArraySizes) {
        if (structType == null || arraySizeExtractor == null) {
            return;
        }
        
        // Extract array sizes for all fields that are struct types (recursively)
        for (Map.Entry<String, Type> field : structType.fields().entrySet()) {
            Type fieldType = TypeSystem.stripConst(field.getValue());
            
            if (fieldType instanceof StructType nestedStructType) {
                String nestedTag = nestedStructType.tag();
                
                // Skip if we've already extracted array sizes for this struct
                if (nestedStructArraySizes.containsKey(nestedTag)) {
                    continue;
                }
                
                // Extract array sizes for this nested struct
                // CRITICAL: Always extract, even if empty, so we know we've tried
                // If the struct has array fields, this will populate the map with array sizes
                Map<String, Integer> nestedArraySizes = arraySizeExtractor.extractArraySizes(nestedTag);
                // Always put in map, even if empty (needed for calculateStructSize to know we've tried)
                nestedStructArraySizes.put(nestedTag, nestedArraySizes);
                
                // CRITICAL: Recursively extract array sizes for even deeper nested structs
                // This ensures we get array sizes for Inner when processing Outer
                // This must be done AFTER extracting array sizes for the current nested struct,
                // so that deeper nested structs can also be found
                extractNestedStructArraySizes(nestedStructType, arraySizeExtractor, nestedStructArraySizes);
            }
        }
    }
}
