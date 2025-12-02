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
 * <p>This class handles the generation of code for primary expressions including:
 * <ul>
 *   <li>Integer constants (BROJ)</li>
 *   <li>Character constants (ZNAK)</li>
 *   <li>Identifiers (IDN) - variable references</li>
 *   <li>String literals (NIZ_ZNAKOVA)</li>
 *   <li>Parenthesized expressions</li>
 * </ul>
 * 
 * <p>Primary expressions are the simplest form of expressions and form the
 * base of the expression hierarchy. They typically load values directly
 * into register R0.
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
     * <p>Primary expressions can be:
     * <ul>
     *   <li>Terminal nodes (BROJ, ZNAK, IDN, NIZ_ZNAKOVA)</li>
     *   <li>Parenthesized expressions: L_ZAGRADA <izraz> D_ZAGRADA</li>
     * </ul>
     * 
     * @param node the primary expression node
     */
    public void generatePrimaryExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        ParseNode child = children.get(0);
        
        if (child instanceof TerminalNode terminal) {
            generateTerminalPrimary(terminal);
        } else if (child instanceof NonTerminalNode && children.size() == 3) {
            // Parenthesized expression: L_ZAGRADA <izraz> D_ZAGRADA
            expressionGenerator.generateExpression((NonTerminalNode) children.get(1));
        }
    }
    
    /**
     * Generates code for terminal primary expressions (constants and identifiers).
     * 
     * @param terminal the terminal node
     */
    private void generateTerminalPrimary(TerminalNode terminal) {
        String symbol = terminal.symbol();
        String value = terminal.lexeme();
        
        switch (symbol) {
            case "BROJ" -> {
                // Integer constant from source - treat as decimal
                context.emitter().emitInstruction("MOVE", "%D " + value, "R0", "load constant " + value);
            }
            case "ZNAK" -> {
                // Character constant - convert to ASCII value (decimal)
                int ascii = value.charAt(1); // Skip the quote
                context.emitter().emitInstruction("MOVE", "%D " + ascii, "R0", "load char '" + value + "'");
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

