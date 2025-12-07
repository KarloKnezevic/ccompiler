package hr.fer.ppj.codegen.expr.unary;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.utils.LiteralExtractor;
import hr.fer.ppj.codegen.utils.TypeConverter;
import hr.fer.ppj.codegen.utils.TypeNodeExtractor;
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
 * <p>This class handles the generation of code for unary operators and explicit type casts,
 * implementing the <b>unary expression code generation algorithm</b> that translates C unary
 * operations and type conversions into FRISC assembly.
 * 
 * <p><b>Grammar Rules:</b> Handles {@code <unarni_izraz>} and {@code <cast_izraz>}:
 * <pre>
 * &lt;unarni_izraz&gt; ::= &lt;postfiks_izraz&gt;
 *                  | OP_INC &lt;unarni_izraz&gt;
 *                  | OP_DEC &lt;unarni_izraz&gt;
 *                  | &lt;unarni_operator&gt; &lt;cast_izraz&gt;
 * 
 * &lt;unarni_operator&gt; ::= PLUS | MINUS | OP_TILDA | OP_NEG
 * 
 * &lt;cast_izraz&gt; ::= &lt;unarni_izraz&gt;
 *                | L_ZAGRADA &lt;ime_tipa&gt; D_ZAGRADA &lt;cast_izraz&gt;
 * </pre>
 * 
 * <p><b>Algorithm: Unary Expression Code Generation</b>
 * 
 * <p>The algorithm works as follows:
 * <ol>
 *   <li><b>Operator Identification:</b> Identify the unary operator (+, -, !, ~) or cast type</li>
 *   <li><b>Operand Evaluation:</b> Evaluate the operand expression (result in R0)</li>
 *   <li><b>Operation Application:</b> Apply the unary operation or type conversion:
 *       <ul>
 *         <li><b>Unary Plus (+):</b> No-op (identity operation)</li>
 *         <li><b>Unary Minus (-):</b> Negation (0 - operand, or direct negative literal)</li>
 *         <li><b>Logical NOT (!):</b> Boolean inversion (0 ↔ 1)</li>
 *         <li><b>Bitwise NOT (~):</b> Bitwise complement (XOR with all 1s)</li>
 *         <li><b>Type Casts:</b> Type conversion operations (see below)</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><b>Unary Minus Algorithm:</b>
 * 
 * <p>Unary minus uses an optimization strategy:
 * <ol>
 *   <li><b>Literal Optimization:</b> If operand is a literal (int or float), directly emit
 *       the negated value using constant loading</li>
 *   <li><b>Runtime Negation:</b> Otherwise, use {@code 0 - operand} pattern:
 *       <pre>
 *       MOVE %D 0, R1          ; zero
 *       SUB R1, R0, R0          ; R0 = 0 - R0 = -R0
 *       </pre>
 *   </li>
 * </ol>
 * 
 * <p>This works for both integers and floats (Q16.16) because subtraction preserves the format.
 * 
 * <p><b>Logical NOT Algorithm:</b>
 * 
 * <p>Logical NOT implements C's boolean semantics:
 * <ol>
 *   <li><b>Zero Check:</b> Compare operand with 0</li>
 *   <li><b>Conditional Assignment:</b>
 *       <ul>
 *         <li>If operand == 0: Result = 1 (true)</li>
 *         <li>If operand != 0: Result = 0 (false)</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><b>Bitwise NOT Algorithm:</b>
 * 
 * <p>Bitwise NOT is implemented as XOR with all 1s:
 * <pre>
 * XOR R0, FFFFFFFF, R0         ; R0 = R0 XOR 0xFFFFFFFF = ~R0
 * </pre>
 * 
 * <p><b>Type Cast Algorithm:</b>
 * 
 * <p>Type casts implement C's type conversion rules:
 * <ul>
 *   <li><b>int → char:</b> Mask lower 8 bits: {@code AND R0, 00FF, R0}</li>
 *   <li><b>char → int:</b> No-op (char is already 32-bit in this implementation)</li>
 *   <li><b>int → float:</b> Call F_I2F helper function (convert to Q16.16)</li>
 *   <li><b>float → int:</b> Call F_F2I helper function (truncate Q16.16 to integer)</li>
 *   <li><b>float → char:</b> First convert float to int, then mask to char</li>
 * </ul>
 * 
 * <p><b>FRISC Code Patterns:</b>
 * 
 * <p><b>Unary Plus:</b>
 * <pre>
 * ; +x - no code generated, just evaluate x
 * ... (operand evaluation) ...
 * </pre>
 * 
 * <p><b>Unary Minus (Literal):</b>
 * <pre>
 * MOVE %D -42, R0              ; direct negative constant
 * </pre>
 * 
 * <p><b>Unary Minus (Runtime):</b>
 * <pre>
 * ... (operand evaluation) ...
 * MOVE %D 0, R1                ; zero
 * SUB R1, R0, R0                ; R0 = 0 - R0 = -R0
 * </pre>
 * 
 * <p><b>Logical NOT:</b>
 * <pre>
 * ... (operand evaluation) ...
 * CMP R0, %D 0                  ; compare with 0
 * JP_EQ L_TRUE                  ; if zero, result is 1
 * MOVE %D 0, R0                 ; result is 0
 * JP L_END
 * L_TRUE:
 * MOVE %D 1, R0                 ; result is 1
 * L_END:
 * </pre>
 * 
 * <p><b>Bitwise NOT:</b>
 * <pre>
 * ... (operand evaluation) ...
 * XOR R0, FFFFFFFF, R0          ; bitwise complement
 * </pre>
 * 
 * <p><b>Type Cast (int → char):</b>
 * <pre>
 * ... (operand evaluation) ...
 * AND R0, 00FF, R0              ; mask lower 8 bits
 * </pre>
 * 
 * <p><b>Type Cast (int → float):</b>
 * <pre>
 * ... (operand evaluation) ...
 * PUSH R0
 * CALL F_I2F                    ; convert int to float
 * ADD R7, %D 4, R7              ; cleanup
 * MOVE R6, R0                   ; result in R0
 * </pre>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for code generation (constant number of instructions)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class UnaryExpressionGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    private final TypeConverter typeConverter;
    
    /**
     * Creates a new unary expression generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the main expression generator for recursive calls
     */
    public UnaryExpressionGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
        this.typeConverter = new TypeConverter(context);
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
            NonTerminalNode operand = (NonTerminalNode) children.get(1);
            
            // Check if first child is a terminal (for OP_INC/OP_DEC) or <unarni_operator> (for +, -, ~, !)
            if (first instanceof TerminalNode terminal) {
                String operator = terminal.symbol();
                
                switch (operator) {
                    case "OP_INC", "OP_DEC" -> {
                        // Pre-increment/decrement - handled by ExpressionCodeGenerator
                        // This shouldn't happen here, but delegate just in case
                        expressionGenerator.generateExpression(operand);
                    }
                    default -> {
                        context.emitter().emitComment("Unexpected terminal in unary expression: " + operator);
                        expressionGenerator.generateExpression(operand);
                    }
                }
            } else if (first instanceof NonTerminalNode unaryOpNode && 
                       "<unarni_operator>".equals(unaryOpNode.symbol())) {
                // Structure: <unarni_izraz> -> <unarni_operator> -> MINUS/PLUS/OP_TILDA/OP_NEG + <cast_izraz>
                // Extract the actual operator from <unarni_operator>
                String operator = extractOperatorFromUnaryOperatorNode(unaryOpNode);
                
                if (operator != null) {
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
                } else {
                    // Could not extract operator - delegate to operand
                    context.emitter().emitComment("Could not extract unary operator");
                    expressionGenerator.generateExpression(operand);
                }
            } else {
                // Unknown structure - delegate to operand
                expressionGenerator.generateExpression(operand);
            }
        }
    }
    
    /**
     * Extracts the operator symbol from a <unarni_operator> node.
     * 
     * <p>The <unarni_operator> node contains a single terminal child with the operator symbol.
     * 
     * @param unaryOpNode the <unarni_operator> node
     * @return the operator symbol (PLUS, MINUS, OP_TILDA, OP_NEG), or null if not found
     */
    private String extractOperatorFromUnaryOperatorNode(NonTerminalNode unaryOpNode) {
        for (ParseNode child : unaryOpNode.children()) {
            if (child instanceof TerminalNode terminal) {
                return terminal.symbol();
            }
        }
        return null;
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
        Type targetType = TypeNodeExtractor.extractTypeFromTypeNode(typeNode);
        
        // Get source type from expression
        Type sourceType = exprNode.attributes() != null ? exprNode.attributes().type() : null;
        
        // Perform cast based on target type
        if (targetType == PrimitiveType.CHAR) {
            // Cast to char: mask lower 8 bits
            // Use hex format for bit mask: 00FF (not %D 255, as this is a bit mask, not a C integer literal)
            if (sourceType == PrimitiveType.FLOAT) {
                // Float to char: convert float to int first, then mask
                typeConverter.convertFloatToInt();
            }
            context.emitter().emitInstruction("AND", "R0", "00FF", "R0", "cast to char (mask lower 8 bits)");
        } else if (targetType == PrimitiveType.INT) {
            // Cast to int
            if (sourceType == PrimitiveType.FLOAT) {
                // Float to int: convert float to int (truncate)
                typeConverter.convertFloatToInt();
            }
            // Otherwise: no-op (value is already int)
        } else if (targetType == PrimitiveType.FLOAT) {
            // Cast to float
            if (sourceType != PrimitiveType.FLOAT) {
                // Int/char to float: convert to float
                typeConverter.convertIntToFloat();
            }
            // Otherwise: no-op (value is already float)
        }
        // Unknown cast type: just pass through (R0 already contains the value)
    }
    
    /**
     * Generates code for unary minus (-operand).
     * 
     * <p><b>Grammar Rule:</b> Handles {@code <unarni_izraz> ::= MINUS <cast_izraz>}
     * 
     * <p><b>Type-Aware Negation:</b>
     * <ul>
     *   <li>If operand is float (Q16.16): Generate direct negation in Q16.16 format (no F_I2F)</li>
     *   <li>If operand is int: Generate integer negation</li>
     * </ul>
     * 
     * <p><b>Optimization:</b> For integer literals, directly emits the negated value
     * using {@code emitLoadIntConstant} (which handles large negative values correctly).
     * 
     * <p><b>FRISC Code Pattern:</b>
     * <ul>
     *   <li>Float literal: Direct Q16.16 negation (already in Q16.16 format)</li>
     *   <li>Integer literal: {@code MOVE %D -value, R0} (or SHL/ADD for large values)</li>
     *   <li>Non-literal: {@code MOVE %D 0, R1; SUB R1, R0, R0} (0 - operand)</li>
     * </ul>
     * 
     * <p><b>FRISC Semantics:</b> Uses SUB instruction for runtime negation:
     * {@code SUB R1, R0, R0} computes R0 = R1 - R0 = 0 - operand = -operand.
     * For float operands, this preserves Q16.16 format (no conversion needed).
     * 
     * @param operand the operand expression ({@code <cast_izraz>})
     */
    private void generateUnaryMinus(NonTerminalNode operand) {
        // Check if operand is a float literal
        String literalValue = LiteralExtractor.tryExtractFloatLiteral(operand);
        if (literalValue != null) {
            // Float literal: convert to Q16.16 and negate
            try {
                int q16_16 = hr.fer.ppj.codegen.util.FloatCodegenHelper.parseFloatLiteral(literalValue);
                int negatedQ16_16 = -q16_16;
                context.emitter().emitLoadIntConstant(negatedQ16_16, "R0", "load float constant -" + literalValue + " (Q16.16)");
                return;
            } catch (NumberFormatException e) {
                // Fall back to runtime negation
            }
        }
        
        // Check if operand is an integer literal
        String intLiteralValue = LiteralExtractor.tryExtractIntegerLiteral(operand);
        if (intLiteralValue != null) {
            // Integer literal: directly emit negative literal
            try {
                int value = Integer.parseInt(intLiteralValue);
                int negatedValue = -value;
                context.emitter().emitLoadIntConstant(negatedValue, "R0", "load constant -" + intLiteralValue);
                return;
            } catch (NumberFormatException e) {
                // Fall back to runtime negation
            }
        }
        
        // Runtime negation: 0 - R0 = -R0
        // This works for both int and float (Q16.16) - no conversion needed
        generateRuntimeNegation(operand);
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
    
}

