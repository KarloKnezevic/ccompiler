package hr.fer.ppj.codegen.expr.binary;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for binary expressions.
 * 
 * <p>This class handles the generation of code for binary operations including:
 * <ul>
 *   <li>Arithmetic operations (+, -, *, /, %)</li>
 *   <li>Bitwise operations (&, |, ^)</li>
 *   <li>Relational operations (<, >, <=, >=)</li>
 *   <li>Equality operations (==, !=)</li>
 * </ul>
 * 
 * <p>All binary operations follow a common pattern:
 * <ol>
 *   <li>Generate code for left operand, leaving result in R0</li>
 *   <li>Push R0 to stack to save left operand</li>
 *   <li>Generate code for right operand, leaving result in R0</li>
 *   <li>Move R0 to R1, pop left operand back to R0</li>
 *   <li>Perform the operation on R0 and R1, leaving result in R0</li>
 * </ol>
 * 
 * <p>For multiplication and division, helper functions F_MUL and F_DIV are called
 * since FRISC architecture doesn't have native MUL/DIV instructions.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BinaryExpressionGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    
    /**
     * Creates a new binary expression generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the main expression generator for recursive calls
     */
    public BinaryExpressionGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    }
    
    /**
     * Generates code for additive expressions (+, -).
     * 
     * @param node the additive expression node
     */
    public void generateAdditiveExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Binary operation: left op right
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            TerminalNode operator = (TerminalNode) children.get(1);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            generateBinaryOperation(left, right, operator.symbol());
        }
    }
    
    /**
     * Generates code for multiplicative expressions (*, /, %).
     * 
     * @param node the multiplicative expression node
     */
    public void generateMultiplicativeExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Binary operation: left op right
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            TerminalNode operator = (TerminalNode) children.get(1);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            String op = operator.symbol();
            switch (op) {
                case "OP_PUTA", "ASTERISK" -> generateMultiplication(left, right);
                case "OP_DIJELI" -> generateDivision(left, right);
                case "OP_MOD" -> generateModulo(left, right);
                default -> context.emitter().emitComment("Unknown multiplicative operator: " + op);
            }
        }
    }
    
    /**
     * Generates code for bitwise OR expressions (|).
     * 
     * @param node the bitwise OR expression node
     */
    public void generateBitwiseOrExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            generateBinaryOperation(left, right, "OR");
        }
    }
    
    /**
     * Generates code for bitwise XOR expressions (^).
     * 
     * @param node the bitwise XOR expression node
     */
    public void generateBitwiseXorExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            generateBinaryOperation(left, right, "XOR");
        }
    }
    
    /**
     * Generates code for bitwise AND expressions (&).
     * 
     * @param node the bitwise AND expression node
     */
    public void generateBitwiseAndExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            generateBinaryOperation(left, right, "AND");
        }
    }
    
    /**
     * Generates code for equality expressions (==, !=).
     * 
     * @param node the equality expression node
     */
    public void generateEqualityExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            TerminalNode operator = (TerminalNode) children.get(1);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            generateBinaryComparison(left, right, operator.symbol());
        }
    }
    
    /**
     * Generates code for relational expressions (<, >, <=, >=).
     * 
     * @param node the relational expression node
     */
    public void generateRelationalExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            TerminalNode operator = (TerminalNode) children.get(1);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            generateBinaryComparison(left, right, operator.symbol());
        }
    }
    
    /**
     * Generates code for a generic binary operation.
     * 
     * <p>This is a helper method that implements the common pattern for binary operations:
     * evaluate left operand, save it, evaluate right operand, perform operation.
     * 
     * @param left the left operand expression
     * @param right the right operand expression
     * @param operation the operation to perform (e.g., "ADD", "SUB", "OR", "XOR", "AND")
     */
    private void generateBinaryOperation(NonTerminalNode left, NonTerminalNode right, String operation) {
        // Generate left operand
        expressionGenerator.generateExpression(left);
        context.emitter().emitInstruction("PUSH", "R0", null, "save left operand");
        
        // Generate right operand
        expressionGenerator.generateExpression(right);
        context.emitter().emitInstruction("MOVE", "R0", "R1", "right operand to R1");
        context.emitter().emitInstruction("POP", "R0", null, "restore left operand");
        
        // Perform operation
        if ("PLUS".equals(operation)) {
            context.emitter().emitInstruction("ADD", "R0", "R1", "R0", "addition");
        } else if ("MINUS".equals(operation)) {
            context.emitter().emitInstruction("SUB", "R0", "R1", "R0", "subtraction");
        } else {
            context.emitter().emitInstruction(operation, "R0", "R1", "R0", operation.toLowerCase());
        }
    }
    
    /**
     * Generates code for binary comparison operations (==, !=, <, >, <=, >=).
     * 
     * <p>Comparison operations return 1 (true) or 0 (false) in R0.
     * The comparison is performed using CMP instruction, followed by
     * conditional jumps to set the result.
     * 
     * @param left the left operand expression
     * @param right the right operand expression
     * @param operator the comparison operator (OP_EQ, OP_NEQ, OP_LT, OP_GT, OP_LTE, OP_GTE)
     */
    private void generateBinaryComparison(NonTerminalNode left, NonTerminalNode right, String operator) {
        // Generate left operand
        expressionGenerator.generateExpression(left);
        context.emitter().emitInstruction("PUSH", "R0", null, "save left operand");
        
        // Generate right operand
        expressionGenerator.generateExpression(right);
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
        
        // False case (decimal 0)
        context.emitter().emitInstruction("MOVE", "%D 0", "R0", "comparison result false");
        context.emitter().emitInstruction("JP", labels.endLabel(), null, null);
        
        // True case (decimal 1)
        context.emitter().emitLabel(labels.trueLabel());
        context.emitter().emitInstruction("MOVE", "%D 1", "R0", "comparison result true");
        
        context.emitter().emitLabel(labels.endLabel());
    }
    
    /**
     * Generates code for multiplication using the F_MUL helper function.
     * 
     * @param left the left operand (multiplicand)
     * @param right the right operand (multiplier)
     */
    private void generateMultiplication(NonTerminalNode left, NonTerminalNode right) {
        // Generate left operand
        expressionGenerator.generateExpression(left);
        context.emitter().emitInstruction("PUSH", "R0", null, "save left operand");
        
        // Generate right operand
        expressionGenerator.generateExpression(right);
        context.emitter().emitInstruction("MOVE", "R0", "R1", "right operand to R1");
        context.emitter().emitInstruction("POP", "R0", null, "restore left operand");
        
        // Mark that F_MUL helper is needed
        context.emitter().markMulNeeded();
        
        // Push arguments (right, then left), call F_MUL, clean up
        context.emitter().emitInstruction("PUSH", "R1", null, "push second arg (right operand)");
        context.emitter().emitInstruction("PUSH", "R0", null, "push first arg (left operand)");
        context.emitter().emitInstruction("CALL", "F_MUL", null, "call multiplication helper");
        context.emitter().emitInstruction("ADD", "R7", "%D 8", "R7", "clean up arguments");
        
        // Result is in R6, move to R0
        context.emitter().emitInstruction("MOVE", "R6", "R0", "move result to R0");
    }
    
    /**
     * Generates code for division using the F_DIV helper function.
     * 
     * @param left the left operand (dividend)
     * @param right the right operand (divisor)
     */
    private void generateDivision(NonTerminalNode left, NonTerminalNode right) {
        // Generate left operand
        expressionGenerator.generateExpression(left);
        context.emitter().emitInstruction("PUSH", "R0", null, "save left operand");
        
        // Generate right operand
        expressionGenerator.generateExpression(right);
        context.emitter().emitInstruction("MOVE", "R0", "R1", "right operand to R1");
        context.emitter().emitInstruction("POP", "R0", null, "restore left operand");
        
        // Mark that F_DIV helper is needed
        context.emitter().markDivNeeded();
        
        // Push arguments (right, then left), call F_DIV, clean up
        context.emitter().emitInstruction("PUSH", "R1", null, "push second arg (divisor)");
        context.emitter().emitInstruction("PUSH", "R0", null, "push first arg (dividend)");
        context.emitter().emitInstruction("CALL", "F_DIV", null, "call division helper");
        context.emitter().emitInstruction("ADD", "R7", "%D 8", "R7", "clean up arguments");
        
        // Result is in R6, move to R0
        context.emitter().emitInstruction("MOVE", "R6", "R0", "move result to R0");
    }
    
    /**
     * Generates code for modulo operation using repeated subtraction.
     * 
     * <p>Modulo is computed as: dividend - (dividend / divisor) * divisor
     * or using repeated subtraction until remainder < divisor.
     * 
     * @param left the left operand (dividend)
     * @param right the right operand (divisor)
     */
    private void generateModulo(NonTerminalNode left, NonTerminalNode right) {
        // Generate left operand
        expressionGenerator.generateExpression(left);
        context.emitter().emitInstruction("PUSH", "R0", null, "save left operand");
        
        // Generate right operand
        expressionGenerator.generateExpression(right);
        context.emitter().emitInstruction("MOVE", "R0", "R1", "right operand to R1");
        context.emitter().emitInstruction("POP", "R0", null, "restore left operand");
        
        var labels = context.labelGenerator().generateLoopLabels();
        String modByZero = context.labelGenerator().generateLabel();
        
        context.emitter().emitComment("Modulo: R0 % R1");
        
        // Check for modulo by zero
        context.emitter().emitInstruction("CMP", "R1", "%D 0", null);
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
        context.emitter().emitInstruction("MOVE", "%D 0", "R2", "result 0 for modulo by zero");
        
        context.emitter().emitLabel(labels.breakLabel(), "end modulo");
        context.emitter().emitInstruction("MOVE", "R2", "R0", "move remainder to result");
    }
}

