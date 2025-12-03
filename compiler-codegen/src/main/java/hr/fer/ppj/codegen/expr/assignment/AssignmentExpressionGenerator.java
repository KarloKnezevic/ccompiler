package hr.fer.ppj.codegen.expr.assignment;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for assignment expressions and increment/decrement operations.
 * 
 * <p>This class handles the generation of code for:
 * <ul>
 *   <li>Assignment expressions (=)</li>
 *   <li>Pre-increment (++var)</li>
 *   <li>Pre-decrement (--var)</li>
 *   <li>Post-increment (var++)</li>
 *   <li>Post-decrement (var--)</li>
 * </ul>
 * 
 * <p>Assignments can be to simple variables or array elements.
 * Increment/decrement operations modify variables and return either
 * the new value (pre-) or the old value (post-).
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class AssignmentExpressionGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator expressionGenerator;
    private hr.fer.ppj.codegen.expr.array.ArrayExpressionGenerator arrayGenerator;
    
    /**
     * Creates a new assignment expression generator.
     * 
     * @param context the code generation context
     * @param expressionGenerator the main expression generator for recursive calls
     */
    public AssignmentExpressionGenerator(CodeGenContext context, ExpressionCodeGenerator expressionGenerator) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.expressionGenerator = Objects.requireNonNull(expressionGenerator, "expressionGenerator must not be null");
    }
    
    /**
     * Sets the array generator for array assignment support.
     * 
     * @param arrayGenerator the array expression generator
     */
    public void setArrayGenerator(hr.fer.ppj.codegen.expr.array.ArrayExpressionGenerator arrayGenerator) {
        this.arrayGenerator = arrayGenerator;
    }
    
    /**
     * Generates code for assignment expressions (=).
     * 
     * @param node the assignment expression node
     */
    public void generateAssignmentExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level
            expressionGenerator.generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Assignment: <postfiks_izraz> OP_PRIDRUZI <izraz_pridruzivanja>
            NonTerminalNode lvalue = (NonTerminalNode) children.get(0);
            NonTerminalNode rvalue = (NonTerminalNode) children.get(2);
            
            // Generate code for right-hand side first
            expressionGenerator.generateExpression(rvalue);
            
            // Generate assignment code (with array generator support)
            generateAssignment(lvalue, "R0", arrayGenerator);
        }
    }
    
    /**
     * Generates code for pre-increment (++var).
     * Returns the new value.
     * 
     * @param operand the operand expression
     */
    public void generatePreIncrement(NonTerminalNode operand) {
        String variableName = extractVariableName(operand);
        
        if (variableName != null) {
            String address = getVariableAddress(variableName);
            
            // Load current value
            context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
            // Increment by decimal 1
            context.emitter().emitInstruction("ADD", "R0", "%D 1", "R0", "pre-increment");
            // Store back
            context.emitter().emitInstruction("STORE", "R0", address, "store incremented " + variableName);
            // Result is the new value (already in R0)
        } else {
            // Complex pre-increment - evaluate operand, increment, store, return new value
            expressionGenerator.generateExpression(operand);
            context.emitter().emitInstruction("ADD", "R0", "%D 1", "R0", "pre-increment");
            // Note: Cannot store back if operand is not a simple lvalue
        }
    }
    
    /**
     * Generates code for pre-decrement (--var).
     * Returns the new value.
     * 
     * @param operand the operand expression
     */
    public void generatePreDecrement(NonTerminalNode operand) {
        String variableName = extractVariableName(operand);
        
        if (variableName != null) {
            String address = getVariableAddress(variableName);
            
            // Load current value
            context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
            // Decrement by decimal 1
            context.emitter().emitInstruction("SUB", "R0", "%D 1", "R0", "pre-decrement");
            // Store back
            context.emitter().emitInstruction("STORE", "R0", address, "store decremented " + variableName);
            // Result is the new value (already in R0)
        } else {
            // Complex pre-decrement - evaluate operand, decrement, store, return new value
            expressionGenerator.generateExpression(operand);
            context.emitter().emitInstruction("SUB", "R0", "%D 1", "R0", "pre-decrement");
        }
    }
    
    /**
     * Generates code for post-increment (var++).
     * Returns the old value.
     * 
     * @param operand the operand expression
     */
    public void generatePostIncrement(NonTerminalNode operand) {
        String variableName = extractVariableName(operand);
        
        if (variableName != null) {
            String address = getVariableAddress(variableName);
            
            // Load current value
            context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
            // Save old value
            context.emitter().emitInstruction("MOVE", "R0", "R1", "save old value");
            // Increment by decimal 1
            context.emitter().emitInstruction("ADD", "R0", "%D 1", "R0", "post-increment");
            // Store new value
            context.emitter().emitInstruction("STORE", "R0", address, "store incremented " + variableName);
            // Return old value
            context.emitter().emitInstruction("MOVE", "R1", "R0", "return old value");
        } else {
            // Complex post-increment - evaluate operand, save old value, increment, store, return old
            expressionGenerator.generateExpression(operand);
            context.emitter().emitInstruction("MOVE", "R0", "R1", "save old value");
            context.emitter().emitInstruction("ADD", "R0", "%D 1", "R0", "post-increment");
            context.emitter().emitInstruction("MOVE", "R1", "R0", "return old value");
        }
    }
    
    /**
     * Generates code for post-decrement (var--).
     * Returns the old value.
     * 
     * @param operand the operand expression
     */
    public void generatePostDecrement(NonTerminalNode operand) {
        String variableName = extractVariableName(operand);
        
        if (variableName != null) {
            String address = getVariableAddress(variableName);
            
            // Load current value
            context.emitter().emitInstruction("LOAD", "R0", address, "load " + variableName);
            // Save old value
            context.emitter().emitInstruction("MOVE", "R0", "R1", "save old value");
            // Decrement by decimal 1
            context.emitter().emitInstruction("SUB", "R0", "%D 1", "R0", "post-decrement");
            // Store new value
            context.emitter().emitInstruction("STORE", "R0", address, "store decremented " + variableName);
            // Return old value
            context.emitter().emitInstruction("MOVE", "R1", "R0", "return old value");
        } else {
            // Complex post-decrement - evaluate operand, save old value, decrement, store, return old
            expressionGenerator.generateExpression(operand);
            context.emitter().emitInstruction("MOVE", "R0", "R1", "save old value");
            context.emitter().emitInstruction("SUB", "R0", "%D 1", "R0", "post-decrement");
            context.emitter().emitInstruction("MOVE", "R1", "R0", "return old value");
        }
    }
    
    /**
     * Generates code to assign a value to an lvalue.
     * 
     * <p>This method handles both simple variable assignments and array element assignments.
     * Array assignments are delegated to the array generator.
     * 
     * @param lvalue the left-hand side expression
     * @param sourceRegister the register containing the value to assign
     * @param arrayGenerator the array generator for array assignments (may be null)
     */
    public void generateAssignment(NonTerminalNode lvalue, String sourceRegister, 
                                    hr.fer.ppj.codegen.expr.array.ArrayExpressionGenerator arrayGenerator) {
        // Check if this is an array indexing assignment: a[i] = value
        if (isArrayIndexing(lvalue) && arrayGenerator != null) {
            arrayGenerator.generateArrayAssignment(lvalue, sourceRegister);
            return;
        }
        
        // Handle simple variable assignments
        String variableName = extractVariableName(lvalue);
        
        if (variableName != null) {
            String address = getVariableAddress(variableName);
            context.emitter().emitInstruction("STORE", sourceRegister, address, 
                                            "assign to " + variableName);
        } else {
            // Complex lvalue - not supported in this subset
            throw new IllegalStateException("Complex lvalue assignment not supported in this subset");
        }
    }
    
    /**
     * Generates code to assign a value to an lvalue (simple variable only).
     * 
     * @param lvalue the left-hand side expression
     * @param sourceRegister the register containing the value to assign
     */
    public void generateAssignment(NonTerminalNode lvalue, String sourceRegister) {
        generateAssignment(lvalue, sourceRegister, null);
    }
    
    /**
     * Checks if an expression is an array indexing expression.
     * 
     * @param expr the expression to check
     * @return true if the expression is array indexing (a[i])
     */
    public boolean isArrayIndexing(NonTerminalNode expr) {
        return extractArrayIndexingInfo(expr) != null;
    }
    
    /**
     * Information about an array indexing expression.
     */
    public record ArrayIndexingInfo(NonTerminalNode base, NonTerminalNode indexExpr) {}
    
    /**
     * Extracts array indexing information from an expression.
     * 
     * @param expr the expression to extract from
     * @return ArrayIndexingInfo with base and indexExpr, or null if not array indexing
     */
    public ArrayIndexingInfo extractArrayIndexingInfo(NonTerminalNode expr) {
        String symbol = expr.symbol();
        List<ParseNode> children = expr.children();
        
        // Check if this node itself is array indexing: <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
        if ("<postfiks_izraz>".equals(symbol) && children.size() == 4) {
            ParseNode first = children.get(1);
            ParseNode third = children.get(3);
            if (first instanceof TerminalNode leftBracket && "L_UGL_ZAGRADA".equals(leftBracket.symbol()) &&
                third instanceof TerminalNode rightBracket && "D_UGL_ZAGRADA".equals(rightBracket.symbol())) {
                NonTerminalNode base = (NonTerminalNode) children.get(0);
                NonTerminalNode indexExpr = (NonTerminalNode) children.get(2);
                return new ArrayIndexingInfo(base, indexExpr);
            }
        }
        
        // Recursively check children
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                ArrayIndexingInfo info = extractArrayIndexingInfo(nonTerminal);
                if (info != null) {
                    return info;
                }
            }
        }
        
        return null;
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
}

