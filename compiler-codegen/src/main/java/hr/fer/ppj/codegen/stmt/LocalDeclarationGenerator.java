package hr.fer.ppj.codegen.stmt;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.expr.ExpressionCodeGenerator;
import hr.fer.ppj.codegen.stmt.decl.ArrayInitializerGenerator;
import hr.fer.ppj.codegen.stmt.decl.ArraySizeExtractor;
import hr.fer.ppj.codegen.types.TypeSizeCalculator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PrimitiveType;
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
    private final ArrayInitializerGenerator arrayInitGen;
    
    /**
     * Creates a new local declaration generator.
     * 
     * @param context the code generation context
     * @param exprGen the expression generator for initializer expressions
     */
    public LocalDeclarationGenerator(CodeGenContext context, ExpressionCodeGenerator exprGen) {
        this.context = Objects.requireNonNull(context, "context must not be null");
        this.exprGen = Objects.requireNonNull(exprGen, "exprGen must not be null");
        this.arrayInitGen = new ArrayInitializerGenerator(context);
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
                NonTerminalNode declarator = (NonTerminalNode) children.get(0);
                NonTerminalNode initializer = (NonTerminalNode) children.get(2);
                context.emitter().emitComment("Local variable " + varName + " at " + address + " with initializer");
                
                // Check if this is an array with an initializer list
                Type variableType = null;
                if (declarator.attributes() != null) {
                    variableType = declarator.attributes().type();
                }
                
                boolean isArray = false;
                int arraySize = 0;
                int elementSize = 4; // Default: 4 bytes for int
                
                if (variableType != null) {
                    Type strippedType = TypeSystem.stripConst(variableType);
                    if (strippedType instanceof ArrayType arrayType) {
                        isArray = true;
                        // Get array size from semantic attributes (elementCount)
                        if (declarator.attributes() != null) {
                            arraySize = declarator.attributes().elementCount();
                        }
                        // Get element size
                        Type elementType = arrayType.elementType();
                        Type strippedElementType = TypeSystem.stripConst(elementType);
                        if (strippedElementType == PrimitiveType.CHAR) {
                            elementSize = 4; // For this project, char is 4 bytes
                        } else {
                            elementSize = TypeSizeCalculator.calculateTypeSize(strippedElementType);
                        }
                    }
                } else {
                    // Fallback: check if size > 4 (arrays are larger than simple variables)
                    Integer varSize = context.activationRecord().getVariableSize(varName);
                    if (varSize != null && varSize > 4) {
                        isArray = true;
                        // Try to extract array size from declarator
                        arraySize = ArraySizeExtractor.extractArrayLength(declarator);
                        if (arraySize == 0) {
                            // Estimate from size: assume 4 bytes per element
                            arraySize = varSize / 4;
                        }
                    }
                }
                
                // Check if initializer is an array initializer list (has L_VIT_ZAGRADA)
                boolean isArrayInitializer = isArrayInitializerList(initializer);
                
                if (isArray && isArrayInitializer) {
                    // Array with initializer list: initialize each element
                    arrayInitGen.generate(varName, address, initializer, arraySize, elementSize);
                } else {
                    // Simple initializer (scalar or array without initializer list)
                    // Generate initializer expression
                    exprGen.generateExpression(initializer);
                    
                    // Store result in local variable
                    context.emitter().emitInstruction("STORE", "R0", address, "initialize " + varName);
                }
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
            return TypeSizeCalculator.calculateTypeSize(strippedType);
        }
        
        // Fallback: extract array size from parse tree
        return ArraySizeExtractor.extractArraySize(declarator);
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
    
    /**
     * Checks if an initializer node is an array initializer list.
     * 
     * <p>Array initializers have the form:
     * <pre>
     * L_VIT_ZAGRADA &lt;lista_izraza_pridruzivanja&gt; D_VIT_ZAGRADA
     * </pre>
     * 
     * @param initializer the initializer node ({@code <inicijalizator>})
     * @return true if it's an array initializer list
     */
    private boolean isArrayInitializerList(NonTerminalNode initializer) {
        List<ParseNode> children = initializer.children();
        // Array initializer: L_VIT_ZAGRADA <lista_izraza_pridruzivanja> D_VIT_ZAGRADA
        // or: L_VIT_ZAGRADA <lista_izraza_pridruzivanja> ZAREZ D_VIT_ZAGRADA
        if (children.size() >= 3) {
            ParseNode first = children.get(0);
            ParseNode last = children.get(children.size() - 1);
            if (first instanceof TerminalNode t1 && "L_VIT_ZAGRADA".equals(t1.symbol()) &&
                last instanceof TerminalNode t2 && "D_VIT_ZAGRADA".equals(t2.symbol())) {
                return true;
            }
        }
        return false;
    }
    
}

