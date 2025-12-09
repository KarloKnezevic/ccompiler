package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.func.FunctionPrologueEpilogueGenerator;
import hr.fer.ppj.codegen.utils.LValueAddressGenerator;
import hr.fer.ppj.codegen.utils.StructLayoutCalculator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for return statements.
 * 
 * <p>This class handles both return statements with values and void returns,
 * optimizing simple literal returns by generating them directly into R6.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ReturnStatementGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator exprGen;
    private final FunctionPrologueEpilogueGenerator prologueEpilogueGenerator;
    private final LValueAddressGenerator addressGenerator;
    
    /**
     * Creates a new return statement generator.
     * 
     * @param context the code generation context
     * @param exprGen the expression generator for return expressions
     */
    public ReturnStatementGenerator(CodeGenContext context, ExpressionCodeGenerator exprGen) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.exprGen = Objects.requireNonNull(exprGen, "exprGen must not be null");
        this.prologueEpilogueGenerator = new FunctionPrologueEpilogueGenerator();
        this.addressGenerator = new LValueAddressGenerator(context, exprGen);
    }
    
    /**
     * Sets the parse tree for extracting struct array sizes.
     * 
     * <p>This propagates the parse tree to the LValueAddressGenerator so it can
     * extract array sizes for nested structs with arrays.
     * 
     * @param parseTree the parse tree from semantic analysis
     */
    public void setParseTree(NonTerminalNode parseTree) {
        if (addressGenerator != null) {
            addressGenerator.setParseTree(parseTree);
        }
    }
    
    /**
     * Generates code for a return statement.
     * 
     * @param node the return statement node ({@code <naredba_skoka>} with KR_RETURN)
     */
    public void generateReturnStatement(NonTerminalNode node) {
        Objects.requireNonNull(node, "node must not be null");
        
        List<ParseNode> children = node.children();
        
        if (children.size() == 3) {
            // KR_RETURN <izraz> TOCKAZAREZ
            NonTerminalNode expression = (NonTerminalNode) children.get(1);
            
            // Check if function returns a struct
            // For struct returns, the return pointer is passed in R2 by the caller
            boolean returnsStruct = false;
            if (context.isInFunction()) {
                // Check return expression type to determine if function returns struct
                Type exprType = expression.attributes() != null ? expression.attributes().type() : null;
                if (exprType != null) {
                    Type strippedType = TypeSystem.stripConst(exprType);
                    returnsStruct = strippedType instanceof StructType;
                }
            }
            
            if (returnsStruct) {
                // Function returns struct: copy struct to hidden return pointer
                generateStructReturn(expression);
            } else {
                // Scalar return: use normal return mechanism
                // Optimization: try to generate expression directly into R6 when possible
                if (tryGenerateReturnExpressionDirectly(expression)) {
                    // Expression was generated directly into R6
                } else {
                    // Fallback: generate into R0, then move to R6
                    exprGen.generateExpression(expression);
                    context.emitter().emitInstruction("MOVE", "R0", "R6", "return value");
                }
            }
        } else {
            // KR_RETURN TOCKAZAREZ (void return)
            context.emitter().emitInstruction("MOVE", "%D 0", "R6", "void return");
        }
        
        // Jump to function exit label to execute epilogue (avoids duplicate epilogues)
        if (context.functionExitLabel() != null) {
            context.emitter().emitInstruction("JP", context.functionExitLabel(), "jump to function exit");
        } else {
            // Fallback: generate epilogue directly if no exit label
            generateFunctionEpilogue();
        }
    }
    
    /**
     * Attempts to generate a return expression directly into R6, avoiding
     * unnecessary MOVE R0, R6 instructions.
     * 
     * <p>This optimization handles simple cases like:
     * <ul>
     *   <li>{@code return 31;}</li>
     *   <li>{@code return -84;}</li>
     * </ul>
     * 
     * @param expression the return expression
     * @return true if expression was generated directly into R6, false otherwise
     */
    private boolean tryGenerateReturnExpressionDirectly(NonTerminalNode expression) {
        // Try simple cases first: integer literals (including negative)
        String literal = tryGetSimpleIntegerLiteral(expression);
        if (literal != null) {
            try {
                int value = Integer.parseInt(literal);
                context.emitter().emitLoadIntConstant(value, "R6", "return constant " + literal);
                return true;
            } catch (NumberFormatException e) {
                // If parsing fails (e.g., float literal), fall through to normal generation
                // This prevents incorrect optimization of float expressions
            }
        }
        
        // For other cases (including float expressions), generate into R0 and move to R6
        return false;
    }
    
    /**
     * Attempts to detect simple return of an integer literal, e.g.:
     * <ul>
     *   <li>{@code return 31;}</li>
     *   <li>{@code return -84;}</li>
     * </ul>
     * 
     * <p>Navigates through single-child expression wrappers until reaching
     * a {@code <primarni_izraz>} with a BROJ terminal, or a unary minus
     * followed by a literal.
     * 
     * @param expression the expression node to analyze
     * @return the literal value as string (with minus if negative), or null if not a simple literal
     */
    private String tryGetSimpleIntegerLiteral(NonTerminalNode expression) {
        NonTerminalNode node = expression;
        boolean isNegative = false;
        
        // Drill down while there is exactly one non-terminal child
        outer:
        while (true) {
            List<ParseNode> children = node.children();
            
            // Check for unary minus at <unarni_izraz> level
            if ("<unarni_izraz>".equals(node.symbol()) && children.size() == 2) {
                ParseNode first = children.get(0);
                if (first instanceof hr.fer.ppj.semantics.tree.NonTerminalNode unaryOp && 
                    "<unarni_operator>".equals(unaryOp.symbol())) {
                    for (ParseNode opChild : unaryOp.children()) {
                        if (opChild instanceof hr.fer.ppj.semantics.tree.TerminalNode terminal && "MINUS".equals(terminal.symbol())) {
                            isNegative = true;
                            if (children.get(1) instanceof NonTerminalNode operand) {
                                node = operand;
                                continue outer;
                            }
                            break outer;
                        }
                    }
                }
            }
            
            // Check for <primarni_izraz> with BROJ terminal
            if ("<primarni_izraz>".equals(node.symbol())) {
                for (ParseNode child : children) {
                    if (child instanceof hr.fer.ppj.semantics.tree.TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
                        String value = terminal.lexeme();
                        // CRITICAL FIX: Only optimize if this is truly an INTEGER literal, not a float
                        // This prevents incorrect optimization of expressions like -(3.0 * 4.0)
                        // where we might extract "3.0" and treat it as "-3.0", ignoring the multiplication
                        if (hr.fer.ppj.codegen.util.FloatCodegenHelper.isFloatLiteral(value)) {
                            // This is a float literal - do not optimize it here
                            // Let the normal expression generation handle it
                            return null;
                        }
                        return isNegative ? "-" + value : value;
                    }
                }
                break;
            }
            
            // Look for single non-terminal child to continue drilling
            NonTerminalNode singleChild = null;
            for (ParseNode child : children) {
                if (child instanceof NonTerminalNode nt) {
                    if (singleChild != null) {
                        break outer;
                    }
                    singleChild = nt;
                } else if (child instanceof hr.fer.ppj.semantics.tree.TerminalNode) {
                    break outer;
                }
            }
            
            if (singleChild == null) {
                break;
            }
            
            node = singleChild;
            
            // Continue drilling through expression wrappers
            String symbol = node.symbol();
            if ("<cast_izraz>".equals(symbol) || 
                "<unarni_izraz>".equals(symbol) ||
                "<postfiks_izraz>".equals(symbol) ||
                "<primarni_izraz>".equals(symbol) ||
                "<izraz_pridruzivanja>".equals(symbol) ||
                "<log_ili_izraz>".equals(symbol) ||
                "<log_i_izraz>".equals(symbol) ||
                "<bin_ili_izraz>".equals(symbol) ||
                "<bin_xili_izraz>".equals(symbol) ||
                "<bin_i_izraz>".equals(symbol) ||
                "<jednakosni_izraz>".equals(symbol) ||
                "<odnosni_izraz>".equals(symbol) ||
                "<aditivni_izraz>".equals(symbol) ||
                "<multiplikativni_izraz>".equals(symbol)) {
                continue;
            }
            
            break;
        }
        
        return null;
    }
    
    /**
     * Generates code for returning a struct by value.
     * 
     * <p>For struct returns, the caller provides the address of a result buffer in R2.
     * This method copies the struct from the return expression to that memory location.
     * 
     * <p><b>Calling Convention:</b>
     * <ul>
     *   <li>Caller sets R2 = address of result buffer before CALL</li>
     *   <li>Callee copies struct from return expression to [R2]</li>
     *   <li>R6 is unused for struct returns</li>
     * </ul>
     * 
     * @param expression the return expression (must evaluate to a struct)
     */
    private void generateStructReturn(NonTerminalNode expression) {
        // Get return expression type to determine struct size
        Type exprType = expression.attributes() != null ? expression.attributes().type() : null;
        if (exprType == null) {
            throw new IllegalStateException("Return expression has no type annotation");
        }
        
        Type strippedType = TypeSystem.stripConst(exprType);
        if (!(strippedType instanceof StructType structType)) {
            throw new IllegalStateException("Struct return but expression is not a struct type: " + strippedType);
        }
        
        int structSize = StructLayoutCalculator.calculateStructSize(structType);
        
        // Compute address of return expression (source struct)
        // This handles variables, field access, etc.
        addressGenerator.generateAddress(expression, "R0"); // Source address in R0
        
        // R2 contains the return buffer address (set by caller)
        // Copy struct from source (R0) to destination (R2)
        
        // Save R2 (return buffer) to R3, since we'll use R2 for counter
        context.emitter().emitInstruction("MOVE", "R2", "R3", "save return buffer address");
        
        // Source address is already in R0
        // Destination address is now in R3
        
        String copyLoopLabel = context.labelGenerator().generateLabel();
        String copyEndLabel = context.labelGenerator().generateLabel();
        
        // Initialize counter: R2 = remaining bytes
        context.emitter().emitInstruction("MOVE", "%D " + structSize, "R2", "remaining bytes");
        
        context.emitter().emitLabel(copyLoopLabel, "struct copy loop");
        
        // Check if counter is zero
        context.emitter().emitInstruction("CMP", "R2", "%D 0", null);
        context.emitter().emitInstruction("JP_EQ", copyEndLabel, "done if counter == 0");
        
        // Load word from source: R1 = *R0
        context.emitter().emitInstruction("LOAD", "R1", "(R0)", "load word from source");
        
        // Store word to destination: *R3 = R1
        context.emitter().emitInstruction("STORE", "R1", "(R3)", "store word to destination");
        
        // Increment pointers: R0 += 4, R3 += 4
        context.emitter().emitInstruction("ADD", "R0", "%D 4", "R0", "increment source pointer");
        context.emitter().emitInstruction("ADD", "R3", "%D 4", "R3", "increment dest pointer");
        
        // Decrement counter: R2 -= 4
        context.emitter().emitInstruction("SUB", "R2", "%D 4", "R2", "decrement remaining bytes");
        
        // Loop back
        context.emitter().emitInstruction("JP", copyLoopLabel, "continue copy loop");
        
        context.emitter().emitLabel(copyEndLabel, "end struct copy");
        
        // R6 is unused for struct returns (can optionally set to 0)
        context.emitter().emitInstruction("MOVE", "%D 0", "R6", "struct return (R6 unused)");
    }
    
    /**
     * Generates the function epilogue (deallocate locals, restore frame pointer, return).
     * 
     * <p>This is a fallback method used when a return statement is encountered
     * but no function exit label is available.
     */
    private void generateFunctionEpilogue() {
        if (context.activationRecord() == null) {
            context.emitter().emitInstruction("RET", null, null, "return from function");
            return;
        }
        
        prologueEpilogueGenerator.generateEpilogue(context, context.activationRecord());
    }
}

