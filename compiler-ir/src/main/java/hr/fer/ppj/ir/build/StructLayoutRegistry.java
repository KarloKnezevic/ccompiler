package hr.fer.ppj.ir.build;

import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrStructDef;
import hr.fer.ppj.ir.types.IrArrayType;
import hr.fer.ppj.ir.types.IrPointerType;
import hr.fer.ppj.ir.types.IrPrimitiveType;
import hr.fer.ppj.ir.types.IrStructType;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.semantics.types.ArrayType;
import hr.fer.ppj.semantics.types.PointerType;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import hr.fer.ppj.semantics.types.TypeSystem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Registry for struct layout information (sizes and alignments).
 *
 * <p>This registry stores computed struct sizes and provides methods to query them.
 * It handles nested struct dependencies by computing sizes lazily.
 * It also handles emitting struct definitions to the IR program.
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StructLayoutRegistry {

  private final Map<String, Integer> structSizes = new HashMap<>();
  private final Map<String, Integer> structAlignments = new HashMap<>();
  private final Map<String, StructType> structTypes = new HashMap<>();
  private final Set<String> emittedStructs = new HashSet<>();
  private final StructNameRegistry structNameRegistry;
  private IrProgram.Builder programBuilder;

  public StructLayoutRegistry(StructNameRegistry structNameRegistry) {
    this.structNameRegistry =
        Objects.requireNonNull(structNameRegistry, "structNameRegistry must not be null");
  }

  /**
   * Gets the struct name registry.
   *
   * @return the struct name registry
   */
  public StructNameRegistry getStructNameRegistry() {
    return structNameRegistry;
  }

  /**
   * Sets the program builder for emitting struct definitions.
   *
   * @param programBuilder the program builder
   */
  public void setProgramBuilder(IrProgram.Builder programBuilder) {
    this.programBuilder = programBuilder;
  }

  /**
   * Registers a struct's size and alignment.
   *
   * @param structName the struct name
   * @param size the struct size in bytes
   * @param alignment the struct alignment in bytes
   */
  public void registerStruct(String structName, int size, int alignment) {
    Objects.requireNonNull(structName, "structName must not be null");
    if (size <= 0) {
      throw new IllegalArgumentException("Struct size must be positive: " + size);
    }
    if (alignment <= 0) {
      throw new IllegalArgumentException("Struct alignment must be positive: " + alignment);
    }
    structSizes.put(structName, size);
    structAlignments.put(structName, alignment);
  }

  /**
   * Gets the size of a struct by name.
   *
   * @param structName the struct name
   * @return the size in bytes
   * @throws IllegalArgumentException if the struct is not registered
   */
  public int getStructSize(String structName) {
    Objects.requireNonNull(structName, "structName must not be null");
    Integer size = structSizes.get(structName);
    if (size == null) {
      throw new IllegalArgumentException("Struct not registered: " + structName);
    }
    return size;
  }

  /**
   * Gets the alignment of a struct by name.
   *
   * @param structName the struct name
   * @return the alignment in bytes
   * @throws IllegalArgumentException if the struct is not registered
   */
  public int getStructAlignment(String structName) {
    Objects.requireNonNull(structName, "structName must not be null");
    Integer alignment = structAlignments.get(structName);
    if (alignment == null) {
      throw new IllegalArgumentException("Struct not registered: " + structName);
    }
    return alignment;
  }

  /**
   * Checks if a struct is registered.
   *
   * @param structName the struct name
   * @return true if the struct is registered
   */
  public boolean isRegistered(String structName) {
    return structSizes.containsKey(structName);
  }

  /**
   * Gets the size of an IR type, using this registry for struct types.
   *
   * @param irType the IR type
   * @return the size in bytes
   */
  public int getTypeSize(IrType irType) {
    Objects.requireNonNull(irType, "irType must not be null");

    return switch (irType) {
      case IrPrimitiveType prim -> switch (prim) {
        case INT32, FLOAT, BOOL -> 4;
        case CHAR, UCHAR -> 1;
      };
      case IrPointerType ptr -> 4;
      case IrArrayType arr -> {
        if (arr.size() < 0) {
          throw new IllegalArgumentException("Array size must be known for size calculation");
        }
        yield arr.size() * getTypeSize(arr.elementType());
      }
      case IrStructType struct -> getStructSize(struct.name());
    };
  }

  /**
   * Gets the alignment of an IR type, using this registry for struct types.
   *
   * @param irType the IR type
   * @return the alignment in bytes
   */
  public int getTypeAlignment(IrType irType) {
    Objects.requireNonNull(irType, "irType must not be null");

    return switch (irType) {
      case IrPrimitiveType prim -> switch (prim) {
        case INT32, FLOAT, BOOL -> 4;
        case CHAR, UCHAR -> 1;
      };
      case IrPointerType ptr -> 4;
      case IrArrayType arr -> getTypeAlignment(arr.elementType());
      case IrStructType struct -> getStructAlignment(struct.name());
    };
  }

  /**
   * Computes and registers the layout of a semantic struct type.
   *
   * @param structType the semantic struct type
   * @return the struct name
   */
  public String computeAndRegisterLayout(StructType structType) {
    Objects.requireNonNull(structType, "structType must not be null");

    String structName = structNameRegistry.getStructName(structType.tag(), structType);

    // Skip if already registered
    if (isRegistered(structName)) {
      // Still need to emit if not yet emitted
      emitStructDefinition(structName);
      return structName;
    }

    // Store the struct type for later emission
    structTypes.put(structName, structType);

    // Compute layout
    int currentOffset = 0;
    int maxAlignment = 1;

    for (var entry : structType.fields().entrySet()) {
      Type fieldType = entry.getValue();
      Type strippedFieldType = TypeSystem.stripConst(fieldType);

      // Handle nested structs first
      if (strippedFieldType instanceof StructType nestedStruct) {
        computeAndRegisterLayout(nestedStruct);
      }

      IrType irFieldType = TypeMapper.toIrType(fieldType, structNameRegistry);
      int fieldAlignment = getTypeAlignment(irFieldType);
      int fieldSize = getTypeSize(irFieldType);

      // Align current offset
      currentOffset = (currentOffset + fieldAlignment - 1) / fieldAlignment * fieldAlignment;
      currentOffset += fieldSize;

      // Track max alignment
      if (fieldAlignment > maxAlignment) {
        maxAlignment = fieldAlignment;
      }
    }

    // Final struct size is aligned to max alignment
    int structSize = (currentOffset + maxAlignment - 1) / maxAlignment * maxAlignment;
    if (structSize == 0) {
      structSize = 1; // Empty structs have size 1
    }

    registerStruct(structName, structSize, maxAlignment);

    // Emit the struct definition
    emitStructDefinition(structName);

    return structName;
  }

  /**
   * Emits the struct definition to the program builder if not already emitted.
   *
   * @param structName the struct name
   */
  private void emitStructDefinition(String structName) {
    if (programBuilder == null || emittedStructs.contains(structName)) {
      return;
    }

    StructType structType = structTypes.get(structName);
    if (structType == null) {
      return;
    }

    emittedStructs.add(structName);

    // Emit nested struct definitions first
    for (var entry : structType.fields().entrySet()) {
      Type fieldType = TypeSystem.stripConst(entry.getValue());
      if (fieldType instanceof StructType nestedStruct) {
        String nestedName = structNameRegistry.getStructName(nestedStruct.tag(), nestedStruct);
        emitStructDefinition(nestedName);
      }
    }

    // Build struct definition with field offsets
    IrStructDef.Builder structBuilder = IrStructDef.builder(structName);

    int currentOffset = 0;
    for (var entry : structType.fields().entrySet()) {
      String fieldName = entry.getKey();
      Type fieldType = entry.getValue();

      IrType irFieldType = TypeMapper.toIrType(fieldType, structNameRegistry);
      int fieldAlignment = getTypeAlignment(irFieldType);

      currentOffset = (currentOffset + fieldAlignment - 1) / fieldAlignment * fieldAlignment;
      structBuilder.addField(fieldName, irFieldType, currentOffset);

      int fieldSize = getTypeSize(irFieldType);
      currentOffset += fieldSize;
    }

    programBuilder.addStructDef(structBuilder.build());
  }

  /**
   * Ensures a struct type is fully registered and its definition emitted.
   *
   * @param structType the struct type
   */
  public void ensureStructReady(StructType structType) {
    computeAndRegisterLayout(structType);
  }
}
