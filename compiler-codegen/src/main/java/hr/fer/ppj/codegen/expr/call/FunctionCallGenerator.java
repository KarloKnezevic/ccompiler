package hr.fer.ppj.codegen.expr.call;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.utils.LValueAddressGenerator;
import hr.fer.ppj.codegen.structs.NestedStructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructArraySizeExtractor;
import hr.fer.ppj.codegen.structs.StructSizeCalculator;
import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates FRISC assembly code for function calls.
 * 
 * <p>This class handles the generation of code for function calls, implementing the
 * <b>function call code generation algorithm</b> that translates C function calls
 * into FRISC assembly following the standard calling convention.
 * 
 * <p><b>Algorithm: Function Call Code Generation</b>
 * 
 * <p>The algorithm works as follows:
 * <ol>
 *   <li><b>Function Name Extraction:</b> Extract function name from function expression
 *       (must be a simple identifier, function pointers not supported)</li>
 *   <li><b>Argument Evaluation:</b> Evaluate all argument expressions in left-to-right order
 *       (for side effects), but push them in right-to-left order (for calling convention)</li>
 *   <li><b>Array Argument Handling:</b> For array arguments, pass the address instead of the value
 *       (C array decay to pointer semantics)</li>
 *   <li><b>Function Call:</b> Emit CALL instruction to function label</li>
 *   <li><b>Stack Cleanup:</b> Remove arguments from stack (caller cleans up)</li>
 *   <li><b>Result Handling:</b> Move return value from R6 to R0</li>
 * </ol>
 * 
 * <p><b>FRISC Calling Convention:</b>
 * 
 * <p>Function calls follow the standard FRISC calling convention:
 * <ul>
 *   <li><b>Argument Passing:</b> Arguments pushed right-to-left on stack (C convention)
 *       <ul>
 *         <li>Last argument pushed first (at highest address)</li>
 *         <li>First argument pushed last (at lowest address, top of stack)</li>
 *       </ul>
 *   </li>
 *   <li><b>Return Value:</b> Function returns value in register R6</li>
 *   <li><b>Stack Cleanup:</b> Caller removes arguments from stack after call</li>
 *   <li><b>Register Preservation:</b> Callee may use R0-R4, must preserve R5 (FP) and R7 (SP)</li>
 * </ul>
 * 
 * <p><b>Array Argument Handling:</b>
 * 
 * <p>C arrays decay to pointers when passed as arguments. This is implemented by:
 * <ol>
 *   <li><b>Array Detection:</b> Check if argument is a simple array variable</li>
 *   <li><b>Address Generation:</b> Generate code to compute array base address:
 *       <ul>
 *         <li>Global arrays: {@code MOVE G_ARRAY, R0}</li>
 *         <li>Local arrays: {@code MOVE R5, R0; ADD R0, -offset, R0}</li>
 *         <li>Array parameters: {@code LOAD R0, (R5+offset)} (load pointer value)</li>
 *       </ul>
 *   </li>
 *   <li><b>Address Push:</b> Push the address (not the array contents) onto the stack</li>
 * </ol>
 * 
 * <p><b>Argument Evaluation Order:</b>
 * 
 * <p>C standard specifies left-to-right evaluation order for function arguments (for side effects).
 * However, arguments are pushed right-to-left for the calling convention:
 * <ol>
 *   <li>Evaluate arguments left-to-right (preserves side effect order)</li>
 *   <li>Store each result temporarily (on stack or in register)</li>
 *   <li>Push arguments right-to-left (last argument first)</li>
 * </ol>
 * 
 * <p>In this implementation, we evaluate and push immediately, which achieves the correct
 * order because we iterate through arguments in reverse order.
 * 
 * <p><b>FRISC Code Pattern (Function Call with Arguments):</b>
 * <pre>
 * ; Function call: foo(x, y, z)
 * 
 * ; Evaluate and push arguments (right-to-left)
 * ... (evaluate z, result in R0) ...
 * PUSH R0                      ; push z (last argument, pushed first)
 * 
 * ... (evaluate y, result in R0) ...
 * PUSH R0                      ; push y
 * 
 * ... (evaluate x, result in R0) ...
 * PUSH R0                      ; push x (first argument, pushed last)
 * 
 * ; Call function
 * CALL F_FOO                   ; call foo
 * 
 * ; Clean up arguments (3 arguments × 4 bytes = 12 bytes)
 * ADD R7, %D 12, R7            ; remove arguments from stack
 * 
 * ; Move return value to R0
 * MOVE R6, R0                  ; function result
 * </pre>
 * 
 * <p><b>FRISC Code Pattern (Array Argument):</b>
 * <pre>
 * ; Function call: foo(arr) where arr is an array
 * 
 * ; Generate array address
 * MOVE G_ARR, R0               ; global array address
 * ; OR
 * MOVE R5, R0                  ; local array
 * ADD R0, -20, R0              ; add base offset
 * 
 * ; Push address (not array contents)
 * PUSH R0                      ; push array address
 * 
 * CALL F_FOO
 * ADD R7, %D 4, R7             ; cleanup
 * MOVE R6, R0                  ; result
 * </pre>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the number of arguments (each argument
 *       is evaluated and pushed once)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers and stack space for arguments</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionCallGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    private final LValueAddressGenerator addressGenerator;
    private final StructArgumentGenerator structArgumentGenerator;
    
    /**
     * Creates a new function call generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the main expression generator for recursive calls
     */
    public FunctionCallGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
        this.addressGenerator = new LValueAddressGenerator(context, expressionGenerator);
        this.structArgumentGenerator = new StructArgumentGenerator(context, addressGenerator);
    }
    
    /**
     * Sets the parse tree for extracting struct array sizes.
     * 
     * <p>This propagates the parse tree to the LValueAddressGenerator so it can
     * extract array sizes for nested structs with arrays.
     * 
     * @param parseTree the parse tree from semantic analysis
     */
    public void setParseTree(NonTerminalNode parseTree) {
        if (addressGenerator != null) {
            addressGenerator.setParseTree(parseTree);
        }
    }
    
    /**
     * Generates code for a function call.
     * 
     * @param function the function expression (should be an identifier)
     * @param arguments the argument list (may be null for no arguments)
     */
    public void generateFunctionCall(NonTerminalNode function, NonTerminalNode arguments) {
        generateFunctionCallWithReturnPointer(function, arguments, null);
    }
    
    /**
     * Generates code for a function call, optionally with a hidden return pointer for struct returns.
     * 
     * @param function the function expression (should be an identifier)
     * @param arguments the argument list (may be null for no arguments)
     * @param returnPointerRegister register containing the return pointer (null if not struct return)
     */
    public void generateFunctionCallWithReturnPointer(NonTerminalNode function, NonTerminalNode arguments, String returnPointerRegister) {
        // Extract function name
        String functionName = extractVariableName(function);
        
        if (functionName == null) {
            // Complex function call - function pointer or complex expression
            throw new IllegalStateException("Complex function call (function pointer) not supported in this subset");
        }
        
        context.emitter().emitComment("Function call: " + functionName);
        
        // Check if function returns a struct
        boolean returnsStruct = returnPointerRegister != null;
        if (!returnsStruct) {
            // Check function type to see if it returns a struct
            var symbolOpt = context.globalScope().lookup(functionName);
            if (symbolOpt.isPresent() && symbolOpt.get() instanceof FunctionSymbol funcSymbol) {
                FunctionType funcType = funcSymbol.type();
                Type returnType = funcType.returnType();
                Type strippedReturnType = TypeSystem.stripConst(returnType);
                returnsStruct = strippedReturnType instanceof StructType;
            }
        }
        
        // If function returns a struct, save return pointer address before argument evaluation
        // Argument evaluation may use R0-R4, so we save the return pointer in R1 temporarily
        // (R1 is less likely to be used than R0, and R2 needs to be free for the calling convention)
        if (returnsStruct) {
            if (returnPointerRegister != null) {
                // Save return pointer address in R1 (temporary storage)
                // R1 will be preserved through argument evaluation
                context.emitter().emitInstruction("MOVE", returnPointerRegister, "R1", "save struct return buffer address");
            } else {
                // No return pointer provided - this shouldn't happen for struct returns
                throw new IllegalStateException("Struct-returning function call requires return pointer register");
            }
        }
        
        // Generate arguments and push them onto the stack
        // Argument evaluation may use R0-R4, but R1 contains our saved return pointer
        int argumentSizeBytes = 0;
        if (arguments != null) {
            argumentSizeBytes = generateFunctionArguments(arguments);
        }
        
        // Set return pointer in R2 (after argument evaluation, before CALL)
        // R2 is the calling convention register for struct return pointer
        if (returnsStruct) {
            // Move saved return pointer from R1 to R2
            context.emitter().emitInstruction("MOVE", "R1", "R2", "set struct return buffer in R2");
        }
        
        // Generate the function call
        String functionLabel = context.labelGenerator().getFunctionLabel(functionName);
        context.emitter().emitInstruction("CALL", functionLabel, null, "call " + functionName);
        
        // Clean up arguments from the stack (caller cleans up)
        // Note: struct return pointer is in R2 (register), not on stack, so no cleanup needed for it
        if (argumentSizeBytes > 0) {
            context.emitter().emitInstruction("ADD", "R7", "%D " + argumentSizeBytes, "R7", 
                                            "cleanup " + argumentSizeBytes + " bytes of arguments");
        }
        
        // For struct returns, result is already written to return pointer, no need to move R6
        if (!returnsStruct) {
            // Function result is now in R6, move to R0 for expression result
            context.emitter().emitInstruction("MOVE", "R6", "R0", "function result");
        }
    }
    
    /**
     * Generates code for function arguments and returns the total size in bytes.
     * 
     * <p>Arguments are pushed onto the stack in right-to-left order (last argument first).
     * Array variables are passed as addresses rather than values.
     * Struct arguments are copied word-by-word onto the stack.
     * 
     * @param arguments the argument list node
     * @return the total size in bytes of all arguments pushed
     */
    private int generateFunctionArguments(NonTerminalNode arguments) {
        List<NonTerminalNode> argumentExpressions = extractArgumentExpressions(arguments);
        int totalSizeBytes = 0;
        
        // Generate arguments in reverse order (last argument pushed first)
        // This way, the first argument will be at the top of the stack
        for (int i = argumentExpressions.size() - 1; i >= 0; i--) {
            NonTerminalNode arg = argumentExpressions.get(i);
            
            // Check argument type
            Type argType = arg.attributes() != null ? arg.attributes().type() : null;
            Type strippedArgType = argType != null ? TypeSystem.stripConst(argType) : null;
            
            if (strippedArgType instanceof StructType structType) {
                // Struct argument: copy struct onto stack word-by-word
                // Extract array sizes if needed (for structs with array fields)
                // Also extract array sizes for nested structs that contain arrays
                Map<String, Integer> arraySizes = null;
                Map<String, Map<String, Integer>> nestedStructArraySizes = null;
                
                if (addressGenerator != null) {
                    var arraySizeExtractor = addressGenerator.getArraySizeExtractor();
                    if (arraySizeExtractor != null) {
                        String structTag = structType.tag();
                        arraySizes = arraySizeExtractor.extractArraySizes(structTag);
                        
                        // Extract array sizes for nested struct fields (recursively)
                        // CRITICAL: Must recursively extract for ALL nested structs at ALL levels
                        // This ensures we get array sizes for Inner when processing Outer
                        nestedStructArraySizes = new java.util.HashMap<>();
                        NestedStructArraySizeExtractor.extractNestedStructArraySizes(structType, arraySizeExtractor, nestedStructArraySizes);
                    }
                }
                
                int structSize = StructSizeCalculator.calculateStructSize(structType, arraySizes, nestedStructArraySizes);
                structArgumentGenerator.generateStructArgument(arg, structType, arraySizes, nestedStructArraySizes);
                totalSizeBytes += structSize;
            } else if (strippedArgType instanceof ArrayType) {
                // Array argument: pass address (array decay to pointer)
                // Check if this is a simple array variable that should be passed as address
                String arrayVarName = extractVariableName(arg);
                if (arrayVarName != null && isArrayVariable(arrayVarName)) {
                    // Generate address of array variable instead of value
                    String baseAddress = getVariableAddress(arrayVarName);
                    if (baseAddress.startsWith("(G_")) {
                        // Global array: extract label and use as address
                        String label = baseAddress.substring(1, baseAddress.length() - 1);
                        context.emitter().emitInstruction("MOVE", label, "R0", "load array address " + arrayVarName);
                    } else {
                        // Local array: compute address from frame pointer
                        String offsetStr = extractOffsetFromAddress(baseAddress);
                        if (offsetStr != null) {
                            context.emitter().emitInstruction("MOVE", "R5", "R0", "load frame pointer");
                            // Use hex offset directly (address offsets are always hex in FRISC)
                            String formattedOffset = offsetStr; // Keep hex format (e.g., "-0C" or "+08")
                            context.emitter().emitInstruction("ADD", "R0", formattedOffset, "R0", "compute array address");
                        } else {
                            // Fallback: generate expression normally (should generate address)
                            expressionGenerator.generateExpression(arg);
                        }
                    }
                    context.emitter().emitInstruction("PUSH", "R0", null, "push argument " + (i + 1));
                } else {
                    // Complex array expression - generate normally (should generate address)
                    expressionGenerator.generateExpression(arg);
                    context.emitter().emitInstruction("PUSH", "R0", null, "push argument " + (i + 1));
                }
                totalSizeBytes += 4; // Pointer: 4 bytes
            } else {
                // Scalar argument (int, char, float, pointer): generate r-value and push
                // CRITICAL: For field access like p.x (where p is a parameter), this MUST
                // generate the VALUE (r-value), not the address (l-value).
                // 
                // expressionGenerator.generateExpression() routes to FieldAccessGenerator
                // which correctly generates: compute address -> LOAD value -> result in R0
                // This works for:
                // - Local structs: p.x where p is local -> R5-offset + field_offset -> LOAD
                // - Parameter structs: p.x where p is parameter -> R5+offset + field_offset -> LOAD
                // - Global structs: p.x where p is global -> G_P + field_offset -> LOAD
                // - Nested access: o.inner.arr[i] -> recursively computes address -> LOAD
                expressionGenerator.generateExpression(arg);
                // R0 now contains the VALUE (not address) of the scalar expression
                context.emitter().emitInstruction("PUSH", "R0", null, "push argument " + (i + 1) + " (value)");
                totalSizeBytes += 4; // Scalar: 4 bytes
            }
        }
        
        return totalSizeBytes;
    }
    
    
    
    /**
     * Checks if a variable is an array variable.
     * 
     * @param variableName the variable name
     * @return true if the variable is an array
     */
    private boolean isArrayVariable(String variableName) {
        // Check global scope first
        var symbolOpt = context.globalScope().lookup(variableName);
        if (symbolOpt.isPresent() && symbolOpt.get() instanceof VariableSymbol varSymbol) {
            Type varType = varSymbol.type();
            return varType instanceof ArrayType;
        }
        
        // Check local scope if in function
        if (context.isInFunction() && context.activationRecord().hasVariable(variableName)) {
            // For local arrays, check the size stored in activation record
            // Arrays have size > 4 bytes (simple variables are 4 bytes)
            Integer size = context.activationRecord().getVariableSize(variableName);
            return size != null && size > 4;
        }
        
        return false;
    }
    
    /**
     * Extracts individual argument expressions from the argument list.
     * 
     * @param arguments the argument list node
     * @return list of argument expressions
     */
    private List<NonTerminalNode> extractArgumentExpressions(NonTerminalNode arguments) {
        List<NonTerminalNode> expressions = new ArrayList<>();
        extractArgumentExpressionsRecursive(arguments, expressions);
        return expressions;
    }
    
    /**
     * Recursively extracts argument expressions from the argument list structure.
     */
    private void extractArgumentExpressionsRecursive(NonTerminalNode node, List<NonTerminalNode> expressions) {
        String symbol = node.symbol();
        
        if ("<lista_argumenata>".equals(symbol)) {
            List<ParseNode> children = node.children();
            
            if (children.size() == 1) {
                // Single argument: <izraz_pridruzivanja>
                expressions.add((NonTerminalNode) children.get(0));
            } else if (children.size() == 3) {
                // Multiple arguments: <lista_argumenata> ZAREZ <izraz_pridruzivanja>
                extractArgumentExpressionsRecursive((NonTerminalNode) children.get(0), expressions);
                expressions.add((NonTerminalNode) children.get(2));
            }
        }
    }
    
    /**
     * Extracts a variable name from an expression.
     * 
     * @param expr the expression
     * @return the variable name, or null if not a simple variable
     */
    private String extractVariableName(NonTerminalNode expr) {
        return hr.fer.ppj.codegen.utils.IdentifierExtractor.findIdentifier(expr);
    }
    
    /**
     * Gets the FRISC address for a variable (local or global).
     * 
     * @param variableName the variable name
     * @return the FRISC address expression
     */
    private String getVariableAddress(String variableName) {
        var resolver = new hr.fer.ppj.codegen.env.VariableAddressResolver(context);
        return resolver.getVariableAddress(variableName);
    }
    
    /**
     * Extracts the offset from an address expression like "(R5-04)" or "(R5+08)".
     * 
     * @param address the address expression
     * @return the offset as a string (e.g., "-04", "+08"), or null if not parseable
     */
    private String extractOffsetFromAddress(String address) {
        // Address format: "(R5-04)" or "(R5+08)" or "(R5-0C)"
        if (address.startsWith("(R5") && address.endsWith(")")) {
            // Extract offset starting from position 3 (after "R5") to before the closing ")"
            String offset = address.substring(3, address.length() - 1);
            return offset; // Returns "-04", "+08", "-0C" etc. (hex format)
        }
        return null;
    }
}

