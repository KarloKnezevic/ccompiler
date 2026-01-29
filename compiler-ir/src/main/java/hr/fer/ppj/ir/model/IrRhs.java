package hr.fer.ppj.ir.model;

import hr.fer.ppj.ir.types.IrType;
import java.util.List;
import java.util.Objects;

/**
 * Sealed interface for RHS operations (right-hand side of assignments).
 *
 * <p>RHS operations include address operations, loads, binary ops, comparisons,
 * calls, unary ops, inc/dec, casts, and constants.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface IrRhs {
  IrType resultType();

  /**
   * Address of symbol: addr_of_symbol local:x
   */
  record AddrOfSymbol(IrSymbolRef symbolRef, IrType resultType) implements IrRhs {
    public AddrOfSymbol {
      Objects.requireNonNull(symbolRef, "symbolRef must not be null");
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  /**
   * Address index: addr_index base, idx, elemSize
   */
  record AddrIndex(IrValue base, IrValue idx, int elemSize, IrType resultType)
      implements IrRhs {
    public AddrIndex {
      Objects.requireNonNull(base, "base must not be null");
      Objects.requireNonNull(idx, "idx must not be null");
      if (elemSize <= 0) {
        throw new IllegalArgumentException("elemSize must be positive");
      }
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  /**
   * Address field: addr_field base, StructName.fieldName
   */
  record AddrField(IrValue base, String structName, String fieldName, IrType resultType)
      implements IrRhs {
    public AddrField {
      Objects.requireNonNull(base, "base must not be null");
      Objects.requireNonNull(structName, "structName must not be null");
      Objects.requireNonNull(fieldName, "fieldName must not be null");
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  /**
   * Load: load addr : T
   */
  record Load(IrValue addr, IrType loadType) implements IrRhs {
    public Load {
      Objects.requireNonNull(addr, "addr must not be null");
      Objects.requireNonNull(loadType, "loadType must not be null");
    }

    @Override
    public IrType resultType() {
      return loadType;
    }
  }

  /**
   * Binary operation: add t0, t1 : int32
   */
  record BinOp(BinOpName op, IrValue left, IrValue right, IrType resultType)
      implements IrRhs {
    public BinOp {
      Objects.requireNonNull(op, "op must not be null");
      Objects.requireNonNull(left, "left must not be null");
      Objects.requireNonNull(right, "right must not be null");
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  public enum BinOpName {
    ADD("add"),
    SUB("sub"),
    MUL("mul"),
    DIV("div"),
    MOD("mod"),
    AND("and"),
    OR("or"),
    XOR("xor"),
    SHL("shl"),
    SHR("shr");

    private final String irString;

    BinOpName(String irString) {
      this.irString = irString;
    }

    public String toIrString() {
      return irString;
    }
  }

  /**
   * Comparison operation: cmp_eq t0, t1 : bool
   */
  record CmpOp(CmpOpName op, IrValue left, IrValue right) implements IrRhs {
    public CmpOp {
      Objects.requireNonNull(op, "op must not be null");
      Objects.requireNonNull(left, "left must not be null");
      Objects.requireNonNull(right, "right must not be null");
    }

    @Override
    public IrType resultType() {
      return hr.fer.ppj.ir.types.IrPrimitiveType.BOOL;
    }
  }

  public enum CmpOpName {
    EQ("cmp_eq"),
    NE("cmp_ne"),
    LT("cmp_lt"),
    LE("cmp_le"),
    GT("cmp_gt"),
    GE("cmp_ge");

    private final String irString;

    CmpOpName(String irString) {
      this.irString = irString;
    }

    public String toIrString() {
      return irString;
    }
  }

  /**
   * Function call: call func:name (t0, t1) : T
   */
  record Call(String funcName, List<IrValue> args, IrType resultType) implements IrRhs {
    public Call {
      Objects.requireNonNull(funcName, "funcName must not be null");
      Objects.requireNonNull(args, "args must not be null");
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  /**
   * Unary operation: neg t0 : int32
   */
  record UnaryOp(UnaryOpName op, IrValue operand, IrType resultType) implements IrRhs {
    public UnaryOp {
      Objects.requireNonNull(op, "op must not be null");
      Objects.requireNonNull(operand, "operand must not be null");
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  public enum UnaryOpName {
    NEG("neg"),
    NOT("not");

    private final String irString;

    UnaryOpName(String irString) {
      this.irString = irString;
    }

    public String toIrString() {
      return irString;
    }
  }

  /**
   * Increment/decrement: preinc addr : T
   */
  record IncDecOp(IncDecName op, IrValue addr, IrType resultType) implements IrRhs {
    public IncDecOp {
      Objects.requireNonNull(op, "op must not be null");
      Objects.requireNonNull(addr, "addr must not be null");
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  public enum IncDecName {
    PREINC("preinc"),
    POSTINC("postinc"),
    PREDEC("predec"),
    POSTDEC("postdec");

    private final String irString;

    IncDecName(String irString) {
      this.irString = irString;
    }

    public String toIrString() {
      return irString;
    }
  }

  /**
   * Cast operation: trunc t0 : char
   */
  record CastOp(CastName op, IrValue operand, IrType resultType) implements IrRhs {
    public CastOp {
      Objects.requireNonNull(op, "op must not be null");
      Objects.requireNonNull(operand, "operand must not be null");
      Objects.requireNonNull(resultType, "resultType must not be null");
    }
  }

  public enum CastName {
    TRUNC("trunc"),
    SEXT("sext"),
    ZEXT("zext"),
    PTRCAST("ptrcast"),
    ITOF("itof"),
    FTOI("ftoi");

    private final String irString;

    CastName(String irString) {
      this.irString = irString;
    }

    public String toIrString() {
      return irString;
    }
  }

  /**
   * Constant: #42:int32 or #'a':char or #3.14:float or null:ptr<T>
   */
  record ConstRhs(IrConst constant) implements IrRhs {
    public ConstRhs {
      Objects.requireNonNull(constant, "constant must not be null");
    }

    @Override
    public IrType resultType() {
      return constant.type();
    }
  }
}

