package hr.fer.ppj.cli.ir;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Byte-addressable interpreter for typed IR programs.
 */
public final class IrInterpreter {

  private final IrProgramModel program;
  private final Map<String, IrProgramModel.Function> functions = new HashMap<>();
  private final Map<String, IrType> globalTypes = new HashMap<>();
  private final Map<String, Integer> globalAddresses = new HashMap<>();
  private final Map<String, StructLayout> structLayouts = new HashMap<>();
  private final Memory memory = new Memory();
  private final int stepLimit;
  private final StringBuilder trace;

  private int steps;

  public IrInterpreter(IrProgramModel program, IrInterpreterOptions options) {
    this.program = Objects.requireNonNull(program, "program must not be null");
    Objects.requireNonNull(options, "options must not be null");

    this.stepLimit = options.stepLimit();
    this.trace = options.trace() ? new StringBuilder(4096) : null;

    for (IrProgramModel.Function function : program.functions()) {
      if (functions.put(function.name(), function) != null) {
        throw new IllegalStateException("Duplicate function: " + function.name());
      }
    }

    registerStructLayouts();
    initGlobals();
  }

  public IrExecutionResult executeMain() {
    try {
      int returnValue = executeFunction("main", List.of());
      String traceText = trace == null ? "" : trace.toString();
      return new IrExecutionResult(returnValue, steps, traceText);
    } catch (TrapSignal trapSignal) {
      String traceText = trace == null ? "" : trace.toString();
      return new IrExecutionResult(trapSignal.code(), steps, traceText);
    }
  }

  private void registerStructLayouts() {
    for (IrProgramModel.StructDef def : program.structDefs()) {
      StructLayout layout = new StructLayout(def.name());
      for (IrProgramModel.StructField field : def.fields()) {
        layout.fields.put(field.name(), new StructFieldInfo(field.offset(), field.type()));
      }
      structLayouts.put(def.name(), layout);
    }
  }

  private void initGlobals() {
    for (IrProgramModel.GlobalVar global : program.globals()) {
      globalTypes.put(global.name(), global.type());
      int size = sizeOf(global.type());
      int addr = memory.alloc(size, alignmentOf(global.type()));
      globalAddresses.put(global.name(), addr);
      if (global.initializer() == null) {
        memory.clear(addr, size);
      } else {
        storeConst(addr, global.initializer(), global.type());
      }
    }
  }

  private int executeFunction(String functionName, List<Integer> args) {
    IrProgramModel.Function function = functions.get(functionName);
    if (function == null) {
      throw new IllegalStateException("Unknown function: " + functionName);
    }

    Frame frame = new Frame();
    allocateSlots(function, frame);
    bindParameters(function, args, frame);
    Set<Integer> addrIndexNeedsCheck = analyzeAddrIndexChecks(function);

    Map<String, IrProgramModel.Block> blocks = new HashMap<>();
    for (IrProgramModel.Block block : function.blocks()) {
      blocks.put(block.label(), block);
    }

    IrProgramModel.Block current = function.blocks().isEmpty() ? null : function.blocks().getFirst();
    while (current != null) {
      List<IrProgramModel.Instruction> instructions = current.instructions();
      for (int i = 0; i < instructions.size(); i++) {
        IrProgramModel.Instruction instruction = instructions.get(i);
        tick(functionName, current.label(), "instr[" + i + "]");
        executeInstruction(functionName, current.label(), instruction, frame, addrIndexNeedsCheck);
      }

      IrProgramModel.Terminator terminator = current.terminator();
      tick(functionName, current.label(), "terminator");
      if (terminator instanceof IrProgramModel.Ret ret) {
        if (ret.value() == null) {
          return 0;
        }
        return evaluateValue(ret.value(), frame);
      }
      if (terminator instanceof IrProgramModel.Jmp jmp) {
        current = requireBlock(blocks, jmp.targetLabel());
        continue;
      }
      if (terminator instanceof IrProgramModel.Br br) {
        int cond = evaluateValue(br.condition(), frame);
        current = requireBlock(blocks, cond != 0 ? br.trueLabel() : br.falseLabel());
        continue;
      }
      throw new IllegalStateException("Unknown terminator: " + terminator);
    }

    return 0;
  }

  private IrProgramModel.Block requireBlock(Map<String, IrProgramModel.Block> blocks, String label) {
    IrProgramModel.Block block = blocks.get(label);
    if (block == null) {
      throw new IllegalStateException("Unknown block label: " + label);
    }
    return block;
  }

  private void allocateSlots(IrProgramModel.Function function, Frame frame) {
    for (IrProgramModel.Slot slot : function.slots()) {
      int size = sizeOf(slot.type());
      int addr = memory.alloc(size, alignmentOf(slot.type()));
      switch (slot.kind()) {
        case PARAM -> {
          frame.paramAddresses.put(slot.name(), addr);
          frame.paramTypes.put(slot.name(), slot.type());
        }
        case LOCAL, SPILL -> {
          frame.localAddresses.put(slot.name(), addr);
          frame.localTypes.put(slot.name(), slot.type());
        }
      }
      memory.clear(addr, size);
    }
  }

  private void bindParameters(IrProgramModel.Function function, List<Integer> args, Frame frame) {
    List<IrProgramModel.Parameter> params = function.parameters();
    if (params.size() != args.size()) {
      throw new IllegalStateException(
          "Argument count mismatch for " + function.name() + ": expected "
              + params.size() + ", got " + args.size());
    }

    for (int i = 0; i < params.size(); i++) {
      IrProgramModel.Parameter param = params.get(i);
      Integer addr = frame.paramAddresses.get(param.name());
      if (addr == null) {
        throw new IllegalStateException("Missing parameter slot: " + param.name());
      }
      IrType paramType = param.type();
      if (isAggregate(paramType)) {
        int sourceAddress = args.get(i);
        memory.copy(sourceAddress, addr, sizeOf(paramType));
      } else {
        storeValue(addr, args.get(i), paramType);
      }
    }
  }

  private void executeInstruction(
      String functionName,
      String blockLabel,
      IrProgramModel.Instruction instruction,
      Frame frame,
      Set<Integer> addrIndexNeedsCheck) {

    if (instruction instanceof IrProgramModel.Assign assign) {
      int value;
      if (assign.rhs() instanceof IrProgramModel.AddrIndex addrIndex) {
        boolean needsCheck = addrIndexNeedsCheck.contains(assign.dest().index());
        value = evaluateAddrIndex(addrIndex, frame, needsCheck);
      } else {
        value = evaluateRhs(assign.rhs(), frame);
      }
      frame.temps.put(assign.dest().index(), value);
      frame.tempTypes.put(assign.dest().index(), rhsType(assign.rhs(), frame));
      trace(functionName, blockLabel, "t" + assign.dest().index() + " = " + value);
      return;
    }

    if (instruction instanceof IrProgramModel.Store store) {
      int addr = evaluateValue(store.address(), frame);
      if (isAggregate(store.storeType())) {
        int sourceAddress = evaluateValue(store.value(), frame);
        int copySize = sizeOf(store.storeType());
        memory.copy(sourceAddress, addr, copySize);
        trace(functionName, blockLabel, "memcpy[" + addr + "] <- " + sourceAddress + " (" + copySize + ")");
      } else {
        int value = evaluateValue(store.value(), frame);
        storeValue(addr, value, store.storeType());
        trace(functionName, blockLabel, "store[" + addr + "] = " + value);
      }
      return;
    }

    if (instruction instanceof IrProgramModel.VoidCall call) {
      List<Integer> args = new ArrayList<>();
      for (IrProgramModel.Value arg : call.args()) {
        args.add(evaluateValue(arg, frame));
      }
      executeFunction(call.funcName(), args);
      return;
    }

    throw new IllegalStateException("Unknown instruction: " + instruction);
  }

  private int evaluateRhs(IrProgramModel.Rhs rhs, Frame frame) {
    if (rhs instanceof IrProgramModel.AddrOfSymbol addr) {
      return resolveSymbol(addr.symbolRef(), frame);
    }
    if (rhs instanceof IrProgramModel.AddrIndex addrIndex) {
      return evaluateAddrIndex(addrIndex, frame, false);
    }
    if (rhs instanceof IrProgramModel.AddrField addrField) {
      int base = evaluateValue(addrField.base(), frame);
      int offset = fieldInfo(addrField.structName(), addrField.fieldName()).offset();
      return base + offset;
    }
    if (rhs instanceof IrProgramModel.Load load) {
      int addr = evaluateValue(load.address(), frame);
      if (isAggregate(load.loadType())) {
        return addr;
      }
      return loadValue(addr, load.loadType());
    }
    if (rhs instanceof IrProgramModel.BinOp binOp) {
      int left = evaluateValue(binOp.left(), frame);
      int right = evaluateValue(binOp.right(), frame);
      boolean isFloat = binOp.resultType() == IrPrimitiveType.FLOAT;
      return evalBinOp(binOp.op(), left, right, isFloat);
    }
    if (rhs instanceof IrProgramModel.CmpOp cmpOp) {
      int left = evaluateValue(cmpOp.left(), frame);
      int right = evaluateValue(cmpOp.right(), frame);
      return evalCmpOp(cmpOp.op(), left, right);
    }
    if (rhs instanceof IrProgramModel.Call call) {
      List<Integer> args = new ArrayList<>();
      for (IrProgramModel.Value arg : call.args()) {
        args.add(evaluateValue(arg, frame));
      }
      return executeFunction(call.funcName(), args);
    }
    if (rhs instanceof IrProgramModel.UnaryOp unaryOp) {
      int value = evaluateValue(unaryOp.operand(), frame);
      return switch (unaryOp.op()) {
        case NEG -> -value;
        case NOT -> value == 0 ? 1 : 0;
      };
    }
    if (rhs instanceof IrProgramModel.CastOp castOp) {
      int value = evaluateValue(castOp.operand(), frame);
      return evalCast(castOp.op(), value);
    }
    if (rhs instanceof IrProgramModel.ConstRhs constRhs) {
      return constToValue(constRhs.constant());
    }
    throw new IllegalStateException("Unknown rhs: " + rhs);
  }

  private int evaluateAddrIndex(IrProgramModel.AddrIndex addrIndex, Frame frame, boolean needsBoundsCheck) {
    int base = evaluateValue(addrIndex.base(), frame);
    int index = evaluateValue(addrIndex.index(), frame);
    if (needsBoundsCheck) {
      IrType baseType = valueType(addrIndex.base(), frame);
      if (baseType instanceof IrPointerType pointerType
          && pointerType.baseType() instanceof IrArrayType arrayType
          && (index < 0 || index >= arrayType.size())) {
        throw new TrapSignal(-6, "Array index out of bounds");
      }
    }
    return base + index * addrIndex.elemSize();
  }

  private int evaluateValue(IrProgramModel.Value value, Frame frame) {
    if (value instanceof IrProgramModel.Temp temp) {
      Integer stored = frame.temps.get(temp.index());
      if (stored == null) {
        throw new IllegalStateException("Undefined temp: t" + temp.index());
      }
      return stored;
    }
    if (value instanceof IrProgramModel.Const constant) {
      return constToValue(constant.constant());
    }
    throw new IllegalStateException("Unknown value: " + value);
  }

  private Set<Integer> analyzeAddrIndexChecks(IrProgramModel.Function function) {
    Set<Integer> addressUsed = new HashSet<>();
    for (IrProgramModel.Block block : function.blocks()) {
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        if (instruction instanceof IrProgramModel.Store store) {
          addTempIfValue(store.address(), addressUsed);
        } else if (instruction instanceof IrProgramModel.Assign assign
            && assign.rhs() instanceof IrProgramModel.Load load) {
          addTempIfValue(load.address(), addressUsed);
        }
      }
    }

    boolean changed;
    do {
      changed = false;
      for (IrProgramModel.Block block : function.blocks()) {
        for (IrProgramModel.Instruction instruction : block.instructions()) {
          if (!(instruction instanceof IrProgramModel.Assign assign)) {
            continue;
          }
          int dest = assign.dest().index();
          if (!addressUsed.contains(dest)) {
            continue;
          }
          IrProgramModel.Rhs rhs = assign.rhs();
          if (rhs instanceof IrProgramModel.AddrField addrField) {
            changed |= addTempIfValue(addrField.base(), addressUsed);
          } else if (rhs instanceof IrProgramModel.AddrIndex addrIndex) {
            changed |= addTempIfValue(addrIndex.base(), addressUsed);
          }
        }
      }
    } while (changed);

    Set<Integer> checks = new HashSet<>();
    for (IrProgramModel.Block block : function.blocks()) {
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        if (instruction instanceof IrProgramModel.Assign assign
            && assign.rhs() instanceof IrProgramModel.AddrIndex
            && addressUsed.contains(assign.dest().index())) {
          checks.add(assign.dest().index());
        }
      }
    }
    return checks;
  }

  private static boolean addTempIfValue(IrProgramModel.Value value, Set<Integer> tempSet) {
    if (value instanceof IrProgramModel.Temp temp) {
      return tempSet.add(temp.index());
    }
    return false;
  }

  private int resolveSymbol(IrProgramModel.SymbolRef symbolRef, Frame frame) {
    return switch (symbolRef.kind()) {
      case LOCAL -> requireAddress(frame.localAddresses, symbolRef.name());
      case PARAM -> requireAddress(frame.paramAddresses, symbolRef.name());
      case GLOBAL -> requireAddress(globalAddresses, symbolRef.name());
    };
  }

  private int requireAddress(Map<String, Integer> map, String name) {
    Integer addr = map.get(name);
    if (addr == null) {
      throw new IllegalStateException("Unknown symbol: " + name);
    }
    return addr;
  }

  private int evalBinOp(IrProgramModel.BinOpName op, int left, int right, boolean isFloat) {
    if (isFloat) {
      return switch (op) {
        case ADD -> left + right;
        case SUB -> left - right;
        case MUL -> q16Mul(left, right);
        case DIV -> q16Div(left, right);
        default -> throw new IllegalStateException("Unsupported float op: " + op);
      };
    }
    return switch (op) {
      case ADD -> left + right;
      case SUB -> left - right;
      case MUL -> left * right;
      case DIV -> right == 0 ? 0 : left / right;
      case MOD -> right == 0 ? 0 : left % right;
      case AND -> left & right;
      case OR -> left | right;
      case XOR -> left ^ right;
      case SHL -> left << right;
      case SHR -> left >> right;
    };
  }

  private int evalCmpOp(IrProgramModel.CmpOpName op, int left, int right) {
    return switch (op) {
      case EQ -> left == right ? 1 : 0;
      case NE -> left != right ? 1 : 0;
      case LT -> left < right ? 1 : 0;
      case LE -> left <= right ? 1 : 0;
      case GT -> left > right ? 1 : 0;
      case GE -> left >= right ? 1 : 0;
    };
  }

  private int evalCast(IrProgramModel.CastName op, int value) {
    return switch (op) {
      case TRUNC, ZEXT -> value & 0xFF;
      case SEXT -> (value << 24) >> 24;
      case PTRCAST -> value;
      case ITOF -> value << 16;
      case FTOI -> value >> 16;
    };
  }

  private int constToValue(IrConst constant) {
    if (constant instanceof IrConst.IntConst intConst) {
      return intConst.value();
    }
    if (constant instanceof IrConst.CharConst charConst) {
      return charConst.value() & 0xFF;
    }
    if (constant instanceof IrConst.FloatConst floatConst) {
      return floatToQ16(floatConst.value());
    }
    if (constant instanceof IrConst.NullConst) {
      return 0;
    }
    if (constant instanceof IrConst.ArrayConst) {
      throw new IllegalStateException("Array constant cannot be used as scalar value.");
    }
    throw new IllegalStateException("Unsupported constant: " + constant);
  }

  private void storeConst(int addr, IrConst constant, IrType type) {
    if (constant instanceof IrConst.ArrayConst arrayConst) {
      IrType elementType = arrayConst.arrayType().elementType();
      int elementSize = sizeOf(elementType);
      for (int i = 0; i < arrayConst.elements().size(); i++) {
        IrConst element = arrayConst.elements().get(i);
        storeConst(addr + i * elementSize, element, elementType);
      }
      return;
    }
    storeValue(addr, constToValue(constant), type);
  }

  private void storeValue(int addr, int value, IrType type) {
    if (type == IrPrimitiveType.CHAR || type == IrPrimitiveType.UCHAR) {
      memory.storeByte(addr, value);
      return;
    }
    memory.storeWord(addr, value);
  }

  private int loadValue(int addr, IrType type) {
    if (type == IrPrimitiveType.CHAR || type == IrPrimitiveType.UCHAR) {
      return memory.loadByte(addr);
    }
    return memory.loadWord(addr);
  }

  private int sizeOf(IrType type) {
    if (type == null) {
      return 0;
    }
    if (type instanceof IrPrimitiveType prim) {
      return switch (prim) {
        case INT32, FLOAT, BOOL -> 4;
        case CHAR, UCHAR -> 1;
      };
    }
    if (type instanceof IrPointerType) {
      return 4;
    }
    if (type instanceof IrArrayType arrayType) {
      return arrayType.size() * sizeOf(arrayType.elementType());
    }
    if (type instanceof IrStructType structType) {
      return structSize(structType.name());
    }
    return 4;
  }

  private int alignmentOf(IrType type) {
    if (type == null) {
      return 1;
    }
    if (type instanceof IrPrimitiveType prim) {
      return switch (prim) {
        case INT32, FLOAT, BOOL -> 4;
        case CHAR, UCHAR -> 1;
      };
    }
    if (type instanceof IrPointerType) {
      return 4;
    }
    if (type instanceof IrArrayType arrayType) {
      return alignmentOf(arrayType.elementType());
    }
    return 4;
  }

  private IrType rhsType(IrProgramModel.Rhs rhs, Frame frame) {
    if (rhs instanceof IrProgramModel.ConstRhs constRhs) {
      return constRhs.constant().type();
    }
    if (rhs instanceof IrProgramModel.AddrOfSymbol addr) {
      return new IrPointerType(symbolType(addr.symbolRef(), frame));
    }
    if (rhs instanceof IrProgramModel.AddrIndex addrIndex) {
      IrType baseType = valueType(addrIndex.base(), frame);
      if (baseType instanceof IrPointerType pointerType) {
        if (pointerType.baseType() instanceof IrArrayType arrayType) {
          return new IrPointerType(arrayType.elementType());
        }
        return pointerType;
      }
      return new IrPointerType(IrPrimitiveType.INT32);
    }
    if (rhs instanceof IrProgramModel.AddrField addrField) {
      StructFieldInfo fieldInfo = fieldInfo(addrField.structName(), addrField.fieldName());
      return new IrPointerType(fieldInfo.type());
    }
    if (rhs instanceof IrProgramModel.Load load) {
      return load.loadType();
    }
    if (rhs instanceof IrProgramModel.BinOp binOp) {
      return binOp.resultType();
    }
    if (rhs instanceof IrProgramModel.CmpOp) {
      return IrPrimitiveType.BOOL;
    }
    if (rhs instanceof IrProgramModel.Call call) {
      return call.resultType();
    }
    if (rhs instanceof IrProgramModel.UnaryOp unaryOp) {
      return unaryOp.resultType();
    }
    if (rhs instanceof IrProgramModel.CastOp castOp) {
      return castOp.resultType();
    }
    return IrPrimitiveType.INT32;
  }

  private IrType valueType(IrProgramModel.Value value, Frame frame) {
    if (value instanceof IrProgramModel.Temp temp) {
      return frame.tempTypes.get(temp.index());
    }
    if (value instanceof IrProgramModel.Const constant) {
      return constant.constant().type();
    }
    return null;
  }

  private IrType symbolType(IrProgramModel.SymbolRef symbolRef, Frame frame) {
    return switch (symbolRef.kind()) {
      case LOCAL -> frame.localTypes.getOrDefault(symbolRef.name(), IrPrimitiveType.INT32);
      case PARAM -> frame.paramTypes.getOrDefault(symbolRef.name(), IrPrimitiveType.INT32);
      case GLOBAL -> globalTypes.getOrDefault(symbolRef.name(), IrPrimitiveType.INT32);
    };
  }

  private StructFieldInfo fieldInfo(String structName, String fieldName) {
    StructLayout layout = structLayouts.computeIfAbsent(structName, StructLayout::new);
    StructFieldInfo field = layout.fields.get(fieldName);
    if (field != null) {
      return field;
    }

    int nextOffset = 0;
    for (StructFieldInfo existing : layout.fields.values()) {
      int align = alignmentOf(existing.type());
      nextOffset = alignTo(nextOffset, align);
      nextOffset += sizeOf(existing.type());
    }

    StructFieldInfo inferred = new StructFieldInfo(nextOffset, IrPrimitiveType.INT32);
    layout.fields.put(fieldName, inferred);
    return inferred;
  }

  private int structSize(String structName) {
    StructLayout layout = structLayouts.get(structName);
    if (layout == null) {
      return 4;
    }

    int offset = 0;
    int maxAlign = 1;
    for (StructFieldInfo field : layout.fields.values()) {
      int align = alignmentOf(field.type());
      maxAlign = Math.max(maxAlign, align);
      offset = alignTo(offset, align);
      offset += sizeOf(field.type());
    }
    return alignTo(offset, maxAlign);
  }

  private static boolean isAggregate(IrType type) {
    return type instanceof IrArrayType || type instanceof IrStructType;
  }

  private static int alignTo(int value, int alignment) {
    if (alignment <= 1) {
      return value;
    }
    int mod = value % alignment;
    if (mod == 0) {
      return value;
    }
    return value + (alignment - mod);
  }

  private void tick(String functionName, String blockLabel, String event) {
    steps++;
    if (steps > stepLimit) {
      throw new IllegalStateException(
          "IR interpreter step limit exceeded at " + functionName + ":" + blockLabel
              + " (limit=" + stepLimit + ")");
    }
    trace(functionName, blockLabel, event);
  }

  private void trace(String functionName, String blockLabel, String message) {
    if (trace == null) {
      return;
    }
    trace.append('[')
        .append(steps)
        .append("] ")
        .append(functionName)
        .append(':')
        .append(blockLabel)
        .append(" -> ")
        .append(message)
        .append('\n');
  }

  private static int floatToQ16(float value) {
    return Math.round(value * 65536.0f);
  }

  private static int q16Mul(int left, int right) {
    long product = (long) left * (long) right;
    return (int) (product >> 16);
  }

  private static int q16Div(int left, int right) {
    if (right == 0) {
      return 0;
    }
    long numerator = ((long) left) << 16;
    return (int) (numerator / right);
  }

  private static final class TrapSignal extends RuntimeException {
    private final int code;

    private TrapSignal(int code, String message) {
      super(message);
      this.code = code;
    }

    private int code() {
      return code;
    }
  }

  private static final class StructLayout {
    private final Map<String, StructFieldInfo> fields = new LinkedHashMap<>();

    private StructLayout(String ignoredName) {
    }
  }

  private record StructFieldInfo(int offset, IrType type) {
  }

  private static final class Frame {
    final Map<String, Integer> localAddresses = new HashMap<>();
    final Map<String, Integer> paramAddresses = new HashMap<>();
    final Map<String, IrType> localTypes = new HashMap<>();
    final Map<String, IrType> paramTypes = new HashMap<>();
    final Map<Integer, Integer> temps = new HashMap<>();
    final Map<Integer, IrType> tempTypes = new HashMap<>();
  }

  private static final class Memory {
    private final Map<Integer, Integer> bytes = new HashMap<>();
    private int nextAddress = 0x1000;

    int alloc(int size, int alignment) {
      int aligned = align(nextAddress, alignment);
      nextAddress = aligned + size;
      return aligned;
    }

    void storeByte(int addr, int value) {
      bytes.put(addr, value & 0xFF);
    }

    int loadByte(int addr) {
      return bytes.getOrDefault(addr, 0);
    }

    void storeWord(int addr, int value) {
      storeByte(addr, value);
      storeByte(addr + 1, value >> 8);
      storeByte(addr + 2, value >> 16);
      storeByte(addr + 3, value >> 24);
    }

    int loadWord(int addr) {
      int b0 = loadByte(addr);
      int b1 = loadByte(addr + 1);
      int b2 = loadByte(addr + 2);
      int b3 = loadByte(addr + 3);
      return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    void clear(int addr, int size) {
      for (int i = 0; i < size; i++) {
        bytes.put(addr + i, 0);
      }
    }

    void copy(int sourceAddr, int targetAddr, int size) {
      if (size <= 0 || sourceAddr == targetAddr) {
        return;
      }
      if (targetAddr > sourceAddr && targetAddr < sourceAddr + size) {
        for (int i = size - 1; i >= 0; i--) {
          storeByte(targetAddr + i, loadByte(sourceAddr + i));
        }
        return;
      }
      for (int i = 0; i < size; i++) {
        storeByte(targetAddr + i, loadByte(sourceAddr + i));
      }
    }

    private static int align(int value, int alignment) {
      if (alignment <= 1) {
        return value;
      }
      int mod = value % alignment;
      if (mod == 0) {
        return value;
      }
      return value + (alignment - mod);
    }
  }
}
