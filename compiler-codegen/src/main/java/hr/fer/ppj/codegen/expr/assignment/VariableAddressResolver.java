package hr.fer.ppj.codegen.expr.assignment;

import hr.fer.ppj.codegen.CodeGenContext;
import hr.fer.ppj.codegen.utils.IdentifierExtractor;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import java.util.Objects;

/**
 * Resolves variable addresses and extracts variable names from expressions.
 * 
 * <p>This utility class provides methods to:
 * <ul>
 *   <li>Extract variable names from lvalue expressions</li>
 *   <li>Get FRISC addresses for variables (local or global)</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class VariableAddressResolver {
    
    private final hr.fer.ppj.codegen.utils.VariableAddressResolver addressResolver;
    
    /**
     * Creates a new variable address resolver.
     * 
     * @param context the code generation context
     */
    public VariableAddressResolver(CodeGenContext context) {
        Objects.requireNonNull(context, "context must not be null");
        this.addressResolver = new hr.fer.ppj.codegen.utils.VariableAddressResolver(context);
    }
    
    /**
     * Extracts a variable name from a simple lvalue expression.
     * 
     * @param lvalue the lvalue expression
     * @return the variable name, or null if not a simple variable
     */
    public String extractVariableName(NonTerminalNode lvalue) {
        return IdentifierExtractor.findIdentifier(lvalue);
    }
    
    /**
     * Gets the FRISC address for a variable (local or global).
     * 
     * @param variableName the variable name
     * @return the FRISC address expression
     */
    public String getVariableAddress(String variableName) {
        return addressResolver.getVariableAddress(variableName);
    }
}

