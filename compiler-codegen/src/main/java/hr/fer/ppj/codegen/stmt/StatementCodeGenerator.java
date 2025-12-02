package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for statements.
 * 
 * <p>This class handles the generation of code for all types of statements
 * in ppjC, including:
 * <ul>
 *   <li>Expression statements</li>
 *   <li>Compound statements (blocks)</li>
 *   <li>Conditional statements (if-else)</li>
 *   <li>Loop statements (while, for)</li>
 *   <li>Jump statements (break, continue, return)</li>
 * </ul>
 * 
 * <p>The generator maintains proper control flow by generating appropriate
 * labels and jump instructions, and handles nested scopes for compound
 * statements.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StatementCodeGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator exprGen;
    
    /**
     * Creates a new statement code generator.
     * 
     * @param context the code generation context
     */
    public StatementCodeGenerator(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.exprGen = new ExpressionCodeGenerator(context);
    }
    
    /**
     * Generates code for a statement.
     * 
     * @param statement the statement node to generate code for
     */
    public void generateStatement(NonTerminalNode statement) {
        Objects.requireNonNull(statement, "statement must not be null");
        
        String symbol = statement.symbol();
        
        switch (symbol) {
            case "<naredba>" -> {
                // Delegate to the specific statement type
                NonTerminalNode child = (NonTerminalNode) statement.children().get(0);
                generateStatement(child);
            }
            case "<slozena_naredba>" -> generateCompoundStatement(statement);
            case "<izraz_naredba>" -> generateExpressionStatement(statement);
            case "<naredba_grananja>" -> generateBranchingStatement(statement);
            case "<naredba_petlje>" -> generateLoopStatement(statement);
            case "<naredba_skoka>" -> generateJumpStatement(statement);
            default -> throw new IllegalArgumentException("Unknown statement type: " + symbol);
        }
    }
    
    /**
     * Generates code for compound statements (blocks).
     */
    private void generateCompoundStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        context.emitter().emitComment("Compound statement");
        
        // Process all statements in the block
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                String symbol = nonTerminal.symbol();
                
                if ("<lista_naredbi>".equals(symbol)) {
                    generateStatementList(nonTerminal);
                } else if ("<lista_deklaracija>".equals(symbol)) {
                    // Handle local variable declarations
                    generateLocalDeclarations(nonTerminal);
                }
            }
        }
    }
    
    /**
     * Generates code for a list of statements.
     */
    private void generateStatementList(NonTerminalNode node) {
        String symbol = node.symbol();
        
        if ("<lista_naredbi>".equals(symbol)) {
            List<ParseNode> children = node.children();
            
            for (ParseNode child : children) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    String childSymbol = nonTerminal.symbol();
                    if ("<lista_naredbi>".equals(childSymbol)) {
                        // Recursive case for nested statement lists
                        generateStatementList(nonTerminal);
                    } else if ("<naredba>".equals(childSymbol)) {
                        // Use this instance to maintain context
                        generateStatement(nonTerminal);
                    }
                }
            }
        }
    }
    
    /**
     * Generates code for expression statements.
     */
    private void generateExpressionStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 2) {
            // <izraz> TOCKAZAREZ
            NonTerminalNode expression = (NonTerminalNode) children.get(0);
            exprGen.generateExpression(expression);
            // Result is discarded (expression statement)
        }
        // If only TOCKAZAREZ, it's an empty statement - no code needed
    }
    
    /**
     * Generates code for branching statements (if-else).
     */
    private void generateBranchingStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 5) {
            // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba>
            NonTerminalNode condition = (NonTerminalNode) children.get(2);
            NonTerminalNode thenStmt = (NonTerminalNode) children.get(4);
            
            var labels = context.labelGenerator().generateIfLabels();
            
            context.emitter().emitComment("If statement");
            
            // Generate condition
            exprGen.generateExpression(condition);
            
            // Jump to end if condition is false
            context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
            context.emitter().emitInstruction("JP_EQ", labels.endLabel(), null, "if condition is false");
            
            // Generate then statement
            generateStatement(thenStmt);
            
            context.emitter().emitLabel(labels.endLabel(), "end if");
            
        } else if (children.size() == 7) {
            // KR_IF L_ZAGRADA <izraz> D_ZAGRADA <naredba> KR_ELSE <naredba>
            NonTerminalNode condition = (NonTerminalNode) children.get(2);
            NonTerminalNode thenStmt = (NonTerminalNode) children.get(4);
            NonTerminalNode elseStmt = (NonTerminalNode) children.get(6);
            
            var labels = context.labelGenerator().generateIfLabels();
            
            context.emitter().emitComment("If-else statement");
            
            // Generate condition
            exprGen.generateExpression(condition);
            
            // Jump to else if condition is false
            context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
            context.emitter().emitInstruction("JP_EQ", labels.elseLabel(), null, "if condition is false");
            
            // Generate then statement
            generateStatement(thenStmt);
            context.emitter().emitInstruction("JP", labels.endLabel(), null, "skip else");
            
            // Generate else statement
            context.emitter().emitLabel(labels.elseLabel(), "else clause");
            generateStatement(elseStmt);
            
            context.emitter().emitLabel(labels.endLabel(), "end if-else");
        }
    }
    
    /**
     * Generates code for loop statements (while, for).
     */
    private void generateLoopStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        ParseNode firstChild = children.get(0);
        
        if (firstChild instanceof TerminalNode terminal) {
            String keyword = terminal.symbol();
            
            if ("KR_WHILE".equals(keyword)) {
                generateWhileLoop(node);
            } else if ("KR_FOR".equals(keyword)) {
                generateForLoop(node);
            }
        }
    }
    
    /**
     * Generates code for while loops.
     */
    private void generateWhileLoop(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        // KR_WHILE L_ZAGRADA <izraz> D_ZAGRADA <naredba>
        
        NonTerminalNode condition = (NonTerminalNode) children.get(2);
        NonTerminalNode body = (NonTerminalNode) children.get(4);
        
        var labels = context.labelGenerator().generateLoopLabels();
        
        context.emitter().emitComment("While loop");
        
        // Loop start
        context.emitter().emitLabel(labels.loopLabel(), "while loop start");
        
        // Generate condition
        exprGen.generateExpression(condition);
        
        // Exit if condition is false
        context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
        context.emitter().emitInstruction("JP_EQ", labels.breakLabel(), null, "exit while loop");
        
        // Generate loop body with break/continue context
        CodeGenContext loopContext = context.withLoopLabels(labels.breakLabel(), labels.continueLabel());
        StatementCodeGenerator bodyGen = new StatementCodeGenerator(loopContext);
        bodyGen.generateStatement(body);
        
        // Continue point (for continue statements)
        context.emitter().emitLabel(labels.continueLabel(), "while continue");
        
        // Jump back to condition
        context.emitter().emitInstruction("JP", labels.loopLabel(), null, "repeat while loop");
        
        // Loop end (for break statements)
        context.emitter().emitLabel(labels.breakLabel(), "while loop end");
    }
    
    /**
     * Generates code for for loops.
     */
    private void generateForLoop(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 6) {
            // KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> D_ZAGRADA <naredba>
            NonTerminalNode init = (NonTerminalNode) children.get(2);
            NonTerminalNode condition = (NonTerminalNode) children.get(3);
            NonTerminalNode body = (NonTerminalNode) children.get(5);
            
            var labels = context.labelGenerator().generateLoopLabels();
            
            // Generate initialization
            generateStatement(init);
            
            // Loop start
            context.emitter().emitLabel(labels.loopLabel(), "for loop");
            
            // Generate condition (if present)
            if (condition.children().size() > 1) {
                NonTerminalNode condExpr = (NonTerminalNode) condition.children().get(0);
                exprGen.generateExpression(condExpr);
                
                // Exit if condition is false
                context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
                context.emitter().emitInstruction("JP_EQ", labels.breakLabel(), null, "exit for loop");
            }
            
            // Generate loop body with break/continue context
            CodeGenContext loopContext = context.withLoopLabels(labels.breakLabel(), labels.continueLabel());
            StatementCodeGenerator bodyGen = new StatementCodeGenerator(loopContext);
            bodyGen.generateStatement(body);
            
            // Jump back to condition
            context.emitter().emitInstruction("JP", labels.loopLabel(), null, "repeat for loop");
            
            // Loop end
            context.emitter().emitLabel(labels.breakLabel());
            
        } else if (children.size() == 7) {
            // KR_FOR L_ZAGRADA <izraz_naredba> <izraz_naredba> <izraz> D_ZAGRADA <naredba>
            NonTerminalNode init = (NonTerminalNode) children.get(2);
            NonTerminalNode condition = (NonTerminalNode) children.get(3);
            NonTerminalNode increment = (NonTerminalNode) children.get(4);
            NonTerminalNode body = (NonTerminalNode) children.get(6);
            
            var labels = context.labelGenerator().generateLoopLabels();
            
            context.emitter().emitComment("For loop with increment");
            
            // Generate initialization
            generateStatement(init);
            
            // Loop start
            context.emitter().emitLabel(labels.loopLabel(), "for loop start");
            
            // Generate condition (if present)
            if (condition.children().size() > 1) {
                NonTerminalNode condExpr = (NonTerminalNode) condition.children().get(0);
                exprGen.generateExpression(condExpr);
                
                // Exit if condition is false
                context.emitter().emitInstruction("CMP", "R0", "%D 0", null);
                context.emitter().emitInstruction("JP_EQ", labels.breakLabel(), "exit for loop");
            }
            
            // Generate loop body with break/continue context
            CodeGenContext loopContext = context.withLoopLabels(labels.breakLabel(), labels.continueLabel());
            StatementCodeGenerator bodyGen = new StatementCodeGenerator(loopContext);
            bodyGen.generateStatement(body);
            
            // Continue point - generate increment
            context.emitter().emitLabel(labels.continueLabel(), "for loop increment");
            exprGen.generateExpression(increment);
            
            // Jump back to condition
            context.emitter().emitInstruction("JP", labels.loopLabel(), "repeat for loop");
            
            // Loop end
            context.emitter().emitLabel(labels.breakLabel(), "for loop end");
        }
    }
    
    /**
     * Generates code for jump statements (break, continue, return).
     */
    private void generateJumpStatement(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        TerminalNode keyword = (TerminalNode) children.get(0);
        
        String jumpType = keyword.symbol();
        
        switch (jumpType) {
            case "KR_RETURN" -> {
                if (children.size() == 3) {
                    // KR_RETURN <izraz> TOCKAZAREZ
                    NonTerminalNode expression = (NonTerminalNode) children.get(1);
                    
                    // Optimization: try to generate expression directly into R6 when possible
                    if (tryGenerateReturnExpressionDirectly(expression)) {
                        // Expression was generated directly into R6
                    } else {
                        // Fallback: generate into R0, then move to R6
                        // Ensure expression is actually generated
                        exprGen.generateExpression(expression);
                        // Verify R0 was set (if not, this is a bug)
                        context.emitter().emitInstruction("MOVE", "R0", "R6", "return value");
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
            case "KR_BREAK" -> {
                if (context.loopBreakLabel() != null) {
                    context.emitter().emitInstruction("JP", context.loopBreakLabel(), "break from loop");
                } else {
                    throw new IllegalStateException("Break statement outside of loop");
                }
            }
            case "KR_CONTINUE" -> {
                if (context.loopContinueLabel() != null) {
                    context.emitter().emitInstruction("JP", context.loopContinueLabel(), "continue loop");
                } else {
                    throw new IllegalStateException("Continue statement outside of loop");
                }
            }
        }
    }
    
    /**
     * Attempts to generate a return expression directly into R6, avoiding
     * unnecessary MOVE R0, R6 instructions.
     * 
     * @param expression the return expression
     * @return true if expression was generated directly into R6, false otherwise
     */
    private boolean tryGenerateReturnExpressionDirectly(NonTerminalNode expression) {
        // Try simple cases first: integer literals (including negative)
        String literal = tryGetSimpleIntegerLiteral(expression);
        if (literal != null) {
            context.emitter().emitInstruction("MOVE", "%D " + literal, "R6", "return constant " + literal);
            return true;
        }
        
        // For other cases, we'd need to modify ExpressionCodeGenerator to support
        // generating directly into R6, which is more complex. For now, we'll
        // generate into R0 and move to R6 (which is acceptable).
        return false;
    }
    
    /**
     * Attempts to detect simple return of an integer literal, e.g.:
     * return 31;
     * return -84;
     * 
     * Navigates through single-child expression wrappers until reaching
     * a &lt;primarni_izraz&gt; with a BROJ terminal, or a unary minus
     * followed by a literal.
     * 
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
            // Structure: <unarni_izraz> -> <unarni_operator> -> MINUS + <cast_izraz>
            if ("<unarni_izraz>".equals(node.symbol()) && children.size() == 2) {
                ParseNode first = children.get(0);
                // Check if first child is <unarni_operator> containing MINUS
                if (first instanceof NonTerminalNode unaryOp && 
                    "<unarni_operator>".equals(unaryOp.symbol())) {
                    // Check if <unarni_operator> contains MINUS
                    for (ParseNode opChild : unaryOp.children()) {
                        if (opChild instanceof TerminalNode terminal && "MINUS".equals(terminal.symbol())) {
                            isNegative = true;
                            // Continue with the operand (<cast_izraz>)
                            if (children.get(1) instanceof NonTerminalNode operand) {
                                node = operand;
                                continue outer; // Continue outer loop with new node
                            }
                            break outer;
                        }
                    }
                }
            }
            
            // Check for <primarni_izraz> with BROJ terminal
            if ("<primarni_izraz>".equals(node.symbol())) {
                for (ParseNode child : children) {
                    if (child instanceof TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
                        String value = terminal.lexeme();
                        return isNegative ? "-" + value : value;
                    }
                }
                // If we found <primarni_izraz> but no BROJ, stop
                break;
            }
            
            // Look for single non-terminal child to continue drilling
            NonTerminalNode singleChild = null;
            for (ParseNode child : children) {
                if (child instanceof NonTerminalNode nt) {
                    if (singleChild != null) {
                        // Multiple non-terminals - can't be a simple literal
                        break outer;
                    }
                    singleChild = nt;
                } else if (child instanceof TerminalNode) {
                    // Terminal found - check if it's part of a simple structure
                    // If it's not BROJ in <primarni_izraz>, we're done
                    break outer;
                }
            }
            
            if (singleChild == null) {
                // No single child to continue with
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
                // Continue drilling down - these are all expression wrappers
                continue;
            }
            
            // If we hit something else, stop
            break;
        }
        
        return null;
    }
    
    /**
     * Generates code for local variable declarations.
     */
    private void generateLocalDeclarations(NonTerminalNode declarationList) {
        context.emitter().emitComment("Local variable declarations");
        
        // Process each declaration in the list
        processDeclarationList(declarationList);
    }
    
    /**
     * Processes a declaration list recursively.
     */
    private void processDeclarationList(NonTerminalNode node) {
        String symbol = node.symbol();
        
        if ("<lista_deklaracija>".equals(symbol)) {
            List<ParseNode> children = node.children();
            
            for (ParseNode child : children) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    String childSymbol = nonTerminal.symbol();
                    
                    if ("<lista_deklaracija>".equals(childSymbol)) {
                        // Recursive case
                        processDeclarationList(nonTerminal);
                    } else if ("<deklaracija>".equals(childSymbol)) {
                        // Process individual declaration
                        processLocalDeclaration(nonTerminal);
                    }
                }
            }
        }
    }
    
    /**
     * Processes a single local variable declaration.
     */
    private void processLocalDeclaration(NonTerminalNode declaration) {
        // Find variable names and their initializers
        List<ParseNode> children = declaration.children();
        
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                String symbol = nonTerminal.symbol();
                
                if ("<lista_init_deklaratora>".equals(symbol)) {
                    processInitDeclaratorList(nonTerminal);
                }
            }
        }
    }
    
    /**
     * Processes a list of init declarators (variable names with optional initializers).
     */
    private void processInitDeclaratorList(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single declarator
            processInitDeclarator((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Multiple declarators: <lista_init_deklaratora> ZAREZ <init_deklarator>
            processInitDeclaratorList((NonTerminalNode) children.get(0));
            processInitDeclarator((NonTerminalNode) children.get(2));
        }
    }
    
    /**
     * Processes a single init declarator (variable with optional initializer).
     */
    private void processInitDeclarator(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        String varName = null;
        
        if (children.size() == 1) {
            // Just a declarator (no initializer)
            varName = extractVariableName((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Declarator with initializer: <deklarator> OP_ASSIGN <inicijalizator>
            varName = extractVariableName((NonTerminalNode) children.get(0));
        }
        
        if (varName != null && context.activationRecord() != null) {
            // Ensure variable is in activation record (fallback if not added during pre-processing)
            if (!context.activationRecord().hasVariable(varName)) {
                // Extract array size if it's an array
                int size = extractArraySize((NonTerminalNode) children.get(0));
                context.activationRecord().addLocalVariable(varName, size);
            }
            
            String address = context.activationRecord().getVariableAddress(varName);
            
            if (children.size() == 1) {
                // Just a declarator (no initializer)
                context.emitter().emitComment("Local variable " + varName + " at " + address + " (no initializer)");
            } else if (children.size() == 3) {
                // Declarator with initializer: <deklarator> OP_ASSIGN <inicijalizator>
                NonTerminalNode initializer = (NonTerminalNode) children.get(2);
                context.emitter().emitComment("Local variable " + varName + " at " + address + " with initializer");
                
                // Generate initializer expression
                exprGen.generateExpression(initializer);
                
                // Store result in local variable
                context.emitter().emitInstruction("STORE", "R0", address, "initialize " + varName);
            }
        }
    }
    
    /**
     * Extracts array size from a declarator node.
     * Returns 4 (default) for non-arrays, or size * element_size for arrays.
     * Note: This method is used as a fallback when variable is not in activation record.
     * It defaults to 4 bytes per element (int), but ideally should check the type specifier.
     */
    private int extractArraySize(NonTerminalNode declarator) {
        // Find <izravni_deklarator>
        for (ParseNode child : declarator.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                return extractArraySizeFromDirectDeclarator(nonTerminal);
            }
        }
        return 4; // Default: simple variable
    }
    
    /**
     * Extracts array size from a direct declarator.
     * Defaults to 4 bytes per element (int). For char arrays, this should be 1 byte,
     * but without access to the type specifier, we default to int.
     */
    private int extractArraySizeFromDirectDeclarator(NonTerminalNode directDeclarator) {
        List<ParseNode> children = directDeclarator.children();
        
        // Handle nested <izravni_deklarator> structure
        // For arrays: <izravni_deklarator> -> <izravni_deklarator> -> IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
        NonTerminalNode nestedDeclarator = null;
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                nestedDeclarator = nonTerminal;
                break;
            }
        }
        
        // If nested, recurse into it
        if (nestedDeclarator != null) {
            return extractArraySizeFromDirectDeclarator(nestedDeclarator);
        }
        
        // Check if it's an array: look for L_UGL_ZAGRADA followed by BROJ followed by D_UGL_ZAGRADA
        // Need to check all possible positions (including at the end)
        for (int i = 0; i <= children.size() - 3; i++) {
            if (i + 2 < children.size()) {
                ParseNode node1 = children.get(i);
                ParseNode node2 = children.get(i + 1);
                ParseNode node3 = children.get(i + 2);
                
                if (node1 instanceof TerminalNode t1 && "L_UGL_ZAGRADA".equals(t1.symbol()) &&
                    node2 instanceof TerminalNode t2 && "BROJ".equals(t2.symbol()) &&
                    node3 instanceof TerminalNode t3 && "D_UGL_ZAGRADA".equals(t3.symbol())) {
                    // It's an array declaration
                    try {
                        int arraySize = Integer.parseInt(t2.lexeme());
                        // Default to 4 bytes per element (int)
                        // Note: For char arrays, this should be 1, but we don't have type info here
                        // The correct size should be determined from the type specifier in the declaration
                        return arraySize * 4; // 4 bytes per element (int)
                    } catch (NumberFormatException e) {
                        return 4; // Invalid size, treat as simple variable
                    }
                }
            }
        }
        
        return 4; // Not an array, simple variable
    }
    
    /**
     * Extracts variable name from a declarator.
     */
    private String extractVariableName(NonTerminalNode declarator) {
        // Find the identifier in the declarator structure
        return findIdentifier(declarator);
    }
    
    /**
     * Recursively finds the first identifier in a node.
     */
    private String findIdentifier(NonTerminalNode node) {
        for (ParseNode child : node.children()) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal) {
                String result = findIdentifier(nonTerminal);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
    
    /**
     * Generates the function epilogue (deallocate locals, restore frame pointer, return).
     * 
     * <p>Generates the canonical epilogue:
     * <pre>
     * ADD  R7, %D K, R7     ; deallocate locals
     * POP  R5               ; restore old frame pointer
     * RET                   ; pops return address and jumps
     * </pre>
     */
    private void generateFunctionEpilogue() {
        if (context.activationRecord() == null) {
            // Not in a function, just return
            context.emitter().emitInstruction("RET", null, null, "return from function");
            return;
        }
        
        int localSize = context.activationRecord().getLocalVariablesSize();
        
        // Deallocate local variables
        if (localSize > 0) {
            context.emitter().emitInstruction("ADD", "R7", "%D " + localSize, "R7", 
                                            "deallocate local variables");
        }
        
        // Restore old frame pointer
        context.emitter().emitInstruction("POP", "R5", null, "restore old frame pointer");
        
        // Return to caller (RET pops return address and jumps)
        context.emitter().emitInstruction("RET", null, null, "return to caller");
    }
    
}
