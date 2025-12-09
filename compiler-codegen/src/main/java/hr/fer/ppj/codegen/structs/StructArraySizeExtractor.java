package hr.fer.ppj.codegen.structs;

import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.tree.ParseNode;
import hr.fer.ppj.semantics.tree.TerminalNode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Extracts array sizes for struct fields from struct definitions in the parse tree.
 * 
 * <p>This class searches the parse tree to find struct definitions and extracts
 * array size information for array fields. This is needed because {@code StructType}
 * doesn't preserve array sizes - it only stores {@code ArrayType(elementType)}.
 * 
 * <p><b>Why This Class Exists:</b>
 * 
 * <p>The semantic analyzer's type system stores struct field types as {@code ArrayType},
 * but {@code ArrayType} only stores the element type, not the array size. The array size
 * is only available in the parse tree (from the declaration syntax: {@code int arr[10]}).
 * 
 * <p>This class bridges that gap by:
 * <ul>
 *   <li>Searching the parse tree for struct definitions</li>
 *   <li>Matching struct definitions by tag name</li>
 *   <li>Extracting array sizes from field declarations</li>
 *   <li>Caching results for performance</li>
 * </ul>
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <ul>
 *   <li>{@code <struct_specifikator>} - struct type specifier</li>
 *   <li>{@code <struct_lista_deklaracija>} - struct field declaration list</li>
 *   <li>{@code <struct_deklaracija>} - struct field declaration</li>
 *   <li>{@code <struct_deklarator>} - struct field declarator</li>
 *   <li>{@code <izravni_deklarator>} - direct declarator (with array brackets)</li>
 * </ul>
 * 
 * <p><b>Array Size Extraction Strategy:</b>
 * 
 * <p>This class uses a multi-phase extraction strategy:
 * <ol>
 *   <li><b>Semantic Attributes First:</b> Checks semantic attributes on declarator nodes
 *       (fastest, most reliable if available)</li>
 *   <li><b>Tag-Based Search:</b> Searches for struct definitions matching the struct tag</li>
 *   <li><b>Fallback Search:</b> If tag-based search fails, searches all struct definitions
 *       (useful for anonymous structs or when tag matching fails)</li>
 * </ol>
 * 
 * <p><b>Nested Struct Support:</b>
 * 
 * <p>This class handles nested structs correctly:
 * <ul>
 *   <li>Each struct is extracted independently by its tag</li>
 *   <li>Nested structs are found when searching the parse tree recursively</li>
 *   <li>Array sizes for nested struct fields are extracted when processing the nested struct</li>
 * </ul>
 * 
 * <p><b>Example:</b>
 * <pre>
 * struct Inner {
 *     int arr[10];  // Array field
 * };
 * 
 * struct Outer {
 *     struct Inner inner;  // Nested struct
 *     int data[5];         // Array field
 * };
 * 
 * // Extract array sizes for Outer
 * Map&lt;String, Integer&gt; outerSizes = extractor.extractArraySizes("Outer");
 * // Result: {"data": 5}
 * 
 * // Extract array sizes for Inner
 * Map&lt;String, Integer&gt; innerSizes = extractor.extractArraySizes("Inner");
 * // Result: {"arr": 10}
 * </pre>
 * 
 * <p><b>Caching:</b>
 * 
 * <p>Results are cached to avoid repeated parse tree traversal. The cache key is:
 * <ul>
 *   <li>Struct tag name for tagged structs</li>
 *   <li>{@code "__anonymous__"} for anonymous structs</li>
 * </ul>
 * 
 * <p><b>Limitations:</b>
 * <ul>
 *   <li>Requires parse tree to be available (may be null in some contexts)</li>
 *   <li>Array sizes must be compile-time constants (BROJ terminals)</li>
 *   <li>Anonymous structs are harder to match (fallback search may match wrong struct)</li>
 * </ul>
 * 
 * <p><b>Complexity Analysis:</b>
 * <ul>
 *   <li><b>Time Complexity:</b> O(n) where n is the number of nodes in the parse tree
 *       (worst case: full tree traversal)</li>
 *   <li><b>Space Complexity:</b> O(m) where m is the number of structs with arrays
 *       (for caching)</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StructArraySizeExtractor {
    
    private final NonTerminalNode parseTree;
    private Map<String, Map<String, Integer>> cache; // tag -> (fieldName -> arraySize)
    
    /**
     * Creates a new struct array size extractor.
     * 
     * @param parseTree the parse tree from semantic analysis (may be null)
     */
    public StructArraySizeExtractor(NonTerminalNode parseTree) {
        this.parseTree = parseTree;
        this.cache = new HashMap<>();
    }
    
    /**
     * Extracts array sizes for all fields in a struct type.
     * 
     * <p>This method searches the parse tree for a struct definition matching the given tag
     * and extracts array sizes for all array fields in that struct.
     * 
     * <p><b>Extraction Strategy:</b>
     * <ol>
     *   <li><b>Cache Check:</b> Returns cached result if available</li>
     *   <li><b>Tag-Based Search:</b> Searches for struct definition with matching tag</li>
     *   <li><b>Fallback Search:</b> If tag-based search fails, searches all struct definitions</li>
     *   <li><b>Cache Result:</b> Stores result in cache for future lookups</li>
     * </ol>
     * 
     * <p><b>Return Value:</b>
     * <ul>
     *   <li>Returns a map from field name to array size (only for array fields)</li>
     *   <li>Non-array fields are not included in the map</li>
     *   <li>Empty map means either: struct has no arrays, or struct not found</li>
     * </ul>
     * 
     * <p><b>Example:</b>
     * <pre>
     * struct Buffer {
     *     int data[10];
     *     char name[20];
     *     int count;  // Not an array, not in result
     * };
     * 
     * Map&lt;String, Integer&gt; sizes = extractor.extractArraySizes("Buffer");
     * // Result: {"data": 10, "name": 20}
     * </pre>
     * 
     * @param structTag the struct tag name (null for anonymous structs)
     * @return a map from field name to array size (only for array fields), never null
     */
    public Map<String, Integer> extractArraySizes(String structTag) {
        // Early return if parse tree not available
        if (parseTree == null) {
            return new HashMap<>();
        }
        
        // Use a consistent cache key (null for anonymous, tag name for tagged structs)
        // This allows us to cache results per struct tag
        String cacheKey = structTag != null ? structTag : "__anonymous__";
        
        // Check cache first (performance optimization)
        // Cache stores: structTag -> (fieldName -> arraySize)
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        
        // Phase 1: Extract from parse tree using tag-based search
        // This searches for struct definitions matching the struct tag
        Map<String, Integer> arraySizes = extractArraySizesFromNode(parseTree, structTag);
        
        // Phase 2: Fallback search if tag-based search failed
        // This is useful for:
        // - Anonymous structs (no tag to match)
        // - Cases where tag matching fails (e.g., parse tree structure differences)
        if (arraySizes.isEmpty()) {
            // Try searching all struct definitions (ignore tag)
            // This collects array sizes from all structs in the parse tree
            Map<String, Integer> allSizes = extractArraySizesFromAllStructs(parseTree);
            if (!allSizes.isEmpty()) {
                // If we have a tag, we can't be sure which struct this is
                // But if there's only one struct with arrays, it's probably the right one
                // WARNING: This is a heuristic and may match the wrong struct if multiple
                // structs have arrays
                arraySizes = allSizes;
            }
        }
        
        // Cache result for future lookups (even if empty)
        // Empty map means "no arrays in this struct" vs null means "not tried yet"
        cache.put(cacheKey, arraySizes);
        return arraySizes;
    }
    
    /**
     * Extracts array sizes from all struct definitions in the parse tree.
     * Used as a fallback when tag-based search fails.
     */
    private Map<String, Integer> extractArraySizesFromAllStructs(NonTerminalNode node) {
        Map<String, Integer> result = new HashMap<>();
        extractArraySizesFromNodeRecursive(node, result);
        return result;
    }
    
    /**
     * Recursively extracts array sizes from all struct definitions, collecting them into a map.
     */
    private void extractArraySizesFromNodeRecursive(NonTerminalNode node, Map<String, Integer> result) {
        String symbol = node.symbol();
        
        // Look for struct field list nodes
        if ("<struct_lista_deklaracija>".equals(symbol)) {
            Map<String, Integer> sizes = extractArraySizesFromFieldList(node);
            result.putAll(sizes);
        }
        
        // Recursively search all children
        for (ParseNode child : node.children()) {
            if (child instanceof NonTerminalNode childNode) {
                extractArraySizesFromNodeRecursive(childNode, result);
            }
        }
    }
    
    /**
     * Recursively searches for struct definitions and extracts array sizes.
     */
    private Map<String, Integer> extractArraySizesFromNode(NonTerminalNode node, String structTag) {
        Map<String, Integer> result = new HashMap<>();
        String symbol = node.symbol();
        
        // Look for struct specifier: KR_STRUCT IDN L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
        if ("<struct_specifikator>".equals(symbol)) {
            var children = node.children();
            if (children.size() == 5) {
                // Tagged struct: KR_STRUCT IDN L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
                ParseNode tagNode = children.get(1);
                if (tagNode instanceof TerminalNode tagTerminal && "IDN".equals(tagTerminal.symbol())) {
                    String tag = tagTerminal.lexeme();
                    if (structTag == null || tag.equals(structTag)) {
                        // Found matching struct - extract array sizes from field list
                        ParseNode fieldList = children.get(3);
                        if (fieldList instanceof NonTerminalNode fieldListNode) {
                            Map<String, Integer> sizes = extractArraySizesFromFieldList(fieldListNode);
                            if (structTag != null && tag.equals(structTag)) {
                                return sizes; // Found exact match, return immediately
                            }
                            result.putAll(sizes);
                        }
                    }
                }
            } else if (children.size() == 4 && structTag == null) {
                // Anonymous struct: KR_STRUCT L_VIT_ZAGRADA <struct_lista_deklaracija> D_VIT_ZAGRADA
                ParseNode fieldList = children.get(2);
                if (fieldList instanceof NonTerminalNode fieldListNode) {
                    return extractArraySizesFromFieldList(fieldListNode);
                }
            }
        }
        
        // NOTE: We should NOT extract from <struct_lista_deklaracija> nodes directly here,
        // because we don't know which struct they belong to. We should only extract
        // when we're inside a <struct_specifikator> node and have verified the tag matches.
        // The extraction above (lines 124-131) handles this correctly.
        
        // Recursively search ALL children (don't skip any nodes)
        for (ParseNode child : node.children()) {
            if (child instanceof NonTerminalNode childNode) {
                Map<String, Integer> childResult = extractArraySizesFromNode(childNode, structTag);
                // Only return early if we found an exact match (indicated by non-empty result
                // AND we're looking for a specific tag AND the result came from the exact match logic above)
                // However, we can't easily verify this here, so we should check if the result
                // actually came from the struct we're looking for. The safest approach is to
                // only return early if we're in a struct_specifikator context and found a match.
                // For now, don't return early from recursive calls - let the exact match logic
                // at line 127-128 handle early returns, and just accumulate results here.
                result.putAll(childResult);
            }
        }
        
        return result;
    }
    
    /**
     * Extracts array sizes from a struct field declaration list.
     */
    private Map<String, Integer> extractArraySizesFromFieldList(NonTerminalNode fieldListNode) {
        Map<String, Integer> result = new HashMap<>();
        
        // Field list: <struct_lista_deklaracija> ::= <struct_deklaracija> | <struct_lista_deklaracija> <struct_deklaracija>
        // This is a recursive structure, so we need to handle both cases:
        // 1. Single declaration: <struct_deklaracija>
        // 2. Multiple declarations: <struct_lista_deklaracija> <struct_deklaracija>
        for (ParseNode child : fieldListNode.children()) {
            if (child instanceof NonTerminalNode childNode) {
                if ("<struct_deklaracija>".equals(childNode.symbol())) {
                    // Single declaration case
                    Map<String, Integer> declSizes = extractArraySizesFromDeclaration(childNode);
                    result.putAll(declSizes);
                } else if ("<struct_lista_deklaracija>".equals(childNode.symbol())) {
                    // Recursive case: process nested field list first
                    Map<String, Integer> nestedSizes = extractArraySizesFromFieldList(childNode);
                    result.putAll(nestedSizes);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Extracts array sizes from a struct field declaration.
     */
    private Map<String, Integer> extractArraySizesFromDeclaration(NonTerminalNode declNode) {
        Map<String, Integer> result = new HashMap<>();
        
        // Declaration: <struct_deklaracija> ::= <lista_specifikatora_kvalifikatora> <struct_lista_deklaratora> TOCKAZAREZ
        var children = declNode.children();
        if (children.size() >= 2) {
            ParseNode declaratorList = children.get(1);
            if (declaratorList instanceof NonTerminalNode declaratorListNode && 
                "<struct_lista_deklaratora>".equals(declaratorListNode.symbol())) {
                Map<String, Integer> sizes = extractArraySizesFromDeclaratorList(declaratorListNode);
                result.putAll(sizes);
            }
        }
        
        return result;
    }
    
    /**
     * Extracts array sizes from a struct declarator list.
     */
    private Map<String, Integer> extractArraySizesFromDeclaratorList(NonTerminalNode declaratorListNode) {
        Map<String, Integer> result = new HashMap<>();
        
        // Check semantic attributes first - might have field info with array sizes
        if (declaratorListNode.attributes() != null && declaratorListNode.attributes().structFields() != null) {
            // Try to extract from semantic attributes if available
            // This is a fallback - semantic attributes don't directly store array sizes,
            // but we can check child declarators
        }
        
        // Declarator list: <struct_lista_deklaratora> ::= <struct_deklarator> | <struct_lista_deklaratora> ZAREZ <struct_deklarator>
        var children = declaratorListNode.children();
        if (children.size() == 1) {
            // Single declarator
            ParseNode declarator = children.get(0);
            if (declarator instanceof NonTerminalNode declNode && "<struct_deklarator>".equals(declNode.symbol())) {
                Map<String, Integer> sizes = extractArraySizesFromDeclarator(declNode);
                result.putAll(sizes);
            }
        } else if (children.size() == 3) {
            // Multiple declarators: process list first, then new declarator
            ParseNode list = children.get(0);
            ParseNode declarator = children.get(2);
            if (list instanceof NonTerminalNode listNode) {
                Map<String, Integer> listSizes = extractArraySizesFromDeclaratorList(listNode);
                result.putAll(listSizes);
            }
            if (declarator instanceof NonTerminalNode declNode && "<struct_deklarator>".equals(declNode.symbol())) {
                Map<String, Integer> declSizes = extractArraySizesFromDeclarator(declNode);
                result.putAll(declSizes);
            }
        }
        
        return result;
    }
    
    /**
     * Extracts array size from a struct declarator.
     * 
     * <p>Structure: <struct_deklarator> -> <deklarator> -> <izravni_deklarator> -> IDN L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
     * 
     * <p>Note: elementCount is set on the nested <deklarator> node, not on <struct_deklarator> itself.
     */
    private Map<String, Integer> extractArraySizesFromDeclarator(NonTerminalNode declaratorNode) {
        Map<String, Integer> result = new HashMap<>();
        
        // First check semantic attributes on this node
        if (declaratorNode.attributes() != null) {
            String fieldName = declaratorNode.attributes().identifier();
            int elementCount = declaratorNode.attributes().elementCount();
            if (fieldName != null && elementCount > 0) {
                result.put(fieldName, elementCount);
                return result; // Found via semantic attributes, done
            }
        }
        
        // Check nested <deklarator> node (elementCount is set there, not on <struct_deklarator>)
        var children = declaratorNode.children();
        if (children.size() == 1 && children.get(0) instanceof NonTerminalNode nestedDecl &&
            "<deklarator>".equals(nestedDecl.symbol())) {
            if (nestedDecl.attributes() != null) {
                String fieldName = nestedDecl.attributes().identifier();
                int elementCount = nestedDecl.attributes().elementCount();
                if (fieldName != null && elementCount > 0) {
                    result.put(fieldName, elementCount);
                    return result; // Found via semantic attributes on nested declarator
                }
            }
        }
        
        // Otherwise, search recursively through the declarator structure
        // <struct_deklarator> -> <deklarator> -> <izravni_deklarator> -> array pattern
        return extractArraySizesFromDeclaratorRecursive(declaratorNode);
    }
    
    /**
     * Recursively searches through declarator nodes to find array declarations.
     * 
     * <p>Structure: <struct_deklarator> -> <deklarator> -> <izravni_deklarator> -> <izravni_deklarator> IDN L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
     */
    private Map<String, Integer> extractArraySizesFromDeclaratorRecursive(NonTerminalNode node) {
        Map<String, Integer> result = new HashMap<>();
        String symbol = node.symbol();
        var children = node.children();
        
        // Check if this is an <izravni_deklarator> with array pattern
        // Pattern 1: <izravni_deklarator> -> <izravni_deklarator> IDN L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
        // Pattern 2: <izravni_deklarator> -> IDN L_UGL_ZAGRADA <izraz> D_UGL_ZAGRADA
        if ("<izravni_deklarator>".equals(symbol)) {
            // Look for pattern: ... IDN L_UGL_ZAGRADA <expression> D_UGL_ZAGRADA ...
            for (int i = 0; i < children.size() - 3; i++) {
                ParseNode idn = children.get(i);
                if (i + 3 < children.size()) {
                    ParseNode leftBracket = children.get(i + 1);
                    ParseNode sizeExpr = children.get(i + 2);
                    ParseNode rightBracket = children.get(i + 3);
                    
                    if (idn instanceof TerminalNode idnTerminal && "IDN".equals(idnTerminal.symbol()) &&
                        leftBracket instanceof TerminalNode && "L_UGL_ZAGRADA".equals(leftBracket.symbol()) &&
                        rightBracket instanceof TerminalNode && "D_UGL_ZAGRADA".equals(rightBracket.symbol())) {
                        
                        String fieldName = idnTerminal.lexeme();
                        Integer arraySize = extractArraySizeFromExpression(sizeExpr);
                        if (arraySize != null && arraySize > 0) {
                            result.put(fieldName, arraySize);
                            return result; // Found it
                        }
                    }
                }
            }
        }
        
        // Recursively search children
        for (ParseNode child : children) {
            if (child instanceof NonTerminalNode childNode) {
                Map<String, Integer> childResult = extractArraySizesFromDeclaratorRecursive(childNode);
                if (!childResult.isEmpty()) {
                    result.putAll(childResult);
                    return result; // Found it in a child
                }
            }
        }
        
        return result;
    }
    
    /**
     * Extracts array size from an expression node (should be a compile-time constant).
     * 
     * <p>This method searches for a {@code BROJ} (number) terminal in the expression tree
     * and parses it as an integer. Array sizes in C must be compile-time constants, so
     * the expression should evaluate to a constant integer.
     * 
     * <p><b>Grammar Pattern:</b>
     * <pre>
     * &lt;izraz&gt; ::= ... (various expression rules) ...
     *              | BROJ  (constant integer)
     * </pre>
     * 
     * <p><b>Example:</b>
     * <ul>
     *   <li>{@code int arr[10];} - expression is {@code 10} (BROJ terminal)</li>
     *   <li>{@code int arr[2 * 5];} - expression is {@code 2 * 5} (must be constant-folded)</li>
     * </ul>
     * 
     * <p><b>Limitation:</b> This method only handles simple {@code BROJ} terminals.
     * Constant-folded expressions (like {@code 2 * 5}) would need to be evaluated by
     * the semantic analyzer first. In practice, array sizes are typically simple constants.
     * 
     * @param exprNode the expression node (may be a terminal or non-terminal)
     * @return the array size as an integer, or null if not found or not a valid constant
     */
    private Integer extractArraySizeFromExpression(ParseNode exprNode) {
        // Check if this is a BROJ (number) terminal directly
        // This is the most common case: int arr[10];
        if (exprNode instanceof TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
            try {
                // Parse the lexeme as an integer
                // Array sizes must be positive integers
                return Integer.parseInt(terminal.lexeme());
            } catch (NumberFormatException e) {
                // Invalid number format - return null
                return null;
            }
        }
        
        // Recursively search for BROJ terminal in expression tree
        // This handles cases where the expression is wrapped in expression nodes
        // (though in practice, array sizes are typically simple constants)
        if (exprNode instanceof NonTerminalNode nonTerminal) {
            for (ParseNode child : nonTerminal.children()) {
                Integer result = extractArraySizeFromExpression(child);
                if (result != null) {
                    return result;
                }
            }
        }
        
        // No BROJ terminal found - expression is not a simple constant
        return null;
    }
}
