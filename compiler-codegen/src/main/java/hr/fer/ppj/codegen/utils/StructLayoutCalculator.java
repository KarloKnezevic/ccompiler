package hr.fer.ppj.codegen.utils;

import hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator;
import hr.fer.ppj.codegen.structs.StructSizeCalculator;
import hr.fer.ppj.codegen.types.TypeSizeCalculator;
import hr.fer.ppj.semantics.types.StructType;
import hr.fer.ppj.semantics.types.Type;
import java.util.Map;

/**
 * Legacy facade for struct layout calculation.
 * 
 * <p><b>DEPRECATED:</b> This class is maintained for backward compatibility.
 * New code should use the specialized classes directly:
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.structs.StructSizeCalculator} - for struct size calculation</li>
 *   <li>{@link hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator} - for field offset calculation</li>
 *   <li>{@link hr.fer.ppj.codegen.types.TypeSizeCalculator} - for general type size calculation</li>
 * </ul>
 * 
 * <p>This class delegates all method calls to the appropriate specialized classes
 * in the {@code structs} and {@code types} packages.
 * 
 * @deprecated Use {@link hr.fer.ppj.codegen.structs.StructSizeCalculator},
 *             {@link hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator}, and
 *             {@link hr.fer.ppj.codegen.types.TypeSizeCalculator} directly
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
@Deprecated
public final class StructLayoutCalculator {
    
    private StructLayoutCalculator() {
        throw new AssertionError("Utility class should not be instantiated");
    }
    
    /**
     * @deprecated Use {@link hr.fer.ppj.codegen.structs.StructSizeCalculator#calculateStructSize(StructType)}
     */
    @Deprecated
    public static int calculateStructSize(StructType structType) {
        return StructSizeCalculator.calculateStructSize(structType);
    }
    
    /**
     * @deprecated Use {@link hr.fer.ppj.codegen.structs.StructSizeCalculator#calculateStructSize(StructType, Map)}
     */
    @Deprecated
    public static int calculateStructSize(StructType structType, Map<String, Integer> arraySizes) {
        return StructSizeCalculator.calculateStructSize(structType, arraySizes);
    }
    
    /**
     * @deprecated Use {@link hr.fer.ppj.codegen.structs.StructSizeCalculator#calculateStructSize(StructType, Map, Map)}
     */
    @Deprecated
    public static int calculateStructSize(StructType structType, Map<String, Integer> arraySizes, 
                                         Map<String, Map<String, Integer>> nestedStructArraySizes) {
        return StructSizeCalculator.calculateStructSize(structType, arraySizes, nestedStructArraySizes);
    }
    
    /**
     * @deprecated Use {@link hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator#calculateFieldOffsets(StructType)}
     */
    @Deprecated
    public static Map<String, Integer> calculateFieldOffsets(StructType structType) {
        return StructFieldOffsetCalculator.calculateFieldOffsets(structType);
    }
    
    /**
     * @deprecated Use {@link hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator#calculateFieldOffsets(StructType, Map)}
     */
    @Deprecated
    public static Map<String, Integer> calculateFieldOffsets(StructType structType, Map<String, Integer> arraySizes) {
        return StructFieldOffsetCalculator.calculateFieldOffsets(structType, arraySizes);
    }
    
    /**
     * @deprecated Use {@link hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator#calculateFieldOffsets(StructType, Map, Map)}
     */
    @Deprecated
    public static Map<String, Integer> calculateFieldOffsets(StructType structType, Map<String, Integer> arraySizes, 
                                                              Map<String, Map<String, Integer>> nestedStructArraySizes) {
        return StructFieldOffsetCalculator.calculateFieldOffsets(structType, arraySizes, nestedStructArraySizes);
    }
    
    /**
     * @deprecated Use {@link hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator#getFieldOffset(StructType, String)}
     */
    @Deprecated
    public static Integer getFieldOffset(StructType structType, String fieldName) {
        return StructFieldOffsetCalculator.getFieldOffset(structType, fieldName);
    }
    
    /**
     * @deprecated Use {@link hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator#getFieldOffset(StructType, String, Map)}
     */
    @Deprecated
    public static Integer getFieldOffset(StructType structType, String fieldName, Map<String, Integer> arraySizes) {
        return StructFieldOffsetCalculator.getFieldOffset(structType, fieldName, arraySizes);
    }
    
    /**
     * @deprecated Use {@link hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator#getFieldOffset(StructType, String, Map, Map)}
     */
    @Deprecated
    public static Integer getFieldOffset(StructType structType, String fieldName, Map<String, Integer> arraySizes,
                                         Map<String, Map<String, Integer>> nestedStructArraySizes) {
        return StructFieldOffsetCalculator.getFieldOffset(structType, fieldName, arraySizes, nestedStructArraySizes);
    }
    
    /**
     * @deprecated Use {@link hr.fer.ppj.codegen.types.TypeSizeCalculator#calculateTypeSize(Type)}
     */
    @Deprecated
    public static int calculateTypeSize(Type type) {
        return TypeSizeCalculator.calculateTypeSize(type);
    }
    
    /**
     * @deprecated Use {@link hr.fer.ppj.codegen.types.TypeSizeCalculator#calculateTypeSize(Type, Map)}
     */
    @Deprecated
    public static int calculateTypeSize(Type type, Map<String, Map<String, Integer>> nestedStructArraySizes) {
        return TypeSizeCalculator.calculateTypeSize(type, nestedStructArraySizes);
    }
}
