package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class IrTextParser {

  public IrProgramModel parse(String text) {
    Objects.requireNonNull(text, "text must not be null");
    LineCursor cursor = new LineCursor(text);

    String line = cursor.nextNonEmptyLine();
    if (line == null || !line.equals(".program")) {
      throw new CodeGenerationException("IR must start with .program");
    }

    List<IrProgramModel.StructDef> structDefs = new ArrayList<>();
    List<IrProgramModel.GlobalVar> globals = new ArrayList<>();
    List<IrProgramModel.Function> functions = new ArrayList<>();

    while (true) {
      String peek = cursor.peekNonEmptyLine();
      if (peek == null) {
        throw new CodeGenerationException("Unexpected end of IR (missing .endprogram)");
      }
      if (peek.equals(".endprogram")) {
        cursor.nextNonEmptyLine();
        break;
      }
      if (peek.startsWith(".type struct")) {
        structDefs.add(parseStructDef(cursor));
        continue;
      }
      if (peek.equals(".globals")) {
        cursor.nextNonEmptyLine();
        globals.addAll(parseGlobals(cursor));
        continue;
      }
      if (peek.startsWith(".func")) {
        functions.add(parseFunction(cursor));
        continue;
      }
      throw new CodeGenerationException("Unexpected top-level IR line: " + peek);
    }

    return new IrProgramModel(structDefs, globals, functions);
  }

  private IrProgramModel.StructDef parseStructDef(LineCursor cursor) {
    String header = cursor.nextNonEmptyLine();
    String trimmed = header.trim();
    if (!trimmed.startsWith(".type struct")) {
      throw new CodeGenerationException("Expected .type struct, got: " + header);
    }

    String after = trimmed.substring(".type struct".length()).trim();
    int braceIndex = after.indexOf('{');
    if (braceIndex < 0) {
      throw new CodeGenerationException("Struct definition missing '{': " + header);
    }
    String structName = after.substring(0, braceIndex).trim();

    List<IrProgramModel.StructField> fields = new ArrayList<>();
    while (true) {
      String line = cursor.nextNonEmptyLine();
      if (line == null) {
        throw new CodeGenerationException("Unexpected end while parsing struct " + structName);
      }
      String t = line.trim();
      if (t.equals("}")) {
        break;
      }
      int colon = t.indexOf(':');
      int at = t.lastIndexOf('@');
      if (colon < 0 || at < 0 || at < colon) {
        throw new CodeGenerationException("Invalid struct field line: " + line);
      }
      String fieldName = t.substring(0, colon).trim();
      String typeStr = t.substring(colon + 1, at).trim();
      String offsetStr = t.substring(at + 1).trim();
      int offset = parseInt(offsetStr, "field offset");
      IrType type = TypeParser.parse(typeStr);
      fields.add(new IrProgramModel.StructField(fieldName, type, offset));
    }

    return new IrProgramModel.StructDef(structName, fields);
  }

  private List<IrProgramModel.GlobalVar> parseGlobals(LineCursor cursor) {
    List<IrProgramModel.GlobalVar> globals = new ArrayList<>();
    while (true) {
      String peek = cursor.peekNonEmptyLine();
      if (peek == null || peek.startsWith(".")) {
        break;
      }
      String line = cursor.nextNonEmptyLine().trim();
      if (!line.startsWith("global")) {
        throw new CodeGenerationException("Invalid global line: " + line);
      }
      String rest = line.substring("global".length()).trim();
      int colon = rest.indexOf(':');
      if (colon < 0) {
        throw new CodeGenerationException("Invalid global line: " + line);
      }
      String name = rest.substring(0, colon).trim();
      String afterColon = rest.substring(colon + 1).trim();

      String typeStr = afterColon;
      String initStr = null;
      int eqIndex = indexOfTopLevel(afterColon, '=');
      if (eqIndex >= 0) {
        typeStr = afterColon.substring(0, eqIndex).trim();
        initStr = afterColon.substring(eqIndex + 1).trim();
      }

      IrType type = TypeParser.parse(typeStr);
      IrConst initializer = null;
      if (initStr != null && !initStr.isEmpty()) {
        initializer = ConstParser.parse(initStr);
      }

      globals.add(new IrProgramModel.GlobalVar(name, type, initializer));
    }
    return globals;
  }

  private IrProgramModel.Function parseFunction(LineCursor cursor) {
    String header = cursor.nextNonEmptyLine();
    String trimmed = header.trim();
    if (!trimmed.startsWith(".func")) {
      throw new CodeGenerationException("Expected .func header, got: " + header);
    }

    String after = trimmed.substring(".func".length()).trim();
    int lparen = after.indexOf('(');
    int rparen = after.lastIndexOf(')');
    if (lparen < 0 || rparen < 0 || rparen < lparen) {
      throw new CodeGenerationException("Invalid function header: " + header);
    }
    String name = after.substring(0, lparen).trim();
    String paramsStr = after.substring(lparen + 1, rparen).trim();
    String returnPart = after.substring(rparen + 1).trim();
    if (!returnPart.startsWith(":")) {
      throw new CodeGenerationException("Invalid function header (missing return type): " + header);
    }
    String returnTypeStr = returnPart.substring(1).trim();

    List<IrProgramModel.Parameter> parameters = parseParameters(paramsStr);
    IrType returnType = "void".equals(returnTypeStr) ? null : TypeParser.parse(returnTypeStr);

    String frameLine = cursor.nextNonEmptyLine();
    FrameInfo frameInfo = parseFrame(frameLine);

    String slotsLine = cursor.nextNonEmptyLine();
    if (!slotsLine.trim().equals(".slots")) {
      throw new CodeGenerationException("Expected .slots, got: " + slotsLine);
    }
    List<IrProgramModel.Slot> slots = parseSlots(cursor);

    String blocksLine = cursor.nextNonEmptyLine();
    if (!blocksLine.trim().equals(".blocks")) {
      throw new CodeGenerationException("Expected .blocks, got: " + blocksLine);
    }
    List<IrProgramModel.Block> blocks = parseBlocks(cursor);

    String endFunc = cursor.nextNonEmptyLine();
    if (endFunc == null || !endFunc.trim().equals(".endfunc")) {
      throw new CodeGenerationException("Expected .endfunc, got: " + endFunc);
    }

    return new IrProgramModel.Function(
        name,
        parameters,
        returnType,
        frameInfo.localsBytes,
        frameInfo.alignBytes,
        slots,
        blocks);
  }

  private List<IrProgramModel.Parameter> parseParameters(String paramsStr) {
    List<IrProgramModel.Parameter> params = new ArrayList<>();
    if (paramsStr.isEmpty()) {
      return params;
    }
    List<String> parts = splitTopLevel(paramsStr, ',');
    for (String part : parts) {
      String trimmed = part.trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      int colon = trimmed.indexOf(':');
      if (colon < 0) {
        throw new CodeGenerationException("Invalid parameter: " + trimmed);
      }
      String name = trimmed.substring(0, colon).trim();
      String typeStr = trimmed.substring(colon + 1).trim();
      IrType type = TypeParser.parse(typeStr);
      params.add(new IrProgramModel.Parameter(name, type));
    }
    return params;
  }

  private FrameInfo parseFrame(String line) {
    String trimmed = line.trim();
    if (!trimmed.startsWith(".frame")) {
      throw new CodeGenerationException("Expected .frame, got: " + line);
    }
    int localsIndex = trimmed.indexOf("locals=");
    int bytesIndex = trimmed.indexOf("bytes", localsIndex);
    int alignIndex = trimmed.indexOf("align=");
    if (localsIndex < 0 || bytesIndex < 0 || alignIndex < 0) {
      throw new CodeGenerationException("Invalid frame line: " + line);
    }
    String localsStr = trimmed.substring(localsIndex + "locals=".length(), bytesIndex).trim();
    int localsBytes = parseInt(localsStr, "locals bytes");

    String alignStr = trimmed.substring(alignIndex + "align=".length()).trim();
    int alignBytes = parseInt(alignStr, "align bytes");
    return new FrameInfo(localsBytes, alignBytes);
  }

  private List<IrProgramModel.Slot> parseSlots(LineCursor cursor) {
    List<IrProgramModel.Slot> slots = new ArrayList<>();
    while (true) {
      String peek = cursor.peekNonEmptyLine();
      if (peek == null || peek.trim().equals(".blocks")) {
        break;
      }
      String line = cursor.nextNonEmptyLine().trim();
      if (line.isEmpty()) {
        continue;
      }
      int space = line.indexOf(' ');
      if (space < 0) {
        throw new CodeGenerationException("Invalid slot line: " + line);
      }
      String kindStr = line.substring(0, space).trim();
      IrProgramModel.SlotKind kind = parseSlotKind(kindStr);

      String rest = line.substring(space + 1).trim();
      int at = rest.indexOf('@');
      int colon = rest.indexOf(':');
      if (at < 0 || colon < 0 || colon < at) {
        throw new CodeGenerationException("Invalid slot line: " + line);
      }
      String name = rest.substring(0, at).trim();
      String offsetStr = rest.substring(at + 1, colon).trim();
      String typeStr = rest.substring(colon + 1).trim();
      int offset = parseInt(offsetStr, "slot offset");
      IrType type = TypeParser.parse(typeStr);
      slots.add(new IrProgramModel.Slot(kind, name, offset, type));
    }
    return slots;
  }

  private List<IrProgramModel.Block> parseBlocks(LineCursor cursor) {
    List<IrProgramModel.Block> blocks = new ArrayList<>();
    while (true) {
      String peek = cursor.peekNonEmptyLine();
      if (peek == null || peek.trim().equals(".endfunc")) {
        break;
      }
      String labelLine = cursor.nextNonEmptyLine();
      if (labelLine == null) {
        break;
      }
      String trimmed = labelLine.trim();
      if (!trimmed.endsWith(":")) {
        throw new CodeGenerationException("Expected block label, got: " + labelLine);
      }
      String label = trimmed.substring(0, trimmed.length() - 1).trim();

      List<IrProgramModel.Instruction> instructions = new ArrayList<>();
      IrProgramModel.Terminator terminator = null;

      while (true) {
        String line = cursor.nextNonEmptyLine();
        if (line == null) {
          throw new CodeGenerationException("Unexpected end while parsing block " + label);
        }
        String t = line.trim();
        if (isTerminatorLine(t)) {
          terminator = parseTerminator(t);
          break;
        }
        if (t.endsWith(":")) {
          throw new CodeGenerationException("Missing terminator before block label: " + t);
        }
        instructions.add(parseInstruction(t));
      }

      blocks.add(new IrProgramModel.Block(label, instructions, terminator));
    }
    return blocks;
  }

  private boolean isTerminatorLine(String line) {
    return line.startsWith("br ") || line.startsWith("jmp ") || line.equals("ret") || line.startsWith("ret ");
  }

  private IrProgramModel.Instruction parseInstruction(String line) {
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
    IrProgramModel.Rhs rhs = parseRhs(rhsStr);
    return new IrProgramModel.Assign(dest, rhs);
  }

  private IrProgramModel.Store parseStore(String line) {
    String rest = line.substring("store".length()).trim();
    String[] split = splitByLastTypeSuffix(rest);
    String valuesPart = split[0];
    String typeStr = split[1];

    List<String> parts = splitTopLevel(valuesPart, ',');
    if (parts.size() != 2) {
      throw new CodeGenerationException("Invalid store: " + line);
    }
    IrProgramModel.Value addr = parseValue(parts.get(0));
    IrProgramModel.Value value = parseValue(parts.get(1));
    IrType storeType = TypeParser.parse(typeStr);
    return new IrProgramModel.Store(addr, value, storeType);
  }

  private IrProgramModel.VoidCall parseVoidCall(String line) {
    String rest = line.substring("call".length()).trim();
    CallInfo callInfo = parseCall(rest);
    if (callInfo.returnType != null && !"void".equals(callInfo.returnType)) {
      throw new CodeGenerationException("Void call must have void return type: " + line);
    }
    return new IrProgramModel.VoidCall(callInfo.funcName, callInfo.args);
  }

  private IrProgramModel.Terminator parseTerminator(String line) {
    if (line.startsWith("br ")) {
      String rest = line.substring("br".length()).trim();
      List<String> parts = splitTopLevel(rest, ',');
      if (parts.size() != 3) {
        throw new CodeGenerationException("Invalid br terminator: " + line);
      }
      IrProgramModel.Value cond = parseValue(parts.get(0));
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
      return new IrProgramModel.Ret(parseValue(valueStr));
    }
    throw new CodeGenerationException("Invalid terminator line: " + line);
  }

  private IrProgramModel.Rhs parseRhs(String rhsStr) {
    String trimmed = rhsStr.trim();
    if (trimmed.startsWith("addr_of_symbol")) {
      String symbolStr = trimmed.substring("addr_of_symbol".length()).trim();
      return new IrProgramModel.AddrOfSymbol(parseSymbolRef(symbolStr));
    }
    if (trimmed.startsWith("addr_index")) {
      String rest = trimmed.substring("addr_index".length()).trim();
      List<String> parts = splitTopLevel(rest, ',');
      if (parts.size() != 3) {
        throw new CodeGenerationException("Invalid addr_index: " + rhsStr);
      }
      IrProgramModel.Value base = parseValue(parts.get(0));
      IrProgramModel.Value index = parseValue(parts.get(1));
      int elemSize = parseInt(parts.get(2).trim(), "elemSize");
      return new IrProgramModel.AddrIndex(base, index, elemSize);
    }
    if (trimmed.startsWith("addr_field")) {
      String rest = trimmed.substring("addr_field".length()).trim();
      List<String> parts = splitTopLevel(rest, ',');
      if (parts.size() != 2) {
        throw new CodeGenerationException("Invalid addr_field: " + rhsStr);
      }
      IrProgramModel.Value base = parseValue(parts.get(0));
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
      String[] split = splitByLastTypeSuffix(rest);
      IrProgramModel.Value addr = parseValue(split[0]);
      IrType type = TypeParser.parse(split[1]);
      return new IrProgramModel.Load(addr, type);
    }
    if (trimmed.startsWith("call ")) {
      CallInfo callInfo = parseCall(trimmed.substring("call".length()).trim());
      IrType returnType = "void".equals(callInfo.returnType)
          ? null
          : TypeParser.parse(callInfo.returnType);
      if (returnType == null) {
        throw new CodeGenerationException("Call used as expression cannot return void: " + rhsStr);
      }
      return new IrProgramModel.Call(callInfo.funcName, callInfo.args, returnType);
    }

    if (startsWithAny(trimmed, "cmp_eq", "cmp_ne", "cmp_lt", "cmp_le", "cmp_gt", "cmp_ge")) {
      String opName = trimmed.substring(0, trimmed.indexOf(' ')).trim();
      String rest = trimmed.substring(opName.length()).trim();
      String[] split = splitByLastTypeSuffix(rest);
      List<String> parts = splitTopLevel(split[0], ',');
      if (parts.size() != 2) {
        throw new CodeGenerationException("Invalid cmp: " + rhsStr);
      }
      IrProgramModel.Value left = parseValue(parts.get(0));
      IrProgramModel.Value right = parseValue(parts.get(1));
      return new IrProgramModel.CmpOp(parseCmpOp(opName), left, right);
    }

    if (startsWithAny(trimmed, "add", "sub", "mul", "div", "mod", "and", "or", "xor", "shl", "shr")) {
      String opName = trimmed.substring(0, trimmed.indexOf(' ')).trim();
      String rest = trimmed.substring(opName.length()).trim();
      String[] split = splitByLastTypeSuffix(rest);
      List<String> parts = splitTopLevel(split[0], ',');
      if (parts.size() != 2) {
        throw new CodeGenerationException("Invalid binop: " + rhsStr);
      }
      IrProgramModel.Value left = parseValue(parts.get(0));
      IrProgramModel.Value right = parseValue(parts.get(1));
      IrType type = TypeParser.parse(split[1]);
      return new IrProgramModel.BinOp(parseBinOp(opName), left, right, type);
    }

    if (startsWithAny(trimmed, "neg", "not")) {
      String opName = trimmed.substring(0, trimmed.indexOf(' ')).trim();
      String rest = trimmed.substring(opName.length()).trim();
      String[] split = splitByLastTypeSuffix(rest);
      IrProgramModel.Value operand = parseValue(split[0]);
      IrType type = TypeParser.parse(split[1]);
      return new IrProgramModel.UnaryOp(parseUnaryOp(opName), operand, type);
    }

    if (startsWithAny(trimmed, "trunc", "sext", "zext", "ptrcast", "itof", "ftoi")) {
      String opName = trimmed.substring(0, trimmed.indexOf(' ')).trim();
      String rest = trimmed.substring(opName.length()).trim();
      String[] split = splitByLastTypeSuffix(rest);
      IrProgramModel.Value operand = parseValue(split[0]);
      IrType type = TypeParser.parse(split[1]);
      return new IrProgramModel.CastOp(parseCastOp(opName), operand, type);
    }

    IrConst constant = ConstParser.parse(trimmed);
    return new IrProgramModel.ConstRhs(constant);
  }

  private IrProgramModel.Value parseValue(String valueStr) {
    String trimmed = valueStr.trim();
    if (trimmed.startsWith("t")) {
      return new IrProgramModel.Temp(parseTempIndex(trimmed));
    }
    IrConst constant = ConstParser.parse(trimmed);
    return new IrProgramModel.Const(constant);
  }

  private IrProgramModel.SymbolRef parseSymbolRef(String symbolStr) {
    String trimmed = symbolStr.trim();
    int colon = trimmed.indexOf(':');
    if (colon < 0) {
      throw new CodeGenerationException("Invalid symbol ref: " + symbolStr);
    }
    String kindStr = trimmed.substring(0, colon).trim();
    String name = trimmed.substring(colon + 1).trim();
    return new IrProgramModel.SymbolRef(parseSymbolKind(kindStr), name);
  }

  private CallInfo parseCall(String callStr) {
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
      List<String> parts = splitTopLevel(argsStr, ',');
      for (String part : parts) {
        args.add(parseValue(part));
      }
    }

    return new CallInfo(funcName, returnType, args);
  }

  private int parseTempIndex(String tempStr) {
    String trimmed = tempStr.trim();
    if (!trimmed.startsWith("t")) {
      throw new CodeGenerationException("Invalid temp: " + tempStr);
    }
    String digits = trimmed.substring(1).trim();
    if (digits.isEmpty()) {
      throw new CodeGenerationException("Invalid temp: " + tempStr);
    }
    return parseInt(digits, "temp index");
  }

  private IrProgramModel.SlotKind parseSlotKind(String kindStr) {
    return switch (kindStr) {
      case "param" -> IrProgramModel.SlotKind.PARAM;
      case "local" -> IrProgramModel.SlotKind.LOCAL;
      case "spill" -> IrProgramModel.SlotKind.SPILL;
      default -> throw new CodeGenerationException("Unknown slot kind: " + kindStr);
    };
  }

  private IrProgramModel.SymbolRefKind parseSymbolKind(String kindStr) {
    return switch (kindStr) {
      case "local" -> IrProgramModel.SymbolRefKind.LOCAL;
      case "param" -> IrProgramModel.SymbolRefKind.PARAM;
      case "global" -> IrProgramModel.SymbolRefKind.GLOBAL;
      default -> throw new CodeGenerationException("Unknown symbol ref kind: " + kindStr);
    };
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

  private static boolean startsWithAny(String text, String... prefixes) {
    for (String prefix : prefixes) {
      if (text.startsWith(prefix + " ") || text.equals(prefix)) {
        return true;
      }
    }
    return false;
  }

  private static String[] splitByLastTypeSuffix(String text) {
    int index = lastIndexOfTypeSuffix(text);
    if (index < 0) {
      throw new CodeGenerationException("Missing type suffix in: " + text);
    }
    String expr = text.substring(0, index).trim();
    String type = text.substring(index + 3).trim();
    return new String[] { expr, type };
  }

  private static int lastIndexOfTypeSuffix(String text) {
    for (int i = text.length() - 3; i >= 0; i--) {
      if (text.charAt(i) == ' ' && text.charAt(i + 1) == ':' && text.charAt(i + 2) == ' ') {
        return i;
      }
    }
    return -1;
  }

  private static List<String> splitTopLevel(String text, char delimiter) {
    List<String> parts = new ArrayList<>();
    int parenDepth = 0;
    int angleDepth = 0;
    int braceDepth = 0;
    boolean inChar = false;
    boolean escape = false;
    int start = 0;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inChar) {
        if (escape) {
          escape = false;
        } else if (c == '\\') {
          escape = true;
        } else if (c == '\'') {
          inChar = false;
        }
        continue;
      }
      if (c == '\'') {
        inChar = true;
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth--;
        case '<' -> angleDepth++;
        case '>' -> angleDepth--;
        case '{' -> braceDepth++;
        case '}' -> braceDepth--;
        default -> {
        }
      }
      if (c == delimiter && parenDepth == 0 && angleDepth == 0 && braceDepth == 0) {
        parts.add(text.substring(start, i).trim());
        start = i + 1;
      }
    }
    parts.add(text.substring(start).trim());
    return parts;
  }

  private static int indexOfTopLevel(String text, char target) {
    int parenDepth = 0;
    int angleDepth = 0;
    int braceDepth = 0;
    boolean inChar = false;
    boolean escape = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inChar) {
        if (escape) {
          escape = false;
        } else if (c == '\\') {
          escape = true;
        } else if (c == '\'') {
          inChar = false;
        }
        continue;
      }
      if (c == '\'') {
        inChar = true;
        continue;
      }
      switch (c) {
        case '(' -> parenDepth++;
        case ')' -> parenDepth--;
        case '<' -> angleDepth++;
        case '>' -> angleDepth--;
        case '{' -> braceDepth++;
        case '}' -> braceDepth--;
        default -> {
        }
      }
      if (c == target && parenDepth == 0 && angleDepth == 0 && braceDepth == 0) {
        return i;
      }
    }
    return -1;
  }

  private static int parseInt(String value, String context) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new CodeGenerationException("Invalid integer for " + context + ": " + value);
    }
  }

  private record FrameInfo(int localsBytes, int alignBytes) {
  }

  private record CallInfo(String funcName, String returnType, List<IrProgramModel.Value> args) {
  }

  private static final class LineCursor {
    private final List<String> lines;
    private int index;

    private LineCursor(String text) {
      this.lines = readLines(text);
      this.index = 0;
    }

    private String peekNonEmptyLine() {
      int i = index;
      while (i < lines.size()) {
        String line = lines.get(i).trim();
        if (!line.isEmpty()) {
          return line;
        }
        i++;
      }
      return null;
    }

    private String nextNonEmptyLine() {
      while (index < lines.size()) {
        String line = lines.get(index++).trim();
        if (!line.isEmpty()) {
          return line;
        }
      }
      return null;
    }

    private static List<String> readLines(String text) {
      List<String> list = new ArrayList<>();
      try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
        String line;
        while ((line = reader.readLine()) != null) {
          list.add(line);
        }
      } catch (IOException e) {
        throw new CodeGenerationException("Failed to read IR text", e);
      }
      return list;
    }
  }

  private static final class TypeParser {
    private final String text;
    private int index;

    private TypeParser(String text) {
      this.text = text;
      this.index = 0;
    }

    public static IrType parse(String text) {
      TypeParser parser = new TypeParser(text);
      IrType type = parser.parseType();
      parser.skipWhitespace();
      if (!parser.isAtEnd()) {
        throw new CodeGenerationException("Unexpected trailing type text: " + text);
      }
      return type;
    }

    private IrType parseType() {
      skipWhitespace();
      if (consumeWord("void")) {
        return null;
      }
      if (consumeWord("int32")) {
        return IrPrimitiveType.INT32;
      }
      if (consumeWord("char")) {
        return IrPrimitiveType.CHAR;
      }
      if (consumeWord("uchar")) {
        return IrPrimitiveType.UCHAR;
      }
      if (consumeWord("float")) {
        return IrPrimitiveType.FLOAT;
      }
      if (consumeWord("bool")) {
        return IrPrimitiveType.BOOL;
      }
      if (consumeWord("ptr")) {
        expect('<');
        IrType inner = parseType();
        expect('>');
        return new IrPointerType(inner);
      }
      if (consumeWord("array")) {
        expect('<');
        IrType inner = parseType();
        skipWhitespace();
        expect(',');
        skipWhitespace();
        int size = parseIntToken();
        skipWhitespace();
        expect('>');
        return new IrArrayType(inner, size);
      }
      if (consumeWord("struct")) {
        skipWhitespace();
        String name = parseIdentifier();
        return new IrStructType(name);
      }
      throw new CodeGenerationException("Unknown type: " + text);
    }

    private boolean consumeWord(String word) {
      skipWhitespace();
      if (text.regionMatches(index, word, 0, word.length())) {
        int end = index + word.length();
        if (end == text.length() || !Character.isLetterOrDigit(text.charAt(end))) {
          index = end;
          return true;
        }
      }
      return false;
    }

    private void expect(char c) {
      skipWhitespace();
      if (isAtEnd() || text.charAt(index) != c) {
        throw new CodeGenerationException("Expected '" + c + "' in type: " + text);
      }
      index++;
    }

    private String parseIdentifier() {
      skipWhitespace();
      int start = index;
      while (!isAtEnd()) {
        char c = text.charAt(index);
        if (Character.isLetterOrDigit(c) || c == '_' || c == '$') {
          index++;
        } else {
          break;
        }
      }
      if (start == index) {
        throw new CodeGenerationException("Expected identifier in type: " + text);
      }
      return text.substring(start, index);
    }

    private int parseIntToken() {
      skipWhitespace();
      int start = index;
      if (!isAtEnd() && text.charAt(index) == '-') {
        index++;
      }
      while (!isAtEnd() && Character.isDigit(text.charAt(index))) {
        index++;
      }
      if (start == index) {
        throw new CodeGenerationException("Expected integer in type: " + text);
      }
      return parseInt(text.substring(start, index), "type integer");
    }

    private void skipWhitespace() {
      while (!isAtEnd() && Character.isWhitespace(text.charAt(index))) {
        index++;
      }
    }

    private boolean isAtEnd() {
      return index >= text.length();
    }
  }

  private static final class ConstParser {
    private ConstParser() {
    }

    public static IrConst parse(String text) {
      String trimmed = text.trim();
      if (trimmed.startsWith("null:")) {
        String typeStr = trimmed.substring("null:".length()).trim();
        IrType type = TypeParser.parse(typeStr);
        if (!(type instanceof IrPointerType)) {
          throw new CodeGenerationException("Null constant must have pointer type: " + text);
        }
        return new IrConst.NullConst(type);
      }
      if (trimmed.startsWith("#'")) {
        return parseCharConst(trimmed);
      }
      if (trimmed.startsWith("#")) {
        return parseNumericConst(trimmed);
      }
      if (trimmed.startsWith("{")) {
        return parseArrayConst(trimmed);
      }
      throw new CodeGenerationException("Unknown constant: " + text);
    }

    private static IrConst parseNumericConst(String text) {
      int colon = text.lastIndexOf(':');
      if (colon < 0) {
        throw new CodeGenerationException("Invalid numeric constant: " + text);
      }
      String valueStr = text.substring(1, colon).trim();
      String typeStr = text.substring(colon + 1).trim();
      IrType type = TypeParser.parse(typeStr);

      if (type == IrPrimitiveType.FLOAT) {
        try {
          float value = Float.parseFloat(valueStr);
          return new IrConst.FloatConst(value);
        } catch (NumberFormatException e) {
          throw new CodeGenerationException("Invalid float constant: " + text);
        }
      }

      int value = parseInt(valueStr, "numeric constant");
      return new IrConst.IntConst(value, type);
    }

    private static IrConst parseCharConst(String text) {
      int endQuote = findCharLiteralEnd(text);
      if (endQuote < 0) {
        throw new CodeGenerationException("Invalid char constant: " + text);
      }
      String literal = text.substring(2, endQuote);
      char value = parseCharLiteral(literal);
      return new IrConst.CharConst(value);
    }

    private static int findCharLiteralEnd(String text) {
      boolean escape = false;
      for (int i = 2; i < text.length(); i++) {
        char c = text.charAt(i);
        if (escape) {
          escape = false;
          continue;
        }
        if (c == '\\') {
          escape = true;
          continue;
        }
        if (c == '\'') {
          return i;
        }
      }
      return -1;
    }

    private static char parseCharLiteral(String literal) {
      if (literal.isEmpty()) {
        throw new CodeGenerationException("Empty char literal");
      }
      if (literal.charAt(0) != '\\') {
        return literal.charAt(0);
      }
      if (literal.length() < 2) {
        throw new CodeGenerationException("Invalid escape in char literal");
      }
      return switch (literal.charAt(1)) {
        case 'n' -> '\n';
        case 't' -> '\t';
        case '\\' -> '\\';
        case '\'' -> '\'';
        default -> throw new CodeGenerationException("Unsupported escape in char literal: \\" + literal);
      };
    }

    private static IrConst parseArrayConst(String text) {
      int braceEnd = findMatchingBrace(text);
      if (braceEnd < 0) {
        throw new CodeGenerationException("Invalid array constant: " + text);
      }
      String inside = text.substring(1, braceEnd).trim();
      String after = text.substring(braceEnd + 1).trim();
      int colonIndex = after.indexOf(':');
      if (colonIndex < 0) {
        throw new CodeGenerationException("Array constant missing type: " + text);
      }
      String typeStr = after.substring(colonIndex + 1).trim();
      IrType type = TypeParser.parse(typeStr);
      if (!(type instanceof IrArrayType arrayType)) {
        throw new CodeGenerationException("Array constant must have array type: " + text);
      }

      List<IrConst> elements = new ArrayList<>();
      if (!inside.isEmpty()) {
        List<String> parts = splitTopLevel(inside, ',');
        for (String part : parts) {
          elements.add(parse(part));
        }
      }

      return new IrConst.ArrayConst(elements, arrayType);
    }

    private static int findMatchingBrace(String text) {
      int depth = 0;
      boolean inChar = false;
      boolean escape = false;
      for (int i = 0; i < text.length(); i++) {
        char c = text.charAt(i);
        if (inChar) {
          if (escape) {
            escape = false;
          } else if (c == '\\') {
            escape = true;
          } else if (c == '\'') {
            inChar = false;
          }
          continue;
        }
        if (c == '\'') {
          inChar = true;
          continue;
        }
        if (c == '{') {
          depth++;
        } else if (c == '}') {
          depth--;
          if (depth == 0) {
            return i;
          }
        }
      }
      return -1;
    }
  }
}
