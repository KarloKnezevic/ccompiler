package hr.fer.ppj.codegen.expr;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.FriscEmitter;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for expressions.
 * 
 * <p>This class handles the generation of code for all types of expressions
 * in ppjC, including:
 * <ul>
 *   <li>Arithmetic expressions (+, -, *, /, %)</li>
 *   <li>Logical expressions (&&, ||, !)</li>
 *   <li>Relational expressions (<, >, <=, >=, ==, !=)</li>
 *   <li>Bitwise expressions (&, |, ^, ~, <<, >>)</li>
 *   <li>Assignment expressions (=)</li>
 *   <li>Increment/decrement expressions (++, --)</li>
 *   <li>Array access expressions</li>
 *   <li>Function call expressions</li>
 *   <li>Primary expressions (identifiers, constants, parenthesized expressions)</li>
 * </ul>
 * 
 * <p>The generator implements short-circuit evaluation for logical operators
 * and handles type conversions as specified in the PPJ-C semantics.
 * 
 * <p>Expression evaluation results are typically left in register R0, though
 * some complex expressions may use additional registers as temporary storage.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ExpressionCodeGenerator {
    
    private final CodeGenContext context;
    
    /**
     * Creates a new expression code generator.
     * 
     * @param context the code generation context
     */
    public ExpressionCodeGenerator(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }
    
    /**
     * Generates code for an expression, leaving the result in register R0.
     * 
     * @param expression the expression node to generate code for
     */
    public void generateExpression(NonTerminalNode expression) {
        Objects.requireNonNull(expression, "expression must not be null");
        
        String symbol = expression.symbol();
        
        switch (symbol) {
            case "<izraz>" -> generateCommaExpression(expression);
            case "<izraz_pridruzivanja>" -> generateAssignmentExpression(expression);
            case "<log_ili_izraz>" -> generateLogicalOrExpression(expression);
            case "<log_i_izraz>" -> generateLogicalAndExpression(expression);
            case "<bin_ili_izraz>" -> generateBitwiseOrExpression(expression);
            case "<bin_xili_izraz>" -> generateBitwiseXorExpression(expression);
            case "<bin_i_izraz>" -> generateBitwiseAndExpression(expression);
            case "<jednakosni_izraz>" -> generateEqualityExpression(expression);
            case "<odnosni_izraz>" -> generateRelationalExpression(expression);
            case "<aditivni_izraz>" -> generateAdditiveExpression(expression);
            case "<multiplikativni_izraz>" -> generateMultiplicativeExpression(expression);
            case "<cast_izraz>" -> generateCastExpression(expression);
            case "<unarni_izraz>" -> generateUnaryExpression(expression);
            case "<postfiks_izraz>" -> generatePostfixExpression(expression);
            case "<primarni_izraz>" -> generatePrimaryExpression(expression);
            case "<inicijalizator>" -> generateInitializer(expression);
            default -> throw new IllegalArgumentException("Unknown expression type: " + symbol);
        }
    }
    
    /**
     * Generates code for assignment expressions (=).
     */
    private void generateAssignmentExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Assignment: <postfiks_izraz> OP_PRIDRUZI <izraz_pridruzivanja>
            NonTerminalNode lvalue = (NonTerminalNode) children.get(0);
            NonTerminalNode rvalue = (NonTerminalNode) children.get(2);
            
            // Generate code for right-hand side first
            generateExpression(rvalue);
            
            // Generate assignment code
            generateAssignment(lvalue, "R0");
        }
    }
    
    /**
     * Generates code to assign a value to an lvalue.
     * 
     * @param lvalue the left-hand side expression
     * @param sourceRegister the register containing the value to assign
     */
    private void generateAssignment(NonTerminalNode lvalue, String sourceRegister) {
        // For now, handle simple variable assignments
        String variableName = extractVariableName(lvalue);
        
        if (variableName != null) {
            String address = getVariableAddress(variableName);
            context.emitter().emitInstruction("STORE", sourceRegister, address, 
                                            "assign to " + variableName);
        } else {
            context.emitter().emitComment("Complex lvalue assignment (TODO)");
        }
    }
    
    /**
     * Extracts a variable name from a simple lvalue expression.
     * 
     * @param lvalue the lvalue expression
     * @return the variable name, or null if not a simple variable
     */
    private String extractVariableName(NonTerminalNode lvalue) {
        // Navigate through the expression hierarchy to find the identifier
        return findIdentifierInExpression(lvalue);
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
     * Generates code for pre-increment (++var).
     * Returns the new value.
     */
    private void generatePreIncrement(NonTerminalNode operand) {
        String variableName = extractVariableName(operand);
        
        if (variableName != null) {
            String address = getVariableAddress(variableName);
            
            // Load current value
            context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
            // Increment
            context.emitter().emitInstruction("ADD", "R0", "1", "R0", "pre-increment");
            // Store back
            context.emitter().emitInstruction("STORE", "R0", address, "store incremented " + variableName);
            // Result is the new value (already in R0)
        } else {
            context.emitter().emitComment("Complex pre-increment (TODO)");
        }
    }
    
    /**
     * Generates code for pre-decrement (--var).
     * Returns the new value.
     */
    private void generatePreDecrement(NonTerminalNode operand) {
        String variableName = extractVariableName(operand);
        
        if (variableName != null) {
            String address = getVariableAddress(variableName);
            
            // Load current value
            context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
            // Decrement
            context.emitter().emitInstruction("SUB", "R0", "1", "R0", "pre-decrement");
            // Store back
            context.emitter().emitInstruction("STORE", "R0", address, "store decremented " + variableName);
            // Result is the new value (already in R0)
        } else {
            context.emitter().emitComment("Complex pre-decrement (TODO)");
        }
    }
    
    /**
     * Generates code for post-increment (var++).
     * Returns the old value.
     */
    private void generatePostIncrement(NonTerminalNode operand) {
        String variableName = extractVariableName(operand);
        
        if (variableName != null) {
            String address = getVariableAddress(variableName);
            
            // Load current value
            context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
            // Save old value
            context.emitter().emitInstruction("MOVE", "R0", "R1", "save old value");
            // Increment
            context.emitter().emitInstruction("ADD", "R0", "1", "R0", "post-increment");
            // Store new value
            context.emitter().emitInstruction("STORE", "R0", address, "store incremented " + variableName);
            // Return old value
            context.emitter().emitInstruction("MOVE", "R1", "R0", "return old value");
        } else {
            context.emitter().emitComment("Complex post-increment (TODO)");
        }
    }
    
    /**
     * Generates code for post-decrement (var--).
     * Returns the old value.
     */
    private void generatePostDecrement(NonTerminalNode operand) {
        String variableName = extractVariableName(operand);
        
        if (variableName != null) {
            String address = getVariableAddress(variableName);
            
            // Load current value
            context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
            // Save old value
            context.emitter().emitInstruction("MOVE", "R0", "R1", "save old value");
            // Decrement
            context.emitter().emitInstruction("SUB", "R0", "1", "R0", "post-decrement");
            // Store new value
            context.emitter().emitInstruction("STORE", "R0", address, "store decremented " + variableName);
            // Return old value
            context.emitter().emitInstruction("MOVE", "R1", "R0", "return old value");
        } else {
            context.emitter().emitComment("Complex post-decrement (TODO)");
        }
    }
    
    /**
     * Generates code for function calls.
     * 
     * @param function the function expression (should be an identifier)
     * @param arguments the argument list (may be null for no arguments)
     */
    private void generateFunctionCall(NonTerminalNode function, NonTerminalNode arguments) {
        // Extract function name
        String functionName = extractVariableName(function);
        
        if (functionName == null) {
            context.emitter().emitComment("Complex function call (TODO)");
            return;
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
            context.emitter().emitInstruction("ADD", "R7", FriscEmitter.formatHex(stackCleanup), "R7", 
                                            "cleanup " + argumentCount + " arguments");
        }
        
        // Function result is now in R6, move to R0 for expression result
        context.emitter().emitInstruction("MOVE", "R6", "R0", "function result");
    }
    
    /**
     * Generates code for function arguments and returns the number of arguments.
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
            generateExpression(arg);
            context.emitter().emitInstruction("PUSH", "R0", null, "push argument " + (i + 1));
        }
        
        return argumentExpressions.size();
    }
    
    /**
     * Extracts individual argument expressions from the argument list.
     * 
     * @param arguments the argument list node
     * @return list of argument expressions
     */
    private List<NonTerminalNode> extractArgumentExpressions(NonTerminalNode arguments) {
        List<NonTerminalNode> expressions = new java.util.ArrayList<>();
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
     * Generates code for logical OR expressions (||) with short-circuit evaluation.
     */
    private void generateLogicalOrExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Logical OR with short-circuit evaluation
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            var labels = context.labelGenerator().generateShortCircuitLabels();
            
            // Evaluate left operand
            generateExpression(left);
            
            // If left is true (non-zero), skip right operand
            context.emitter().emitInstruction("CMP", "R0", "0", null);
            context.emitter().emitInstruction("JP_NE", labels.trueLabel(), null, "short-circuit: left is true");
            
            // Evaluate right operand
            generateExpression(right);
            
            // Check if right is true
            context.emitter().emitInstruction("CMP", "R0", "0", null);
            context.emitter().emitInstruction("JP_NE", labels.trueLabel(), null, "right is true");
            
            // Both false - result is 0
            context.emitter().emitInstruction("MOVE", "0", "R0", "result is false");
            context.emitter().emitInstruction("JP", labels.endLabel(), null, null);
            
            // At least one true - result is 1
            context.emitter().emitLabel(labels.trueLabel());
            context.emitter().emitInstruction("MOVE", "1", "R0", "result is true");
            
            context.emitter().emitLabel(labels.endLabel());
        }
    }
    
    /**
     * Generates code for logical AND expressions (&&) with short-circuit evaluation.
     */
    private void generateLogicalAndExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Logical AND with short-circuit evaluation
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            var labels = context.labelGenerator().generateShortCircuitLabels();
            
            // Evaluate left operand
            generateExpression(left);
            
            // If left is false (zero), skip right operand
            context.emitter().emitInstruction("CMP", "R0", "0", null);
            context.emitter().emitInstruction("JP_EQ", labels.falseLabel(), null, "short-circuit: left is false");
            
            // Evaluate right operand
            generateExpression(right);
            
            // Check if right is true
            context.emitter().emitInstruction("CMP", "R0", "0", null);
            context.emitter().emitInstruction("JP_EQ", labels.falseLabel(), null, "right is false");
            
            // Both true - result is 1
            context.emitter().emitInstruction("MOVE", "1", "R0", "result is true");
            context.emitter().emitInstruction("JP", labels.endLabel(), null, null);
            
            // At least one false - result is 0
            context.emitter().emitLabel(labels.falseLabel());
            context.emitter().emitInstruction("MOVE", "0", "R0", "result is false");
            
            context.emitter().emitLabel(labels.endLabel());
        }
    }
    
    /**
     * Generates code for additive expressions (+, -).
     */
    private void generateAdditiveExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Binary operation: left op right
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            TerminalNode operator = (TerminalNode) children.get(1);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            // Generate left operand
            generateExpression(left);
            context.emitter().emitInstruction("PUSH", "R0", null, "save left operand");
            
            // Generate right operand
            generateExpression(right);
            context.emitter().emitInstruction("MOVE", "R0", "R1", "right operand to R1");
            context.emitter().emitInstruction("POP", "R0", null, "restore left operand");
            
            // Perform operation
            String op = operator.symbol();
            if ("PLUS".equals(op)) {
                context.emitter().emitInstruction("ADD", "R0", "R1", "R0", "addition");
            } else if ("MINUS".equals(op)) {
                context.emitter().emitInstruction("SUB", "R0", "R1", "R0", "subtraction");
            }
        }
    }
    
    /**
     * Generates code for primary expressions (identifiers, constants, etc.).
     */
    private void generatePrimaryExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        ParseNode child = children.get(0);
        
        if (child instanceof TerminalNode terminal) {
            String symbol = terminal.symbol();
            String value = terminal.lexeme();
            
            switch (symbol) {
                case "BROJ" -> {
                    // Integer constant
                    context.emitter().emitInstruction("MOVE", value, "R0", "load constant " + value);
                }
                case "ZNAK" -> {
                    // Character constant - convert to ASCII value
                    int ascii = value.charAt(1); // Skip the quote
                    context.emitter().emitInstruction("MOVE", String.valueOf(ascii), "R0", "load char '" + value + "'");
                }
                case "IDN" -> {
                    // Identifier - load from local or global variable
                    String address = getVariableAddress(value);
                    context.emitter().emitInstruction("LOAD", "R0", address, "load variable " + value);
                }
                case "NIZ_ZNAKOVA" -> {
                    // String literal - generate label and return address
                    String stringLabel = context.labelGenerator().getUniqueLabel("STR");
                    context.emitter().emitComment("String literal: " + value);
                    context.emitter().emitInstruction("MOVE", stringLabel, "R0", "string address");
                    
                    // Store string data for later generation
                    // For now, just return the label address
                    // TODO: Implement proper string data section
                }
            }
        } else if (child instanceof NonTerminalNode && children.size() == 3) {
            // Parenthesized expression: L_ZAGRADA <izraz> D_ZAGRADA
            generateExpression((NonTerminalNode) children.get(1));
        }
    }
    
    // Bitwise operations implementations
    private void generateBitwiseOrExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Binary bitwise OR: <left> OP_BIN_ILI <right>
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            // Generate left operand
            generateExpression(left);
            context.emitter().emitInstruction("PUSH", "R0", "save left operand");
            
            // Generate right operand
            generateExpression(right);
            context.emitter().emitInstruction("MOVE", "R0", "R1", "prepare right operand");
            context.emitter().emitInstruction("POP", "R0", "restore left operand");
            
            // Perform bitwise OR
            context.emitter().emitInstruction("OR", "R0", "R1", "R0", "bitwise OR");
        }
    }
    
    private void generateBitwiseXorExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Binary bitwise XOR: <left> OP_BIN_XILI <right>
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            // Generate left operand
            generateExpression(left);
            context.emitter().emitInstruction("PUSH", "R0", "save left operand");
            
            // Generate right operand
            generateExpression(right);
            context.emitter().emitInstruction("MOVE", "R0", "R1", "prepare right operand");
            context.emitter().emitInstruction("POP", "R0", "restore left operand");
            
            // Perform bitwise XOR
            context.emitter().emitInstruction("XOR", "R0", "R1", "R0", "bitwise XOR");
        }
    }
    
    private void generateBitwiseAndExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Binary bitwise AND: <left> OP_BIN_I <right>
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            // Generate left operand
            generateExpression(left);
            context.emitter().emitInstruction("PUSH", "R0", "save left operand");
            
            // Generate right operand
            generateExpression(right);
            context.emitter().emitInstruction("MOVE", "R0", "R1", "prepare right operand");
            context.emitter().emitInstruction("POP", "R0", "restore left operand");
            
            // Perform bitwise AND
            context.emitter().emitInstruction("AND", "R0", "R1", "R0", "bitwise AND");
        }
    }
    
    private void generateEqualityExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Binary equality operation
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            TerminalNode operator = (TerminalNode) children.get(1);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            generateBinaryComparison(left, right, operator.symbol());
        }
    }
    
    private void generateRelationalExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Binary relational operation
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            TerminalNode operator = (TerminalNode) children.get(1);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            generateBinaryComparison(left, right, operator.symbol());
        }
    }
    
    /**
     * Generates code for binary comparison operations.
     */
    private void generateBinaryComparison(NonTerminalNode left, NonTerminalNode right, String operator) {
        // Generate left operand
        generateExpression(left);
        context.emitter().emitInstruction("PUSH", "R0", null, "save left operand");
        
        // Generate right operand
        generateExpression(right);
        context.emitter().emitInstruction("MOVE", "R0", "R1", "right operand to R1");
        context.emitter().emitInstruction("POP", "R0", null, "restore left operand");
        
        // Compare operands
        context.emitter().emitInstruction("CMP", "R0", "R1", null);
        
        // Generate conditional result
        var labels = context.labelGenerator().generateShortCircuitLabels();
        
        String jumpCondition = switch (operator) {
            case "OP_EQ" -> "JP_EQ";
            case "OP_NEQ" -> "JP_NE";
            case "OP_LT" -> "JP_SLT";
            case "OP_GT" -> "JP_SGT";
            case "OP_LTE" -> "JP_SLE";
            case "OP_GTE" -> "JP_SGE";
            default -> "JP_EQ"; // fallback
        };
        
        context.emitter().emitInstruction(jumpCondition, labels.trueLabel(), null, 
                                        "comparison " + operator);
        
        // False case
        context.emitter().emitInstruction("MOVE", "0", "R0", "comparison result false");
        context.emitter().emitInstruction("JP", labels.endLabel(), null, null);
        
        // True case
        context.emitter().emitLabel(labels.trueLabel());
        context.emitter().emitInstruction("MOVE", "1", "R0", "comparison result true");
        
        context.emitter().emitLabel(labels.endLabel());
    }
    
    private void generateMultiplicativeExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Binary operation: left op right
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            TerminalNode operator = (TerminalNode) children.get(1);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            // Generate left operand
            generateExpression(left);
            context.emitter().emitInstruction("PUSH", "R0", null, "save left operand");
            
            // Generate right operand
            generateExpression(right);
            context.emitter().emitInstruction("MOVE", "R0", "R1", "right operand to R1");
            context.emitter().emitInstruction("POP", "R0", null, "restore left operand");
            
            // Perform operation
            String op = operator.symbol();
            switch (op) {
                case "OP_PUTA", "ASTERISK" -> {
                    // Multiplication - use simple loop for now (FRISC doesn't have MUL)
                    generateMultiplication();
                }
                case "OP_DIJELI" -> {
                    // Division - use simple loop implementation
                    generateDivision();
                }
                case "OP_MOD" -> {
                    // Modulo - use division with remainder
                    generateModulo();
                }
                default -> {
                    context.emitter().emitComment("Unknown multiplicative operator: " + op);
                }
            }
        }
    }
    
    /**
     * Generates multiplication using repeated addition (simple implementation).
     * R0 = R0 * R1, result in R0
     */
    private void generateMultiplication() {
        var labels = context.labelGenerator().generateLoopLabels();
        
        context.emitter().emitComment("Multiplication: R0 * R1");
        
        // Handle special cases
        context.emitter().emitInstruction("CMP", "R1", "0", null);
        context.emitter().emitInstruction("JP_EQ", labels.breakLabel(), null, "if multiplier is 0");
        
        // Save original values
        context.emitter().emitInstruction("MOVE", "R0", "R2", "save multiplicand");
        context.emitter().emitInstruction("MOVE", "R1", "R3", "save multiplier");
        context.emitter().emitInstruction("MOVE", "0", "R0", "initialize result");
        
        // Multiplication loop: result += multiplicand, multiplier--
        context.emitter().emitLabel(labels.loopLabel(), "multiplication loop");
        context.emitter().emitInstruction("ADD", "R0", "R2", "R0", "add multiplicand to result");
        context.emitter().emitInstruction("SUB", "R3", "1", "R3", "decrement multiplier");
        context.emitter().emitInstruction("CMP", "R3", "0", null);
        context.emitter().emitInstruction("JP_SGT", labels.loopLabel(), "repeat if multiplier > 0");
        
        context.emitter().emitLabel(labels.breakLabel(), "multiplication done");
    }
    
    /**
     * Generates division using repeated subtraction.
     * R0 = R0 / R1, result in R0
     */
    private void generateDivision() {
        var labels = context.labelGenerator().generateLoopLabels();
        String divByZero = context.labelGenerator().generateLabel();
        
        context.emitter().emitComment("Division: R0 / R1");
        
        // Check for division by zero
        context.emitter().emitInstruction("CMP", "R1", "0", null);
        context.emitter().emitInstruction("JP_EQ", divByZero, "division by zero");
        
        // Save dividend and divisor, initialize quotient
        context.emitter().emitInstruction("MOVE", "R0", "R2", "save dividend");
        context.emitter().emitInstruction("MOVE", "R1", "R3", "save divisor");
        context.emitter().emitInstruction("MOVE", "0", "R0", "initialize quotient");
        
        // Division loop
        context.emitter().emitLabel(labels.loopLabel(), "division loop");
        context.emitter().emitInstruction("CMP", "R2", "R3", "compare dividend with divisor");
        context.emitter().emitInstruction("JP_SLT", labels.breakLabel(), "exit if dividend < divisor");
        
        context.emitter().emitInstruction("SUB", "R2", "R3", "R2", "subtract divisor from dividend");
        context.emitter().emitInstruction("ADD", "R0", "1", "R0", "increment quotient");
        context.emitter().emitInstruction("JP", labels.loopLabel(), "continue division");
        
        context.emitter().emitLabel(divByZero, "division by zero");
        context.emitter().emitInstruction("MOVE", "0", "R0", "result 0 for division by zero");
        
        context.emitter().emitLabel(labels.breakLabel(), "end division");
    }
    
    /**
     * Generates modulo using division with remainder.
     * R0 = R0 % R1, result in R0
     */
    private void generateModulo() {
        var labels = context.labelGenerator().generateLoopLabels();
        String modByZero = context.labelGenerator().generateLabel();
        
        context.emitter().emitComment("Modulo: R0 % R1");
        
        // Check for modulo by zero
        context.emitter().emitInstruction("CMP", "R1", "0", null);
        context.emitter().emitInstruction("JP_EQ", modByZero, "modulo by zero");
        
        // Save dividend and divisor
        context.emitter().emitInstruction("MOVE", "R0", "R2", "save dividend");
        context.emitter().emitInstruction("MOVE", "R1", "R3", "save divisor");
        
        // Modulo loop (repeated subtraction until remainder < divisor)
        context.emitter().emitLabel(labels.loopLabel(), "modulo loop");
        context.emitter().emitInstruction("CMP", "R2", "R3", "compare remainder with divisor");
        context.emitter().emitInstruction("JP_SLT", labels.breakLabel(), "exit if remainder < divisor");
        
        context.emitter().emitInstruction("SUB", "R2", "R3", "R2", "subtract divisor from remainder");
        context.emitter().emitInstruction("JP", labels.loopLabel(), "continue modulo");
        
        context.emitter().emitLabel(modByZero, "modulo by zero");
        context.emitter().emitInstruction("MOVE", "0", "R2", "result 0 for modulo by zero");
        
        context.emitter().emitLabel(labels.breakLabel(), "end modulo");
        context.emitter().emitInstruction("MOVE", "R2", "R0", "move remainder to result");
    }
    
    private void generateCastExpression(NonTerminalNode node) {
        // TODO: Implement type casting
        generateExpression((NonTerminalNode) node.children().get(0));
    }
    
    private void generateInitializer(NonTerminalNode node) {
        // <inicijalizator> ::= <izraz_pridruzivanja>
        List<ParseNode> children = node.children();
        if (children.size() == 1) {
            generateExpression((NonTerminalNode) children.get(0));
        } else {
            // Handle array initializers or other complex cases
            context.emitter().emitComment("Complex initializer (TODO)");
            context.emitter().emitInstruction("MOVE", "0", "R0", "default initializer");
        }
    }
    
    private void generateUnaryExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 2) {
            ParseNode first = children.get(0);
            
            if (first instanceof TerminalNode terminal) {
                String operator = terminal.symbol();
                NonTerminalNode operand = (NonTerminalNode) children.get(1);
                
                switch (operator) {
                    case "OP_INC" -> generatePreIncrement(operand);
                    case "OP_DEC" -> generatePreDecrement(operand);
                    case "PLUS" -> {
                        // Unary plus - just evaluate the operand
                        generateExpression(operand);
                    }
                    case "MINUS" -> {
                        // Unary minus - negate the operand
                        generateExpression(operand);
                        context.emitter().emitInstruction("SUB", "0", "R0", "R0", "unary minus");
                    }
                    case "OP_TILDA" -> {
                        // Bitwise NOT
                        generateExpression(operand);
                        context.emitter().emitInstruction("XOR", "R0", "-1", "R0", "bitwise NOT");
                    }
                    case "OP_NEG" -> {
                        // Logical NOT
                        generateExpression(operand);
                        context.emitter().emitInstruction("CMP", "R0", "0", null);
                        
                        var labels = context.labelGenerator().generateShortCircuitLabels();
                        context.emitter().emitInstruction("JP_EQ", labels.trueLabel(), null, "if zero, result is 1");
                        context.emitter().emitInstruction("MOVE", "0", "R0", "result is 0");
                        context.emitter().emitInstruction("JP", labels.endLabel(), null, null);
                        context.emitter().emitLabel(labels.trueLabel());
                        context.emitter().emitInstruction("MOVE", "1", "R0", "result is 1");
                        context.emitter().emitLabel(labels.endLabel());
                    }
                    default -> {
                        context.emitter().emitComment("Unknown unary operator: " + operator);
                        generateExpression(operand);
                    }
                }
            }
        }
    }
    
    private void generatePostfixExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 2) {
            NonTerminalNode operand = (NonTerminalNode) children.get(0);
            ParseNode operator = children.get(1);
            
            if (operator instanceof TerminalNode terminal) {
                String op = terminal.symbol();
                
                switch (op) {
                    case "OP_INC" -> generatePostIncrement(operand);
                    case "OP_DEC" -> generatePostDecrement(operand);
                    default -> {
                        context.emitter().emitComment("Unknown postfix operator: " + op);
                        generateExpression(operand);
                    }
                }
            }
        } else if (children.size() == 3) {
            // Function call without arguments: <postfiks_izraz> L_ZAGRADA D_ZAGRADA
            NonTerminalNode function = (NonTerminalNode) children.get(0);
            ParseNode leftParen = children.get(1);
            ParseNode rightParen = children.get(2);
            
            if (leftParen instanceof TerminalNode leftTerm && "L_ZAGRADA".equals(leftTerm.symbol()) &&
                rightParen instanceof TerminalNode rightTerm && "D_ZAGRADA".equals(rightTerm.symbol())) {
                
                generateFunctionCall(function, null);
            } else {
                context.emitter().emitComment("Complex postfix expression (TODO)");
                generateExpression(function);
            }
        } else if (children.size() == 4) {
            // Function call with arguments: <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
            NonTerminalNode function = (NonTerminalNode) children.get(0);
            ParseNode leftParen = children.get(1);
            NonTerminalNode arguments = (NonTerminalNode) children.get(2);
            ParseNode rightParen = children.get(3);
            
            if (leftParen instanceof TerminalNode leftTerm && "L_ZAGRADA".equals(leftTerm.symbol()) &&
                rightParen instanceof TerminalNode rightTerm && "D_ZAGRADA".equals(rightTerm.symbol())) {
                
                generateFunctionCall(function, arguments);
            } else {
                context.emitter().emitComment("Complex postfix expression (TODO)");
                generateExpression(function);
            }
        } else {
            // Handle other complex postfix expressions
            context.emitter().emitComment("Complex postfix expression (TODO)");
            generateExpression((NonTerminalNode) children.get(0));
        }
    }
    
    /**
     * Generates code for comma expressions.
     */
    private void generateCommaExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single expression - delegate to assignment expression
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Comma expression: <izraz> ZAREZ <izraz_pridruzivanja>
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            // Evaluate left expression (result is discarded)
            generateExpression(left);
            
            // Evaluate right expression (result is kept)
            generateExpression(right);
        }
    }
}
