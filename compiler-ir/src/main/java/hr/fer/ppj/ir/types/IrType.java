package hr.fer.ppj.ir.types;

/**
 * Base sealed interface for IR types.
 *
 * <p>IR types represent the type system used in the intermediate representation.
 * They correspond to the Type production in the IR grammar.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public sealed interface IrType
    permits IrPrimitiveType, IrPointerType, IrArrayType, IrStructType {

  /**
   * Returns a string representation of this type in the IR grammar format.
   *
   * @return the type string (e.g., "int32", "ptr<int32>", "array<int32,10>", "struct Name")
   */
  String toIrString();
}

