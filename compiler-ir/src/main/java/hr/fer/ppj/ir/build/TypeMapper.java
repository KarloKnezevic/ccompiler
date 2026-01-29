package hr.fer.ppj.ir.build;

import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.ConstType;
import hr.fer.ppj.semantics.types.FunctionType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.PrimitiveType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.List;

/**
 * Maps semantic types to IR types.
 *
 * <p>Type mapping rules:
 * <ul>
 *   <li>int -> int32</li>
 *   <li>char -> char (signed char)</li>
 *   <li>float -> float</li>
 *   <li>void -> not representable in IR (only used for function return types)</li>
 *   <li>pointers -> ptr&lt;T&gt;</li>
 *   <li>arrays -> array&lt;T,N&gt;</li>
 *   <li>structs -> struct Name</li>
 * </ul>
 *
 * <p>Note: const qualification is stripped during IR generation.
 * The IR does not track const-ness.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class TypeMapper {

  private TypeMapper() {}

  /**
   * Converts a semantic type to an IR type.
   *
   * <p>Strips const qualification and maps primitives, pointers, arrays, and structs.
   * Function types are not directly mappable (they're used for function signatures).
   *
   * @param semanticType the semantic type to convert
   * @return the corresponding IR type
   * @throws IllegalArgumentException if the type cannot be mapped (e.g., void, function)
   */
  public static IrType toIrType(Type semanticType) {
    if (semanticType == null) {
      throw new IllegalArgumentException("Type cannot be null");
    }

    // Strip const qualification
    Type baseType = TypeSystem.stripConst(semanticType);

    return switch (baseType) {
      case PrimitiveType primitive -> mapPrimitive(primitive);
      case PointerType ptr -> new IrPointerType(toIrType(ptr.baseType()));
      case ArrayType arr -> {
        // Use dimensions from semantic ArrayType
        // For multi-dimensional arrays, use the outermost dimension
        // IR array type is single-dimensional, so nested arrays become nested IR array types
        if (arr.dimensions().isEmpty()) {
          // Unsized array - use a default size of 0 (will need special handling)
          // For now, throw an error as IR doesn't support unsized arrays
          throw new IllegalArgumentException("Unsized arrays are not supported in IR");
        }
        int size = arr.dimensions().get(0);
        yield new IrArrayType(toIrType(arr.elementType()), size);
      }
      case StructType struct -> {
        // For struct types, we need a StructNameRegistry to get deterministic names
        // This method is called without registry - use fallback for now
        // Callers should use toIrType(Type, StructNameRegistry) for struct types
        String structName = struct.tag() != null ? struct.tag() : "Anonymous";
        yield new IrStructType(structName);
      }
      case FunctionType func -> throw new IllegalArgumentException(
          "Function types cannot be converted to IR types directly");
      case ConstType ignored -> throw new IllegalStateException(
          "ConstType should have been stripped");
    };
  }

  /**
   * Converts a semantic type to an IR type using a struct name registry.
   *
   * <p>This version should be used when converting struct types to ensure
   * deterministic naming for anonymous structs.
   *
   * @param semanticType the semantic type to convert
   * @param structNameRegistry the registry for struct names
   * @return the corresponding IR type
   */
  public static IrType toIrType(Type semanticType, StructNameRegistry structNameRegistry) {
    if (semanticType == null) {
      throw new IllegalArgumentException("Type cannot be null");
    }

    // Strip const qualification
    Type baseType = TypeSystem.stripConst(semanticType);

    return switch (baseType) {
      case PrimitiveType primitive -> mapPrimitive(primitive);
      case PointerType ptr -> new IrPointerType(toIrType(ptr.baseType(), structNameRegistry));
      case ArrayType arr -> {
        if (arr.dimensions().isEmpty()) {
          throw new IllegalArgumentException("Unsized arrays are not supported in IR");
        }
        int size = arr.dimensions().get(0);
        yield new IrArrayType(toIrType(arr.elementType(), structNameRegistry), size);
      }
      case StructType struct -> {
        String structName = structNameRegistry.getStructName(struct.tag(), struct);
        yield new IrStructType(structName);
      }
      case FunctionType func -> throw new IllegalArgumentException(
          "Function types cannot be converted to IR types directly");
      case ConstType ignored -> throw new IllegalStateException(
          "ConstType should have been stripped");
    };
  }

  /**
   * Converts a semantic type to an IR type, handling arrays with known sizes.
   *
   * <p>This version is used when we have array size information from the semantic tree.
   *
   * @param semanticType the semantic type to convert
   * @param arraySize the array size (for array types), or -1 if unknown
   * @return the corresponding IR type
   */
  public static IrType toIrType(Type semanticType, int arraySize) {
    if (semanticType == null) {
      throw new IllegalArgumentException("Type cannot be null");
    }

    Type baseType = TypeSystem.stripConst(semanticType);

    if (baseType instanceof ArrayType arr) {
      return new IrArrayType(toIrType(arr.elementType()), arraySize);
    }

    return toIrType(semanticType);
  }

  private static IrType mapPrimitive(PrimitiveType primitive) {
    return switch (primitive) {
      case INT -> IrPrimitiveType.INT32;
      case CHAR -> IrPrimitiveType.CHAR;
      case FLOAT -> IrPrimitiveType.FLOAT;
      case VOID -> throw new IllegalArgumentException(
          "void type cannot be converted to IR type");
    };
  }

  /**
   * Gets the size of a type in bytes for layout calculations.
   *
   * @param irType the IR type
   * @return the size in bytes
   * @throws UnsupportedOperationException for struct types (use overloaded version)
   */
  public static int getTypeSize(IrType irType) {
    return TypeSizeCalculator.getTypeSize(irType);
  }

  /**
   * Gets the size of a type in bytes, with struct size provided.
   *
   * @param irType the IR type
   * @param structSize the struct size in bytes (only used for struct types)
   * @return the size in bytes
   */
  public static int getTypeSize(IrType irType, int structSize) {
    return TypeSizeCalculator.getTypeSize(irType, structSize);
  }

  /**
   * Gets the alignment requirement for a type.
   *
   * @param irType the IR type
   * @return the alignment in bytes
   * @throws UnsupportedOperationException for struct types (use overloaded version)
   */
  public static int getTypeAlignment(IrType irType) {
    return TypeAlignmentCalculator.getTypeAlignment(irType);
  }

  /**
   * Gets the alignment requirement for a type, with struct alignment provided.
   *
   * @param irType the IR type
   * @param structAlignment the struct alignment in bytes (only used for struct types)
   * @return the alignment in bytes
   */
  public static int getTypeAlignment(IrType irType, int structAlignment) {
    return TypeAlignmentCalculator.getTypeAlignment(irType, structAlignment);
  }
}

