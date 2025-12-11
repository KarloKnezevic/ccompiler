/**
 * Type system utilities for code generation.
 *
 * <p>This package contains utilities for working with types during code generation, including type
 * extraction, type conversion, type size calculation, and type-related helper functions.
 *
 * <p><b>Key Responsibilities:</b>
 *
 * <ul>
 *   <li><b>Type Extraction:</b> Extract type information from AST nodes ({@link
 *       hr.fer.ppj.codegen.types.TypeExtractor TypeExtractor}, {@link
 *       hr.fer.ppj.codegen.types.TypeNodeExtractor TypeNodeExtractor})
 *   <li><b>Type Conversion:</b> Handle type conversions and casts ({@link
 *       hr.fer.ppj.codegen.types.TypeConverter TypeConverter})
 *   <li><b>Type Size:</b> Calculate sizes of types ({@link
 *       hr.fer.ppj.codegen.types.TypeSizeCalculator TypeSizeCalculator}) - Delegates to {@link
 *       hr.fer.ppj.codegen.structs.StructSizeCalculator StructSizeCalculator} for struct types
 * </ul>
 *
 * <p><b>Struct Type Support:</b>
 *
 * <p>This package works seamlessly with struct types:
 *
 * <ul>
 *   <li><b>Type Extraction:</b> {@link hr.fer.ppj.codegen.types.TypeExtractor TypeExtractor}
 *       extracts {@link hr.fer.ppj.semantics.types.StructType StructType} from struct expressions
 *   <li><b>Type Size:</b> {@link hr.fer.ppj.codegen.types.TypeSizeCalculator TypeSizeCalculator}
 *       delegates struct size calculation to {@link hr.fer.ppj.codegen.structs.StructSizeCalculator
 *       StructSizeCalculator}, which handles nested structs and arrays within structs
 * </ul>
 *
 * <p><b>Package Dependencies:</b>
 *
 * <ul>
 *   <li>Depends on {@link hr.fer.ppj.codegen.structs structs} package for struct size calculation
 *   <li>No dependencies on other codegen packages (self-contained utilities)
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.types;
