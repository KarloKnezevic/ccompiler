package hr.fer.ppj.codegen.func;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.model.ActivationRecord;
import hr.fer.ppj.codegen.stmt.StatementCodeGenerator;
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
        
        // Generate exit label for this function
        String exitLabel = context.labelGenerator().generateLabel();
        
        // Create function context with activation record and exit label
        CodeGenContext functionContext = context.withActivationRecord(activationRecord)
                                               .withFunctionExitLabel(exitLabel);
        
        // Generate function prologue
        generateFunctionPrologue(functionContext, activationRecord);
        
        // Generate function body
        StatementCodeGenerator stmtGen = new StatementCodeGenerator(functionContext);
        stmtGen.generateStatement(body);
        
        // Generate function epilogue label (for functions that fall through without explicit return)
        // Return statements will jump to this label to avoid duplicate epilogues
        context.emitter().emitLabel(exitLabel, "function exit");
        generateFunctionEpilogue(functionContext, activationRecord);
        
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
     * 
     * Structure for compound statement:
     * <slozena_naredba> -> L_VIT_ZAGRADA <lista_deklaracija> <lista_naredbi> D_VIT_ZAGRADA
     */
    private void findLocalVariables(NonTerminalNode node, ActivationRecord activationRecord) {
        String symbol = node.symbol();
        
        if ("<lista_deklaracija>".equals(symbol)) {
            // Process declaration list
            processDeclarationList(node, activationRecord);
        } else if ("<slozena_naredba>".equals(symbol)) {
            // Compound statement: look for <lista_deklaracija> in children
            for (ParseNode child : node.children()) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    String childSymbol = nonTerminal.symbol();
                    if ("<lista_deklaracija>".equals(childSymbol)) {
                        processDeclarationList(nonTerminal, activationRecord);
                    } else {
                        // Continue searching in other children
                        findLocalVariables(nonTerminal, activationRecord);
                    }
                }
            }
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
     * Processes a single declaration and extracts variable names with their sizes.
     * 
     * <p>For this project, both int and char arrays use element size 4 bytes.
     * We treat chars as 4-byte elements and use LOAD/STORE instead of LOADB/STOREB.
     */
    private void processDeclaration(NonTerminalNode declaration, ActivationRecord activationRecord) {
        // For this project: both int and char arrays use element size 4 bytes
        // We treat chars as 4-byte elements and use LOAD/STORE instead of LOADB/STOREB
        int elementSize = 4;
        
        // Find variable names and sizes in the declaration
        List<VariableInfo> variables = extractVariableInfo(declaration, elementSize);
        
        for (VariableInfo varInfo : variables) {
            activationRecord.addLocalVariable(varInfo.name(), varInfo.sizeInBytes());
            context.emitter().emitComment("Local variable " + varInfo.name() + " at " + 
                                        activationRecord.getVariableAddress(varInfo.name()));
        }
    }
    
    
    /**
     * Information about a local variable including its name and size.
     */
    private record VariableInfo(String name, int sizeInBytes) {}
    
    /**
     * Extracts variable information (name and size) from a declaration node.
     * 
     * Structure: <deklaracija> -> <lista_init_deklaratora> -> <init_deklarator> -> <deklarator> -> <izravni_deklarator> -> IDN
     * 
     * <p>For this project, both int and char arrays use element size 4 bytes.
     * 
     * @param declaration the declaration node
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private List<VariableInfo> extractVariableInfo(NonTerminalNode declaration, int elementSize) {
        List<VariableInfo> variables = new java.util.ArrayList<>();
        
        // Find <lista_init_deklaratora>
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<lista_init_deklaratora>".equals(nonTerminal.symbol())) {
                extractVariableInfoFromList(nonTerminal, variables, elementSize);
            }
        }
        
        return variables;
    }
    
    /**
     * Extracts variable information from a list of init declarators.
     * 
     * @param list the list of init declarators
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (1 for char, 4 for int)
     */
    private void extractVariableInfoFromList(NonTerminalNode list, List<VariableInfo> variables, int elementSize) {
        List<ParseNode> children = list.children();
        
        if (children.size() == 1) {
            // Single <init_deklarator>
            extractVariableInfoFromInitDeclarator((NonTerminalNode) children.get(0), variables, elementSize);
        } else if (children.size() == 3) {
            // <lista_init_deklaratora> ZAREZ <init_deklarator>
            extractVariableInfoFromList((NonTerminalNode) children.get(0), variables, elementSize);
            extractVariableInfoFromInitDeclarator((NonTerminalNode) children.get(2), variables, elementSize);
        }
    }
    
    /**
     * Extracts variable information from an init declarator.
     * Structure: <init_deklarator> -> <deklarator> -> <izravni_deklarator> -> IDN
     * 
     * @param initDeclarator the init declarator node
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (1 for char, 4 for int)
     */
    private void extractVariableInfoFromInitDeclarator(NonTerminalNode initDeclarator, List<VariableInfo> variables, int elementSize) {
        // Find <deklarator>
        for (ParseNode child : initDeclarator.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<deklarator>".equals(nonTerminal.symbol())) {
                extractVariableInfoFromDeclarator(nonTerminal, variables, elementSize);
            }
        }
    }
    
    /**
     * Extracts variable information from a declarator.
     * Structure: <deklarator> -> <izravni_deklarator> -> IDN
     * 
     * @param declarator the declarator node
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (1 for char, 4 for int)
     */
    private void extractVariableInfoFromDeclarator(NonTerminalNode declarator, List<VariableInfo> variables, int elementSize) {
        // Find <izravni_deklarator>
        for (ParseNode child : declarator.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                extractVariableInfoFromDirectDeclarator(nonTerminal, variables, elementSize);
            }
        }
    }
    
    /**
     * Extracts variable information from a direct declarator.
     * Structure: <izravni_deklarator> -> IDN (or IDN with array/function syntax)
     * For arrays: <izravni_deklarator> -> IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
     * 
     * <p>For this project, both int and char arrays use element size 4 bytes.
     * 
     * @param directDeclarator the direct declarator node
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void extractVariableInfoFromDirectDeclarator(NonTerminalNode directDeclarator, List<VariableInfo> variables, int elementSize) {
        String varName = null;
        int arraySize = 0;
        boolean isArray = false;
        
        List<ParseNode> children = directDeclarator.children();
        
        
        // Handle nested <izravni_deklarator> structure
        // For arrays: <izravni_deklarator> -> <izravni_deklarator> -> IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
        // Or: <izravni_deklarator> -> IDN L_UGL_ZAGRADA BROJ D_UGL_ZAGRADA
        NonTerminalNode nestedDeclarator = null;
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                nestedDeclarator = nonTerminal;
                break;
            }
        }
        
        // Find IDN (variable name) - check both current level and nested level
        for (ParseNode child : children) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                varName = terminal.lexeme();
                break;
            }
        }
        
        // If nested, check current level for array brackets after nested declarator
        // Structure: <izravni_deklarator> -> <izravni_deklarator> -> IDN, then L_UGL_ZAGRADA <log_ili_izraz> D_UGL_ZAGRADA
        if (nestedDeclarator != null) {
            // Find IDN in nested level
            List<ParseNode> nestedChildren = nestedDeclarator.children();
            for (ParseNode nestedChild : nestedChildren) {
                if (nestedChild instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                    varName = terminal.lexeme();
                    break;
                }
            }
            
            // Check current level for array brackets after nested declarator
            // Structure: <izravni_deklarator> L_UGL_ZAGRADA <log_ili_izraz> D_UGL_ZAGRADA
            // where <log_ili_izraz> contains BROJ terminal
            int nestedIndex = -1;
            for (int i = 0; i < children.size(); i++) {
                if (children.get(i) == nestedDeclarator) {
                    nestedIndex = i;
                    break;
                }
            }
            if (nestedIndex >= 0 && nestedIndex + 3 < children.size()) {
                ParseNode node1 = children.get(nestedIndex + 1);
                ParseNode node2 = children.get(nestedIndex + 2);
                ParseNode node3 = children.get(nestedIndex + 3);
                if (node1 instanceof TerminalNode t1 && "L_UGL_ZAGRADA".equals(t1.symbol()) &&
                    node2 instanceof NonTerminalNode exprNode &&
                    node3 instanceof TerminalNode t3 && "D_UGL_ZAGRADA".equals(t3.symbol())) {
                    // Extract BROJ from <log_ili_izraz> expression
                    String numberValue = extractNumberFromExpression(exprNode);
                    if (numberValue != null) {
                        isArray = true;
                        try {
                            arraySize = Integer.parseInt(numberValue);
                        } catch (NumberFormatException e) {
                            arraySize = 0;
                        }
                    }
                }
            }
            
            // If we found varName and array brackets, we're done
            if (varName != null && isArray && arraySize > 0) {
                variables.add(new VariableInfo(varName, arraySize * elementSize));
                return;
            }
            
            // If we found varName but no array brackets, it's a simple variable
            if (varName != null && !isArray) {
                variables.add(new VariableInfo(varName, 4));
                return;
            }
            
            // Otherwise, recurse into nested level to handle it (might have different structure)
            extractVariableInfoFromDirectDeclarator(nestedDeclarator, variables, elementSize);
            return;
        }
        
        if (varName == null) {
            return; // No variable name found
        }
        
        // Check if it's an array: look for L_UGL_ZAGRADA followed by BROJ followed by D_UGL_ZAGRADA
        // (Only if we haven't already found array brackets above)
        if (!isArray) {
            for (int i = 0; i <= children.size() - 3; i++) {
                if (i + 2 < children.size()) {
                    ParseNode node1 = children.get(i);
                    ParseNode node2 = children.get(i + 1);
                    ParseNode node3 = children.get(i + 2);
                    
                    if (node1 instanceof TerminalNode t1 && "L_UGL_ZAGRADA".equals(t1.symbol()) &&
                        node2 instanceof TerminalNode t2 && "BROJ".equals(t2.symbol()) &&
                        node3 instanceof TerminalNode t3 && "D_UGL_ZAGRADA".equals(t3.symbol())) {
                        // It's an array declaration
                        isArray = true;
                        try {
                            arraySize = Integer.parseInt(t2.lexeme());
                        } catch (NumberFormatException e) {
                            arraySize = 0; // Invalid size, treat as error
                        }
                        break;
                    }
                }
            }
        }
        
        if (isArray) {
            // Array: size * element_size bytes
            variables.add(new VariableInfo(varName, arraySize * elementSize));
        } else {
            // Simple variable: 4 bytes (int or char both stored as 4 bytes on stack)
            variables.add(new VariableInfo(varName, 4));
        }
    }
    
    /**
     * Extracts a number value from an expression node (e.g., <log_ili_izraz>).
     * Recursively searches for a BROJ terminal and returns its lexeme.
     * 
     * @param exprNode the expression node
     * @return the number value as string, or null if not found
     */
    private String extractNumberFromExpression(NonTerminalNode exprNode) {
        if (exprNode == null) {
            return null;
        }
        
        // Search for BROJ terminal in the expression tree
        for (ParseNode child : exprNode.children()) {
            if (child instanceof TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
                return terminal.lexeme();
            } else if (child instanceof NonTerminalNode nonTerminal) {
                String result = extractNumberFromExpression(nonTerminal);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Generates the function prologue (save frame pointer, allocate local variables).
     * 
     * <p>Generates the canonical prologue:
     * <pre>
     * PUSH R5               ; save old frame pointer
     * MOVE R7, R5           ; R5 = current SP -> base of frame
     * SUB  R7, %D K, R7     ; allocate K bytes for locals (stack grows down)
     * </pre>
     */
    private void generateFunctionPrologue(CodeGenContext functionContext, ActivationRecord activationRecord) {
        int localSize = activationRecord.getLocalVariablesSize();
        
        // Always save old frame pointer (even if no locals)
        functionContext.emitter().emitInstruction("PUSH", "R5", null, "save old frame pointer");
        
        // Set R5 = R7 (current stack pointer)
        functionContext.emitter().emitInstruction("MOVE", "R7", "R5", "R5 = current SP -> base of frame");
        
        // Allocate space for local variables
        if (localSize > 0) {
            functionContext.emitter().emitInstruction("SUB", "R7", "%D " + localSize, "R7", 
                                                    "allocate " + (localSize / 4) + " local variables");
        }
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
    private void generateFunctionEpilogue(CodeGenContext functionContext, ActivationRecord activationRecord) {
        int localSize = activationRecord.getLocalVariablesSize();
        
        // Deallocate local variables
        if (localSize > 0) {
            functionContext.emitter().emitInstruction("ADD", "R7", "%D " + localSize, "R7", 
                                                    "deallocate local variables");
        }
        
        // Restore old frame pointer
        functionContext.emitter().emitInstruction("POP", "R5", null, "restore old frame pointer");
        
        // Return to caller (RET pops return address and jumps)
        functionContext.emitter().emitInstruction("RET", null, null, "return to caller");
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
            activationRecord.addParameter(paramName);
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
