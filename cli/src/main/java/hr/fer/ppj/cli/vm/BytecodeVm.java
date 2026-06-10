package hr.fer.ppj.cli.vm;

import hr.fer.ppj.codegen.frisc.ir.IrProgramModel;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A stack-based virtual machine that executes lowered {@link Bytecode}.
 *
 * <p>Where {@code IrInterpreter} walks the typed IR object graph and leans on the host's call stack
 * for recursion, this machine takes the opposite design. It runs a flat instruction stream with an
 * explicit program counter, an explicit operand stack on which all computation happens, and an
 * explicit stack of call frames that it manages itself. A recursive bytecode program does <em>not</em>
 * become a recursive Java program: a single dispatch loop pushes and pops frames, so the machine
 * controls its own depth limit independently of the host thread's stack.
 *
 * <p>The arithmetic, the memory model, the fixed-point routines, the divide-by-zero convention, the
 * out-of-bounds trap code, and the watchdog all match {@code IrInterpreter} to the bit, so that the
 * two back ends are interchangeable oracles for one another.
 */
public final class BytecodeVm {

  private static final int OPERAND_STACK_SIZE = 1 << 16;

  private final Bytecode.Program bytecode;
  private final Memory memory = new Memory();
  private final Map<String, Integer> globalAddresses = new HashMap<>();

  private final int[] operandStack = new int[OPERAND_STACK_SIZE];
  private int sp;

  private final Deque<CallFrame> callStack = new ArrayDeque<>();
  private final long dispatchLimit;
  private final StringBuilder trace;
  private long dispatched;

  public BytecodeVm(IrProgramModel program, Bytecode.Program bytecode, VmExecutionOptions options) {
    Objects.requireNonNull(program, "program must not be null");
    this.bytecode = Objects.requireNonNull(bytecode, "bytecode must not be null");
    Objects.requireNonNull(options, "options must not be null");
    this.dispatchLimit = options.dispatchLimit();
    this.trace = options.trace() ? new StringBuilder(4096) : null;
    initGlobals();
  }

  private void initGlobals() {
    for (Bytecode.GlobalImage global : bytecode.globals()) {
      int addr = memory.alloc(global.size(), global.align());
      globalAddresses.put(global.name(), addr);
      for (int i = 0; i < global.image().length; i++) {
        memory.storeByte(addr + i, global.image()[i]);
      }
    }
  }

  public VmExecutionResult execute() {
    Integer mainIndex = bytecode.functionIndex().get("main");
    if (mainIndex == null) {
      throw new IllegalStateException("Program has no main function");
    }
    try {
      int result = run(mainIndex);
      return new VmExecutionResult(result, dispatched, traceText());
    } catch (TrapSignal trap) {
      return new VmExecutionResult(trap.code(), dispatched, traceText());
    }
  }

  private String traceText() {
    return trace == null ? "" : trace.toString();
  }

  /** Drives the single dispatch loop until the entry function returns. */
  private int run(int entryFunctionIndex) {
    pushFrame(entryFunctionIndex, false);

    while (true) {
      CallFrame frame = callStack.peek();
      byte[] code = frame.fn.code();
      int opByte = code[frame.pc++];
      Opcode op = Opcode.fromByte(opByte);

      dispatched++;
      if (dispatched > dispatchLimit) {
        throw new IllegalStateException(
            "Bytecode VM dispatch limit exceeded in " + frame.fn.name()
                + " (limit=" + dispatchLimit + ")");
      }
      if (trace != null) {
        traceStep(frame, op);
      }

      switch (op) {
        case PUSH_CONST -> push(readInt(code, frame));
        case LOAD_TEMP -> push(frame.temps[readInt(code, frame)]);
        case STORE_TEMP -> frame.temps[readInt(code, frame)] = pop();

        case ADDR_SYM -> push(resolveSymbol(frame, readInt(code, frame)));
        case ADDR_FIELD -> {
          int offset = readInt(code, frame);
          push(pop() + offset);
        }
        case ADDR_INDEX -> {
          int elemSize = readInt(code, frame);
          int index = pop();
          int base = pop();
          push(base + index * elemSize);
        }
        case ADDR_INDEX_CHK -> {
          int elemSize = readInt(code, frame);
          int arraySize = readInt(code, frame);
          int index = pop();
          int base = pop();
          if (index < 0 || index >= arraySize) {
            throw new TrapSignal(-6, "Array index out of bounds");
          }
          push(base + index * elemSize);
        }

        case LOAD_WORD -> push(memory.loadWord(pop()));
        case LOAD_BYTE -> push(memory.loadByte(pop()));
        case STORE_WORD -> {
          int value = pop();
          memory.storeWord(pop(), value);
        }
        case STORE_BYTE -> {
          int value = pop();
          memory.storeByte(pop(), value);
        }
        case MEMCPY -> {
          int size = readInt(code, frame);
          int src = pop();
          int dst = pop();
          memory.copy(src, dst, size);
        }

        case ADD -> binary((a, b) -> a + b);
        case SUB -> binary((a, b) -> a - b);
        case MUL -> binary((a, b) -> a * b);
        case DIV -> binary((a, b) -> b == 0 ? 0 : a / b);
        case MOD -> binary((a, b) -> b == 0 ? 0 : a % b);
        case AND -> binary((a, b) -> a & b);
        case OR -> binary((a, b) -> a | b);
        case XOR -> binary((a, b) -> a ^ b);
        case SHL -> binary((a, b) -> a << b);
        case SHR -> binary((a, b) -> a >> b);
        case MUL_Q16 -> binary(BytecodeVm::q16Mul);
        case DIV_Q16 -> binary(BytecodeVm::q16Div);

        case CMP_EQ -> binary((a, b) -> a == b ? 1 : 0);
        case CMP_NE -> binary((a, b) -> a != b ? 1 : 0);
        case CMP_LT -> binary((a, b) -> a < b ? 1 : 0);
        case CMP_LE -> binary((a, b) -> a <= b ? 1 : 0);
        case CMP_GT -> binary((a, b) -> a > b ? 1 : 0);
        case CMP_GE -> binary((a, b) -> a >= b ? 1 : 0);

        case NEG -> push(-pop());
        case NOT -> push(pop() == 0 ? 1 : 0);

        case CAST_BYTE -> push(pop() & 0xFF);
        case CAST_SEXT -> push((pop() << 24) >> 24);
        case CAST_ITOF -> push(pop() << 16);
        case CAST_FTOI -> push(pop() >> 16);

        case CALL -> doCall(code, frame, true);
        case CALL_VOID -> doCall(code, frame, false);

        case JMP -> frame.pc = readInt(code, frame);
        case BR -> {
          int trueOffset = readInt(code, frame);
          int falseOffset = readInt(code, frame);
          frame.pc = pop() != 0 ? trueOffset : falseOffset;
        }
        case RET -> {
          int value = pop();
          if (returnFrom(value)) {
            return value;
          }
        }
        case RET_VOID -> {
          if (returnFrom(0)) {
            return 0;
          }
        }
      }
    }
  }

  /** Performs a call: pops arguments, builds the callee frame, and makes it current. */
  private void doCall(byte[] code, CallFrame caller, boolean producesValue) {
    int funcIndex = readInt(code, caller);
    int argc = readInt(code, caller);
    int[] argv = new int[argc];
    for (int i = argc - 1; i >= 0; i--) {
      argv[i] = pop();
    }
    CallFrame callee = newFrame(funcIndex, producesValue);
    bindArguments(callee, argv);
    callStack.push(callee);
  }

  /**
   * Pops the current frame and resumes the caller. Returns {@code true} when the call stack is now
   * empty, meaning the entry function has returned and the run is over.
   */
  private boolean returnFrom(int value) {
    CallFrame finished = callStack.pop();
    if (callStack.isEmpty()) {
      return true;
    }
    if (finished.producesValue) {
      push(value);
    }
    return false;
  }

  private void pushFrame(int functionIndex, boolean producesValue) {
    callStack.push(newFrame(functionIndex, producesValue));
  }

  private CallFrame newFrame(int functionIndex, boolean producesValue) {
    Bytecode.Function fn = bytecode.function(functionIndex);
    CallFrame frame = new CallFrame(fn, producesValue);
    for (Bytecode.SlotInfo slot : fn.slots()) {
      int addr = memory.alloc(slot.size(), slot.align());
      memory.clear(addr, slot.size());
      if (slot.kind() == IrProgramModel.SlotKind.PARAM) {
        frame.paramAddresses.put(slot.name(), addr);
      } else {
        frame.localAddresses.put(slot.name(), addr);
      }
    }
    return frame;
  }

  private void bindArguments(CallFrame frame, int[] argv) {
    if (argv.length != frame.fn.arity()) {
      throw new IllegalStateException(
          "Argument count mismatch for " + frame.fn.name() + ": expected "
              + frame.fn.arity() + ", got " + argv.length);
    }
    for (int i = 0; i < argv.length; i++) {
      Bytecode.ParamBind bind = frame.fn.params().get(i);
      int addr = frame.paramAddresses.get(bind.slotName());
      switch (bind.bind()) {
        case AGGREGATE -> memory.copy(argv[i], addr, bind.size());
        case BYTE -> memory.storeByte(addr, argv[i]);
        case WORD -> memory.storeWord(addr, argv[i]);
      }
    }
  }

  private int resolveSymbol(CallFrame frame, int symbolIndex) {
    IrProgramModel.SymbolRef ref = frame.fn.symbols().get(symbolIndex);
    Integer addr = switch (ref.kind()) {
      case LOCAL -> frame.localAddresses.get(ref.name());
      case PARAM -> frame.paramAddresses.get(ref.name());
      case GLOBAL -> globalAddresses.get(ref.name());
    };
    if (addr == null) {
      throw new IllegalStateException("Unknown symbol: " + ref.name());
    }
    return addr;
  }

  // --- operand stack -------------------------------------------------------

  private void push(int value) {
    if (sp >= operandStack.length) {
      throw new IllegalStateException("Operand stack overflow");
    }
    operandStack[sp++] = value;
  }

  private int pop() {
    if (sp <= 0) {
      throw new IllegalStateException("Operand stack underflow");
    }
    return operandStack[--sp];
  }

  private void binary(IntBinary fn) {
    int right = pop();
    int left = pop();
    push(fn.apply(left, right));
  }

  @FunctionalInterface
  private interface IntBinary {
    int apply(int left, int right);
  }

  // --- shared arithmetic ---------------------------------------------------

  private static int q16Mul(int left, int right) {
    return (int) (((long) left * (long) right) >> 16);
  }

  private static int q16Div(int left, int right) {
    if (right == 0) {
      return 0;
    }
    return (int) ((((long) left) << 16) / right);
  }

  private static int readInt(byte[] code, CallFrame frame) {
    int pc = frame.pc;
    int value = (code[pc] & 0xFF)
        | ((code[pc + 1] & 0xFF) << 8)
        | ((code[pc + 2] & 0xFF) << 16)
        | ((code[pc + 3] & 0xFF) << 24);
    frame.pc = pc + 4;
    return value;
  }

  private void traceStep(CallFrame frame, Opcode op) {
    trace.append('[').append(dispatched).append("] ")
        .append(frame.fn.name()).append('@').append(frame.pc - 1)
        .append(' ').append(op);
    int peekPc = frame.pc;
    for (int i = 0; i < op.operandCount(); i++) {
      int value = (frame.fn.code()[peekPc] & 0xFF)
          | ((frame.fn.code()[peekPc + 1] & 0xFF) << 8)
          | ((frame.fn.code()[peekPc + 2] & 0xFF) << 16)
          | ((frame.fn.code()[peekPc + 3] & 0xFF) << 24);
      trace.append(' ').append(value);
      peekPc += 4;
    }
    trace.append("   | sp=").append(sp);
    if (sp > 0) {
      trace.append(" top=").append(operandStack[sp - 1]);
    }
    trace.append('\n');
  }

  // --- runtime state -------------------------------------------------------

  private static final class CallFrame {
    final Bytecode.Function fn;
    final boolean producesValue;
    final int[] temps;
    final Map<String, Integer> localAddresses = new HashMap<>();
    final Map<String, Integer> paramAddresses = new HashMap<>();
    int pc;

    CallFrame(Bytecode.Function fn, boolean producesValue) {
      this.fn = fn;
      this.producesValue = producesValue;
      this.temps = new int[fn.maxTemps()];
    }
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

  /** Flat byte-addressable memory, identical in behaviour to {@code IrInterpreter.Memory}. */
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
      return mod == 0 ? value : value + (alignment - mod);
    }
  }
}
