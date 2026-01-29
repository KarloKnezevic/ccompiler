package hr.fer.ppj.ir.lowering;

import hr.fer.ppj.ir.build.TypeMapper;
import hr.fer.ppj.ir.model.IrProgram;
import hr.fer.ppj.ir.model.IrStructDef;
import hr.fer.ppj.ir.types.IrType;
import hr.fer.ppj.ir.build.TypeAlignmentCalculator;
import hr.fer.ppj.ir.build.TypeSizeCalculator;
import hr.fer.ppj.semantics.tree.NonTerminalNode;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import java.util.Objects;

/**
 * Generates IR for struct type definitions.
 *
 * <p>This generator handles:
 * <ul>
 *   <li>Struct type definitions</li>
 *   <li>Struct field layout and offset calculation</li>
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
public final class StructGenerator {

  private final IrProgram.Builder programBuilder;
  private final hr.fer.ppj.ir.build.StructNameRegistry structNameRegistry;
  private final java.util.Set<String> emittedStructs = new java.util.HashSet<>();

  public StructGenerator(IrProgram.Builder programBuilder, hr.fer.ppj.ir.build.StructNameRegistry structNameRegistry) {
    this.programBuilder = Objects.requireNonNull(programBuilder, "programBuilder must not be null");
    this.structNameRegistry = Objects.requireNonNull(structNameRegistry, "structNameRegistry must not be null");
  }

  /**
   * Generates a struct definition from a struct specifier node.
   *
   * <p>Extracts struct type information from the semantic attributes and generates
   * an IR struct definition with field offsets.
   *
   * @param node the struct specifier node (must have StructType in attributes)
   */
  public void generateStructDefinition(NonTerminalNode node) {
    Objects.requireNonNull(node, "node must not be null");
    
    // Extract struct type from semantic attributes
    Type type = node.attributes().type();
    if (!(type instanceof StructType structType)) {
      // Not a struct type - nothing to generate
      return;
    }
    
    // Get struct name from registry (handles both tagged and anonymous)
    String structName = structNameRegistry.getStructName(structType.tag(), structType);
    
    // Check if struct is already emitted
    if (emittedStructs.contains(structName)) {
      // Already emitted - skip
      return;
    }
    
    // Only generate IR definitions for structs that need them
    // (Tagged structs always need definitions; anonymous structs only if used)
    // For now, we generate for all structs that are encountered
    emittedStructs.add(structName);
    
    // Build struct definition with field offsets
    IrStructDef.Builder structBuilder = IrStructDef.builder(structName);
    
    int currentOffset = 0;
    for (var entry : structType.fields().entrySet()) {
      String fieldName = entry.getKey();
      Type fieldType = entry.getValue();
      
      // Convert to IR type (using registry for struct types)
      IrType irFieldType = TypeMapper.toIrType(fieldType, structNameRegistry);
      
      // Calculate alignment for this field
      int fieldAlignment = TypeAlignmentCalculator.getTypeAlignment(irFieldType);
      
      // Align current offset to field alignment
      currentOffset = (currentOffset + fieldAlignment - 1) / fieldAlignment * fieldAlignment;
      
      // Add field with calculated offset
      structBuilder.addField(fieldName, irFieldType, currentOffset);
      
      // Calculate field size and advance offset
      int fieldSize = TypeSizeCalculator.getTypeSize(irFieldType);
      currentOffset += fieldSize;
    }
    
    // Add struct definition to program
    programBuilder.addStructDef(structBuilder.build());
  }
}
