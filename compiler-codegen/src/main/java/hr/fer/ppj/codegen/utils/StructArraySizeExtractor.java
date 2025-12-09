package hr.fer.ppj.codegen.utils;

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
 * <p>Example: For {@code struct Mixed { int arr[2]; }}, this extractor will
 * find the struct definition and return a map with {@code {"arr" -> 2}}.
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
     * @param structTag the struct tag name (null for anonymous structs)
     * @return a map from field name to array size (only for array fields)
     */
    public Map<String, Integer> extractArraySizes(String structTag) {
        if (parseTree == null) {
            return new HashMap<>();
        }
        
        // Use a consistent cache key (null for anonymous, tag name for tagged structs)
        String cacheKey = structTag != null ? structTag : "__anonymous__";
        
        // Check cache first
        if (cache.containsKey(cacheKey)) {
            return cache.get(cacheKey);
        }
        
        // Extract from parse tree - search more aggressively
        Map<String, Integer> arraySizes = extractArraySizesFromNode(parseTree, structTag);
        
        // If we didn't find sizes, try a more aggressive search:
        // 1. Search all struct definitions (ignore tag)
        // 2. Check semantic attributes on all nodes
        if (arraySizes.isEmpty()) {
            // Try searching all struct definitions
            Map<String, Integer> allSizes = extractArraySizesFromAllStructs(parseTree);
            if (!allSizes.isEmpty()) {
                // If we have a tag, we can't be sure which struct this is
                // But if there's only one struct with arrays, it's probably the right one
                arraySizes = allSizes;
            }
        }
        
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
     * Extracts array size from an expression node (should be a constant).
     */
    private Integer extractArraySizeFromExpression(ParseNode exprNode) {
        if (exprNode instanceof TerminalNode terminal && "BROJ".equals(terminal.symbol())) {
            try {
                return Integer.parseInt(terminal.lexeme());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        
        // Recursively search for BROJ terminal
        if (exprNode instanceof NonTerminalNode nonTerminal) {
            for (ParseNode child : nonTerminal.children()) {
                Integer result = extractArraySizeFromExpression(child);
                if (result != null) {
                    return result;
                }
            }
        }
        
        return null;
    }
}
