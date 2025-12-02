package hr.fer.ppj.codegen.expr;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.array.ArrayExpressionGenerator;
import hr.fer.ppj.codegen.expr.assignment.AssignmentExpressionGenerator;
import hr.fer.ppj.codegen.expr.binary.BinaryExpressionGenerator;
import hr.fer.ppj.codegen.expr.call.FunctionCallGenerator;
import hr.fer.ppj.codegen.expr.logical.LogicalExpressionGenerator;
import hr.fer.ppj.codegen.expr.primary.PrimaryExpressionGenerator;
import hr.fer.ppj.codegen.expr.unary.UnaryExpressionGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
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
    private final BinaryExpressionGenerator binaryGenerator;
    private final LogicalExpressionGenerator logicalGenerator;
    private final PrimaryExpressionGenerator primaryGenerator;
    private final UnaryExpressionGenerator unaryGenerator;
    private final AssignmentExpressionGenerator assignmentGenerator;
    private final ArrayExpressionGenerator arrayGenerator;
    private final FunctionCallGenerator functionCallGenerator;
    
    /**
     * Creates a new expression code generator.
     * 
     * <p>This class orchestrates code generation for all expression types by delegating
     * to specialized generators for different expression categories.
     * 
     * @param context the code generation context
     */
    public ExpressionCodeGenerator(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        
        // Initialize generators (order matters due to dependencies)
        this.primaryGenerator = new PrimaryExpressionGenerator(context, this);
        this.unaryGenerator = new UnaryExpressionGenerator(context, this);
        this.assignmentGenerator = new AssignmentExpressionGenerator(context, this);
        this.arrayGenerator = new ArrayExpressionGenerator(context, this, assignmentGenerator);
        this.functionCallGenerator = new FunctionCallGenerator(context, this);
        this.binaryGenerator = new BinaryExpressionGenerator(context, this);
        this.logicalGenerator = new LogicalExpressionGenerator(context, this);
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
            case "<izraz_pridruzivanja>" -> assignmentGenerator.generateAssignmentExpression(expression);
            case "<log_ili_izraz>" -> logicalGenerator.generateLogicalOrExpression(expression);
            case "<log_i_izraz>" -> logicalGenerator.generateLogicalAndExpression(expression);
            case "<bin_ili_izraz>" -> binaryGenerator.generateBitwiseOrExpression(expression);
            case "<bin_xili_izraz>" -> binaryGenerator.generateBitwiseXorExpression(expression);
            case "<bin_i_izraz>" -> binaryGenerator.generateBitwiseAndExpression(expression);
            case "<jednakosni_izraz>" -> binaryGenerator.generateEqualityExpression(expression);
            case "<odnosni_izraz>" -> binaryGenerator.generateRelationalExpression(expression);
            case "<aditivni_izraz>" -> binaryGenerator.generateAdditiveExpression(expression);
            case "<multiplikativni_izraz>" -> binaryGenerator.generateMultiplicativeExpression(expression);
            case "<cast_izraz>" -> unaryGenerator.generateCastExpression(expression);
            case "<unarni_izraz>" -> generateUnaryExpression(expression);
            case "<postfiks_izraz>" -> generatePostfixExpression(expression);
            case "<primarni_izraz>" -> primaryGenerator.generatePrimaryExpression(expression);
            case "<inicijalizator>" -> generateInitializer(expression);
            default -> throw new IllegalArgumentException("Unknown expression type: " + symbol);
        }
    }
    
    
    
    private void generateInitializer(NonTerminalNode node) {
        // <inicijalizator> ::= <izraz_pridruzivanja>
        List<ParseNode> children = node.children();
        if (children.size() == 1) {
            generateExpression((NonTerminalNode) children.get(0));
        } else {
            // Handle array initializers or other complex cases
            // For array initializers, we'd need to generate code to initialize each element
            // For now, default to 0
            context.emitter().emitInstruction("MOVE", "%D 0", "R0", "default initializer");
        }
    }
    
    /**
     * Generates code for unary expressions (handles pre-increment/decrement).
     * 
     * <p>Unary expressions can be:
     * <ul>
     *   <li>Single operand (delegates to next level)</li>
     *   <li>Unary operator followed by operand: OP <operand></li>
     * </ul>
     * 
     * <p>Note: Pre-increment/decrement are handled here, while other unary operators
     * are delegated to UnaryExpressionGenerator.
     * 
     * @param node the unary expression node
     */
    private void generateUnaryExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 2) {
            ParseNode first = children.get(0);
            
            if (first instanceof hr.fer.ppj.semantics.tree.TerminalNode terminal) {
                String operator = terminal.symbol();
                NonTerminalNode operand = (NonTerminalNode) children.get(1);
                
                // Pre-increment/decrement are handled by assignment generator
                if ("OP_INC".equals(operator)) {
                    assignmentGenerator.generatePreIncrement(operand);
                } else if ("OP_DEC".equals(operator)) {
                    assignmentGenerator.generatePreDecrement(operand);
                } else {
                    // Other unary operators are handled by unary generator
                    // Delegate to unary generator for +, -, !, ~
                    unaryGenerator.generateUnaryExpression(node);
                }
            }
        }
    }
    
    /**
     * Generates code for postfix expressions.
     * 
     * <p>Postfix expressions include:
     * <ul>
     *   <li>Post-increment/decrement (var++, var--)</li>
     *   <li>Function calls (with or without arguments)</li>
     *   <li>Array indexing (a[i])</li>
     * </ul>
     * 
     * @param node the postfix expression node
     */
    private void generatePostfixExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 2) {
            NonTerminalNode operand = (NonTerminalNode) children.get(0);
            ParseNode operator = children.get(1);
            
            if (operator instanceof hr.fer.ppj.semantics.tree.TerminalNode terminal) {
                String op = terminal.symbol();
                
                switch (op) {
                    case "OP_INC" -> assignmentGenerator.generatePostIncrement(operand);
                    case "OP_DEC" -> assignmentGenerator.generatePostDecrement(operand);
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
            
            if (leftParen instanceof hr.fer.ppj.semantics.tree.TerminalNode leftTerm && "L_ZAGRADA".equals(leftTerm.symbol()) &&
                rightParen instanceof hr.fer.ppj.semantics.tree.TerminalNode rightTerm && "D_ZAGRADA".equals(rightTerm.symbol())) {
                
                functionCallGenerator.generateFunctionCall(function, null);
            } else {
                // Complex postfix expression - fall back to evaluating the base expression
                generateExpression(function);
            }
        } else if (children.size() == 4) {
            ParseNode first = children.get(1);
            ParseNode second = children.get(2);
            ParseNode third = children.get(3);
            
            // Check for array indexing: <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
            if (first instanceof hr.fer.ppj.semantics.tree.TerminalNode leftBracket && "L_UGL_ZAGRADA".equals(leftBracket.symbol()) &&
                second instanceof NonTerminalNode indexExpr &&
                third instanceof hr.fer.ppj.semantics.tree.TerminalNode rightBracket && "D_UGL_ZAGRADA".equals(rightBracket.symbol())) {
                
                arrayGenerator.generateArrayIndexing((NonTerminalNode) children.get(0), indexExpr);
            }
            // Check for function call with arguments: <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
            else if (first instanceof hr.fer.ppj.semantics.tree.TerminalNode leftParen && "L_ZAGRADA".equals(leftParen.symbol()) &&
                     second instanceof NonTerminalNode arguments &&
                     third instanceof hr.fer.ppj.semantics.tree.TerminalNode rightParen && "D_ZAGRADA".equals(rightParen.symbol())) {
                
                functionCallGenerator.generateFunctionCall((NonTerminalNode) children.get(0), arguments);
            } else {
                // Complex postfix expression - fall back to evaluating the base expression
                generateExpression((NonTerminalNode) children.get(0));
            }
        } else {
            // Handle other complex postfix expressions - fall back to evaluating the base expression
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
