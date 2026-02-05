package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.ir.types.IrType;
import java.util.List;

/**
 * Parses instructions and terminators inside blocks.
 */
final class IrInstructionParser {
  private final IrRhsParser rhsParser;
  private final IrValueParser valueParser;

  IrInstructionParser(IrRhsParser rhsParser, IrValueParser valueParser) {
    this.rhsParser = rhsParser;
    this.valueParser = valueParser;
  }

  IrProgramModel.Instruction parseInstruction(String line) {
    if (line.startsWith("store ")) {
      return parseStore(line);
    }
    if (line.startsWith("call ")) {
      return parseVoidCall(line);
    }
    int eq = line.indexOf('=');
    if (eq < 0) {
      throw new CodeGenerationException("Invalid instruction line: " + line);
    }
    String destStr = line.substring(0, eq).trim();
    int tempIndex = parseTempIndex(destStr);
    IrProgramModel.Temp dest = new IrProgramModel.Temp(tempIndex);
    String rhsStr = line.substring(eq + 1).trim();
    IrProgramModel.Rhs rhs = rhsParser.parseRhs(rhsStr);
    return new IrProgramModel.Assign(dest, rhs);
  }

  IrProgramModel.Terminator parseTerminator(String line) {
    if (line.startsWith("br ")) {
      String rest = line.substring("br".length()).trim();
      List<String> parts = IrParseUtil.splitTopLevel(rest, ',');
      if (parts.size() != 3) {
        throw new CodeGenerationException("Invalid br terminator: " + line);
      }
      IrProgramModel.Value cond = rhsParser.parseValue(parts.get(0));
      String trueLabel = parts.get(1).trim();
      String falseLabel = parts.get(2).trim();
      return new IrProgramModel.Br(cond, trueLabel, falseLabel);
    }
    if (line.startsWith("jmp ")) {
      String target = line.substring("jmp".length()).trim();
      return new IrProgramModel.Jmp(target);
    }
    if (line.equals("ret")) {
      return new IrProgramModel.Ret(null);
    }
    if (line.startsWith("ret ")) {
      String valueStr = line.substring("ret".length()).trim();
      return new IrProgramModel.Ret(rhsParser.parseValue(valueStr));
    }
    throw new CodeGenerationException("Invalid terminator line: " + line);
  }

  private IrProgramModel.Store parseStore(String line) {
    String rest = line.substring("store".length()).trim();
    String[] split = IrParseUtil.splitByLastTypeSuffix(rest);
    String valuesPart = split[0];
    String typeStr = split[1];

    List<String> parts = IrParseUtil.splitTopLevel(valuesPart, ',');
    if (parts.size() != 2) {
      throw new CodeGenerationException("Invalid store: " + line);
    }
    IrProgramModel.Value addr = valueParser.parseValue(parts.get(0));
    IrProgramModel.Value value = valueParser.parseValue(parts.get(1));
    IrType storeType = IrTypeParser.parse(typeStr);
    return new IrProgramModel.Store(addr, value, storeType);
  }

  private IrProgramModel.VoidCall parseVoidCall(String line) {
    String rest = line.substring("call".length()).trim();
    IrValueParser.CallInfo callInfo = valueParser.parseCall(rest);
    if (callInfo.returnType() != null && !"void".equals(callInfo.returnType())) {
      throw new CodeGenerationException("Void call must have void return type: " + line);
    }
    return new IrProgramModel.VoidCall(callInfo.funcName(), callInfo.args());
  }

  private int parseTempIndex(String tempStr) {
    return valueParser.parseTempIndex(tempStr);
  }
}
