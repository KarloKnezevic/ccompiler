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
 * Orchestrates FRISC assembly code generation for all expression types.
 * 
 * <p>This class serves as the main dispatcher for expression code generation, delegating
 * to specialized generators based on the expression type. It implements a hierarchical
 * visitor pattern that matches the C expression grammar structure.
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <pre>
 * &lt;izraz&gt; ::= &lt;izraz_pridruzivanja&gt;
 *          | &lt;izraz&gt; ZAREZ &lt;izraz_pridruzivanja&gt;
 * 
 * &lt;izraz_pridruzivanja&gt; ::= &lt;log_ili_izraz&gt;
 *                          | &lt;unarni_izraz&gt; OP_PRIDRUZI &lt;izraz_pridruzivanja&gt;
 *                          | &lt;unarni_izraz&gt; OP_INC
 *                          | &lt;unarni_izraz&gt; OP_DEC
 * 
 * &lt;log_ili_izraz&gt; ::= &lt;log_i_izraz&gt;
 *                   | &lt;log_ili_izraz&gt; OP_ILI &lt;log_i_izraz&gt;
 * 
 * &lt;log_i_izraz&gt; ::= &lt;bin_ili_izraz&gt;
 *                 | &lt;log_i_izraz&gt; OP_I &lt;bin_ili_izraz&gt;
 * 
 * &lt;bin_ili_izraz&gt; ::= &lt;bin_xili_izraz&gt;
 *                    | &lt;bin_ili_izraz&gt; OP_BIN_ILI &lt;bin_xili_izraz&gt;
 * 
 * &lt;bin_xili_izraz&gt; ::= &lt;bin_i_izraz&gt;
 *                     | &lt;bin_xili_izraz&gt; OP_BIN_XILI &lt;bin_i_izraz&gt;
 * 
 * &lt;bin_i_izraz&gt; ::= &lt;jednakosni_izraz&gt;
 *                  | &lt;bin_i_izraz&gt; OP_BIN_I &lt;jednakosni_izraz&gt;
 * 
 * &lt;jednakosni_izraz&gt; ::= &lt;odnosni_izraz&gt;
 *                       | &lt;jednakosni_izraz&gt; OP_EQ &lt;odnosni_izraz&gt;
 *                       | &lt;jednakosni_izraz&gt; OP_NE &lt;odnosni_izraz&gt;
 * 
 * &lt;odnosni_izraz&gt; ::= &lt;aditivni_izraz&gt;
 *                    | &lt;odnosni_izraz&gt; OP_LT &lt;aditivni_izraz&gt;
 *                    | &lt;odnosni_izraz&gt; OP_GT &lt;aditivni_izraz&gt;
 *                    | &lt;odnosni_izraz&gt; OP_LTE &lt;aditivni_izraz&gt;
 *                    | &lt;odnosni_izraz&gt; OP_GTE &lt;aditivni_izraz&gt;
 * 
 * &lt;aditivni_izraz&gt; ::= &lt;multiplikativni_izraz&gt;
 *                     | &lt;aditivni_izraz&gt; PLUS &lt;multiplikativni_izraz&gt;
 *                     | &lt;aditivni_izraz&gt; MINUS &lt;multiplikativni_izraz&gt;
 * 
 * &lt;multiplikativni_izraz&gt; ::= &lt;cast_izraz&gt;
 *                            | &lt;multiplikativni_izraz&gt; OP_PUTA &lt;cast_izraz&gt;
 *                            | &lt;multiplikativni_izraz&gt; OP_DIJELI &lt;cast_izraz&gt;
 *                            | &lt;multiplikativni_izraz&gt; OP_MOD &lt;cast_izraz&gt;
 * 
 * &lt;cast_izraz&gt; ::= &lt;unarni_izraz&gt;
 *                  | L_ZAGRADA &lt;ime_tipa&gt; D_ZAGRADA &lt;cast_izraz&gt;
 * 
 * &lt;unarni_izraz&gt; ::= &lt;postfiks_izraz&gt;
 *                   | OP_INC &lt;unarni_izraz&gt;
 *                   | OP_DEC &lt;unarni_izraz&gt;
 *                   | &lt;unarni_operator&gt; &lt;cast_izraz&gt;
 * 
 * &lt;postfiks_izraz&gt; ::= &lt;primarni_izraz&gt;
 *                      | &lt;postfiks_izraz&gt; L_ZAGRADA D_ZAGRADA
 *                      | &lt;postfiks_izraz&gt; L_ZAGRADA &lt;lista_argumenata&gt; D_ZAGRADA
 *                      | &lt;postfiks_izraz&gt; L_UGL_ZAGRADA &lt;izraz&gt; D_UGL_ZAGRADA
 *                      | &lt;postfiks_izraz&gt; OP_INC
 *                      | &lt;postfiks_izraz&gt; OP_DEC
 * 
 * &lt;primarni_izraz&gt; ::= IDN | BROJ | ZNAK | NIZ_ZNAKOVA
 *                      | L_ZAGRADA &lt;izraz&gt; D_ZAGRADA
 * </pre>
 * 
 * <p><b>Delegation Strategy:</b>
 * <ul>
 *   <li><b>Primary expressions:</b> Delegated to {@link PrimaryExpressionGenerator}</li>
 *   <li><b>Unary expressions:</b> Delegated to {@link UnaryExpressionGenerator} (except pre-inc/dec)</li>
 *   <li><b>Postfix expressions:</b> Handles array indexing and function calls, delegates to specialized generators</li>
 *   <li><b>Binary expressions:</b> Delegated to {@link BinaryExpressionGenerator}</li>
 *   <li><b>Logical expressions:</b> Delegated to {@link LogicalExpressionGenerator} (short-circuit evaluation)</li>
 *   <li><b>Assignment expressions:</b> Delegated to {@link AssignmentExpressionGenerator}</li>
 *   <li><b>Array operations:</b> Delegated to {@link ArrayExpressionGenerator}</li>
 *   <li><b>Function calls:</b> Delegated to {@link FunctionCallGenerator}</li>
 * </ul>
 * 
 * <p><b>FRISC Semantics:</b>
 * <ul>
 *   <li><b>Register Usage:</b> Expression results are typically left in R0</li>
 *   <li><b>Stack Usage:</b> Left operands are saved on stack before evaluating right operands</li>
 *   <li><b>Short-Circuit Evaluation:</b> Logical AND (&&) and OR (||) use conditional jumps</li>
 *   <li><b>Type Conversions:</b> Handled according to C semantics (int↔char via masking)</li>
 *   <li><b>Large Immediates:</b> Constants >20 bits constructed using SHL/ADD pattern</li>
 * </ul>
 * 
 * <p><b>Expression Evaluation Order:</b>
 * <ol>
 *   <li>Primary expressions (identifiers, constants)</li>
 *   <li>Postfix operations (array indexing, function calls, post-inc/dec)</li>
 *   <li>Unary operations (+, -, !, ~, casts, pre-inc/dec)</li>
 *   <li>Multiplicative operations (*, /, %)</li>
 *   <li>Additive operations (+, -)</li>
 *   <li>Relational operations (<, >, <=, >=)</li>
 *   <li>Equality operations (==, !=)</li>
 *   <li>Bitwise operations (&, ^, |)</li>
 *   <li>Logical operations (&&, ||)</li>
 *   <li>Assignment operations (=, +=, -=, etc.)</li>
 *   <li>Comma expressions (,)</li>
 * </ol>
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
     * to specialized generators for different expression categories. The generators
     * form a hierarchical structure matching the C expression grammar precedence.
     * 
     * <p><b>Generator Initialization Order:</b>
     * <ol>
     *   <li>Primary generator (base level - no dependencies)</li>
     *   <li>Unary generator (depends on primary)</li>
     *   <li>Assignment generator (depends on unary)</li>
     *   <li>Array generator (depends on assignment and expression generator)</li>
     *   <li>Function call generator (depends on expression generator)</li>
     *   <li>Binary generator (depends on expression generator)</li>
     *   <li>Logical generator (depends on expression generator)</li>
     * </ol>
     * 
     * <p><b>Circular Dependency Resolution:</b>
     * <ul>
     *   <li>Array generator needs assignment generator for array assignments</li>
     *   <li>Assignment generator needs array generator for array element assignments</li>
     *   <li>Resolved by setting array generator reference after both are created</li>
     * </ul>
     * 
     * @param context the code generation context (must not be null)
     */
    public ExpressionCodeGenerator(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        
        // Initialize generators (order matters due to dependencies)
        // Start with base-level generators that have no dependencies
        this.primaryGenerator = new PrimaryExpressionGenerator(context, this);
        this.unaryGenerator = new UnaryExpressionGenerator(context, this);
        
        // Assignment generator needs unary generator (for pre/post inc/dec)
        this.assignmentGenerator = new AssignmentExpressionGenerator(context, this);
        
        // Array generator needs assignment generator (for array assignments)
        this.arrayGenerator = new ArrayExpressionGenerator(context, this, assignmentGenerator);
        
        // Resolve circular dependency: assignment generator needs array generator
        // for handling array element assignments (a[i] = value)
        this.assignmentGenerator.setArrayGenerator(this.arrayGenerator);
        
        // Function call generator needs expression generator (for argument evaluation)
        this.functionCallGenerator = new FunctionCallGenerator(context, this);
        
        // Binary and logical generators need expression generator (for operand evaluation)
        this.binaryGenerator = new BinaryExpressionGenerator(context, this);
        this.logicalGenerator = new LogicalExpressionGenerator(context, this);
    }
    
    /**
     * Generates FRISC code for an expression, leaving the result in register R0.
     * 
     * <p>This is the main entry point for expression code generation. It dispatches
     * to specialized generators based on the nonterminal symbol, following the
     * C expression grammar hierarchy.
     * 
     * <p><b>Grammar Rule:</b> Handles all expression nonterminals from {@code <izraz>}
     * down to {@code <primarni_izraz>}.
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>Result is always left in R0</li>
     *   <li>Complex expressions may use R1, R2 for temporary values</li>
     *   <li>Stack is used to save left operands before evaluating right operands</li>
     *   <li>Function calls use R6 for return values, then move to R0</li>
     * </ul>
     * 
     * @param expression the expression node ({@code <izraz>} or any sub-expression)
     * @throws IllegalArgumentException if the expression type is unknown
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
    /**
     * Generates code for initializers.
     * 
     * <p><b>Grammar Rule:</b> Handles {@code <inicijalizator>}:
     * <pre>
     * &lt;inicijalizator&gt; ::= &lt;izraz_pridruzivanja&gt;
     *                    | L_VIT_ZAGRADA &lt;lista_izraza_pridruzivanja&gt; D_VIT_ZAGRADA
     *                    | L_VIT_ZAGRADA &lt;lista_izraza_pridruzivanja&gt; ZAREZ D_VIT_ZAGRADA
     * </pre>
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>Simple initializers: Evaluates the assignment expression</li>
     *   <li>Array initializers: Currently defaults to 0 (full array initialization
     *       would require generating code for each element)</li>
     * </ul>
     * 
     * <p><b>Note:</b> Array initializers in global scope are handled by
     * {@link hr.fer.ppj.codegen.GlobalVariableGenerator}, not here.
     * 
     * @param node the initializer node ({@code <inicijalizator>})
     */
    private void generateInitializer(NonTerminalNode node) {
        // <inicijalizator> ::= <izraz_pridruzivanja>
        List<ParseNode> children = node.children();
        if (children.size() == 1) {
            // Simple initializer: evaluate the assignment expression
            generateExpression((NonTerminalNode) children.get(0));
        } else {
            // Handle array initializers or other complex cases
            // For array initializers, we'd need to generate code to initialize each element
            // For now, default to 0
            // Note: Global array initializers are handled separately by GlobalVariableGenerator
            context.emitter().emitInstruction("MOVE", "%D 0", "R0", "default initializer");
        }
    }
    
    /**
     * Generates code for unary expressions.
     * 
     * <p><b>Grammar Rule:</b> Handles {@code <unarni_izraz>}:
     * <pre>
     * &lt;unarni_izraz&gt; ::= &lt;postfiks_izraz&gt;
     *                   | OP_INC &lt;unarni_izraz&gt;
     *                   | OP_DEC &lt;unarni_izraz&gt;
     *                   | &lt;unarni_operator&gt; &lt;cast_izraz&gt;
     * </pre>
     * 
     * <p><b>Delegation Strategy:</b>
     * <ul>
     *   <li><b>Pre-increment/decrement (++x, --x):</b> Handled by {@link AssignmentExpressionGenerator}</li>
     *   <li><b>Other unary operators (+, -, !, ~):</b> Delegated to {@link UnaryExpressionGenerator}</li>
     *   <li><b>Single operand:</b> Delegates to {@code <postfiks_izraz>}</li>
     * </ul>
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>Pre-increment/decrement: Load, increment/decrement, store, return new value</li>
     *   <li>Unary minus: Uses {@code 0 - operand} pattern or direct negative constant</li>
     *   <li>Logical NOT: Compares operand to 0, returns 1 if zero, 0 otherwise</li>
     * </ul>
     * 
     * @param node the unary expression node ({@code <unarni_izraz>})
     */
    private void generateUnaryExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level (<postfiks_izraz>)
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 2) {
            ParseNode first = children.get(0);
            NonTerminalNode operand = (NonTerminalNode) children.get(1);
            
            if (first instanceof hr.fer.ppj.semantics.tree.TerminalNode terminal) {
                String operator = terminal.symbol();
                
                // Pre-increment/decrement are handled by assignment generator
                // These modify the operand in-place and return the new value
                if ("OP_INC".equals(operator)) {
                    assignmentGenerator.generatePreIncrement(operand);
                } else if ("OP_DEC".equals(operator)) {
                    assignmentGenerator.generatePreDecrement(operand);
                } else {
                    // Other unary operators (+, -, !, ~) are handled by unary generator
                    // Delegate to unary generator which handles <unarni_operator> structure
                    unaryGenerator.generateUnaryExpression(node);
                }
            } else if (first instanceof NonTerminalNode) {
                // First child is a non-terminal (e.g., <unarni_operator> containing MINUS/PLUS/etc.)
                // Structure: <unarni_izraz> -> <unarni_operator> -> MINUS + <cast_izraz>
                // Delegate to unary generator which handles this nested structure
                unaryGenerator.generateUnaryExpression(node);
            } else {
                // Unknown structure - delegate to operand (fallback)
                generateExpression(operand);
            }
        }
    }
    
    /**
     * Generates code for postfix expressions.
     * 
     * <p><b>Grammar Rule:</b> Handles {@code <postfiks_izraz>}:
     * <pre>
     * &lt;postfiks_izraz&gt; ::= &lt;primarni_izraz&gt;
     *                      | &lt;postfiks_izraz&gt; L_ZAGRADA D_ZAGRADA
     *                      | &lt;postfiks_izraz&gt; L_ZAGRADA &lt;lista_argumenata&gt; D_ZAGRADA
     *                      | &lt;postfiks_izraz&gt; L_UGL_ZAGRADA &lt;izraz&gt; D_UGL_ZAGRADA
     *                      | &lt;postfiks_izraz&gt; OP_INC
     *                      | &lt;postfiks_izraz&gt; OP_DEC
     * </pre>
     * 
     * <p><b>Postfix Operations:</b>
     * <ul>
     *   <li><b>Post-increment/decrement (x++, x--):</b> Handled by {@link AssignmentExpressionGenerator}
     *       - returns old value, then increments/decrements</li>
     *   <li><b>Function calls (f(), f(args)):</b> Handled by {@link FunctionCallGenerator}
     *       - evaluates arguments, calls function, returns R6</li>
     *   <li><b>Array indexing (a[i]):</b> Handled by {@link ArrayExpressionGenerator}
     *       - computes base + index*4, loads/stores element</li>
     * </ul>
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>Post-increment/decrement: Load value, save, increment/decrement, store, return saved value</li>
     *   <li>Function calls: Push arguments (right-to-left), CALL, cleanup stack, result in R6</li>
     *   <li>Array indexing: SHL index by 2 (multiply by 4), ADD to base, LOAD/STORE</li>
     * </ul>
     * 
     * @param node the postfix expression node ({@code <postfiks_izraz>})
     */
    private void generatePostfixExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single child - delegate to next level (<primarni_izraz>)
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 2) {
            // Post-increment/decrement: <postfiks_izraz> OP_INC/DEC
            NonTerminalNode operand = (NonTerminalNode) children.get(0);
            ParseNode operator = children.get(1);
            
            if (operator instanceof hr.fer.ppj.semantics.tree.TerminalNode terminal) {
                String op = terminal.symbol();
                
                switch (op) {
                    case "OP_INC" -> {
                        // Post-increment: return old value, then increment
                        assignmentGenerator.generatePostIncrement(operand);
                    }
                    case "OP_DEC" -> {
                        // Post-decrement: return old value, then decrement
                        assignmentGenerator.generatePostDecrement(operand);
                    }
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
                
                // No arguments - call function with empty argument list
                functionCallGenerator.generateFunctionCall(function, null);
            } else {
                // Complex postfix expression - fall back to evaluating the base expression
                generateExpression(function);
            }
        } else if (children.size() == 4) {
            // Array indexing or function call with arguments
            ParseNode first = children.get(1);
            ParseNode second = children.get(2);
            ParseNode third = children.get(3);
            
            // Check for array indexing: <postfiks_izraz> L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
            if (first instanceof hr.fer.ppj.semantics.tree.TerminalNode leftBracket && "L_UGL_ZAGRADA".equals(leftBracket.symbol()) &&
                second instanceof NonTerminalNode indexExpr &&
                third instanceof hr.fer.ppj.semantics.tree.TerminalNode rightBracket && "D_UGL_ZAGRADA".equals(rightBracket.symbol())) {
                
                // Array indexing: compute base + index*4, load element
                arrayGenerator.generateArrayIndexing((NonTerminalNode) children.get(0), indexExpr);
            }
            // Check for function call with arguments: <postfiks_izraz> L_ZAGRADA <lista_argumenata> D_ZAGRADA
            else if (first instanceof hr.fer.ppj.semantics.tree.TerminalNode leftParen && "L_ZAGRADA".equals(leftParen.symbol()) &&
                     second instanceof NonTerminalNode arguments &&
                     third instanceof hr.fer.ppj.semantics.tree.TerminalNode rightParen && "D_ZAGRADA".equals(rightParen.symbol())) {
                
                // Function call: evaluate arguments, push (right-to-left), CALL, cleanup
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
     * 
     * <p><b>Grammar Rule:</b> Handles {@code <izraz>} with comma operator:
     * <pre>
     * &lt;izraz&gt; ::= &lt;izraz_pridruzivanja&gt;
     *          | &lt;izraz&gt; ZAREZ &lt;izraz_pridruzivanja&gt;
     * </pre>
     * 
     * <p><b>FRISC Semantics:</b>
     * <ul>
     *   <li>Left expression is evaluated first (result discarded)</li>
     *   <li>Right expression is evaluated second (result kept in R0)</li>
     *   <li>No explicit cleanup needed - left expression's side effects remain</li>
     * </ul>
     * 
     * <p><b>Example:</b> {@code (a = 5, b = 10)} evaluates both assignments,
     * returns the value of {@code b = 10} (which is 10).
     * 
     * @param node the comma expression node ({@code <izraz>})
     */
    private void generateCommaExpression(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (children.size() == 1) {
            // Single expression - delegate to assignment expression
            // This is the base case: <izraz> ::= <izraz_pridruzivanja>
            generateExpression((NonTerminalNode) children.get(0));
        } else if (children.size() == 3) {
            // Comma expression: <izraz> ZAREZ <izraz_pridruzivanja>
            NonTerminalNode left = (NonTerminalNode) children.get(0);
            NonTerminalNode right = (NonTerminalNode) children.get(2);
            
            // Evaluate left expression (result is discarded, but side effects remain)
            // Left expression may contain nested comma expressions (recursive)
            generateExpression(left);
            
            // Evaluate right expression (result is kept in R0)
            // This is the final value of the comma expression
            generateExpression(right);
        }
    }
}
