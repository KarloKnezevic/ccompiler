package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.utils.StructLayoutCalculator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for local variable declarations.
 * 
 * <p>This class handles the generation of code for local variable declarations
 * within compound statements (blocks), including:
 * <ul>
 *   <li>Simple variable declarations: {@code int x;}</li>
 *   <li>Variable declarations with initializers: {@code int x = 5;}</li>
 *   <li>Array declarations: {@code int a[5];}</li>
 * </ul>
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <pre>
 * &lt;lista_deklaracija&gt; ::= &lt;deklaracija&gt;
 *                         | &lt;lista_deklaracija&gt; &lt;deklaracija&gt;
 * 
 * &lt;deklaracija&gt; ::= &lt;ime_tipa&gt; &lt;lista_init_deklaratora&gt;
 * 
 * &lt;lista_init_deklaratora&gt; ::= &lt;init_deklarator&gt;
 *                             | &lt;lista_init_deklaratora&gt; ZAREZ &lt;init_deklarator&gt;
 * </pre>
 * 
 * <p><b>FRISC Code Pattern:</b>
 * <pre>
 * ; Local variable declaration with initializer:
 * &lt;initializer expression&gt;   ; result in R0
 * STORE R0, (R5-offset)      ; store in local variable
 * </pre>
 * 
 * <p><b>FRISC Semantics:</b>
 * <ul>
 *   <li>Local variables allocated in activation record (stack frame)</li>
 *   <li>Variables accessed via negative offsets from R5 (frame pointer)</li>
 *   <li>Initializers evaluated and stored in variable location</li>
 *   <li>Array size extracted from parse tree (defaults to 4 bytes per element)</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LocalDeclarationGenerator {
    
    private final CodeGenContext context;
    private final ExpressionCodeGenerator exprGen;
    
    /**
     * Creates a new local declaration generator.
     * 
     * @param context the code generation context
     * @param exprGen the expression generator for initializer expressions
     */
    public LocalDeclarationGenerator(CodeGenContext context, ExpressionCodeGenerator exprGen) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.exprGen = Objects.requireNonNull(exprGen, "exprGen must not be null");
    }
    
    /**
     * Generates code for local variable declarations.
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <lista_deklaracija>}
     * 
     * @param declarationList the declaration list node ({@code <lista_deklaracija>})
     */
    public void generateLocalDeclarations(NonTerminalNode declarationList) {
        Objects.requireNonNull(declarationList, "declarationList must not be null");
        
        context.emitter().emitComment("Local variable declarations");
        
        // Process each declaration in the list
        processDeclarationList(declarationList);
    }
    
    /**
     * Processes a declaration list recursively.
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <lista_deklaracija>}:
     * <pre>
     * &lt;lista_deklaracija&gt; ::= &lt;deklaracija&gt;
     *                         | &lt;lista_deklaracija&gt; &lt;deklaracija&gt;
     * </pre>
     * 
     * @param node the declaration list node ({@code <lista_deklaracija>})
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
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <deklaracija>}:
     * <pre>
     * &lt;deklaracija&gt; ::= &lt;ime_tipa&gt; &lt;lista_init_deklaratora&gt;
     * </pre>
     * 
     * @param declaration the declaration node ({@code <deklaracija>})
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
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <lista_init_deklaratora>}:
     * <pre>
     * &lt;lista_init_deklaratora&gt; ::= &lt;init_deklarator&gt;
     *                             | &lt;lista_init_deklaratora&gt; ZAREZ &lt;init_deklarator&gt;
     * </pre>
     * 
     * @param node the init declarator list node ({@code <lista_init_deklaratora>})
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
     * 
     * <p><b>Grammar Rule:</b> Processes {@code <init_deklarator>}:
     * <pre>
     * &lt;init_deklarator&gt; ::= &lt;deklarator&gt;
     *                      | &lt;deklarator&gt; OP_ASSIGN &lt;inicijalizator&gt;
     * </pre>
     * 
     * <p><b>FRISC Code:</b>
     * <pre>
     * ; With initializer:
     * &lt;initializer expression&gt;   ; result in R0
     * STORE R0, (R5-offset)      ; store in local variable
     * </pre>
     * 
     * @param node the init declarator node ({@code <init_deklarator>})
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
                // Try to get type from semantic attributes
                Type variableType = null;
                if (children.get(0) instanceof NonTerminalNode declarator) {
                    if (declarator.attributes() != null) {
                        variableType = declarator.attributes().type();
                    }
                }
                
                // Calculate size based on type
                int size = calculateVariableSize(variableType, (NonTerminalNode) children.get(0));
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
     * Calculates the size of a variable based on its type and declaration.
     * 
     * <p>This method tries to get type from semantic attributes first, then falls back
     * to extracting array size from the parse tree.
     * 
     * @param variableType the variable type (from semantic attributes, may be null)
     * @param declarator the declarator node ({@code <deklarator>})
     * @return the size in bytes
     */
    private int calculateVariableSize(Type variableType, NonTerminalNode declarator) {
        if (variableType != null) {
            Type strippedType = TypeSystem.stripConst(variableType);
            return StructLayoutCalculator.calculateTypeSize(strippedType);
        }
        
        // Fallback: extract array size from parse tree
        return extractArraySize(declarator);
    }
    
    /**
     * Extracts array size from a declarator node.
     * 
     * <p>Returns 4 (default) for non-arrays, or size * element_size for arrays.
     * Note: This method is used as a fallback when variable is not in activation record.
     * It defaults to 4 bytes per element (int), but ideally should check the type specifier.
     * 
     * @param declarator the declarator node ({@code <deklarator>})
     * @return the size in bytes (4 for simple variables, size * 4 for arrays)
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
     * 
     * <p>Defaults to 4 bytes per element (int). For char arrays, this should be 1 byte,
     * but without access to the type specifier, we default to int.
     * 
     * @param directDeclarator the direct declarator node ({@code <izravni_deklarator>})
     * @return the size in bytes (4 for simple variables, size * 4 for arrays)
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
     * 
     * @param declarator the declarator node ({@code <deklarator>})
     * @return the variable name (IDN lexeme), or null if not found
     */
    private String extractVariableName(NonTerminalNode declarator) {
        // Find the identifier in the declarator structure
        return findIdentifier(declarator);
    }
    
    /**
     * Recursively finds the first identifier in a node.
     * 
     * @param node the node to search in
     * @return the identifier name (IDN lexeme), or null if not found
     */
    private String findIdentifier(NonTerminalNode node) {
        return hr.fer.ppj.codegen.utils.IdentifierExtractor.findIdentifier(node);
    }
}

