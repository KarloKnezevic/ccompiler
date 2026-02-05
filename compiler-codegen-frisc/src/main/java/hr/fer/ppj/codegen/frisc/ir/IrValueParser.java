package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.ir.model.IrConst;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses values and call operands used in RHS expressions.
 */
final class IrValueParser {

  IrProgramModel.Value parseValue(String valueStr) {
    String trimmed = valueStr.trim();
    if (trimmed.startsWith("t")) {
      return new IrProgramModel.Temp(parseTempIndex(trimmed));
    }
    IrConst constant = IrConstParser.parse(trimmed);
    return new IrProgramModel.Const(constant);
  }

  IrProgramModel.SymbolRef parseSymbolRef(String symbolStr) {
    String trimmed = symbolStr.trim();
    int colon = trimmed.indexOf(':');
    if (colon < 0) {
      throw new CodeGenerationException("Invalid symbol ref: " + symbolStr);
    }
    String kindStr = trimmed.substring(0, colon).trim();
    String name = trimmed.substring(colon + 1).trim();
    return new IrProgramModel.SymbolRef(parseSymbolKind(kindStr), name);
  }

  CallInfo parseCall(String callStr) {
    String trimmed = callStr.trim();
    if (!trimmed.startsWith("func:")) {
      throw new CodeGenerationException("Invalid call: " + callStr);
    }
    String afterFunc = trimmed.substring("func:".length()).trim();
    int lparen = afterFunc.indexOf('(');
    int rparen = afterFunc.lastIndexOf(')');
    if (lparen < 0 || rparen < 0 || rparen < lparen) {
      throw new CodeGenerationException("Invalid call: " + callStr);
    }
    String funcName = afterFunc.substring(0, lparen).trim();
    String argsStr = afterFunc.substring(lparen + 1, rparen).trim();
    String after = afterFunc.substring(rparen + 1).trim();
    if (!after.startsWith(":")) {
      throw new CodeGenerationException("Invalid call (missing return type): " + callStr);
    }
    String returnType = after.substring(1).trim();

    List<IrProgramModel.Value> args = new ArrayList<>();
    if (!argsStr.isEmpty()) {
      List<String> parts = IrParseUtil.splitTopLevel(argsStr, ',');
      for (String part : parts) {
        args.add(parseValue(part));
      }
    }

    return new CallInfo(funcName, returnType, args);
  }

  int parseTempIndex(String tempStr) {
    String trimmed = tempStr.trim();
    if (!trimmed.startsWith("t")) {
      throw new CodeGenerationException("Invalid temp: " + tempStr);
    }
    String digits = trimmed.substring(1).trim();
    if (digits.isEmpty()) {
      throw new CodeGenerationException("Invalid temp: " + tempStr);
    }
    return IrParseUtil.parseInt(digits, "temp index");
  }

  private IrProgramModel.SymbolRefKind parseSymbolKind(String kindStr) {
    return switch (kindStr) {
      case "local" -> IrProgramModel.SymbolRefKind.LOCAL;
      case "param" -> IrProgramModel.SymbolRefKind.PARAM;
      case "global" -> IrProgramModel.SymbolRefKind.GLOBAL;
      default -> throw new CodeGenerationException("Unknown symbol ref kind: " + kindStr);
    };
  }

  record CallInfo(String funcName, String returnType, List<IrProgramModel.Value> args) {
  }
}
