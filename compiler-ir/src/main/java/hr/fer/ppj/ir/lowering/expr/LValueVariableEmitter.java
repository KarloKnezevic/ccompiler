package hr.fer.ppj.ir.lowering.expr;

import hr.fer.ppj.ir.build.IrFunctionBuilder;
import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.model.IrInstruction;
import hr.fer.ppj.ir.model.IrRhs;
import hr.fer.ppj.ir.model.IrSymbolRef;
import hr.fer.ppj.ir.model.IrTemp;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.util.AddressReuseContext;
import hr.fer.ppj.ir.util.BlockLocalSymbolAddressCache;
import hr.fer.ppj.ir.util.SymbolResolver;
import hr.fer.ppj.ir.util.VariableNameManager;
import hr.fer.ppj.semantics.symbols.SymbolTable;
import hr.fer.ppj.semantics.types.Type;
import java.util.Objects;

/**
 * Handles l-value generation for variable references.
 *
 * <p>Generates {@code addr_of_symbol} IR instructions as defined in the grammar:
 * <pre>
 * AddrOfSymbol ::= "addr_of_symbol" SymbolRef ;
 * SymbolRef    ::= ("local:" | "param:" | "global:") Ident ;
 * </pre>
 *
 * <p>Implements address caching to avoid redundant addr_of_symbol emissions
 * within the same basic block.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
final class LValueVariableEmitter {

  private final SymbolTable globalScope;
  private final VariableNameManager variableNameManager;
  private final AddressReuseContext addressReuseContext;

  LValueVariableEmitter(
      SymbolTable globalScope,
      VariableNameManager variableNameManager,
      AddressReuseContext addressReuseContext) {
    this.globalScope = Objects.requireNonNull(globalScope);
    this.variableNameManager = Objects.requireNonNull(variableNameManager);
    this.addressReuseContext = Objects.requireNonNull(addressReuseContext);
  }

  /**
   * Emits l-value for a variable by name.
   *
   * @param varName the variable name
   * @param varType the variable type
   * @param functionContext the function context
   * @return the address temp
   */
  IrTemp emitLValueForVariable(
      String varName,
      Type varType,
      hr.fer.ppj.ir.lowering.FunctionContext functionContext) {

    IrFunctionBuilder builder = functionContext.functionBuilder();
    if (builder.getCurrentBlockLabel() == null) {
      builder.startNewBlock();
    }

    String actualVarName = variableNameManager.getActualName(varName);
    IrSymbolRef.Kind kind = SymbolResolver.determineSymbolKind(
        varName, actualVarName, builder, functionContext.functionScope(), globalScope);

    // Check block-local address cache first
    String symbolRefKey = BlockLocalSymbolAddressCache.createSymbolRefKey(kind, actualVarName);
    BlockLocalSymbolAddressCache blockCache = functionContext.blockLocalAddressCache();
    IrTemp cachedAddr = blockCache.get(symbolRefKey);
    if (cachedAddr != null) {
      return cachedAddr;
    }

    // Check for last load address (for reusing address from recent loads)
    IrTemp lastLoadAddr = addressReuseContext.getLastLoadAddress(varName);
    if (lastLoadAddr != null) {
      blockCache.put(symbolRefKey, lastLoadAddr);
      return lastLoadAddr;
    }

    // Create new address temp
    IrSymbolRef symbolRef = new IrSymbolRef(kind, actualVarName);
    IrType irType = TypeMapper.toIrType(varType);
    IrRhs.AddrOfSymbol addrOf = new IrRhs.AddrOfSymbol(symbolRef, new IrPointerType(irType));
    IrTemp addrTemp = builder.tempFactory().newTemp(addrOf.resultType());
    builder.addInstruction(new IrInstruction.IrAssignInstr(addrTemp, addrOf));

    // Cache for future reuse in this block
    blockCache.put(symbolRefKey, addrTemp);
    return addrTemp;
  }

  /**
   * Checks if an address can be reused from context.
   *
   * @param varName the variable name
   * @return true if reuse is possible
   */
  boolean canReuseAddress(String varName) {
    return addressReuseContext.canReuse(varName);
  }

  /**
   * Gets the reuse address for a variable.
   *
   * @param varName the variable name
   * @return the reuse address, or null if not available
   */
  IrTemp getReuseAddress(String varName) {
    return addressReuseContext.getReuseAddress(varName);
  }

  /**
   * Gets the last load address for a variable.
   *
   * @param varName the variable name
   * @return the last load address, or null if not available
   */
  IrTemp getLastLoadAddress(String varName) {
    return addressReuseContext.getLastLoadAddress(varName);
  }
}
