package hr.fer.ppj.codegen.expr.primary;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for primary expressions.
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
 * <p>This class handles the generation of code for primary expressions including:
 * <ul>
 *   <li>Integer constants (BROJ) - loads value into R0</li>
 *   <li>Character constants (ZNAK) - loads ASCII value into R0</li>
 *   <li>Identifiers (IDN) - loads variable value from memory into R0</li>
 *   <li>String literals (NIZ_ZNAKOVA) - loads string address into R0</li>
 *   <li>Parenthesized expressions - evaluates inner expression</li>
 * </ul>
 * 
 * <p><b>FRISC Semantics:</b>
 * <ul>
 *   <li>Integer constants: Uses {@code emitLoadIntConstant} which handles 20-bit immediate
 *       limitations. Large constants (>524287 or <-524288) are constructed using SHL/ADD pattern.</li>
 *   <li>Character constants: ASCII value (0-255) loaded as integer</li>
 *   <li>Variable access: Uses LOAD instruction with address from ActivationRecord (local)
 *       or global label (global)</li>
 *   <li>String literals: Returns address (label) pointing to string data</li>
 *   <li>Parentheses: No code generated, just evaluates inner expression</li>
 * </ul>
 * 
 * <p><b>Register Usage:</b> Result is always left in R0.
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
                generateTerminalPrimary(terminal);
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
     *   <li>{@code BROJ} - Integer constant</li>
     *   <li>{@code ZNAK} - Character constant</li>
     *   <li>{@code IDN} - Identifier (variable)</li>
     *   <li>{@code NIZ_ZNAKOVA} - String literal</li>
     * </ul>
     * 
     * <p><b>FRISC Code:</b>
     * <ul>
     *   <li>BROJ: {@code MOVE %D value, R0} (or SHL/ADD sequence for large values)</li>
     *   <li>ZNAK: {@code MOVE %D ascii, R0}</li>
     *   <li>IDN: {@code LOAD R0, (address)} where address is (R5±offset) or (G_LABEL)</li>
     *   <li>NIZ_ZNAKOVA: {@code MOVE STR_label, R0} (returns address)</li>
     * </ul>
     * 
     * @param terminal the terminal node (BROJ, ZNAK, IDN, or NIZ_ZNAKOVA)
     */
    private void generateTerminalPrimary(TerminalNode terminal) {
        String symbol = terminal.symbol();
        String value = terminal.lexeme();
        
        switch (symbol) {
            case "BROJ" -> {
                // Integer constant from source - treat as decimal
                // Parse the value and use helper to handle large literals
                try {
                    int intValue = Integer.parseInt(value);
                    context.emitter().emitLoadIntConstant(intValue, "R0", "load constant " + value);
                } catch (NumberFormatException e) {
                    // Fallback: if parsing fails, emit as-is (shouldn't happen for valid C)
                    context.emitter().emitInstruction("MOVE", "%D " + value, "R0", "load constant " + value);
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
        // Check if we're in a function and the variable is local
        if (context.isInFunction() && context.activationRecord().hasVariable(variableName)) {
            return context.activationRecord().getVariableAddress(variableName);
        } else {
            // Global variable
            String label = context.labelGenerator().getGlobalVariableLabel(variableName);
            return "(" + label + ")";
        }
    }
}

