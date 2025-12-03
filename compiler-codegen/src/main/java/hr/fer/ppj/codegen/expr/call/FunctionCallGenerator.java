package hr.fer.ppj.codegen.expr.call;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for function calls.
 * 
 * <p>This class handles the generation of code for function calls including:
 * <ul>
 *   <li>Function calls with arguments</li>
 *   <li>Function calls without arguments</li>
 *   <li>Argument passing (right-to-left on stack)</li>
 *   <li>Array arguments (passed as addresses)</li>
 * </ul>
 * 
 * <p>Function calls follow the FRISC calling convention:
 * <ol>
 *   <li>Push arguments onto stack (right-to-left order)</li>
 *   <li>Call the function</li>
 *   <li>Clean up arguments from stack (caller cleans up)</li>
 *   <li>Result is in R6, move to R0</li>
 * </ol>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionCallGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    
    /**
     * Creates a new function call generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the main expression generator for recursive calls
     */
    public FunctionCallGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    }
    
    /**
     * Generates code for a function call.
     * 
     * @param function the function expression (should be an identifier)
     * @param arguments the argument list (may be null for no arguments)
     */
    public void generateFunctionCall(NonTerminalNode function, NonTerminalNode arguments) {
        // Extract function name
        String functionName = extractVariableName(function);
        
        if (functionName == null) {
            // Complex function call - function pointer or complex expression
            throw new IllegalStateException("Complex function call (function pointer) not supported in this subset");
        }
        
        context.emitter().emitComment("Function call: " + functionName);
        
        // Generate arguments and push them onto the stack
        int argumentCount = 0;
        if (arguments != null) {
            argumentCount = generateFunctionArguments(arguments);
        }
        
        // Generate the function call
        String functionLabel = context.labelGenerator().getFunctionLabel(functionName);
        context.emitter().emitInstruction("CALL", functionLabel, null, "call " + functionName);
        
        // Clean up arguments from the stack (caller cleans up)
        if (argumentCount > 0) {
            int stackCleanup = argumentCount * 4; // Each argument is 4 bytes
            context.emitter().emitInstruction("ADD", "R7", "%D " + stackCleanup, "R7", 
                                            "cleanup " + argumentCount + " arguments");
        }
        
        // Function result is now in R6, move to R0 for expression result
        context.emitter().emitInstruction("MOVE", "R6", "R0", "function result");
    }
    
    /**
     * Generates code for function arguments and returns the number of arguments.
     * 
     * <p>Arguments are pushed onto the stack in right-to-left order (last argument first).
     * Array variables are passed as addresses rather than values.
     * 
     * @param arguments the argument list node
     * @return the number of arguments processed
     */
    private int generateFunctionArguments(NonTerminalNode arguments) {
        List<NonTerminalNode> argumentExpressions = extractArgumentExpressions(arguments);
        
        // Generate arguments in reverse order (last argument pushed first)
        // This way, the first argument will be at the top of the stack
        for (int i = argumentExpressions.size() - 1; i >= 0; i--) {
            NonTerminalNode arg = argumentExpressions.get(i);
            
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
                        // Fallback: generate expression normally
                        expressionGenerator.generateExpression(arg);
                    }
                }
            } else {
                // Normal expression - generate normally
                expressionGenerator.generateExpression(arg);
            }
            
            context.emitter().emitInstruction("PUSH", "R0", null, "push argument " + (i + 1));
        }
        
        return argumentExpressions.size();
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
        // Use assignment generator's method
        return findIdentifierInExpression(expr);
    }
    
    /**
     * Recursively searches for an identifier in an expression.
     */
    private String findIdentifierInExpression(NonTerminalNode node) {
        for (ParseNode child : node.children()) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal) {
                String result = findIdentifierInExpression(nonTerminal);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
    
    /**
     * Gets the FRISC address for a variable (local or global).
     * 
     * @param variableName the variable name
     * @return the FRISC address expression
     */
    private String getVariableAddress(String variableName) {
        // Check if we're in a function and the variable is local
        if (context.isInFunction() && context.activationRecord().hasVariable(variableName)) {
            return context.activationRecord().getVariableAddress(variableName);
        } else {
            // Global variable
            String label = context.labelGenerator().getGlobalVariableLabel(variableName);
            return "(" + label + ")";
        }
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

