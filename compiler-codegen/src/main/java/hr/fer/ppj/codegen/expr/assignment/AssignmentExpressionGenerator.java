package hr.fer.ppj.codegen.expr.assignment;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.expr.call.FunctionCallGenerator;
import hr.fer.ppj.codegen.expr.field.FieldAccessGenerator;
import hr.fer.ppj.codegen.utils.LValueAddressGenerator;
import hr.fer.ppj.codegen.utils.StructLayoutCalculator;
import hr.fer.ppj.codegen.utils.VariableAddressResolver;
import hr.fer.ppj.semantics.symbols.FunctionSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Generates FRISC assembly code for assignment expressions and increment/decrement operations.
 * 
 * <p>This class handles the generation of code for assignments and increment/decrement operations,
 * implementing the <b>assignment code generation algorithm</b> that translates C assignment
 * operations into FRISC assembly.
 * 
 * <p><b>Algorithm: Assignment Code Generation</b>
 * 
 * <p>The algorithm works as follows:
 * <ol>
 *   <li><b>Right-Hand Side Evaluation:</b> Evaluate the right-hand side expression first
 *       (result in R0)</li>
 *   <li><b>Left-Hand Side Resolution:</b> Resolve the left-hand side (lvalue):
 *       <ul>
 *         <li>Simple variable: Get address from activation record or global scope</li>
 *         <li>Array element: Delegate to array generator for address calculation</li>
 *       </ul>
 *   </li>
 *   <li><b>Value Storage:</b> Store the value from R0 to the resolved address</li>
 * </ol>
 * 
 * <p><b>Assignment Types Handled:</b>
 * <ul>
 *   <li><b>Simple Variable Assignment:</b> {@code x = value} - direct STORE to variable address</li>
 *   <li><b>Array Element Assignment:</b> {@code a[i] = value} - delegate to array generator</li>
 * </ul>
 * 
 * <p><b>Increment/Decrement Operations:</b>
 * 
 * <p>Increment and decrement operations are delegated to {@link IncrementDecrementGenerator}:
 * <ul>
 *   <li><b>Pre-increment (++var):</b> Increment variable, return new value</li>
 *   <li><b>Pre-decrement (--var):</b> Decrement variable, return new value</li>
 *   <li><b>Post-increment (var++):</b> Save old value, increment variable, return old value</li>
 *   <li><b>Post-decrement (var--):</b> Save old value, decrement variable, return old value</li>
 * </ul>
 * 
 * <p><b>FRISC Code Pattern (Simple Assignment):</b>
 * <pre>
 * ; Assignment: x = y + z
 * 
 * ; Evaluate right-hand side
 * ... (evaluate y + z, result in R0) ...
 * 
 * ; Store to left-hand side
 * STORE R0, (R5-04)            ; local variable
 * ; OR
 * STORE R0, (G_X)               ; global variable
 * </pre>
 * 
 * <p><b>FRISC Code Pattern (Array Assignment):</b>
 * <pre>
 * ; Assignment: a[i] = value
 * 
 * ; Evaluate value (already in R0)
 * MOVE R0, R2                   ; save value
 * 
 * ; Evaluate index
 * ... (evaluate i, result in R0) ...
 * SHL R0, %D 2, R0              ; index * 4
 * 
 * ; Compute element address
 * MOVE G_A, R1                  ; base address
 * ADD R1, R0, R1                ; element address
 * 
 * ; Store value
 * STORE R2, (R1)                ; store to array element
 * </pre>
 * 
 * <p><b>Lvalue Resolution:</b>
 * 
 * <p>Lvalues (left-hand sides of assignments) must be:
 * <ul>
 *   <li><b>Modifiable:</b> Must be a variable or array element (not a constant)</li>
 *   <li><b>Addressable:</b> Must have a memory address (not a temporary value)</li>
 * </ul>
 * 
 * <p>This implementation supports:
 * <ul>
 *   <li>Simple variables (local and global)</li>
 *   <li>Array elements (via array generator)</li>
 * </ul>
 * 
 * <p>Complex lvalues (e.g., pointer dereferences, structure members) are not supported
 * in this subset.
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for simple assignments, O(1) for array assignments
 *       (constant number of instructions)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class AssignmentExpressionGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    private final IncrementDecrementGenerator incDecGenerator;
    private final VariableAddressResolver addressResolver;
    private final LValueAddressGenerator addressGenerator;
    private hr.fer.ppj.codegen.expr.array.ArrayExpressionGenerator arrayGenerator;
    private FieldAccessGenerator fieldAccessGenerator;
    
    /**
     * Creates a new assignment expression generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the main expression generator for recursive calls
     */
    public AssignmentExpressionGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
        this.incDecGenerator = new IncrementDecrementGenerator(context, expressionGenerator);
        this.addressResolver = new VariableAddressResolver(context);
        this.addressGenerator = new LValueAddressGenerator(context, expressionGenerator);
    }
    
    /**
     * Sets the array generator for array assignment support.
     * 
     * @param arrayGenerator the array expression generator
     */
    public void setArrayGenerator(hr.fer.ppj.codegen.expr.array.ArrayExpressionGenerator arrayGenerator) {
        this.arrayGenerator = arrayGenerator;
    }
    
    /**
     * Sets the field access generator for struct field assignment support.
     * 
     * @param fieldAccessGenerator the field access generator
     */
    public void setFieldAccessGenerator(FieldAccessGenerator fieldAccessGenerator) {
        this.fieldAccessGenerator = fieldAccessGenerator;
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
     * Generates code for assignment expressions (=).
     * 
     * @param node the assignment expression node
     */
    public void generateAssignmentExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Assignment: <postfiks_izraz> OP_PRIDRUZI <izraz_pridruzivanja>
            NonTerminalNode lvalue = (NonTerminalNode) children.get(0);
            NonTerminalNode rvalue = (NonTerminalNode) children.get(2);
            
            // Check if this is a struct assignment (both sides are structs)
            Type lvalueType = lvalue.attributes() != null ? lvalue.attributes().type() : null;
            Type rvalueType = rvalue.attributes() != null ? rvalue.attributes().type() : null;
            Type strippedLvalueType = lvalueType != null ? TypeSystem.stripConst(lvalueType) : null;
            Type strippedRvalueType = rvalueType != null ? TypeSystem.stripConst(rvalueType) : null;
            
            if (strippedLvalueType instanceof StructType) {
                // LHS is a struct - check if RHS is a struct-returning function call
                if (isStructReturningFunctionCall(rvalue)) {
                    // Optimized path: p = makePoint(...) - pass &p as hidden return pointer
                    generateStructAssignmentFromFunctionCall(lvalue, rvalue, (StructType) strippedLvalueType);
                } else if (strippedRvalueType instanceof StructType) {
                    // Struct assignment: p = q (byte-wise copy)
                    generateStructAssignment(lvalue, rvalue, (StructType) strippedLvalueType);
                } else {
                    throw new IllegalStateException("Cannot assign non-struct to struct: " + strippedRvalueType);
                }
            } else {
                // Regular assignment: evaluate rvalue, then assign
                expressionGenerator.generateExpression(rvalue);
                
                // Generate assignment code (with array generator support)
                generateAssignment(lvalue, "R0", arrayGenerator);
            }
        }
    }
    
    /**
     * Generates code for pre-increment (++var).
     * Returns the new value.
     * 
     * @param operand the operand expression
     */
    public void generatePreIncrement(NonTerminalNode operand) {
        incDecGenerator.generatePreIncrement(operand);
    }
    
    /**
     * Generates code for pre-decrement (--var).
     * Returns the new value.
     * 
     * @param operand the operand expression
     */
    public void generatePreDecrement(NonTerminalNode operand) {
        incDecGenerator.generatePreDecrement(operand);
    }
    
    /**
     * Generates code for post-increment (var++).
     * Returns the old value.
     * 
     * @param operand the operand expression
     */
    public void generatePostIncrement(NonTerminalNode operand) {
        incDecGenerator.generatePostIncrement(operand);
    }
    
    /**
     * Generates code for post-decrement (var--).
     * Returns the old value.
     * 
     * @param operand the operand expression
     */
    public void generatePostDecrement(NonTerminalNode operand) {
        incDecGenerator.generatePostDecrement(operand);
    }
    
    /**
     * Generates code to assign a value to an lvalue.
     * 
     * <p>This method handles:
     * <ul>
     *   <li>Simple variable assignments: {@code x = value}</li>
     *   <li>Array element assignments: {@code a[i] = value}</li>
     *   <li>Struct field assignments: {@code p.x = value}</li>
     *   <li>Struct assignments: {@code p = q} (byte-wise copy)</li>
     * </ul>
     * 
     * @param lvalue the left-hand side expression
     * @param sourceRegister the register containing the value to assign
     * @param arrayGenerator the array generator for array assignments (may be null)
     */
    public void generateAssignment(NonTerminalNode lvalue, String sourceRegister, 
                                    hr.fer.ppj.codegen.expr.array.ArrayExpressionGenerator arrayGenerator) {
        // Save source value on stack (safe - won't be overwritten by address computation)
        context.emitter().emitInstruction("PUSH", sourceRegister, null, "save RHS value");
        
        // Use the address generator to compute the L-value address
        // This handles: simple variables, field access (including nested), and array indexing
        // Address computation may use R0, R1, R2 freely, but won't touch the stack
        addressGenerator.generateAddress(lvalue, "R0");
        
        // Restore RHS value from stack
        context.emitter().emitInstruction("POP", "R1", null, "restore RHS value");
        
        // Get lvalue type to determine store instruction
        Type lvalueType = lvalue.attributes() != null ? lvalue.attributes().type() : null;
        Type strippedLvalueType = lvalueType != null ? TypeSystem.stripConst(lvalueType) : null;
        
        // Store the value to the computed address
        // Note: char, int, and float are all 4 bytes, so use word STORE for all
        context.emitter().emitInstruction("STORE", "R1", "(R0)", "assign value");
        
        // Assignment result is the value assigned (for expressions like x = y = z)
        context.emitter().emitInstruction("MOVE", "R1", "R0", "assignment result");
    }
    
    /**
     * Generates code for struct assignment from a struct-returning function call: p = makePoint(...).
     * 
     * <p>This is an optimized path that passes the LHS address as a hidden return pointer,
     * avoiding an extra copy.
     * 
     * @param lvalue the left-hand side (destination struct)
     * @param rvalue the right-hand side (function call expression)
     * @param structType the struct type
     */
    private void generateStructAssignmentFromFunctionCall(NonTerminalNode lvalue, NonTerminalNode rvalue, StructType structType) {
        // Extract function call information
        FunctionCallInfo callInfo = extractFunctionCallInfo(rvalue);
        if (callInfo == null) {
            throw new IllegalStateException("RHS is not a function call");
        }
        
        // Compute LHS address (destination for struct return)
        addressGenerator.generateAddress(lvalue, "R0");
        
        // Generate function call with hidden return pointer
        // The return pointer is pushed inside generateFunctionCallWithReturnPointer
        FunctionCallGenerator callGen = new FunctionCallGenerator(context, expressionGenerator);
        callGen.generateFunctionCallWithReturnPointer(callInfo.function(), callInfo.arguments(), "R0");
        
        // Struct is already written to LHS by callee, no extra copy needed
        // Assignment result can be ignored (struct assignment returns void-like)
    }
    
    /**
     * Generates code for struct assignment: p = q (byte-wise copy).
     * 
     * <p>This performs a memory copy of the entire struct from source to destination.
     * Uses address generator to handle complex expressions.
     * 
     * @param lvalue the left-hand side (destination struct)
     * @param rvalue the right-hand side (source struct)
     * @param structType the struct type
     */
    private void generateStructAssignment(NonTerminalNode lvalue, NonTerminalNode rvalue, StructType structType) {
        // Calculate struct size - try without array sizes first, then with if needed
        int structSize;
        try {
            structSize = StructLayoutCalculator.calculateStructSize(structType);
        } catch (IllegalArgumentException e) {
            // Struct has array fields - need to extract array sizes
            // Extract array sizes for this struct and nested structs (similar to generateStructArgument)
            Map<String, Integer> arraySizes = null;
            Map<String, Map<String, Integer>> nestedStructArraySizes = null;
            
            if (addressGenerator != null) {
                var arraySizeExtractor = addressGenerator.getArraySizeExtractor();
                if (arraySizeExtractor != null) {
                    String structTag = structType.tag();
                    arraySizes = arraySizeExtractor.extractArraySizes(structTag);
                    
                    // Extract array sizes for nested struct fields
                    nestedStructArraySizes = new java.util.HashMap<>();
                    for (Map.Entry<String, Type> field : structType.fields().entrySet()) {
                        Type fieldType = TypeSystem.stripConst(field.getValue());
                        if (fieldType instanceof StructType nestedStructType) {
                            String nestedTag = nestedStructType.tag();
                            Map<String, Integer> nestedArraySizes = arraySizeExtractor.extractArraySizes(nestedTag);
                            if (!nestedArraySizes.isEmpty()) {
                                nestedStructArraySizes.put(nestedTag, nestedArraySizes);
                            }
                        }
                    }
                    
                    structSize = StructLayoutCalculator.calculateStructSize(structType, arraySizes, nestedStructArraySizes);
                } else {
                    // Can't extract array sizes - throw helpful error
                    throw new IllegalStateException("Cannot calculate struct size for assignment: struct has array fields. " +
                        "Array sizes must be available from parse tree. Call setParseTree() on AssignmentExpressionGenerator. " +
                        "Error: " + e.getMessage());
                }
            } else {
                // Can't extract array sizes - throw helpful error
                throw new IllegalStateException("Cannot calculate struct size for assignment: struct has array fields. " +
                    "Array sizes must be available from parse tree. Error: " + e.getMessage());
            }
        }
        
        context.emitter().emitComment("Struct assignment: copy " + structSize + " bytes");
        
        // Compute source address (RHS) using address generator (handles field access, etc.)
        addressGenerator.generateAddress(rvalue, "R0");
        context.emitter().emitInstruction("MOVE", "R0", "R2", "source addr (struct)");
        
        // Compute destination address (LHS) using address generator
        addressGenerator.generateAddress(lvalue, "R0");
        context.emitter().emitInstruction("MOVE", "R0", "R3", "dest addr (struct)");
        
        // Generate word-wise copy loop (more efficient than byte-wise for 4-byte fields)
        String loopLabel = context.labelGenerator().generateLabel();
        String endLabel = context.labelGenerator().generateLabel();
        
        // Initialize counter: R4 = remaining bytes
        context.emitter().emitInstruction("MOVE", "%D " + structSize, "R4", "remaining bytes");
        
        context.emitter().emitLabel(loopLabel, "struct copy loop");
        
        // Check if counter is zero
        context.emitter().emitInstruction("CMP", "R4", "%D 0", null);
        context.emitter().emitInstruction("JP_EQ", endLabel, "done if counter == 0");
        
        // Load word from source: R0 = *R2
        context.emitter().emitInstruction("LOAD", "R0", "(R2)", "load word from source");
        
        // Store word to destination: *R3 = R0
        context.emitter().emitInstruction("STORE", "R0", "(R3)", "store word to destination");
        
        // Increment pointers: R2 += 4, R3 += 4
        context.emitter().emitInstruction("ADD", "R2", "%D 4", "R2", "increment source pointer");
        context.emitter().emitInstruction("ADD", "R3", "%D 4", "R3", "increment dest pointer");
        
        // Decrement counter: R4 -= 4
        context.emitter().emitInstruction("SUB", "R4", "%D 4", "R4", "decrement remaining bytes");
        
        // Loop back
        context.emitter().emitInstruction("JP", loopLabel, "continue copy loop");
        
        context.emitter().emitLabel(endLabel, "end struct copy");
        
        // Assignment result: address of LHS (for consistency with C semantics)
        context.emitter().emitInstruction("MOVE", "R3", "R0", "assignment result (addr of LHS)");
    }
    
    /**
     * Information about a function call expression.
     */
    private record FunctionCallInfo(NonTerminalNode function, NonTerminalNode arguments) {}
    
    /**
     * Extracts function call information from an expression node.
     * 
     * <p>Recursively searches through expression wrappers to find a function call.
     * Handles cases where the function call is wrapped in expression nodes like
     * {@code <izraz_pridruzivanja>}, {@code <log_ili_izraz>}, etc.
     * 
     * @param expr the expression node (may be wrapped)
     * @return FunctionCallInfo if the expression is a function call, null otherwise
     */
    private FunctionCallInfo extractFunctionCallInfo(NonTerminalNode expr) {
        // Check if this is a postfix expression with function call pattern
        if ("<postfiks_izraz>".equals(expr.symbol())) {
            List<ParseNode> children = expr.children();
            if (children.size() == 3) {
                // Pattern: <postfiks_izraz> L_ZAGRADA D_ZAGRADA (no arguments)
                ParseNode second = children.get(1);
                ParseNode third = children.get(2);
                if (second instanceof TerminalNode leftParen && "L_ZAGRADA".equals(leftParen.symbol()) &&
                    third instanceof TerminalNode rightParen && "D_ZAGRADA".equals(rightParen.symbol())) {
                    return new FunctionCallInfo((NonTerminalNode) children.get(0), null);
                }
            } else if (children.size() == 4) {
                // Pattern: <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
                ParseNode second = children.get(1);
                ParseNode third = children.get(2);
                ParseNode fourth = children.get(3);
                if (second instanceof TerminalNode leftParen && "L_ZAGRADA".equals(leftParen.symbol()) &&
                    third instanceof NonTerminalNode &&
                    fourth instanceof TerminalNode rightParen && "D_ZAGRADA".equals(rightParen.symbol())) {
                    return new FunctionCallInfo((NonTerminalNode) children.get(0), (NonTerminalNode) third);
                }
            }
        }
        
        // If not a direct function call, check if it's wrapped in expression nodes
        // Recursively search through single-child expression wrappers
        List<ParseNode> children = expr.children();
        if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
            // Single child - might be an expression wrapper
            String symbol = expr.symbol();
            if ("<izraz_pridruzivanja>".equals(symbol) ||
                "<log_ili_izraz>".equals(symbol) ||
                "<log_i_izraz>".equals(symbol) ||
                "<bin_ili_izraz>".equals(symbol) ||
                "<bin_xili_izraz>".equals(symbol) ||
                "<bin_i_izraz>".equals(symbol) ||
                "<jednakosni_izraz>".equals(symbol) ||
                "<odnosni_izraz>".equals(symbol) ||
                "<aditivni_izraz>".equals(symbol) ||
                "<multiplikativni_izraz>".equals(symbol) ||
                "<cast_izraz>".equals(symbol) ||
                "<unarni_izraz>".equals(symbol) ||
                "<postfiks_izraz>".equals(symbol) ||
                "<primarni_izraz>".equals(symbol)) {
                // Recursively check the child
                return extractFunctionCallInfo(child);
            }
        }
        
        return null;
    }
    
    /**
     * Checks if an expression is a struct-returning function call.
     * 
     * @param expr the expression node
     * @return true if the expression is a function call that returns a struct
     */
    private boolean isStructReturningFunctionCall(NonTerminalNode expr) {
        FunctionCallInfo callInfo = extractFunctionCallInfo(expr);
        if (callInfo == null) {
            return false;
        }
        
        // Extract function name
        String functionName = addressResolver.extractVariableName(callInfo.function());
        if (functionName == null) {
            return false;
        }
        
        // Look up function in global scope
        var symbolOpt = context.globalScope().lookup(functionName);
        if (symbolOpt.isEmpty() || !(symbolOpt.get() instanceof FunctionSymbol funcSymbol)) {
            return false;
        }
        
        // Check if return type is a struct
        FunctionType funcType = funcSymbol.type();
        Type returnType = funcType.returnType();
        Type strippedReturnType = TypeSystem.stripConst(returnType);
        return strippedReturnType instanceof StructType;
    }
    
    /**
     * Loads the address of a variable into a register.
     * 
     * @param variableName the variable name
     * @param targetRegister the register to load the address into
     */
    private void loadVariableAddress(String variableName, String targetRegister) {
        String address = addressResolver.getVariableAddress(variableName);
        
        if (address.startsWith("(G_")) {
            // Global variable: extract label
            String label = address.substring(1, address.length() - 1);
            context.emitter().emitInstruction("MOVE", label, targetRegister, "load global struct address");
        } else if (address.startsWith("(R5")) {
            // Local variable: compute address from frame pointer
            context.emitter().emitInstruction("MOVE", "R5", targetRegister, "load frame pointer");
            String offsetStr = extractOffsetFromAddress(address);
            if (offsetStr != null) {
                context.emitter().emitInstruction("ADD", targetRegister, offsetStr, targetRegister, "add struct offset");
            }
        }
    }
    
    /**
     * Extracts the offset part from an address expression.
     * 
     * @param address the address expression (e.g., "(R5-04)" or "(R5+08)")
     * @return the offset string (e.g., "-04" or "+08"), or null if not found
     */
    private String extractOffsetFromAddress(String address) {
        int r5Index = address.indexOf("R5");
        if (r5Index == -1) {
            return null;
        }
        
        int offsetStart = r5Index + 2;
        if (offsetStart >= address.length()) {
            return null;
        }
        
        char firstChar = address.charAt(offsetStart);
        if (firstChar != '+' && firstChar != '-') {
            return null;
        }
        
        int offsetEnd = address.indexOf(')', offsetStart);
        if (offsetEnd == -1) {
            return null;
        }
        
        return address.substring(offsetStart, offsetEnd);
    }
    
    /**
     * Gets the type of a variable by looking it up in the symbol table.
     * 
     * @param variableName the variable name
     * @return the variable type, or null if not found
     */
    private Type getVariableType(String variableName) {
        // Check local scope first
        if (context.isInFunction() && context.activationRecord().hasVariable(variableName)) {
            // Local variable - type info not stored in activation record
            // Would need to look up in symbol table, but we don't have function scope access
            return null;
        }
        
        // Check global scope
        return context.globalScope().lookup(variableName)
            .filter(s -> s instanceof hr.fer.ppj.semantics.symbols.VariableSymbol)
            .map(s -> ((hr.fer.ppj.semantics.symbols.VariableSymbol) s).type())
            .orElse(null);
    }
    
    /**
     * Checks if an expression is a field access expression.
     * 
     * @param expr the expression to check
     * @return true if the expression is field access (p.x)
     */
    /**
     * Checks if an expression node represents a field access.
     * 
     * <p>Recursively checks for field access pattern, handling cases where
     * the field access is wrapped in `<unarni_izraz>` (as in assignments).
     * 
     * @param expr the expression node to check
     * @return true if the expression is a field access
     */
    private boolean isFieldAccess(NonTerminalNode expr) {
        // Check if this is a direct field access: <postfiks_izraz> TOCKA IDN
        if ("<postfiks_izraz>".equals(expr.symbol())) {
            List<ParseNode> children = expr.children();
            if (children.size() == 3) {
                ParseNode second = children.get(1);
                if (second instanceof TerminalNode terminal && "TOCKA".equals(terminal.symbol())) {
                    return true;
                }
            }
        }
        // Check if this is wrapped in <unarni_izraz> (common in assignments)
        // <unarni_izraz> -> <postfiks_izraz> TOCKA IDN
        if ("<unarni_izraz>".equals(expr.symbol())) {
            List<ParseNode> children = expr.children();
            if (children.size() == 1 && children.get(0) instanceof NonTerminalNode child) {
                // Recursively check the child
                return isFieldAccess(child);
            }
        }
        return false;
    }
    
    /**
     * Extracts the base expression from a field access node.
     * 
     * <p>Handles cases where field access is wrapped in `<unarni_izraz>`.
     * 
     * @param fieldAccessNode the field access node (may be wrapped in <unarni_izraz>)
     * @return the base expression
     */
    private NonTerminalNode extractFieldAccessBase(NonTerminalNode fieldAccessNode) {
        // If wrapped in <unarni_izraz>, unwrap it
        if ("<unarni_izraz>".equals(fieldAccessNode.symbol())) {
            NonTerminalNode child = (NonTerminalNode) fieldAccessNode.children().get(0);
            return extractFieldAccessBase(child);
        }
        // Direct field access: <postfiks_izraz> TOCKA IDN
        if ("<postfiks_izraz>".equals(fieldAccessNode.symbol())) {
            return (NonTerminalNode) fieldAccessNode.children().get(0);
        }
        throw new IllegalStateException("Not a field access node: " + fieldAccessNode.symbol());
    }
    
    /**
     * Extracts the field name from a field access node.
     * 
     * <p>Handles cases where field access is wrapped in `<unarni_izraz>`.
     * 
     * @param fieldAccessNode the field access node (may be wrapped in <unarni_izraz>)
     * @return the field name (IDN lexeme)
     */
    private String extractFieldAccessFieldName(NonTerminalNode fieldAccessNode) {
        // If wrapped in <unarni_izraz>, unwrap it
        if ("<unarni_izraz>".equals(fieldAccessNode.symbol())) {
            NonTerminalNode child = (NonTerminalNode) fieldAccessNode.children().get(0);
            return extractFieldAccessFieldName(child);
        }
        // Direct field access: <postfiks_izraz> TOCKA IDN
        if ("<postfiks_izraz>".equals(fieldAccessNode.symbol())) {
            ParseNode fieldNode = fieldAccessNode.children().get(2);
            if (fieldNode instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                return terminal.lexeme();
            }
        }
        throw new IllegalStateException("Field access node does not contain IDN: " + fieldAccessNode.symbol());
    }
    
    /**
     * Generates code to assign a value to an lvalue (simple variable only).
     * 
     * @param lvalue the left-hand side expression
     * @param sourceRegister the register containing the value to assign
     */
    public void generateAssignment(NonTerminalNode lvalue, String sourceRegister) {
        generateAssignment(lvalue, sourceRegister, null);
    }
    
    /**
     * Checks if an expression is an array indexing expression.
     * 
     * @param expr the expression to check
     * @return true if the expression is array indexing (a[i])
     */
    public boolean isArrayIndexing(NonTerminalNode expr) {
        return extractArrayIndexingInfo(expr) != null;
    }
    
    /**
     * Information about an array indexing expression.
     */
    public record ArrayIndexingInfo(NonTerminalNode base, NonTerminalNode indexExpr) {}
    
    /**
     * Extracts array indexing information from an expression.
     * 
     * @param expr the expression to extract from
     * @return ArrayIndexingInfo with base and indexExpr, or null if not array indexing
     */
    public ArrayIndexingInfo extractArrayIndexingInfo(NonTerminalNode expr) {
        String symbol = expr.symbol();
        List<ParseNode> children = expr.children();
        
        // Check if this node itself is array indexing: <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
        if ("<postfiks_izraz>".equals(symbol) && children.size() == 4) {
            ParseNode first = children.get(1);
            ParseNode third = children.get(3);
            if (first instanceof TerminalNode leftBracket && "L_UGL_ZAGRADA".equals(leftBracket.symbol()) &&
                third instanceof TerminalNode rightBracket && "D_UGL_ZAGRADA".equals(rightBracket.symbol())) {
                NonTerminalNode base = (NonTerminalNode) children.get(0);
                NonTerminalNode indexExpr = (NonTerminalNode) children.get(2);
                return new ArrayIndexingInfo(base, indexExpr);
            }
        }
        
        // Recursively check children
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                ArrayIndexingInfo info = extractArrayIndexingInfo(nonTerminal);
                if (info != null) {
                    return info;
                }
            }
        }
        
        return null;
    }
}

