package hr.fer.ppj.ir.util;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.model.IrSlot;
import hr.fer.ppj.ir.model.IrSymbolRef;
import hr.fer.ppj.semantics.symbols.Symbol;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.symbols.VariableSymbol;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolves variable names to their symbol kind (LOCAL, PARAM, or GLOBAL).
 *
 * <p>This utility implements the canonical symbol resolution logic:
 * <ol>
 *   <li>Check if it's a parameter</li>
 *   <li>Check if there's a local slot</li>
 *   <li>Check if it's declared in global scope</li>
 *   <li>Check function scope chain</li>
 *   <li>Default to GLOBAL</li>
 * </ol>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class SymbolResolver {

  private SymbolResolver() {}

  /**
   * Determines the symbol kind for a variable name.
   *
   * @param varName the variable name (original, not renamed)
   * @param actualVarName the actual IR name (may be renamed for shadowing)
   * @param functionBuilder the function builder (may be null)
   * @param functionScope the function scope (may be null)
   * @param globalScope the global scope
   * @return the symbol kind
   */
  public static IrSymbolRef.Kind determineSymbolKind(
      String varName,
      String actualVarName,
      IrFunctionBuilder functionBuilder,
      SymbolTable functionScope,
      SymbolTable globalScope) {
    Objects.requireNonNull(varName, "varName must not be null");
    Objects.requireNonNull(actualVarName, "actualVarName must not be null");
    Objects.requireNonNull(globalScope, "globalScope must not be null");

    // Check if it's a parameter
    if (functionBuilder != null && functionBuilder.isParameter(actualVarName)) {
      return IrSymbolRef.Kind.PARAM;
    }

    if (functionBuilder != null) {
      // Check if there's a local slot (most reliable indicator)
      boolean hasLocalSlot = functionBuilder.getSlots().stream()
          .anyMatch(s -> s.kind() == IrSlot.Kind.LOCAL && s.name().equals(actualVarName));
      if (hasLocalSlot) {
        return IrSymbolRef.Kind.LOCAL;
      }

      // Check if it's declared in global scope
      Optional<Symbol> globalOpt = globalScope.lookupLocal(varName);
      if (globalOpt.isPresent() && globalOpt.get() instanceof VariableSymbol) {
        // Found in global scope - check if it's shadowed by a local
        boolean shadowedByLocal = false;
        if (functionScope != null) {
          SymbolTable scope = functionScope;
          while (scope != null && scope != globalScope) {
            Optional<Symbol> localOpt = scope.lookupLocal(varName);
            if (localOpt.isPresent() && localOpt.get() instanceof VariableSymbol) {
              shadowedByLocal = true;
              break;
            }
            scope = scope.parent();
          }
        }

        if (!shadowedByLocal) {
          return IrSymbolRef.Kind.GLOBAL;
        } else {
          return IrSymbolRef.Kind.LOCAL;
        }
      } else if (functionScope != null) {
        // Not in global scope - check function scope chain
        SymbolTable scope = functionScope;
        boolean foundInFunctionScope = false;
        while (scope != null && scope != globalScope) {
          Optional<Symbol> localOpt = scope.lookupLocal(varName);
          if (localOpt.isPresent() && localOpt.get() instanceof VariableSymbol) {
            foundInFunctionScope = true;
            break;
          }
          scope = scope.parent();
        }

        if (foundInFunctionScope) {
          return IrSymbolRef.Kind.LOCAL;
        } else {
          // Not found anywhere - assume global (might be forward reference)
          return IrSymbolRef.Kind.GLOBAL;
        }
      } else {
        return IrSymbolRef.Kind.GLOBAL;
      }
    }

    return IrSymbolRef.Kind.GLOBAL;
  }
}
