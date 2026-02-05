package hr.fer.ppj.cli;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class IrInterpreter {

  private final IrProgramModel program;
  private final Map<String, IrProgramModel.Function> functions = new HashMap<>();
  private final Map<String, Integer> globalAddresses = new HashMap<>();
  private final Map<String, Map<String, Integer>> structOffsets = new HashMap<>();
  private final Map<String, Integer> structSizes = new HashMap<>();
  private final Memory memory = new Memory();

  IrInterpreter(IrProgramModel program) {
    this.program = Objects.requireNonNull(program, "program");
    for (IrProgramModel.Function function : program.functions()) {
      functions.put(function.name(), function);
    }
    buildStructLayouts();
    initGlobals();
  }

  int executeMain() {
    return executeFunction("main", List.of());
  }

  private void buildStructLayouts() {
    for (IrProgramModel.StructDef def : program.structDefs()) {
      Map<String, Integer> offsets = new HashMap<>();
      int size = 0;
      for (IrProgramModel.StructField field : def.fields()) {
        offsets.put(field.name(), field.offset());
        int end = field.offset() + sizeOf(field.type());
        if (end > size) {
          size = end;
        }
      }
      structOffsets.put(def.name(), offsets);
      structSizes.put(def.name(), size);
    }
  }

  private void initGlobals() {
    for (IrProgramModel.GlobalVar global : program.globals()) {
      int size = sizeOf(global.type());
      int addr = memory.alloc(size, alignmentOf(global.type()));
      globalAddresses.put(global.name(), addr);
      if (global.initializer() != null) {
        storeConst(addr, global.initializer(), global.type());
      } else {
        memory.clear(addr, size);
      }
    }
  }

  private int executeFunction(String name, List<Integer> args) {
    IrProgramModel.Function function = functions.get(name);
    if (function == null) {
      throw new IllegalStateException("Unknown function: " + name);
    }

    Frame frame = new Frame();
    allocateSlots(function, frame);
    bindParameters(function, args, frame);

    Map<String, IrProgramModel.Block> blocks = new HashMap<>();
    for (IrProgramModel.Block block : function.blocks()) {
      blocks.put(block.label(), block);
    }

    IrProgramModel.Block current = function.blocks().isEmpty() ? null : function.blocks().getFirst();
    while (current != null) {
      for (IrProgramModel.Instruction instruction : current.instructions()) {
        executeInstruction(instruction, frame);
      }
      IrProgramModel.Terminator term = current.terminator();
      if (term instanceof IrProgramModel.Ret ret) {
        return evaluateValue(ret.value(), frame);
      }
      if (term instanceof IrProgramModel.Jmp jmp) {
        current = blocks.get(jmp.targetLabel());
        continue;
      }
      if (term instanceof IrProgramModel.Br br) {
        int cond = evaluateValue(br.condition(), frame);
        current = blocks.get(cond != 0 ? br.trueLabel() : br.falseLabel());
        continue;
      }
      throw new IllegalStateException("Unknown terminator: " + term);
    }
    return 0;
  }

  private void allocateSlots(IrProgramModel.Function function, Frame frame) {
    for (IrProgramModel.Slot slot : function.slots()) {
      int size = sizeOf(slot.type());
      int addr = memory.alloc(size, alignmentOf(slot.type()));
      switch (slot.kind()) {
        case PARAM -> frame.paramAddresses.put(slot.name(), addr);
        case LOCAL, SPILL -> frame.localAddresses.put(slot.name(), addr);
      }
      memory.clear(addr, size);
    }
  }

  private void bindParameters(IrProgramModel.Function function, List<Integer> args, Frame frame) {
    List<IrProgramModel.Parameter> params = function.parameters();
    if (args.size() != params.size()) {
      throw new IllegalStateException("Argument count mismatch for " + function.name());
    }
    for (int i = 0; i < params.size(); i++) {
      IrProgramModel.Parameter param = params.get(i);
      Integer addr = frame.paramAddresses.get(param.name());
      if (addr == null) {
        throw new IllegalStateException("Missing param slot: " + param.name());
      }
      storeValue(addr, args.get(i), param.type());
    }
  }

  private void executeInstruction(IrProgramModel.Instruction instruction, Frame frame) {
    if (instruction instanceof IrProgramModel.Assign assign) {
      int value = evaluateRhs(assign.rhs(), frame);
      frame.temps.put(assign.dest().index(), value);
      return;
    }
    if (instruction instanceof IrProgramModel.Store store) {
      int addr = evaluateValue(store.address(), frame);
      int value = evaluateValue(store.value(), frame);
      storeValue(addr, value, store.storeType());
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
      int base = evaluateValue(addrIndex.base(), frame);
      int index = evaluateValue(addrIndex.index(), frame);
      return base + index * addrIndex.elemSize();
    }
    if (rhs instanceof IrProgramModel.AddrField addrField) {
      int base = evaluateValue(addrField.base(), frame);
      Map<String, Integer> offsets = structOffsets.get(addrField.structName());
      if (offsets == null) {
        throw new IllegalStateException("Unknown struct: " + addrField.structName());
      }
      Integer offset = offsets.get(addrField.fieldName());
      if (offset == null) {
        throw new IllegalStateException("Unknown field: " + addrField.structName() + "." + addrField.fieldName());
      }
      return base + offset;
    }
    if (rhs instanceof IrProgramModel.Load load) {
      int addr = evaluateValue(load.address(), frame);
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
        int value = constToValue(element);
        storeValue(addr + i * elementSize, value, elementType);
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
      Integer size = structSizes.get(structType.name());
      if (size == null) {
        throw new IllegalStateException("Unknown struct type: " + structType.name());
      }
      return size;
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

  private static int floatToQ16(float value) {
    return Math.round(value * 65536.0f);
  }

  private static int q16Mul(int left, int right) {
    long prod = (long) left * (long) right;
    return (int) (prod >> 16);
  }

  private static int q16Div(int left, int right) {
    if (right == 0) {
      return 0;
    }
    long num = ((long) left) << 16;
    return (int) (num / right);
  }

  private static final class Frame {
    final Map<String, Integer> localAddresses = new HashMap<>();
    final Map<String, Integer> paramAddresses = new HashMap<>();
    final Map<Integer, Integer> temps = new HashMap<>();
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
