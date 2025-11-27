package hr.fer.ppj.codegen;

import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly declarations for global variables.
 * 
 * <p>This class processes the global symbol table and generates appropriate
 * FRISC data declarations (DW, DH, DB) for global variables and constants.
 * Global variables are placed at the end of the program, after all function
 * definitions.
 * 
 * <p>Each global variable gets a unique label following the pattern
 * {@code G_<VARIABLE_NAME>} and is initialized with its declared value
 * or zero if no initializer is provided.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class GlobalVariableGenerator {
    
    private final CodeGenContext context;
    private NonTerminalNode parseTree;
    
    /**
     * Creates a new global variable generator.
     * 
     * @param context the code generation context
     */
    public GlobalVariableGenerator(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }
    
    /**
     * Sets the parse tree for extracting initializer values.
     * 
     * @param parseTree the parse tree from semantic analysis
     */
    public void setParseTree(NonTerminalNode parseTree) {
        this.parseTree = parseTree;
    }
    
    /**
     * Generates FRISC data declarations for all global variables.
     * 
     * <p>This method examines the global symbol table and generates
     * appropriate data declarations for each global variable found.
     */
    public void generateGlobalVariables() {
        context.emitter().emitComment("Global variables");
        
        // Process all symbols in the global scope
        for (Symbol symbol : context.globalScope().entries().values()) {
            if (symbol instanceof VariableSymbol varSymbol) {
                generateGlobalVariable(varSymbol);
            }
        }
        
        context.emitter().emitNewline();
    }
    
    /**
     * Generates a FRISC data declaration for a single global variable.
     * 
     * @param variable the variable symbol to generate code for
     */
    private void generateGlobalVariable(VariableSymbol variable) {
        String label = context.labelGenerator().getGlobalVariableLabel(variable.name());
        
        // Try to find initializer value from parse tree
        String initValue = findInitializerValue(variable.name());
        if (initValue == null) {
            initValue = "0"; // Default initialization
        }
        
        String comment = "global " + (variable.isConst() ? "const " : "") + 
                        variable.type() + " " + variable.name() + 
                        (initValue.equals("0") ? "" : " = " + initValue);
        
        context.emitter().emitData(label, "DW", "%D " + initValue, comment);
    }
    
    /**
     * Finds the initializer value for a global variable from the parse tree.
     * 
     * @param variableName the name of the variable to find
     * @return the initializer value as string, or null if not found
     */
    private String findInitializerValue(String variableName) {
        if (parseTree == null) {
            return null;
        }
        
        return findInitializerInNode(parseTree, variableName);
    }
    
    /**
     * Recursively searches for variable initializer in parse tree nodes.
     */
    private String findInitializerInNode(NonTerminalNode node, String variableName) {
        String symbol = node.symbol();
        
        // Look for global variable declarations
        if ("<vanjska_deklaracija>".equals(symbol)) {
            return findInitializerInDeclaration(node, variableName);
        }
        
        // Recursively search children
        for (ParseNode child : node.children()) {
            if (child instanceof NonTerminalNode nonTerminal) {
                String result = findInitializerInNode(nonTerminal, variableName);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Searches for variable initializer in a declaration node.
     */
    private String findInitializerInDeclaration(NonTerminalNode declaration, String variableName) {
        // Look for <deklaracija> nodes
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<deklaracija>".equals(nonTerminal.symbol())) {
                return findInitializerInVariableDeclaration(nonTerminal, variableName);
            }
        }
        return null;
    }
    
    /**
     * Searches for variable initializer in a variable declaration.
     */
    private String findInitializerInVariableDeclaration(NonTerminalNode declaration, String variableName) {
        // Find <lista_init_deklaratora>
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<lista_init_deklaratora>".equals(nonTerminal.symbol())) {
                return findInitializerInInitDeclaratorList(nonTerminal, variableName);
            }
        }
        return null;
    }
    
    /**
     * Searches for variable initializer in init declarator list.
     */
    private String findInitializerInInitDeclaratorList(NonTerminalNode list, String variableName) {
        List<ParseNode> children = list.children();
        
        if (children.size() == 1) {
            // Single declarator
            return findInitializerInInitDeclarator((NonTerminalNode) children.get(0), variableName);
        } else if (children.size() == 3) {
            // Multiple declarators: <lista_init_deklaratora> ZAREZ <init_deklarator>
            String result = findInitializerInInitDeclaratorList((NonTerminalNode) children.get(0), variableName);
            if (result != null) return result;
            return findInitializerInInitDeclarator((NonTerminalNode) children.get(2), variableName);
        }
        
        return null;
    }
    
    /**
     * Searches for variable initializer in init declarator.
     */
    private String findInitializerInInitDeclarator(NonTerminalNode declarator, String variableName) {
        List<ParseNode> children = declarator.children();
        
        if (children.size() == 3) {
            // <deklarator> OP_PRIDRUZI <inicijalizator>
            ParseNode operator = children.get(1);
            if (operator instanceof TerminalNode terminal && "OP_PRIDRUZI".equals(terminal.symbol())) {
                String declaredName = extractVariableName((NonTerminalNode) children.get(0));
                if (variableName.equals(declaredName)) {
                    return extractInitializerValue((NonTerminalNode) children.get(2));
                }
            }
        }
        
        return null;
    }
    
    /**
     * Extracts variable name from declarator.
     */
    private String extractVariableName(NonTerminalNode declarator) {
        // Find IDN token in declarator structure
        return findIdentifier(declarator);
    }
    
    /**
     * Recursively finds identifier in node.
     */
    private String findIdentifier(NonTerminalNode node) {
        for (ParseNode child : node.children()) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal) {
                String result = findIdentifier(nonTerminal);
                if (result != null) return result;
            }
        }
        return null;
    }
    
    /**
     * Extracts initializer value from inicijalizator node.
     */
    private String extractInitializerValue(NonTerminalNode initializer) {
        // Look for <izraz_pridruzivanja> -> ... -> BROJ
        return findConstantValue(initializer);
    }
    
    /**
     * Recursively finds constant value in expression tree.
     */
    private String findConstantValue(NonTerminalNode node) {
        for (ParseNode child : node.children()) {
            if (child instanceof TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal) {
                String result = findConstantValue(nonTerminal);
                if (result != null) return result;
            }
        }
        return null;
    }
}
