package hr.fer.ppj.codegen.expr.unary;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for unary expressions and type casts.
 * 
 * <p>This class handles the generation of code for:
 * <ul>
 *   <li>Unary operators: +, -, !, ~</li>
 *   <li>Type casts: (type) expression</li>
 * </ul>
 * 
 * <p>Unary operators operate on a single operand and produce a result in R0.
 * Type casts convert values between types (e.g., int to char by masking).
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class UnaryExpressionGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    
    /**
     * Creates a new unary expression generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the main expression generator for recursive calls
     */
    public UnaryExpressionGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    }
    
    /**
     * Generates code for unary expressions.
     * 
     * <p>Unary expressions can be:
     * <ul>
     *   <li>Single operand (delegates to next level)</li>
     *   <li>Unary operator followed by operand: OP <operand></li>
     * </ul>
     * 
     * @param node the unary expression node
     */
    public void generateUnaryExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 2) {
            ParseNode first = children.get(0);
            
            if (first instanceof TerminalNode terminal) {
                String operator = terminal.symbol();
                NonTerminalNode operand = (NonTerminalNode) children.get(1);
                
                switch (operator) {
                    case "PLUS" -> {
                        // Unary plus - just evaluate the operand
                        expressionGenerator.generateExpression(operand);
                    }
                    case "MINUS" -> generateUnaryMinus(operand);
                    case "OP_TILDA" -> generateBitwiseNot(operand);
                    case "OP_NEG" -> generateLogicalNot(operand);
                    default -> {
                        context.emitter().emitComment("Unknown unary operator: " + operator);
                        expressionGenerator.generateExpression(operand);
                    }
                }
            }
        }
    }
    
    /**
     * Generates code for type cast expressions.
     * 
     * <p>Cast expressions have the form: L_ZAGRADA <ime_tipa> D_ZAGRADA <cast_izraz>
     * 
     * @param node the cast expression node
     */
    public void generateCastExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // No actual cast, just delegate to inner expression
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
            return;
        }
        
        // Cast expression: L_ZAGRADA <ime_tipa> D_ZAGRADA <cast_izraz>
        // Structure: children[0] = L_ZAGRADA, children[1] = <ime_tipa>, children[2] = D_ZAGRADA, children[3] = <cast_izraz>
        NonTerminalNode typeNode = (NonTerminalNode) children.get(1);
        NonTerminalNode exprNode = (NonTerminalNode) children.get(3);
        
        // Generate code for the inner expression (result in R0)
        expressionGenerator.generateExpression(exprNode);
        
        // Get target type from semantic attributes (if available)
        Type targetType = extractTypeFromTypeNode(typeNode);
        
        // Perform cast based on target type
        if (targetType == PrimitiveType.CHAR) {
            // Cast to char: mask lower 8 bits
            // Use hex format for bit mask: 00FF (not %D 255, as this is a bit mask, not a C integer literal)
            context.emitter().emitInstruction("AND", "R0", "00FF", "R0", "cast to char (mask lower 8 bits)");
        } else if (targetType == PrimitiveType.INT) {
            // Cast to int: no-op (value is already int)
            // R0 already contains the value
        }
        // Unknown cast type: just pass through (R0 already contains the value)
    }
    
    /**
     * Generates code for unary minus (-operand).
     * 
     * <p>Optimizes for integer literals by directly emitting the negative value.
     * For non-literals, performs runtime negation: 0 - R0 = -R0.
     * 
     * @param operand the operand expression
     */
    private void generateUnaryMinus(NonTerminalNode operand) {
        String literalValue = tryExtractIntegerLiteral(operand);
        if (literalValue != null) {
            // Optimize: directly emit negative literal
            try {
                int value = Integer.parseInt(literalValue);
                context.emitter().emitInstruction("MOVE", "%D " + (-value), "R0", "load constant -" + literalValue);
            } catch (NumberFormatException e) {
                // Fall back to runtime negation
                generateRuntimeNegation(operand);
            }
        } else {
            // Runtime negation: 0 - R0 = -R0
            generateRuntimeNegation(operand);
        }
    }
    
    /**
     * Generates code for runtime negation of an expression.
     * 
     * @param operand the operand expression
     */
    private void generateRuntimeNegation(NonTerminalNode operand) {
        expressionGenerator.generateExpression(operand);
        context.emitter().emitInstruction("MOVE", "%D 0", "R1", "zero for negation");
        context.emitter().emitInstruction("SUB", "R1", "R0", "R0", "unary minus");
    }
    
    /**
     * Generates code for bitwise NOT (~operand).
     * 
     * <p>Bitwise NOT is implemented as XOR with all 1s (0xFFFFFFFF).
     * 
     * @param operand the operand expression
     */
    private void generateBitwiseNot(NonTerminalNode operand) {
        expressionGenerator.generateExpression(operand);
        // Use hex format for bit mask: FFFFFFFF
        context.emitter().emitInstruction("XOR", "R0", "FFFFFFFF", "R0", "bitwise NOT");
    }
    
    /**
     * Generates code for logical NOT (!operand).
     * 
     * <p>Logical NOT returns 1 if operand is zero, 0 otherwise.
     * 
     * @param operand the operand expression
     */
    private void generateLogicalNot(NonTerminalNode operand) {
        expressionGenerator.generateExpression(operand);
        context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
        
        var labels = context.labelGenerator().generateShortCircuitLabels();
        context.emitter().emitInstruction("JP_EQ", labels.trueLabel(), null, "if zero, result is 1");
        context.emitter().emitInstruction("MOVE", "%D 0", "R0", "result is 0");
        context.emitter().emitInstruction("JP", labels.endLabel(), null, null);
        
        context.emitter().emitLabel(labels.trueLabel());
        context.emitter().emitInstruction("MOVE", "%D 1", "R0", "result is 1");
        
        context.emitter().emitLabel(labels.endLabel());
    }
    
    /**
     * Tries to extract an integer literal value from an expression.
     * 
     * <p>This is used for optimization - if the operand is a constant,
     * we can directly emit the negated value instead of computing it at runtime.
     * 
     * @param expr the expression node
     * @return the literal value as a string, or null if not a literal
     */
    private String tryExtractIntegerLiteral(NonTerminalNode expr) {
        // Recursively search for a BROJ terminal
        for (ParseNode child : expr.children()) {
            if (child instanceof TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal) {
                String result = tryExtractIntegerLiteral(nonTerminal);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
    
    /**
     * Extracts the target type from a type node (<ime_tipa>).
     * 
     * @param typeNode the type node
     * @return the target type, or null if not determinable
     */
    private Type extractTypeFromTypeNode(NonTerminalNode typeNode) {
        // Try to get type from semantic attributes first
        if (typeNode.attributes() != null && typeNode.attributes().type() != null) {
            return typeNode.attributes().type();
        }
        
        // Fallback: parse from tree structure
        // <ime_tipa> -> <specifikator_tipa>
        // <specifikator_tipa> -> KR_CHAR or KR_INT
        return extractTypeFromSpecifikatorTipa(typeNode);
    }
    
    /**
     * Extracts type from specifikator_tipa node by looking for KR_CHAR or KR_INT.
     * 
     * @param node the node to search
     * @return the type, or null if not found
     */
    private Type extractTypeFromSpecifikatorTipa(NonTerminalNode node) {
        // Recursively search for KR_CHAR or KR_INT terminal
        for (ParseNode child : node.children()) {
            if (child instanceof TerminalNode terminal) {
                String symbol = terminal.symbol();
                if ("KR_CHAR".equals(symbol)) {
                    return PrimitiveType.CHAR;
                } else if ("KR_INT".equals(symbol)) {
                    return PrimitiveType.INT;
                }
            } else if (child instanceof NonTerminalNode nonTerminal) {
                Type result = extractTypeFromSpecifikatorTipa(nonTerminal);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}

