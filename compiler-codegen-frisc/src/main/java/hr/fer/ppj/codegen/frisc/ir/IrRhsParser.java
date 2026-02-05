package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrType;
import java.util.List;

/**
 * Parses IR RHS expressions.
 */
final class IrRhsParser {
  private final IrValueParser valueParser;

  IrRhsParser(IrValueParser valueParser) {
    this.valueParser = valueParser;
  }

  IrProgramModel.Rhs parseRhs(String rhsStr) {
    String trimmed = rhsStr.trim();
    if (trimmed.startsWith("addr_of_symbol")) {
      String symbolStr = trimmed.substring("addr_of_symbol".length()).trim();
      return new IrProgramModel.AddrOfSymbol(valueParser.parseSymbolRef(symbolStr));
    }
    if (trimmed.startsWith("addr_index")) {
      String rest = trimmed.substring("addr_index".length()).trim();
      List<String> parts = IrParseUtil.splitTopLevel(rest, ',');
      if (parts.size() != 3) {
        throw new CodeGenerationException("Invalid addr_index: " + rhsStr);
      }
      IrProgramModel.Value base = valueParser.parseValue(parts.get(0));
      IrProgramModel.Value index = valueParser.parseValue(parts.get(1));
      int elemSize = IrParseUtil.parseInt(parts.get(2).trim(), "elemSize");
      return new IrProgramModel.AddrIndex(base, index, elemSize);
    }
    if (trimmed.startsWith("addr_field")) {
      String rest = trimmed.substring("addr_field".length()).trim();
      List<String> parts = IrParseUtil.splitTopLevel(rest, ',');
      if (parts.size() != 2) {
        throw new CodeGenerationException("Invalid addr_field: " + rhsStr);
      }
      IrProgramModel.Value base = valueParser.parseValue(parts.get(0));
      String fieldPart = parts.get(1).trim();
      int dot = fieldPart.indexOf('.');
      if (dot < 0) {
        throw new CodeGenerationException("Invalid addr_field: " + rhsStr);
      }
      String structName = fieldPart.substring(0, dot).trim();
      String fieldName = fieldPart.substring(dot + 1).trim();
      return new IrProgramModel.AddrField(base, structName, fieldName);
    }
    if (trimmed.startsWith("load ")) {
      String rest = trimmed.substring("load".length()).trim();
      String[] split = IrParseUtil.splitByLastTypeSuffix(rest);
      IrProgramModel.Value addr = valueParser.parseValue(split[0]);
      IrType type = IrTypeParser.parse(split[1]);
      return new IrProgramModel.Load(addr, type);
    }
    if (trimmed.startsWith("call ")) {
      IrValueParser.CallInfo callInfo = valueParser.parseCall(trimmed.substring("call".length()).trim());
      IrType returnType = "void".equals(callInfo.returnType())
          ? null
          : IrTypeParser.parse(callInfo.returnType());
      if (returnType == null) {
        throw new CodeGenerationException("Call used as expression cannot return void: " + rhsStr);
      }
      return new IrProgramModel.Call(callInfo.funcName(), callInfo.args(), returnType);
    }

    if (IrParseUtil.startsWithAny(trimmed, "cmp_eq", "cmp_ne", "cmp_lt", "cmp_le", "cmp_gt", "cmp_ge")) {
      String opName = trimmed.substring(0, trimmed.indexOf(' ')).trim();
      String rest = trimmed.substring(opName.length()).trim();
      String[] split = IrParseUtil.splitByLastTypeSuffix(rest);
      List<String> parts = IrParseUtil.splitTopLevel(split[0], ',');
      if (parts.size() != 2) {
        throw new CodeGenerationException("Invalid cmp: " + rhsStr);
      }
      IrProgramModel.Value left = valueParser.parseValue(parts.get(0));
      IrProgramModel.Value right = valueParser.parseValue(parts.get(1));
      return new IrProgramModel.CmpOp(parseCmpOp(opName), left, right);
    }

    if (IrParseUtil.startsWithAny(trimmed, "add", "sub", "mul", "div", "mod", "and", "or", "xor", "shl", "shr")) {
      String opName = trimmed.substring(0, trimmed.indexOf(' ')).trim();
      String rest = trimmed.substring(opName.length()).trim();
      String[] split = IrParseUtil.splitByLastTypeSuffix(rest);
      List<String> parts = IrParseUtil.splitTopLevel(split[0], ',');
      if (parts.size() != 2) {
        throw new CodeGenerationException("Invalid binop: " + rhsStr);
      }
      IrProgramModel.Value left = valueParser.parseValue(parts.get(0));
      IrProgramModel.Value right = valueParser.parseValue(parts.get(1));
      IrType type = IrTypeParser.parse(split[1]);
      return new IrProgramModel.BinOp(parseBinOp(opName), left, right, type);
    }

    if (IrParseUtil.startsWithAny(trimmed, "neg", "not")) {
      String opName = trimmed.substring(0, trimmed.indexOf(' ')).trim();
      String rest = trimmed.substring(opName.length()).trim();
      String[] split = IrParseUtil.splitByLastTypeSuffix(rest);
      IrProgramModel.Value operand = valueParser.parseValue(split[0]);
      IrType type = IrTypeParser.parse(split[1]);
      return new IrProgramModel.UnaryOp(parseUnaryOp(opName), operand, type);
    }

    if (IrParseUtil.startsWithAny(trimmed, "trunc", "sext", "zext", "ptrcast", "itof", "ftoi")) {
      String opName = trimmed.substring(0, trimmed.indexOf(' ')).trim();
      String rest = trimmed.substring(opName.length()).trim();
      String[] split = IrParseUtil.splitByLastTypeSuffix(rest);
      IrProgramModel.Value operand = valueParser.parseValue(split[0]);
      IrType type = IrTypeParser.parse(split[1]);
      return new IrProgramModel.CastOp(parseCastOp(opName), operand, type);
    }

    IrConst constant = IrConstParser.parse(trimmed);
    return new IrProgramModel.ConstRhs(constant);
  }

  IrProgramModel.Value parseValue(String valueStr) {
    return valueParser.parseValue(valueStr);
  }

  private IrProgramModel.BinOpName parseBinOp(String opName) {
    return switch (opName) {
      case "add" -> IrProgramModel.BinOpName.ADD;
      case "sub" -> IrProgramModel.BinOpName.SUB;
      case "mul" -> IrProgramModel.BinOpName.MUL;
      case "div" -> IrProgramModel.BinOpName.DIV;
      case "mod" -> IrProgramModel.BinOpName.MOD;
      case "and" -> IrProgramModel.BinOpName.AND;
      case "or" -> IrProgramModel.BinOpName.OR;
      case "xor" -> IrProgramModel.BinOpName.XOR;
      case "shl" -> IrProgramModel.BinOpName.SHL;
      case "shr" -> IrProgramModel.BinOpName.SHR;
      default -> throw new CodeGenerationException("Unknown binop: " + opName);
    };
  }

  private IrProgramModel.CmpOpName parseCmpOp(String opName) {
    return switch (opName) {
      case "cmp_eq" -> IrProgramModel.CmpOpName.EQ;
      case "cmp_ne" -> IrProgramModel.CmpOpName.NE;
      case "cmp_lt" -> IrProgramModel.CmpOpName.LT;
      case "cmp_le" -> IrProgramModel.CmpOpName.LE;
      case "cmp_gt" -> IrProgramModel.CmpOpName.GT;
      case "cmp_ge" -> IrProgramModel.CmpOpName.GE;
      default -> throw new CodeGenerationException("Unknown cmp op: " + opName);
    };
  }

  private IrProgramModel.UnaryOpName parseUnaryOp(String opName) {
    return switch (opName) {
      case "neg" -> IrProgramModel.UnaryOpName.NEG;
      case "not" -> IrProgramModel.UnaryOpName.NOT;
      default -> throw new CodeGenerationException("Unknown unary op: " + opName);
    };
  }

  private IrProgramModel.CastName parseCastOp(String opName) {
    return switch (opName) {
      case "trunc" -> IrProgramModel.CastName.TRUNC;
      case "sext" -> IrProgramModel.CastName.SEXT;
      case "zext" -> IrProgramModel.CastName.ZEXT;
      case "ptrcast" -> IrProgramModel.CastName.PTRCAST;
      case "itof" -> IrProgramModel.CastName.ITOF;
      case "ftoi" -> IrProgramModel.CastName.FTOI;
      default -> throw new CodeGenerationException("Unknown cast op: " + opName);
    };
  }
}
