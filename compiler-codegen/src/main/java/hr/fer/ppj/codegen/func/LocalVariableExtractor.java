package hr.fer.ppj.codegen.func;

import hr.fer.ppj.codegen.utils.StructArraySizeExtractor;
import hr.fer.ppj.codegen.utils.StructLayoutCalculator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Extracts local variable information from function body nodes.
 * 
 * <p>This class processes compound statements ({@code <slozena_naredba>}) to extract
 * information about local variables, including their names and sizes.
 * 
 * <p><b>Grammar Rule:</b> Processes {@code <slozena_naredba>}:
 * <pre>
 * &lt;slozena_naredba&gt; ::= L_VIT_ZAGRADA &lt;lista_deklaracija&gt; &lt;lista_naredbi&gt; D_VIT_ZAGRADA
 * </pre>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class LocalVariableExtractor {
    
    /**
     * Information about a local variable including its name and size.
     */
    public record VariableInfo(String name, int sizeInBytes) {}
    
    private final StructArraySizeExtractor arraySizeExtractor;
    
    /**
     * Creates a new local variable extractor.
     * 
     * @param parseTree the parse tree from semantic analysis (for extracting struct array sizes)
     */
    public LocalVariableExtractor(NonTerminalNode parseTree) {
        this.arraySizeExtractor = new StructArraySizeExtractor(parseTree);
    }
    
    /**
     * Extracts local variable information from a function body.
     * 
     * @param body the function body node ({@code <slozena_naredba>})
     * @param elementSize the element size in bytes (always 4 for this project)
     * @return list of variable information (name and size in bytes)
     */
    public List<VariableInfo> extractLocalVariables(NonTerminalNode body, int elementSize) {
        Objects.requireNonNull(body, "body must not be null");
        
        List<VariableInfo> variables = new ArrayList<>();
        findLocalVariables(body, variables, elementSize);
        return variables;
    }
    
    /**
     * Recursively finds local variable declarations in the parse tree.
     * 
     * @param node the node to search in
     * @param variables the list to add variable information to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void findLocalVariables(NonTerminalNode node, List<VariableInfo> variables, int elementSize) {
        String symbol = node.symbol();
        
        if ("<lista_deklaracija>".equals(symbol)) {
            processDeclarationList(node, variables, elementSize);
        } else if ("<slozena_naredba>".equals(symbol)) {
            for (ParseNode child : node.children()) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    String childSymbol = nonTerminal.symbol();
                    if ("<lista_deklaracija>".equals(childSymbol)) {
                        processDeclarationList(nonTerminal, variables, elementSize);
                    } else {
                        findLocalVariables(nonTerminal, variables, elementSize);
                    }
                }
            }
        } else {
            for (ParseNode child : node.children()) {
                if (child instanceof NonTerminalNode nonTerminal) {
                    findLocalVariables(nonTerminal, variables, elementSize);
                }
            }
        }
    }
    
    /**
     * Processes a declaration list and extracts variable information.
     * 
     * @param declarationList the declaration list node ({@code <lista_deklaracija>})
     * @param variables the list to add variable information to
     * @param elementSize the element size in bytes (always 4 for this project)
     */
    private void processDeclarationList(NonTerminalNode declarationList, List<VariableInfo> variables, int elementSize) {
        List<ParseNode> children = declarationList.children();
        
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal) {
                String childSymbol = nonTerminal.symbol();
                
                if ("<lista_deklaracija>".equals(childSymbol)) {
                    processDeclarationList(nonTerminal, variables, elementSize);
                } else if ("<deklaracija>".equals(childSymbol)) {
                    List<VariableInfo> declVars = extractVariableInfo(nonTerminal, elementSize);
                    variables.addAll(declVars);
                }
            }
        }
    }
    
    /**
     * Extracts variable information (name and size) from a declaration node.
     * 
     * @param declaration the declaration node ({@code <deklaracija>})
     * @param elementSize the element size in bytes (always 4 for this project)
     * @return list of variable information (name and size in bytes)
     */
    private List<VariableInfo> extractVariableInfo(NonTerminalNode declaration, int elementSize) {
        List<VariableInfo> variables = new ArrayList<>();
        
        // Extract base type from <ime_tipa> node
        Type baseType = null;
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<ime_tipa>".equals(nonTerminal.symbol())) {
                if (nonTerminal.attributes() != null) {
                    baseType = nonTerminal.attributes().type();
                }
                break;
            }
        }
        
        for (ParseNode child : declaration.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<lista_init_deklaratora>".equals(nonTerminal.symbol())) {
                // Pass base type to the list extractor
                extractVariableInfoFromList(nonTerminal, variables, elementSize, baseType);
            }
        }
        
        return variables;
    }
    
    /**
     * Extracts variable information from a list of init declarators.
     * 
     * @param list the list of init declarators ({@code <lista_init_deklaratora>})
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     * @param baseType the base type from the declaration (may be null)
     */
    private void extractVariableInfoFromList(NonTerminalNode list, List<VariableInfo> variables, int elementSize, Type baseType) {
        List<ParseNode> children = list.children();
        
        // Get inherited type from semantic attributes if available
        Type inheritedType = baseType;
        if (list.attributes() != null && list.attributes().inheritedType() != null) {
            inheritedType = list.attributes().inheritedType();
        }
        
        if (children.size() == 1) {
            extractVariableInfoFromInitDeclarator((NonTerminalNode) children.get(0), variables, elementSize, inheritedType);
        } else if (children.size() == 3) {
            extractVariableInfoFromList((NonTerminalNode) children.get(0), variables, elementSize, inheritedType);
            extractVariableInfoFromInitDeclarator((NonTerminalNode) children.get(2), variables, elementSize, inheritedType);
        }
    }
    
    /**
     * Extracts variable information from an init declarator.
     * 
     * @param initDeclarator the init declarator node ({@code <init_deklarator>})
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     * @param inheritedType the inherited type from the declaration (may be null)
     */
    private void extractVariableInfoFromInitDeclarator(NonTerminalNode initDeclarator, List<VariableInfo> variables, int elementSize, Type inheritedType) {
        // Try to get type from semantic attributes
        Type variableType = inheritedType;
        if (initDeclarator.attributes() != null && initDeclarator.attributes().inheritedType() != null) {
            variableType = initDeclarator.attributes().inheritedType();
        }
        
        for (ParseNode child : initDeclarator.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<deklarator>".equals(nonTerminal.symbol())) {
                // Get type from declarator if not already available
                if (variableType == null && nonTerminal.attributes() != null) {
                    variableType = nonTerminal.attributes().type();
                }
                extractVariableInfoFromDeclarator(nonTerminal, variables, elementSize, variableType);
            }
        }
    }
    
    /**
     * Extracts variable information from a declarator.
     * 
     * @param declarator the declarator node ({@code <deklarator>})
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     * @param inheritedType the inherited type from the declaration (may be null)
     */
    private void extractVariableInfoFromDeclarator(NonTerminalNode declarator, List<VariableInfo> variables, int elementSize, Type inheritedType) {
        // Try to get type from semantic attributes
        Type variableType = inheritedType;
        if (declarator.attributes() != null) {
            if (declarator.attributes().type() != null) {
                variableType = declarator.attributes().type();
            } else if (declarator.attributes().inheritedType() != null) {
                variableType = declarator.attributes().inheritedType();
            }
        }
        
        for (ParseNode child : declarator.children()) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                extractVariableInfoFromDirectDeclarator(nonTerminal, variables, elementSize, variableType);
            }
        }
    }
    
    /**
     * Extracts variable information from a direct declarator.
     * 
     * <p>Handles both simple variables and arrays, extracting array sizes from
     * expression nodes when present. Also handles struct types by calculating their size.
     * 
     * @param directDeclarator the direct declarator node ({@code <izravni_deklarator>})
     * @param variables the list to add variable info to
     * @param elementSize the element size in bytes (always 4 for this project)
     * @param variableType the type of the variable (from semantic attributes, may be null)
     */
    private void extractVariableInfoFromDirectDeclarator(NonTerminalNode directDeclarator, List<VariableInfo> variables, int elementSize, Type variableType) {
        String varName = null;
        int arraySize = 0;
        boolean isArray = false;
        
        List<ParseNode> children = directDeclarator.children();
        
        // Handle nested <izravni_deklarator> structure
        NonTerminalNode nestedDeclarator = null;
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode nonTerminal && 
                "<izravni_deklarator>".equals(nonTerminal.symbol())) {
                nestedDeclarator = nonTerminal;
                break;
            }
        }
        
        // Find IDN (variable name)
        for (ParseNode child : children) {
            if (child instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                varName = terminal.lexeme();
                break;
            }
        }
        
        // Handle nested declarator case
        if (nestedDeclarator != null) {
            List<ParseNode> nestedChildren = nestedDeclarator.children();
            for (ParseNode nestedChild : nestedChildren) {
                if (nestedChild instanceof TerminalNode terminal && "IDN".equals(terminal.symbol())) {
                    varName = terminal.lexeme();
                    break;
                }
            }
            
            // Check for array brackets after nested declarator
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
            
            if (varName != null && isArray && arraySize > 0) {
                // Calculate array size based on type if available
                int size = calculateVariableSize(variableType, arraySize, elementSize);
                variables.add(new VariableInfo(varName, size));
                return;
            }
            
            if (varName != null && !isArray) {
                // Calculate variable size based on type
                int size = calculateVariableSize(variableType, 0, elementSize);
                variables.add(new VariableInfo(varName, size));
                return;
            }
            
            extractVariableInfoFromDirectDeclarator(nestedDeclarator, variables, elementSize, variableType);
            return;
        }
        
        if (varName == null) {
            return;
        }
        
        // Check if it's an array
        if (!isArray) {
            for (int i = 0; i <= children.size() - 3; i++) {
                if (i + 2 < children.size()) {
                    ParseNode node1 = children.get(i);
                    ParseNode node2 = children.get(i + 1);
                    ParseNode node3 = children.get(i + 2);
                    
                    if (node1 instanceof TerminalNode t1 && "L_UGL_ZAGRADA".equals(t1.symbol()) &&
                        node2 instanceof TerminalNode t2 && "BROJ".equals(t2.symbol()) &&
                        node3 instanceof TerminalNode t3 && "D_UGL_ZAGRADA".equals(t3.symbol())) {
                        isArray = true;
                        try {
                            arraySize = Integer.parseInt(t2.lexeme());
                        } catch (NumberFormatException e) {
                            arraySize = 0;
                        }
                        break;
                    }
                }
            }
        }
        
        if (isArray) {
            // Calculate array size based on type if available
            int size = calculateVariableSize(variableType, arraySize, elementSize);
            variables.add(new VariableInfo(varName, size));
        } else {
            // Calculate variable size based on type
            int size = calculateVariableSize(variableType, 0, elementSize);
            variables.add(new VariableInfo(varName, size));
        }
    }
    
    /**
     * Calculates the size in bytes of a variable based on its type.
     * 
     * <p>This method handles:
     * <ul>
     *   <li>Primitive types: char (1), int (4), float (4)</li>
     *   <li>Array types: element_size × array_length</li>
     *   <li>Struct types: calculated using StructLayoutCalculator</li>
     *   <li>Pointer types: 4 bytes</li>
     * </ul>
     * 
     * <p>If type is null, falls back to default sizes:
     * <ul>
     *   <li>Arrays: arraySize × elementSize</li>
     *   <li>Non-arrays: 4 bytes</li>
     * </ul>
     * 
     * @param variableType the variable type (from semantic attributes, may be null)
     * @param arraySize the array size (if array, 0 otherwise)
     * @param elementSize the element size in bytes (fallback, always 4 for this project)
     * @return the size in bytes
     */
    private int calculateVariableSize(Type variableType, int arraySize, int elementSize) {
        if (variableType == null) {
            // Fallback: use default sizes
            if (arraySize > 0) {
                return arraySize * elementSize;
            }
            return 4; // Default for simple variables
        }
        
        Type strippedType = TypeSystem.stripConst(variableType);
        
        if (strippedType instanceof StructType structType) {
            // Struct type: calculate struct size
            // For structs with array fields, extract array sizes from struct definition
            // Also extract array sizes for nested structs that contain arrays (recursively)
            String structTag = structType.tag();
            Map<String, Integer> arraySizes = arraySizeExtractor.extractArraySizes(structTag);
            
            // CRITICAL: Recursively extract array sizes for ALL nested structs at ALL levels
            // This must be done BEFORE calling calculateStructSize, because calculateStructSize
            // will recursively call calculateTypeSize for nested struct fields, and those
            // nested structs may also have array fields that need array sizes.
            Map<String, Map<String, Integer>> nestedStructArraySizes = new java.util.HashMap<>();
            extractNestedStructArraySizes(structType, nestedStructArraySizes);
            
            // Also extract array sizes for the current struct itself if it's nested in another struct
            // (needed when this struct is a field of another struct)
            if (structTag != null && !nestedStructArraySizes.containsKey(structTag)) {
                Map<String, Integer> currentArraySizes = arraySizeExtractor.extractArraySizes(structTag);
                if (!currentArraySizes.isEmpty()) {
                    nestedStructArraySizes.put(structTag, currentArraySizes);
                }
            }
            
            // Use the overload that accepts array sizes for both current and nested structs
            return StructLayoutCalculator.calculateStructSize(structType, arraySizes, nestedStructArraySizes);
        } else if (strippedType instanceof ArrayType arrayType) {
            // Array type: calculate array size
            // ArrayType doesn't store size - use provided arraySize parameter
            if (arraySize > 0) {
                // Calculate element size - may need nested struct array sizes if element is struct
                Type elementType = arrayType.elementType();
                Type strippedElementType = TypeSystem.stripConst(elementType);
                
                int elemSize;
                if (strippedElementType instanceof StructType elementStructType) {
                    // Element is a struct - need to extract array sizes for it and nested structs
                    String elementStructTag = elementStructType.tag();
                    Map<String, Integer> elementArraySizes = arraySizeExtractor.extractArraySizes(elementStructTag);
                    
                    // Extract array sizes for nested structs in element type
                    Map<String, Map<String, Integer>> elementNestedStructArraySizes = new java.util.HashMap<>();
                    extractNestedStructArraySizes(elementStructType, elementNestedStructArraySizes);
                    
                    elemSize = StructLayoutCalculator.calculateStructSize(elementStructType, elementArraySizes, elementNestedStructArraySizes);
                } else {
                    // Element is primitive or pointer - use simple type size calculator
                    elemSize = StructLayoutCalculator.calculateTypeSize(strippedElementType);
                }
                return elemSize * arraySize;
            }
            // Fallback: can't determine size without semantic attributes
            return elementSize; // Default to single element size
        } else {
            // Primitive or pointer type: use type size calculator
            // Note: strippedType should not be StructType here (handled above), but if it is,
            // we'd need array sizes - but this shouldn't happen for primitive/pointer types
            return StructLayoutCalculator.calculateTypeSize(strippedType);
        }
    }
    
    /**
     * Recursively extracts array sizes for all nested structs at all levels.
     * 
     * <p>This method traverses the struct type hierarchy and extracts array sizes
     * for all struct types that contain arrays, including deeply nested ones.
     * 
     * @param structType the struct type to extract nested array sizes for
     * @param nestedStructArraySizes the map to populate with nested struct array sizes
     */
    private void extractNestedStructArraySizes(hr.fer.ppj.semantics.types.StructType structType, 
                                               Map<String, Map<String, Integer>> nestedStructArraySizes) {
        if (structType == null || arraySizeExtractor == null) {
            return;
        }
        
        // Extract array sizes for all fields that are struct types (recursively)
        for (Map.Entry<String, hr.fer.ppj.semantics.types.Type> field : structType.fields().entrySet()) {
            hr.fer.ppj.semantics.types.Type fieldType = hr.fer.ppj.semantics.types.TypeSystem.stripConst(field.getValue());
            
            if (fieldType instanceof hr.fer.ppj.semantics.types.StructType nestedStructType) {
                String nestedTag = nestedStructType.tag();
                
                // Skip if we've already extracted array sizes for this struct
                if (nestedStructArraySizes.containsKey(nestedTag)) {
                    continue;
                }
                
                // Extract array sizes for this nested struct
                // CRITICAL: Always extract, even if empty, so we know we've tried
                // If the struct has array fields, this will populate the map with array sizes
                Map<String, Integer> nestedArraySizes = arraySizeExtractor.extractArraySizes(nestedTag);
                // Always put in map, even if empty (needed for calculateStructSize to know we've tried)
                nestedStructArraySizes.put(nestedTag, nestedArraySizes);
                
                // CRITICAL: Recursively extract array sizes for even deeper nested structs
                // This ensures we get array sizes for Inner when processing Middle
                // This must be done AFTER extracting array sizes for the current nested struct,
                // so that deeper nested structs can also be found
                extractNestedStructArraySizes(nestedStructType, nestedStructArraySizes);
            }
        }
    }
    
    /**
     * Extracts a number value from an expression node.
     * 
     * <p>Recursively searches for a BROJ terminal and returns its lexeme.
     * 
     * @param exprNode the expression node
     * @return the number value as string, or null if not found
     */
    private String extractNumberFromExpression(NonTerminalNode exprNode) {
        if (exprNode == null) {
            return null;
        }
        
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
}

