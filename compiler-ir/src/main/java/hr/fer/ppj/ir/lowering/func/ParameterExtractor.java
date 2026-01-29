package hr.fer.ppj.ir.lowering.func;

import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.model.IrFunction;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Extracts function parameters from declarator nodes.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class ParameterExtractor {

  /**
   * Extracts parameters from a declarator node.
   */
  public List<IrFunction.Parameter> extractParameters(
      NonTerminalNode declaratorNode, FunctionType funcType) {
    Objects.requireNonNull(declaratorNode, "declaratorNode must not be null");
    Objects.requireNonNull(funcType, "funcType must not be null");

    List<IrFunction.Parameter> parameters = new ArrayList<>();
    List<String> paramNames = declaratorNode.attributes().parameterNames();
    List<Type> paramTypes = declaratorNode.attributes().parameterTypes();

    if (!paramNames.isEmpty() && paramNames.size() == paramTypes.size()) {
      // Use parameters from declarator (has names)
      for (int i = 0; i < paramNames.size(); i++) {
        hr.fer.ppj.ir.types.IrType irParamType = TypeMapper.toIrType(paramTypes.get(i));
        parameters.add(new IrFunction.Parameter(paramNames.get(i), irParamType));
      }
    } else {
      // Fall back to function type (may not have names)
      List<Type> funcParamTypes = funcType.parameterTypes();
      for (int i = 0; i < funcParamTypes.size(); i++) {
        hr.fer.ppj.ir.types.IrType irParamType = TypeMapper.toIrType(funcParamTypes.get(i));
        String paramName = (i < paramNames.size()) ? paramNames.get(i) : "param" + i;
        parameters.add(new IrFunction.Parameter(paramName, irParamType));
      }
    }

    return parameters;
  }
}
