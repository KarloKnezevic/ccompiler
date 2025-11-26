package hr.fer.ppj.codegen.func;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.stmt.StatementCodeGenerator;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.List;
import java.util.Objects;

/**
 * Generates FRISC assembly code for function definitions and calls.
 * 
 * <p>This class handles the generation of FRISC subroutines from ppjC function
 * definitions, implementing the standard calling convention:
 * <ul>
 *   <li>Arguments are passed on the stack</li>
 *   <li>Return values are placed in register R6</li>
 *   <li>Caller cleans up arguments from the stack</li>
 *   <li>Local variables are allocated on the stack</li>
 * </ul>
 * 
 * <p>Each function is translated to a FRISC subroutine with a label following
 * the pattern {@code F_<FUNCTION_NAME>}. The subroutine manages its own
 * activation record (stack frame) for parameters and local variables.
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class FunctionCodeGenerator {
    
    private final CodeGenContext context;
    
    /**
     * Creates a new function code generator.
     * 
     * @param context the code generation context
     */
    public FunctionCodeGenerator(CodeGenContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
    }
    
    /**
     * Processes the translation unit, generating code for all function definitions.
     * 
     * <p>This method traverses the parse tree looking for function definitions
     * and generates the corresponding FRISC subroutines.
     * 
     * @param translationUnit the root node of the translation unit
     */
    public void processTranslationUnit(NonTerminalNode translationUnit) {
        Objects.requireNonNull(translationUnit, "translationUnit must not be null");
        
        context.emitter().emitComment("Function definitions");
        
        // Process all external declarations
        processExternalDeclarations(translationUnit);
    }
    
    /**
     * Processes external declarations, looking for function definitions.
     */
    private void processExternalDeclarations(NonTerminalNode node) {
        String symbol = node.symbol();
        
        if ("<prijevodna_jedinica>".equals(symbol)) {
            List<ParseNode> children = node.children();
            
            for (ParseNode child : children) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    processExternalDeclarations(nonTerminal);
                }
            }
        } else if ("<vanjska_deklaracija>".equals(symbol)) {
            processExternalDeclaration(node);
        }
    }
    
    /**
     * Processes a single external declaration.
     */
    private void processExternalDeclaration(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        if (!children.isEmpty() && children.get(0) instanceof NonTerminalNode child) {
            String symbol = child.symbol();
            
            if ("<definicija_funkcije>".equals(symbol)) {
                generateFunctionDefinition(child);
            }
            // Ignore other external declarations (global variables are handled separately)
        }
    }
    
    /**
     * Generates code for a function definition.
     */
    private void generateFunctionDefinition(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        // Extract function name, parameters, and body from the function definition structure
        String functionName = null;
        NonTerminalNode parameters = null;
        NonTerminalNode body = null;
        
        // Look for the deklarator which contains the function name and parameters
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                if ("<deklarator>".equals(nonTerminal.symbol())) {
                    functionName = extractFunctionName(nonTerminal);
                    parameters = extractFunctionParameters(nonTerminal);
                } else if ("<slozena_naredba>".equals(nonTerminal.symbol())) {
                    body = nonTerminal;
                }
            }
        }
        
        if (functionName != null && body != null) {
            generateFunction(functionName, parameters, body);
        }
    }
    
    /**
     * Extracts the function name from a deklarator node.
     */
    private String extractFunctionName(NonTerminalNode deklarator) {
        // Navigate through <deklarator> -> <izravni_deklarator> -> <izravni_deklarator> -> IDN
        List<ParseNode> children = deklarator.children();
        
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                return extractFunctionNameFromDirectDeclarator(nonTerminal);
            }
        }
        
        return null;
    }
    
    /**
     * Extracts the function name from an izravni_deklarator node.
     */
    private String extractFunctionNameFromDirectDeclarator(NonTerminalNode directDeclarator) {
        List<ParseNode> children = directDeclarator.children();
        
        for (ParseNode child : children) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal && 
                      "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                // Recursive case for nested izravni_deklarator
                String name = extractFunctionNameFromDirectDeclarator(nonTerminal);
                if (name != null) {
                    return name;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Generates FRISC code for a single function.
     */
    private void generateFunction(String functionName, NonTerminalNode parameters, NonTerminalNode body) {
        String functionLabel = context.labelGenerator().getFunctionLabel(functionName);
        
        context.emitter().emitLabel(functionLabel, "Function " + functionName);
        
        // Create activation record for this function
        ActivationRecord activationRecord = new ActivationRecord();
        
        // Process function parameters and add them to activation record
        if (parameters != null) {
            processFunctionParameters(parameters, activationRecord);
        }
        
        // Process local variable declarations in the function body
        processLocalDeclarations(body, activationRecord);
        
        // Create function context with activation record
        CodeGenContext functionContext = context.withActivationRecord(activationRecord);
        
        // Generate function prologue
        generateFunctionPrologue(functionContext, activationRecord);
        
        // Generate function body
        StatementCodeGenerator stmtGen = new StatementCodeGenerator(functionContext);
        stmtGen.generateStatement(body);
        
        // Generate function epilogue
        generateFunctionEpilogue(functionContext, activationRecord);
        
        // Default return (in case no explicit return)
        context.emitter().emitInstruction("MOVE", "0", "R6", "default return value");
        context.emitter().emitInstruction("RET", null, null, "return from " + functionName);
        context.emitter().emitNewline();
    }
    
    /**
     * Processes local variable declarations in the function body.
     */
    private void processLocalDeclarations(NonTerminalNode body, ActivationRecord activationRecord) {
        // Find all local variable declarations in the compound statement
        findLocalVariables(body, activationRecord);
    }
    
    /**
     * Recursively finds local variable declarations in the parse tree.
     */
    private void findLocalVariables(NonTerminalNode node, ActivationRecord activationRecord) {
        String symbol = node.symbol();
        
        if ("<lista_deklaracija>".equals(symbol)) {
            // Process declaration list
            processDeclarationList(node, activationRecord);
        } else {
            // Recursively process children
            for (ParseNode child : node.children()) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    findLocalVariables(nonTerminal, activationRecord);
                }
            }
        }
    }
    
    /**
     * Processes a declaration list and adds variables to activation record.
     */
    private void processDeclarationList(NonTerminalNode declarationList, ActivationRecord activationRecord) {
        List<ParseNode> children = declarationList.children();
        
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                String childSymbol = nonTerminal.symbol();
                
                if ("<lista_deklaracija>".equals(childSymbol)) {
                    // Recursive case
                    processDeclarationList(nonTerminal, activationRecord);
                } else if ("<deklaracija>".equals(childSymbol)) {
                    // Process individual declaration
                    processDeclaration(nonTerminal, activationRecord);
                }
            }
        }
    }
    
    /**
     * Processes a single declaration and extracts variable names.
     */
    private void processDeclaration(NonTerminalNode declaration, ActivationRecord activationRecord) {
        // Find variable names in the declaration
        List<String> variableNames = extractVariableNames(declaration);
        
        for (String varName : variableNames) {
            int offset = activationRecord.addLocalVariable(varName);
            context.emitter().emitComment("Local variable " + varName + " at " + 
                                        activationRecord.getVariableAddress(varName));
        }
    }
    
    /**
     * Extracts variable names from a declaration node.
     */
    private List<String> extractVariableNames(NonTerminalNode declaration) {
        // This is a simplified implementation - in a full compiler,
        // you'd need to parse the declaration structure more carefully
        return findIdentifiers(declaration);
    }
    
    /**
     * Finds all identifiers in a declaration.
     */
    private List<String> findIdentifiers(NonTerminalNode node) {
        List<String> identifiers = new java.util.ArrayList<>();
        
        for (ParseNode child : node.children()) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                identifiers.add(terminal.lexeme());
            } else if (child instanceof NonTerminalNode nonTerminal) {
                identifiers.addAll(findIdentifiers(nonTerminal));
            }
        }
        
        return identifiers;
    }
    
    /**
     * Generates the function prologue (allocate local variables).
     */
    private void generateFunctionPrologue(CodeGenContext functionContext, ActivationRecord activationRecord) {
        int localSize = activationRecord.getLocalVariablesSize();
        
        if (localSize > 0) {
            functionContext.emitter().emitInstruction("SUB", "R7", String.valueOf(localSize), "R7", 
                                                    "allocate " + (localSize / 4) + " local variables");
        }
    }
    
    /**
     * Generates the function epilogue (deallocate local variables).
     */
    private void generateFunctionEpilogue(CodeGenContext functionContext, ActivationRecord activationRecord) {
        int localSize = activationRecord.getLocalVariablesSize();
        
        if (localSize > 0) {
            functionContext.emitter().emitInstruction("ADD", "R7", String.valueOf(localSize), "R7", 
                                                    "deallocate local variables");
        }
    }
    
    /**
     * Extracts function parameters from a deklarator node.
     */
    private NonTerminalNode extractFunctionParameters(NonTerminalNode deklarator) {
        // Navigate through the deklarator structure to find parameter list
        return findParameterList(deklarator);
    }
    
    /**
     * Recursively searches for parameter list in the deklarator structure.
     */
    private NonTerminalNode findParameterList(NonTerminalNode node) {
        List<ParseNode> children = node.children();
        
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                String symbol = nonTerminal.symbol();
                
                if ("<lista_parametara>".equals(symbol)) {
                    return nonTerminal;
                } else {
                    NonTerminalNode result = findParameterList(nonTerminal);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * Processes function parameters and adds them to the activation record.
     */
    private void processFunctionParameters(NonTerminalNode parameters, ActivationRecord activationRecord) {
        List<String> parameterNames = extractParameterNames(parameters);
        
        for (String paramName : parameterNames) {
            int offset = activationRecord.addParameter(paramName);
            context.emitter().emitComment("Parameter " + paramName + " at " + 
                                        activationRecord.getVariableAddress(paramName));
        }
    }
    
    /**
     * Extracts parameter names from the parameter list.
     */
    private List<String> extractParameterNames(NonTerminalNode parameters) {
        List<String> names = new java.util.ArrayList<>();
        extractParameterNamesRecursive(parameters, names);
        return names;
    }
    
    /**
     * Recursively extracts parameter names from the parameter list structure.
     */
    private void extractParameterNamesRecursive(NonTerminalNode node, List<String> names) {
        String symbol = node.symbol();
        
        if ("<lista_parametara>".equals(symbol)) {
            List<ParseNode> children = node.children();
            
            if (children.size() == 1) {
                // Single parameter: <deklaracija_parametra>
                extractParameterFromDeclaration((NonTerminalNode) children.get(0), names);
            } else if (children.size() == 3) {
                // Multiple parameters: <lista_parametara> ZAREZ <deklaracija_parametra>
                extractParameterNamesRecursive((NonTerminalNode) children.get(0), names);
                extractParameterFromDeclaration((NonTerminalNode) children.get(2), names);
            }
        }
    }
    
    /**
     * Extracts parameter name from a parameter declaration.
     */
    private void extractParameterFromDeclaration(NonTerminalNode declaration, List<String> names) {
        // Find the identifier in the parameter declaration
        String paramName = findIdentifierInDeclaration(declaration);
        if (paramName != null) {
            names.add(paramName);
        }
    }
    
    /**
     * Finds the identifier in a parameter declaration.
     */
    private String findIdentifierInDeclaration(NonTerminalNode node) {
        for (ParseNode child : node.children()) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal) {
                String result = findIdentifierInDeclaration(nonTerminal);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }
}
