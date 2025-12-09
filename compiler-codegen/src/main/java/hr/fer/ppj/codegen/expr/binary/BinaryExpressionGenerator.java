package hr.fer.ppj.codegen.expr.binary;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.codegen.types.TypeExtractor;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for binary expressions.
 * 
 * <p>This class orchestrates the generation of code for binary operations by delegating
 * to specialized generators:
 * <ul>
 *   <li>{@link ArithmeticOperationGenerator} - arithmetic operations (+, -, *, /, %)</li>
 *   <li>{@link BitwiseOperationGenerator} - bitwise operations (&, |, ^)</li>
 *   <li>{@link ComparisonOperationGenerator} - relational and equality operations</li>
 *   <li>{@link FloatOperationGenerator} - float operations</li>
 * </ul>
 * 
 * <p>This class handles the grammar-level dispatch and type checking, delegating
 * the actual code generation to specialized generators.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class BinaryExpressionGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    private final ArithmeticOperationGenerator arithmeticGenerator;
    private final BitwiseOperationGenerator bitwiseGenerator;
    private final ComparisonOperationGenerator comparisonGenerator;
    private final FloatOperationGenerator floatGenerator;
    
    /**
     * Creates a new binary expression generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the main expression generator for recursive calls
     */
    public BinaryExpressionGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
        
        // Initialize specialized generators
        this.arithmeticGenerator = new ArithmeticOperationGenerator(context, expressionGenerator);
        this.bitwiseGenerator = new BitwiseOperationGenerator(context, expressionGenerator);
        this.comparisonGenerator = new ComparisonOperationGenerator(context, expressionGenerator);
        this.floatGenerator = new FloatOperationGenerator(context, expressionGenerator);
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
            
            // Check if result type is float
            var resultType = TypeExtractor.getExpressionType(node);
            if (resultType == PrimitiveType.FLOAT) {
                floatGenerator.generateAdditiveOperation(left, right, operator.symbol());
            } else {
                String op = operator.symbol();
                if ("PLUS".equals(op)) {
                    arithmeticGenerator.generateAddition(left, right);
                } else if ("MINUS".equals(op)) {
                    arithmeticGenerator.generateSubtraction(left, right);
                }
            }
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
            
            // Check if result type is float
            var resultType = TypeExtractor.getExpressionType(node);
            String op = operator.symbol();
            
            if (resultType == PrimitiveType.FLOAT) {
                // Float multiplication or division
                if ("OP_PUTA".equals(op) || "ASTERISK".equals(op)) {
                    floatGenerator.generateMultiplication(left, right);
                } else if ("OP_DIJELI".equals(op)) {
                    floatGenerator.generateDivision(left, right);
                } else {
                    // Modulo not supported for floats
                    context.emitter().emitComment("ERROR: Modulo not supported for floats");
                }
            } else {
                // Integer operations
                switch (op) {
                    case "OP_PUTA", "ASTERISK" -> arithmeticGenerator.generateMultiplication(left, right);
                    case "OP_DIJELI" -> arithmeticGenerator.generateDivision(left, right);
                    case "OP_MOD" -> arithmeticGenerator.generateModulo(left, right);
                    default -> context.emitter().emitComment("Unknown multiplicative operator: " + op);
                }
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
            
            bitwiseGenerator.generateBitwiseOr(left, right);
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
            
            bitwiseGenerator.generateBitwiseXor(left, right);
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
            
            bitwiseGenerator.generateBitwiseAnd(left, right);
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
            
            // Check if operands are floats
            var leftType = TypeExtractor.getExpressionType(left);
            var rightType = TypeExtractor.getExpressionType(right);
            
            if (leftType == PrimitiveType.FLOAT || rightType == PrimitiveType.FLOAT) {
                floatGenerator.generateComparison(left, right, operator.symbol());
            } else {
                comparisonGenerator.generateComparison(left, right, operator.symbol());
            }
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
            
            // Check if operands are floats
            var leftType = TypeExtractor.getExpressionType(left);
            var rightType = TypeExtractor.getExpressionType(right);
            
            if (leftType == PrimitiveType.FLOAT || rightType == PrimitiveType.FLOAT) {
                floatGenerator.generateComparison(left, right, operator.symbol());
            } else {
                comparisonGenerator.generateComparison(left, right, operator.symbol());
            }
        }
    }
}

