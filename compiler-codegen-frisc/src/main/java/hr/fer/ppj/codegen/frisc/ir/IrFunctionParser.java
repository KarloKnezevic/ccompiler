package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.codegen.frisc.CodeGenerationException;
import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses function definitions in the IR text.
 */
final class IrFunctionParser {
  private final IrBlockParser blockParser;

  IrFunctionParser(IrBlockParser blockParser) {
    this.blockParser = blockParser;
  }

  IrProgramModel.Function parseFunction(IrLineCursor cursor) {
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
    IrType returnType = "void".equals(returnTypeStr) ? null : IrTypeParser.parse(returnTypeStr);

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
    List<IrProgramModel.Block> blocks = blockParser.parseBlocks(cursor);

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
    List<String> parts = IrParseUtil.splitTopLevel(paramsStr, ',');
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
      IrType type = IrTypeParser.parse(typeStr);
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
    int localsBytes = IrParseUtil.parseInt(localsStr, "locals bytes");

    String alignStr = trimmed.substring(alignIndex + "align=".length()).trim();
    int alignBytes = IrParseUtil.parseInt(alignStr, "align bytes");
    return new FrameInfo(localsBytes, alignBytes);
  }

  private List<IrProgramModel.Slot> parseSlots(IrLineCursor cursor) {
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
      int offset = IrParseUtil.parseInt(offsetStr, "slot offset");
      IrType type = IrTypeParser.parse(typeStr);
      slots.add(new IrProgramModel.Slot(kind, name, offset, type));
    }
    return slots;
  }

  private IrProgramModel.SlotKind parseSlotKind(String kindStr) {
    return switch (kindStr) {
      case "param" -> IrProgramModel.SlotKind.PARAM;
      case "local" -> IrProgramModel.SlotKind.LOCAL;
      case "spill" -> IrProgramModel.SlotKind.SPILL;
      default -> throw new CodeGenerationException("Unknown slot kind: " + kindStr);
    };
  }

  private record FrameInfo(int localsBytes, int alignBytes) {
  }
}
