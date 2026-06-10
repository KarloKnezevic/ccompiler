package hr.fer.ppj.cli.vm;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Lowers a typed {@link IrProgramModel} into flat {@link Bytecode}.
 *
 * <p>This is the second translation in the book's two-chapter arc: where the FRISC code generator
 * lowers the same IR to a real instruction set, this lowerer targets the virtual instruction set of
 * {@link BytecodeVm}. The translation is the point where the IR's three-address form becomes
 * stack-machine code (operands are pushed, an opcode consumes them, the result is pushed back) and
 * where every static fact the {@code IrInterpreter} re-derives at run time --- result types,
 * struct-field offsets, array sizes, and which {@code addr_index} computations need a bounds check
 * --- is computed once, here, and baked into the bytecode. The running machine is then typeless.
 */
public final class IrToBytecodeCompiler {

  private final IrProgramModel program;
  private final Map<String, StructLayout> structLayouts = new HashMap<>();
  private final Map<String, IrType> globalTypes = new HashMap<>();
  private final Map<String, Integer> functionIndex = new HashMap<>();

  public IrToBytecodeCompiler(IrProgramModel program) {
    this.program = Objects.requireNonNull(program, "program must not be null");
    registerStructLayouts();
    for (IrProgramModel.GlobalVar global : program.globals()) {
      globalTypes.put(global.name(), global.type());
    }
    int index = 0;
    for (IrProgramModel.Function function : program.functions()) {
      functionIndex.put(function.name(), index++);
    }
  }

  public Bytecode.Program compile() {
    List<Bytecode.GlobalImage> globals = new ArrayList<>();
    for (IrProgramModel.GlobalVar global : program.globals()) {
      globals.add(buildGlobalImage(global));
    }

    List<Bytecode.Function> functions = new ArrayList<>();
    for (IrProgramModel.Function function : program.functions()) {
      functions.add(lowerFunction(function));
    }
    return new Bytecode.Program(functions, new HashMap<>(functionIndex), globals);
  }

  // =====================================================================
  // Function lowering
  // =====================================================================

  private Bytecode.Function lowerFunction(IrProgramModel.Function function) {
    Map<Integer, IrProgramModel.Rhs> defOf = new HashMap<>();
    for (IrProgramModel.Block block : function.blocks()) {
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        if (instruction instanceof IrProgramModel.Assign assign) {
          defOf.put(assign.dest().index(), assign.rhs());
        }
      }
    }

    Map<String, IrType> slotTypes = new HashMap<>();
    for (IrProgramModel.Slot slot : function.slots()) {
      slotTypes.put(slot.name(), slot.type());
    }

    Set<Integer> boundsChecks = analyzeAddrIndexChecks(function);
    Map<Integer, IrType> tempTypeCache = new HashMap<>();

    CodeBuffer code = new CodeBuffer();
    LinkedHashMap<String, Integer> symbolIndex = new LinkedHashMap<>();
    List<IrProgramModel.SymbolRef> symbols = new ArrayList<>();
    Map<String, Integer> blockOffset = new HashMap<>();
    Map<Integer, String> blockLabels = new LinkedHashMap<>();
    List<Fixup> fixups = new ArrayList<>();

    LoweringContext ctx = new LoweringContext(
        code, symbolIndex, symbols, fixups, defOf, slotTypes, boundsChecks, tempTypeCache);

    for (IrProgramModel.Block block : function.blocks()) {
      blockOffset.put(block.label(), code.size());
      blockLabels.put(code.size(), block.label());
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        emitInstruction(instruction, ctx);
      }
      emitTerminator(block.terminator(), ctx);
    }

    for (Fixup fixup : fixups) {
      Integer target = blockOffset.get(fixup.label());
      if (target == null) {
        throw new IllegalStateException("Branch to unknown block: " + fixup.label());
      }
      code.patch(fixup.position(), target);
    }

    List<Bytecode.SlotInfo> slots = new ArrayList<>();
    for (IrProgramModel.Slot slot : function.slots()) {
      slots.add(new Bytecode.SlotInfo(slot.kind(), slot.name(), sizeOf(slot.type()), alignmentOf(slot.type())));
    }

    List<Bytecode.ParamBind> params = new ArrayList<>();
    for (IrProgramModel.Parameter param : function.parameters()) {
      params.add(new Bytecode.ParamBind(param.name(), bindKind(param.type()), sizeOf(param.type())));
    }

    return new Bytecode.Function(
        function.name(),
        code.toArray(),
        computeMaxTemps(function),
        function.parameters().size(),
        slots,
        params,
        symbols,
        blockLabels);
  }

  private void emitInstruction(IrProgramModel.Instruction instruction, LoweringContext ctx) {
    if (instruction instanceof IrProgramModel.Assign assign) {
      emitRhs(assign.rhs(), assign.dest().index(), ctx);
      ctx.code.op(Opcode.STORE_TEMP);
      ctx.code.i32(assign.dest().index());
      return;
    }
    if (instruction instanceof IrProgramModel.Store store) {
      emitValue(store.address(), ctx);
      emitValue(store.value(), ctx);
      if (isAggregate(store.storeType())) {
        ctx.code.op(Opcode.MEMCPY);
        ctx.code.i32(sizeOf(store.storeType()));
      } else if (isByte(store.storeType())) {
        ctx.code.op(Opcode.STORE_BYTE);
      } else {
        ctx.code.op(Opcode.STORE_WORD);
      }
      return;
    }
    if (instruction instanceof IrProgramModel.VoidCall call) {
      for (IrProgramModel.Value arg : call.args()) {
        emitValue(arg, ctx);
      }
      ctx.code.op(Opcode.CALL_VOID);
      ctx.code.i32(requireFunctionIndex(call.funcName()));
      ctx.code.i32(call.args().size());
      return;
    }
    throw new IllegalStateException("Unknown instruction: " + instruction);
  }

  private void emitRhs(IrProgramModel.Rhs rhs, int destIndex, LoweringContext ctx) {
    if (rhs instanceof IrProgramModel.ConstRhs constRhs) {
      ctx.code.op(Opcode.PUSH_CONST);
      ctx.code.i32(constToValue(constRhs.constant()));
      return;
    }
    if (rhs instanceof IrProgramModel.AddrOfSymbol addr) {
      ctx.code.op(Opcode.ADDR_SYM);
      ctx.code.i32(symbolIndexOf(addr.symbolRef(), ctx));
      return;
    }
    if (rhs instanceof IrProgramModel.AddrIndex addrIndex) {
      emitValue(addrIndex.base(), ctx);
      emitValue(addrIndex.index(), ctx);
      Integer arraySize = checkedArraySize(addrIndex, destIndex, ctx);
      if (arraySize != null) {
        ctx.code.op(Opcode.ADDR_INDEX_CHK);
        ctx.code.i32(addrIndex.elemSize());
        ctx.code.i32(arraySize);
      } else {
        ctx.code.op(Opcode.ADDR_INDEX);
        ctx.code.i32(addrIndex.elemSize());
      }
      return;
    }
    if (rhs instanceof IrProgramModel.AddrField addrField) {
      emitValue(addrField.base(), ctx);
      ctx.code.op(Opcode.ADDR_FIELD);
      ctx.code.i32(fieldInfo(addrField.structName(), addrField.fieldName()).offset());
      return;
    }
    if (rhs instanceof IrProgramModel.Load load) {
      emitValue(load.address(), ctx);
      if (isAggregate(load.loadType())) {
        return; // aggregate "load" is the address itself
      }
      ctx.code.op(isByte(load.loadType()) ? Opcode.LOAD_BYTE : Opcode.LOAD_WORD);
      return;
    }
    if (rhs instanceof IrProgramModel.BinOp binOp) {
      emitValue(binOp.left(), ctx);
      emitValue(binOp.right(), ctx);
      ctx.code.op(binOpcode(binOp.op(), binOp.resultType() == IrPrimitiveType.FLOAT));
      return;
    }
    if (rhs instanceof IrProgramModel.CmpOp cmpOp) {
      emitValue(cmpOp.left(), ctx);
      emitValue(cmpOp.right(), ctx);
      ctx.code.op(cmpOpcode(cmpOp.op()));
      return;
    }
    if (rhs instanceof IrProgramModel.Call call) {
      for (IrProgramModel.Value arg : call.args()) {
        emitValue(arg, ctx);
      }
      ctx.code.op(Opcode.CALL);
      ctx.code.i32(requireFunctionIndex(call.funcName()));
      ctx.code.i32(call.args().size());
      return;
    }
    if (rhs instanceof IrProgramModel.UnaryOp unaryOp) {
      emitValue(unaryOp.operand(), ctx);
      ctx.code.op(unaryOp.op() == IrProgramModel.UnaryOpName.NEG ? Opcode.NEG : Opcode.NOT);
      return;
    }
    if (rhs instanceof IrProgramModel.CastOp castOp) {
      emitValue(castOp.operand(), ctx);
      switch (castOp.op()) {
        case TRUNC, ZEXT -> ctx.code.op(Opcode.CAST_BYTE);
        case SEXT -> ctx.code.op(Opcode.CAST_SEXT);
        case ITOF -> ctx.code.op(Opcode.CAST_ITOF);
        case FTOI -> ctx.code.op(Opcode.CAST_FTOI);
        case PTRCAST -> { /* identity: operand already on the stack */ }
      }
      return;
    }
    throw new IllegalStateException("Unknown rhs: " + rhs);
  }

  private void emitValue(IrProgramModel.Value value, LoweringContext ctx) {
    if (value instanceof IrProgramModel.Temp temp) {
      ctx.code.op(Opcode.LOAD_TEMP);
      ctx.code.i32(temp.index());
      return;
    }
    if (value instanceof IrProgramModel.Const constant) {
      ctx.code.op(Opcode.PUSH_CONST);
      ctx.code.i32(constToValue(constant.constant()));
      return;
    }
    throw new IllegalStateException("Unknown value: " + value);
  }

  private void emitTerminator(IrProgramModel.Terminator terminator, LoweringContext ctx) {
    if (terminator instanceof IrProgramModel.Ret ret) {
      if (ret.value() == null) {
        ctx.code.op(Opcode.RET_VOID);
      } else {
        emitValue(ret.value(), ctx);
        ctx.code.op(Opcode.RET);
      }
      return;
    }
    if (terminator instanceof IrProgramModel.Jmp jmp) {
      ctx.code.op(Opcode.JMP);
      ctx.fixups.add(new Fixup(ctx.code.size(), jmp.targetLabel()));
      ctx.code.i32(0);
      return;
    }
    if (terminator instanceof IrProgramModel.Br br) {
      emitValue(br.condition(), ctx);
      ctx.code.op(Opcode.BR);
      ctx.fixups.add(new Fixup(ctx.code.size(), br.trueLabel()));
      ctx.code.i32(0);
      ctx.fixups.add(new Fixup(ctx.code.size(), br.falseLabel()));
      ctx.code.i32(0);
      return;
    }
    throw new IllegalStateException("Unknown terminator: " + terminator);
  }

  private static Opcode binOpcode(IrProgramModel.BinOpName op, boolean isFloat) {
    if (isFloat) {
      return switch (op) {
        case ADD -> Opcode.ADD;
        case SUB -> Opcode.SUB;
        case MUL -> Opcode.MUL_Q16;
        case DIV -> Opcode.DIV_Q16;
        default -> throw new IllegalStateException("Unsupported float op: " + op);
      };
    }
    return switch (op) {
      case ADD -> Opcode.ADD;
      case SUB -> Opcode.SUB;
      case MUL -> Opcode.MUL;
      case DIV -> Opcode.DIV;
      case MOD -> Opcode.MOD;
      case AND -> Opcode.AND;
      case OR -> Opcode.OR;
      case XOR -> Opcode.XOR;
      case SHL -> Opcode.SHL;
      case SHR -> Opcode.SHR;
    };
  }

  private static Opcode cmpOpcode(IrProgramModel.CmpOpName op) {
    return switch (op) {
      case EQ -> Opcode.CMP_EQ;
      case NE -> Opcode.CMP_NE;
      case LT -> Opcode.CMP_LT;
      case LE -> Opcode.CMP_LE;
      case GT -> Opcode.CMP_GT;
      case GE -> Opcode.CMP_GE;
    };
  }

  private int requireFunctionIndex(String name) {
    Integer index = functionIndex.get(name);
    if (index == null) {
      throw new IllegalStateException("Call to unknown function: " + name);
    }
    return index;
  }

  private int symbolIndexOf(IrProgramModel.SymbolRef ref, LoweringContext ctx) {
    String key = ref.kind() + ":" + ref.name();
    Integer existing = ctx.symbolIndex.get(key);
    if (existing != null) {
      return existing;
    }
    int index = ctx.symbols.size();
    ctx.symbols.add(ref);
    ctx.symbolIndex.put(key, index);
    return index;
  }

  /**
   * Decides, exactly as {@code IrInterpreter} does at run time, whether this {@code addr_index} must
   * be bounds-checked, and returns the array size to check against (or {@code null} for no check).
   */
  private Integer checkedArraySize(IrProgramModel.AddrIndex addrIndex, int destIndex, LoweringContext ctx) {
    if (!ctx.boundsChecks.contains(destIndex)) {
      return null;
    }
    IrType baseType = valueTypeStatic(addrIndex.base(), ctx);
    if (baseType instanceof IrPointerType pointerType
        && pointerType.baseType() instanceof IrArrayType arrayType) {
      return arrayType.size();
    }
    return null;
  }

  // =====================================================================
  // Static type inference (mirror of IrInterpreter.rhsType / valueType)
  // =====================================================================

  private IrType valueTypeStatic(IrProgramModel.Value value, LoweringContext ctx) {
    if (value instanceof IrProgramModel.Temp temp) {
      return tempType(temp.index(), ctx);
    }
    if (value instanceof IrProgramModel.Const constant) {
      return constant.constant().type();
    }
    return null;
  }

  private IrType tempType(int index, LoweringContext ctx) {
    if (ctx.tempTypeCache.containsKey(index)) {
      return ctx.tempTypeCache.get(index);
    }
    IrProgramModel.Rhs rhs = ctx.defOf.get(index);
    ctx.tempTypeCache.put(index, IrPrimitiveType.INT32); // break cycles defensively
    IrType type = rhs == null ? IrPrimitiveType.INT32 : rhsTypeStatic(rhs, ctx);
    ctx.tempTypeCache.put(index, type);
    return type;
  }

  private IrType rhsTypeStatic(IrProgramModel.Rhs rhs, LoweringContext ctx) {
    if (rhs instanceof IrProgramModel.ConstRhs constRhs) {
      return constRhs.constant().type();
    }
    if (rhs instanceof IrProgramModel.AddrOfSymbol addr) {
      return new IrPointerType(symbolTypeStatic(addr.symbolRef(), ctx));
    }
    if (rhs instanceof IrProgramModel.AddrIndex addrIndex) {
      IrType baseType = valueTypeStatic(addrIndex.base(), ctx);
      if (baseType instanceof IrPointerType pointerType) {
        if (pointerType.baseType() instanceof IrArrayType arrayType) {
          return new IrPointerType(arrayType.elementType());
        }
        return pointerType;
      }
      return new IrPointerType(IrPrimitiveType.INT32);
    }
    if (rhs instanceof IrProgramModel.AddrField addrField) {
      return new IrPointerType(fieldInfo(addrField.structName(), addrField.fieldName()).type());
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

  private IrType symbolTypeStatic(IrProgramModel.SymbolRef ref, LoweringContext ctx) {
    return switch (ref.kind()) {
      case LOCAL, PARAM -> ctx.slotTypes.getOrDefault(ref.name(), IrPrimitiveType.INT32);
      case GLOBAL -> globalTypes.getOrDefault(ref.name(), IrPrimitiveType.INT32);
    };
  }

  // =====================================================================
  // Bounds-check dataflow (verbatim from IrInterpreter, kept in lock-step)
  // =====================================================================

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

  // =====================================================================
  // Globals: pre-compute a flat byte image so the VM never sees a type
  // =====================================================================

  private Bytecode.GlobalImage buildGlobalImage(IrProgramModel.GlobalVar global) {
    int size = sizeOf(global.type());
    byte[] image = new byte[Math.max(size, 0)];
    if (global.initializer() != null) {
      storeConstImage(image, 0, global.initializer(), global.type());
    }
    return new Bytecode.GlobalImage(global.name(), size, alignmentOf(global.type()), image);
  }

  private void storeConstImage(byte[] image, int offset, IrConst constant, IrType type) {
    if (constant instanceof IrConst.ArrayConst arrayConst) {
      IrType elementType = arrayConst.arrayType().elementType();
      int elementSize = sizeOf(elementType);
      for (int i = 0; i < arrayConst.elements().size(); i++) {
        storeConstImage(image, offset + i * elementSize, arrayConst.elements().get(i), elementType);
      }
      return;
    }
    int value = constToValue(constant);
    if (isByte(type)) {
      image[offset] = (byte) value;
    } else {
      image[offset] = (byte) value;
      image[offset + 1] = (byte) (value >> 8);
      image[offset + 2] = (byte) (value >> 16);
      image[offset + 3] = (byte) (value >> 24);
    }
  }

  // =====================================================================
  // Shared scalar helpers (same definitions IrInterpreter uses)
  // =====================================================================

  private static int constToValue(IrConst constant) {
    if (constant instanceof IrConst.IntConst intConst) {
      return intConst.value();
    }
    if (constant instanceof IrConst.CharConst charConst) {
      return charConst.value() & 0xFF;
    }
    if (constant instanceof IrConst.FloatConst floatConst) {
      return Math.round(floatConst.value() * 65536.0f);
    }
    if (constant instanceof IrConst.NullConst) {
      return 0;
    }
    if (constant instanceof IrConst.ArrayConst) {
      throw new IllegalStateException("Array constant cannot be used as scalar value.");
    }
    throw new IllegalStateException("Unsupported constant: " + constant);
  }

  private static Bytecode.BindKind bindKind(IrType type) {
    if (isAggregate(type)) {
      return Bytecode.BindKind.AGGREGATE;
    }
    return isByte(type) ? Bytecode.BindKind.BYTE : Bytecode.BindKind.WORD;
  }

  private int computeMaxTemps(IrProgramModel.Function function) {
    int[] max = {-1};
    for (IrProgramModel.Block block : function.blocks()) {
      for (IrProgramModel.Instruction instruction : block.instructions()) {
        if (instruction instanceof IrProgramModel.Assign assign) {
          bump(max, assign.dest().index());
          walkRhs(assign.rhs(), max);
        } else if (instruction instanceof IrProgramModel.Store store) {
          walkValue(store.address(), max);
          walkValue(store.value(), max);
        } else if (instruction instanceof IrProgramModel.VoidCall call) {
          for (IrProgramModel.Value arg : call.args()) {
            walkValue(arg, max);
          }
        }
      }
      IrProgramModel.Terminator terminator = block.terminator();
      if (terminator instanceof IrProgramModel.Br br) {
        walkValue(br.condition(), max);
      } else if (terminator instanceof IrProgramModel.Ret ret && ret.value() != null) {
        walkValue(ret.value(), max);
      }
    }
    return max[0] + 1;
  }

  private void walkRhs(IrProgramModel.Rhs rhs, int[] max) {
    if (rhs instanceof IrProgramModel.AddrIndex addrIndex) {
      walkValue(addrIndex.base(), max);
      walkValue(addrIndex.index(), max);
    } else if (rhs instanceof IrProgramModel.AddrField addrField) {
      walkValue(addrField.base(), max);
    } else if (rhs instanceof IrProgramModel.Load load) {
      walkValue(load.address(), max);
    } else if (rhs instanceof IrProgramModel.BinOp binOp) {
      walkValue(binOp.left(), max);
      walkValue(binOp.right(), max);
    } else if (rhs instanceof IrProgramModel.CmpOp cmpOp) {
      walkValue(cmpOp.left(), max);
      walkValue(cmpOp.right(), max);
    } else if (rhs instanceof IrProgramModel.Call call) {
      for (IrProgramModel.Value arg : call.args()) {
        walkValue(arg, max);
      }
    } else if (rhs instanceof IrProgramModel.UnaryOp unaryOp) {
      walkValue(unaryOp.operand(), max);
    } else if (rhs instanceof IrProgramModel.CastOp castOp) {
      walkValue(castOp.operand(), max);
    }
  }

  private void walkValue(IrProgramModel.Value value, int[] max) {
    if (value instanceof IrProgramModel.Temp temp) {
      bump(max, temp.index());
    }
  }

  private static void bump(int[] max, int index) {
    if (index > max[0]) {
      max[0] = index;
    }
  }

  // =====================================================================
  // Struct layout + sizes (same definitions IrInterpreter uses)
  // =====================================================================

  private void registerStructLayouts() {
    for (IrProgramModel.StructDef def : program.structDefs()) {
      StructLayout layout = new StructLayout();
      for (IrProgramModel.StructField field : def.fields()) {
        layout.fields.put(field.name(), new StructFieldInfo(field.offset(), field.type()));
      }
      structLayouts.put(def.name(), layout);
    }
  }

  private StructFieldInfo fieldInfo(String structName, String fieldName) {
    StructLayout layout = structLayouts.computeIfAbsent(structName, ignored -> new StructLayout());
    StructFieldInfo field = layout.fields.get(fieldName);
    if (field != null) {
      return field;
    }
    int nextOffset = 0;
    for (StructFieldInfo existing : layout.fields.values()) {
      nextOffset = alignTo(nextOffset, alignmentOf(existing.type()));
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

  private static boolean isAggregate(IrType type) {
    return type instanceof IrArrayType || type instanceof IrStructType;
  }

  private static boolean isByte(IrType type) {
    return type == IrPrimitiveType.CHAR || type == IrPrimitiveType.UCHAR;
  }

  private static int alignTo(int value, int alignment) {
    if (alignment <= 1) {
      return value;
    }
    int mod = value % alignment;
    return mod == 0 ? value : value + (alignment - mod);
  }

  // =====================================================================
  // Small helper types
  // =====================================================================

  /** Mutable, growable byte buffer for the instruction stream, with little-endian operand writes. */
  static final class CodeBuffer {
    private byte[] buffer = new byte[64];
    private int length;

    void op(Opcode opcode) {
      ensure(1);
      buffer[length++] = (byte) opcode.ordinal();
    }

    void i32(int value) {
      ensure(4);
      buffer[length++] = (byte) value;
      buffer[length++] = (byte) (value >> 8);
      buffer[length++] = (byte) (value >> 16);
      buffer[length++] = (byte) (value >> 24);
    }

    void patch(int position, int value) {
      buffer[position] = (byte) value;
      buffer[position + 1] = (byte) (value >> 8);
      buffer[position + 2] = (byte) (value >> 16);
      buffer[position + 3] = (byte) (value >> 24);
    }

    int size() {
      return length;
    }

    byte[] toArray() {
      return Arrays.copyOf(buffer, length);
    }

    private void ensure(int extra) {
      if (length + extra > buffer.length) {
        buffer = Arrays.copyOf(buffer, Math.max(buffer.length * 2, length + extra));
      }
    }
  }

  private record Fixup(int position, String label) {
  }

  private record LoweringContext(
      CodeBuffer code,
      LinkedHashMap<String, Integer> symbolIndex,
      List<IrProgramModel.SymbolRef> symbols,
      List<Fixup> fixups,
      Map<Integer, IrProgramModel.Rhs> defOf,
      Map<String, IrType> slotTypes,
      Set<Integer> boundsChecks,
      Map<Integer, IrType> tempTypeCache) {
  }

  private static final class StructLayout {
    private final Map<String, StructFieldInfo> fields = new LinkedHashMap<>();
  }

  private record StructFieldInfo(int offset, IrType type) {
  }
}
