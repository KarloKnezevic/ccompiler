package hr.fer.ppj.codegen.frisc.ir;

import hr.fer.ppj.ir.model.IrConst;
import hr.fer.ppj.ir.types.IrType;
import java.util.List;
import java.util.Objects;

public record IrProgramModel(
    List<StructDef> structDefs,
    List<GlobalVar> globals,
    List<Function> functions) {

  public IrProgramModel {
    Objects.requireNonNull(structDefs, "structDefs must not be null");
    Objects.requireNonNull(globals, "globals must not be null");
    Objects.requireNonNull(functions, "functions must not be null");
  }

  public record StructDef(String name, List<StructField> fields) {
    public StructDef {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(fields, "fields must not be null");
    }
  }

  public record StructField(String name, IrType type, int offset) {
    public StructField {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(type, "type must not be null");
    }
  }

  public record GlobalVar(String name, IrType type, IrConst initializer) {
    public GlobalVar {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(type, "type must not be null");
    }
  }

  public record Function(
      String name,
      List<Parameter> parameters,
      IrType returnType,
      int localsBytes,
      int alignBytes,
      List<Slot> slots,
      List<Block> blocks) {
    public Function {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(parameters, "parameters must not be null");
      Objects.requireNonNull(slots, "slots must not be null");
      Objects.requireNonNull(blocks, "blocks must not be null");
    }
  }

  public record Parameter(String name, IrType type) {
    public Parameter {
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(type, "type must not be null");
    }
  }

  public enum SlotKind {
    PARAM,
    LOCAL,
    SPILL
  }

  public record Slot(SlotKind kind, String name, int offset, IrType type) {
    public Slot {
      Objects.requireNonNull(kind, "kind must not be null");
      Objects.requireNonNull(name, "name must not be null");
      Objects.requireNonNull(type, "type must not be null");
    }
  }

  public record Block(String label, List<Instruction> instructions, Terminator terminator) {
    public Block {
      Objects.requireNonNull(label, "label must not be null");
      Objects.requireNonNull(instructions, "instructions must not be null");
      Objects.requireNonNull(terminator, "terminator must not be null");
    }
  }

  public sealed interface Instruction permits Assign, Store, VoidCall {
  }

  public record Assign(Temp dest, Rhs rhs) implements Instruction {
    public Assign {
      Objects.requireNonNull(dest, "dest must not be null");
      Objects.requireNonNull(rhs, "rhs must not be null");
    }
  }

  public record Store(Value address, Value value, IrType storeType) implements Instruction {
    public Store {
      Objects.requireNonNull(address, "address must not be null");
      Objects.requireNonNull(value, "value must not be null");
      Objects.requireNonNull(storeType, "storeType must not be null");
    }
  }

  public record VoidCall(String funcName, List<Value> args) implements Instruction {
    public VoidCall {
      Objects.requireNonNull(funcName, "funcName must not be null");
      Objects.requireNonNull(args, "args must not be null");
    }
  }

  public sealed interface Rhs permits AddrOfSymbol, AddrIndex, AddrField, Load, BinOp, CmpOp,
      Call, UnaryOp, CastOp, ConstRhs {
  }

  public record AddrOfSymbol(SymbolRef symbolRef) implements Rhs {
    public AddrOfSymbol {
      Objects.requireNonNull(symbolRef, "symbolRef must not be null");
    }
  }

  public record AddrIndex(Value base, Value index, int elemSize) implements Rhs {
    public AddrIndex {
      Objects.requireNonNull(base, "base must not be null");
      Objects.requireNonNull(index, "index must not be null");
    }
  }

  public record AddrField(Value base, String structName, String fieldName) implements Rhs {
    public AddrField {
      Objects.requireNonNull(base, "base must not be null");
      Objects.requireNonNull(structName, "structName must not be null");
      Objects.requireNonNull(fieldName, "fieldName must not be null");
    }
  }

  public record Load(Value address, IrType loadType) implements Rhs {
    public Load {
      Objects.requireNonNull(address, "address must not be null");
      Objects.requireNonNull(loadType, "loadType must not be null");
    }
  }

  public enum BinOpName {
    ADD,
    SUB,
    MUL,
    DIV,
    MOD,
    AND,
    OR,
    XOR,
    SHL,
    SHR
  }

  public record BinOp(BinOpName op, Value left, Value right, IrType resultType) implements Rhs {
    public BinOp {
      Objects.requireNonNull(op, "op must not be null");
      Objects.requireNonNull(left, "left must not be null");
      Objects.requireNonNull(right, "right must not be null");
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  public enum CmpOpName {
    EQ,
    NE,
    LT,
    LE,
    GT,
    GE
  }

  public record CmpOp(CmpOpName op, Value left, Value right) implements Rhs {
    public CmpOp {
      Objects.requireNonNull(op, "op must not be null");
      Objects.requireNonNull(left, "left must not be null");
      Objects.requireNonNull(right, "right must not be null");
    }
  }

  public record Call(String funcName, List<Value> args, IrType resultType) implements Rhs {
    public Call {
      Objects.requireNonNull(funcName, "funcName must not be null");
      Objects.requireNonNull(args, "args must not be null");
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  public enum UnaryOpName {
    NEG,
    NOT
  }

  public record UnaryOp(UnaryOpName op, Value operand, IrType resultType) implements Rhs {
    public UnaryOp {
      Objects.requireNonNull(op, "op must not be null");
      Objects.requireNonNull(operand, "operand must not be null");
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  public enum CastName {
    TRUNC,
    SEXT,
    ZEXT,
    PTRCAST,
    ITOF,
    FTOI
  }

  public record CastOp(CastName op, Value operand, IrType resultType) implements Rhs {
    public CastOp {
      Objects.requireNonNull(op, "op must not be null");
      Objects.requireNonNull(operand, "operand must not be null");
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  public record ConstRhs(IrConst constant) implements Rhs {
    public ConstRhs {
      Objects.requireNonNull(constant, "constant must not be null");
    }
  }

  public sealed interface Value permits Temp, Const {
  }

  public record Temp(int index) implements Value {
  }

  public record Const(IrConst constant) implements Value {
    public Const {
      Objects.requireNonNull(constant, "constant must not be null");
    }
  }

  public enum SymbolRefKind {
    LOCAL,
    PARAM,
    GLOBAL
  }

  public record SymbolRef(SymbolRefKind kind, String name) {
    public SymbolRef {
      Objects.requireNonNull(kind, "kind must not be null");
      Objects.requireNonNull(name, "name must not be null");
    }
  }

  public sealed interface Terminator permits Br, Jmp, Ret {
  }

  public record Br(Value condition, String trueLabel, String falseLabel) implements Terminator {
    public Br {
      Objects.requireNonNull(condition, "condition must not be null");
      Objects.requireNonNull(trueLabel, "trueLabel must not be null");
      Objects.requireNonNull(falseLabel, "falseLabel must not be null");
    }
  }

  public record Jmp(String targetLabel) implements Terminator {
    public Jmp {
      Objects.requireNonNull(targetLabel, "targetLabel must not be null");
    }
  }

  public record Ret(Value value) implements Terminator {
  }
}
