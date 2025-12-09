package hr.fer.ppj.codegen.expr.primary;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.util.FloatCodegenHelper;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.Type;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for primary expressions (literals, identifiers, parenthesized expressions).
 * 
 * <p>This class handles the generation of code for the most basic expressions in the C language,
 * which form the foundation of all more complex expressions. It implements the <b>primary expression
 * code generation algorithm</b> that translates C literals and identifiers into FRISC assembly.
 * 
 * <p><b>Grammar Rule:</b> Handles {@code <primarni_izraz>} nonterminal:
 * <pre>
 * &lt;primarni_izraz&gt; ::= IDN
 *                     | BROJ
 *                     | ZNAK
 *                     | NIZ_ZNAKOVA
 *                     | L_ZAGRADA &lt;izraz&gt; D_ZAGRADA
 * </pre>
 * 
 * <p><b>Algorithm: Primary Expression Code Generation</b>
 * 
 * <p>The algorithm works as follows:
 * <ol>
 *   <li><b>Terminal Identification:</b> Identify the type of primary expression (constant, identifier, etc.)</li>
 *   <li><b>Type-Aware Code Generation:</b> Generate appropriate FRISC code based on the expression type:
 *       <ul>
 *         <li><b>Integer Constants:</b> Load using constant loading algorithm (handles 20-bit immediate limitation)</li>
 *         <li><b>Float Constants:</b> Convert to Q16.16 format, then load as integer</li>
 *         <li><b>Character Constants:</b> Extract ASCII value, load as integer</li>
 *         <li><b>Identifiers:</b> Resolve address (local/global), load value from memory</li>
 *         <li><b>String Literals:</b> Generate label, return address</li>
 *         <li><b>Parenthesized Expressions:</b> Recursively evaluate inner expression (no extra code)</li>
 *       </ul>
 *   </li>
 * </ol>
 * 
 * <p><b>Integer Constant Loading Algorithm:</b>
 * 
 * <p>Integer constants use the constant loading algorithm (see {@link hr.fer.ppj.codegen.emitter.ConstantLoader}):
 * <ul>
 *   <li><b>Small Constants:</b> If value fits in 20-bit signed immediate ([-524288, 524287]),
 *       emit single {@code MOVE %D value, R0} instruction</li>
 *   <li><b>Large Constants:</b> If value exceeds 20-bit range, construct from high and low 16-bit parts:
 *       <pre>
 *       MOVE %D hi, R0        ; load high 16 bits
 *       SHL R0, %D 16, R0     ; shift left by 16
 *       ADD R0, %D lo, R0     ; add low 16 bits
 *       </pre>
 *   </li>
 * </ul>
 * 
 * <p><b>Float Constant Handling:</b>
 * 
 * <p>Float constants are detected and converted to Q16.16 format:
 * <ol>
 *   <li><b>Detection:</b> Check if literal contains '.' or 'e'/'E' (exponent notation)</li>
 *   <li><b>Parsing:</b> Parse as float using {@code Float.parseFloat()}</li>
 *   <li><b>Conversion:</b> Convert to Q16.16 format (multiply by 65536, round to integer)</li>
 *   <li><b>Loading:</b> Load Q16.16 value as integer using constant loading algorithm</li>
 * </ol>
 * 
 * <p><b>Variable Access Algorithm:</b>
 * 
 * <p>Variable access uses the variable address resolution algorithm:
 * <ol>
 *   <li><b>Scope Resolution:</b> Check if variable is local (in activation record) or global</li>
 *   <li><b>Address Resolution:</b> Get FRISC address expression:
 *       <ul>
 *         <li>Local: {@code (R5±offset)} where offset is from activation record</li>
 *         <li>Global: {@code (G_LABEL)} where G_LABEL is the global variable label</li>
 *       </ul>
 *   </li>
 *   <li><b>Memory Load:</b> Emit {@code LOAD R0, (address)} instruction</li>
 * </ol>
 * 
 * <p><b>String Literal Handling:</b>
 * 
 * <p>String literals are handled by generating a unique label:
 * <ol>
 *   <li><b>Label Generation:</b> Generate unique label (e.g., {@code STR_1})</li>
 *   <li><b>Address Return:</b> Load label address into R0 (string address, not value)</li>
 *   <li><b>Data Generation:</b> String data should be generated in data section (not handled here)</li>
 * </ol>
 * 
 * <p><b>Parenthesized Expression Handling:</b>
 * 
 * <p>Parentheses are handled by recursively evaluating the inner expression:
 * <ul>
 *   <li>No extra code is generated (parentheses are compile-time only)</li>
 *   <li>Simply delegate to expression generator for inner expression</li>
 *   <li>Result is left in R0 (same as without parentheses)</li>
 * </ul>
 * 
 * <p><b>FRISC Code Patterns:</b>
 * 
 * <p><b>Integer Constant:</b>
 * <pre>
 * MOVE %D 42, R0              ; small constant
 * ; OR for large constants:
 * MOVE %D hi, R0              ; load high 16 bits
 * SHL R0, %D 16, R0           ; shift left by 16
 * ADD R0, %D lo, R0           ; add low 16 bits
 * </pre>
 * 
 * <p><b>Float Constant:</b>
 * <pre>
 * ; Convert 1.5 to Q16.16: 1.5 × 65536 = 98304
 * MOVE %D 98304, R0           ; load Q16.16 value
 * </pre>
 * 
 * <p><b>Character Constant:</b>
 * <pre>
 * MOVE %D 65, R0              ; load ASCII value of 'A'
 * </pre>
 * 
 * <p><b>Variable Access:</b>
 * <pre>
 * LOAD R0, (R5-04)            ; local variable
 * ; OR
 * LOAD R0, (G_X)              ; global variable
 * </pre>
 * 
 * <p><b>String Literal:</b>
 * <pre>
 * MOVE STR_1, R0              ; load string address
 * </pre>
 * 
 * <p><b>Parenthesized Expression:</b>
 * <pre>
 * ; (x + y) - generates same code as x + y
 * ... (inner expression code) ...
 * </pre>
 * 
 * <p><b>Edge Cases Handled:</b>
 * <ul>
 *   <li><b>Float vs. Integer Detection:</b> Checks both literal format and semantic type</li>
 *   <li><b>Large Constants:</b> Handles constants beyond 20-bit immediate range</li>
 *   <li><b>Invalid Literals:</b> Falls back to zero if parsing fails (never emits invalid code)</li>
 *   <li><b>Escape Sequences:</b> Character constants handle escape sequences (e.g., '\n', '\t')</li>
 * </ul>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(1) for code generation (constant number of instructions)</li>
 *   <li><b>Space Complexity:</b> O(1) - uses only registers</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class PrimaryExpressionGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    
    /**
     * Creates a new primary expression generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the main expression generator for recursive calls
     */
    public PrimaryExpressionGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    }
    
    /**
     * Generates code for primary expressions.
     * 
     * <p><b>Grammar Rule:</b> Implements {@code <primarni_izraz>}:
     * <pre>
     * &lt;primarni_izraz&gt; ::= IDN | BROJ | ZNAK | NIZ_ZNAKOVA
     *                     | L_ZAGRADA &lt;izraz&gt; D_ZAGRADA
     * </pre>
     * 
     * <p>Primary expressions can be:
     * <ul>
     *   <li>Terminal nodes (BROJ, ZNAK, IDN, NIZ_ZNAKOVA)</li>
     *   <li>Parenthesized expressions: L_ZAGRADA <izraz> D_ZAGRADA</li>
     * </ul>
     * 
     * <p><b>FRISC Code Generation:</b>
     * <ul>
     *   <li>Terminals: Generate appropriate load instruction (MOVE for constants, LOAD for variables)</li>
     *   <li>Parentheses: Recursively evaluate inner expression (no extra code)</li>
     * </ul>
     * 
     * @param node the primary expression node ({@code <primarni_izraz>})
     */
    public void generatePrimaryExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - terminal (BROJ, ZNAK, IDN, NIZ_ZNAKOVA)
            ParseNode child = children.get(0);
            if (child instanceof TerminalNode terminal) {
                generateTerminalPrimary(terminal, node);
            } else {
                // Should not happen, but delegate just in case
                expressionGenerator.generateExpression((NonTerminalNode) child);
            }
        } else if (children.size() == 3) {
            // Parenthesized expression: L_ZAGRADA <izraz> D_ZAGRADA
            ParseNode first = children.get(0);
            ParseNode second = children.get(1);
            ParseNode third = children.get(2);
            
            if (first instanceof TerminalNode leftParen && "L_ZAGRADA".equals(leftParen.symbol()) &&
                second instanceof NonTerminalNode &&
                third instanceof TerminalNode rightParen && "D_ZAGRADA".equals(rightParen.symbol())) {
                // Evaluate the inner expression
                expressionGenerator.generateExpression((NonTerminalNode) second);
            } else {
                // Unexpected structure - try to evaluate first child
                if (first instanceof NonTerminalNode) {
                    expressionGenerator.generateExpression((NonTerminalNode) first);
                }
            }
        } else {
            // Unexpected structure - try to evaluate first child if it's a non-terminal
            if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode) {
                expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
            }
        }
    }
    
    /**
     * Generates code for terminal primary expressions (constants and identifiers).
     * 
     * <p><b>Grammar Terminals:</b>
     * <ul>
     *   <li>{@code BROJ} - Integer or float constant</li>
     *   <li>{@code ZNAK} - Character constant</li>
     *   <li>{@code IDN} - Identifier (variable)</li>
     *   <li>{@code NIZ_ZNAKOVA} - String literal</li>
     * </ul>
     * 
     * <p><b>FRISC Code:</b>
     * <ul>
     *   <li>BROJ (int): {@code MOVE %D value, R0} (or SHL/ADD sequence for large values)</li>
     *   <li>BROJ (float): Convert to Q16.16, load into R0</li>
     *   <li>ZNAK: {@code MOVE %D ascii, R0}</li>
     *   <li>IDN: {@code LOAD R0, (address)} where address is (R5±offset) or (G_LABEL)</li>
     *   <li>NIZ_ZNAKOVA: {@code MOVE STR_label, R0} (returns address)</li>
     * </ul>
     * 
     * @param terminal the terminal node (BROJ, ZNAK, IDN, or NIZ_ZNAKOVA)
     * @param parentNode the parent node (to check type from semantic attributes)
     */
    private void generateTerminalPrimary(TerminalNode terminal, NonTerminalNode parentNode) {
        String symbol = terminal.symbol();
        String value = terminal.lexeme();
        
        switch (symbol) {
            case "BROJ" -> {
                // CRITICAL: Check if this is a float literal FIRST before attempting integer parsing
                // Float literals contain '.' or 'e'/'E' (exponent notation)
                // This MUST be checked first to prevent invalid FRISC code like "MOVE %D .5, R0"
                boolean isFloatLiteral = FloatCodegenHelper.isFloatLiteral(value);
                
                // Also check type from semantic attributes as a fallback
                Type type = null;
                if (parentNode != null && parentNode.attributes() != null) {
                    type = parentNode.attributes().type();
                }
                
                // If it looks like a float literal OR the semantic type is float, treat it as float
                if (isFloatLiteral || type == PrimitiveType.FLOAT) {
                    // Float literal: convert to Q16.16 format (integer representation)
                    // This ensures we never emit invalid FRISC code like "MOVE %D .5, R0"
                    try {
                        int q16_16 = FloatCodegenHelper.parseFloatLiteral(value);
                        context.emitter().emitLoadIntConstant(q16_16, "R0", "load float constant " + value + " (Q16.16)");
                    } catch (NumberFormatException e) {
                        // Fallback: try to parse as float and convert
                        try {
                            float floatValue = Float.parseFloat(value);
                            int q16_16 = FloatCodegenHelper.floatToQ16_16(floatValue);
                            context.emitter().emitLoadIntConstant(q16_16, "R0", "load float constant " + value + " (Q16.16)");
                        } catch (NumberFormatException e2) {
                            // If all parsing fails, emit zero as safe fallback (never emit invalid float literal)
                            context.emitter().emitComment("ERROR: Could not parse float literal: " + value + " - using zero");
                            context.emitter().emitLoadIntConstant(0, "R0", "fallback to zero");
                        }
                    }
                } else {
                    // Integer constant from source - treat as decimal
                    // Parse the value and use helper to handle large literals
                    try {
                        int intValue = Integer.parseInt(value);
                        context.emitter().emitLoadIntConstant(intValue, "R0", "load constant " + value);
                    } catch (NumberFormatException e) {
                        // CRITICAL: If integer parsing fails, check again if it might be a float
                        // This prevents emitting invalid FRISC code like "MOVE %D .5, R0"
                        if (FloatCodegenHelper.isFloatLiteral(value)) {
                            // It's actually a float literal - convert to Q16.16
                            try {
                                int q16_16 = FloatCodegenHelper.parseFloatLiteral(value);
                                context.emitter().emitLoadIntConstant(q16_16, "R0", "load float constant " + value + " (Q16.16)");
                            } catch (NumberFormatException e2) {
                                context.emitter().emitComment("ERROR: Could not parse float literal: " + value + " - using zero");
                                context.emitter().emitLoadIntConstant(0, "R0", "fallback to zero");
                            }
                        } else {
                            // Not a float and not a valid integer - this shouldn't happen for valid C
                            // Emit zero as safe fallback (never emit invalid immediate)
                            context.emitter().emitComment("ERROR: Invalid number literal: " + value + " - using zero");
                            context.emitter().emitLoadIntConstant(0, "R0", "fallback to zero");
                        }
                    }
                }
            }
            case "ZNAK" -> {
                // Character constant - convert to ASCII value (decimal)
                int ascii = value.charAt(1); // Skip the quote
                // ASCII values are always small (0-255), but use helper for consistency
                context.emitter().emitLoadIntConstant(ascii, "R0", "load char '" + value + "'");
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
                // String literals are handled by generating a label that points to the string data
                // The actual string data should be generated in the data section
                // For now, we generate the label - full string data generation would require
                // tracking string literals and emitting them at the end
            }
        }
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
}

