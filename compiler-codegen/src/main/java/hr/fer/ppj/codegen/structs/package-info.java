/**
 * Struct type support for code generation.
 * 
 * <p>This package contains all code related to struct type handling in code generation,
 * including struct layout calculation, field offset computation, array size extraction,
 * and struct memory access code generation.
 * 
 * <p><b>Key Responsibilities:</b>
 * <ul>
 *   <li><b>Struct Layout:</b> Calculate struct sizes and field offsets according to
 *       the FRISC memory layout rules (tightly packed, no padding)</li>
 *   <li><b>Array Size Extraction:</b> Extract array sizes for struct fields from the
 *       parse tree (needed because ArrayType doesn't preserve size information)</li>
 *   <li><b>Struct Access:</b> Generate code for accessing struct fields and nested
 *       structs, including arrays within structs</li>
 *   <li><b>Struct Passing:</b> Handle struct arguments and return values according
 *       to the FRISC calling convention</li>
 * </ul>
 * 
 * <p><b>Struct Memory Layout:</b>
 * 
 * <p>Structs in FRISC are laid out with the following rules:
 * <ul>
 *   <li><b>Tightly Packed:</b> Fields are laid out back-to-back with no padding</li>
 *   <li><b>Declaration Order:</b> Fields appear in memory in the order they are declared</li>
 *   <li><b>Nested Structs:</b> Nested structs are laid out inline (no special handling)</li>
 *   <li><b>Arrays:</b> Arrays are contiguous sequences of elements</li>
 * </ul>
 * 
 * <p><b>Struct Calling Convention:</b>
 * 
 * <p>When structs are passed as function arguments or returned from functions:
 * <ul>
 *   <li><b>Struct Arguments:</b> Passed by value, copied word-by-word onto the stack</li>
 *   <li><b>Struct Returns:</b> Returned via a hidden pointer parameter in R2 (caller
 *       allocates space and passes address in R2 before CALL)</li>
 * </ul>
 * 
 * <p><b>Key Classes:</b>
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.structs.StructSizeCalculator} - Struct size calculation</li>
 *   <li>{@link hr.fer.ppj.codegen.structs.StructFieldOffsetCalculator} - Field offset calculation</li>
 *   <li>{@link hr.fer.ppj.codegen.structs.StructArraySizeExtractor} - Array size extraction from parse tree</li>
 *   <li>{@link hr.fer.ppj.codegen.structs.StructFieldAddressGenerator} - Field address code generation</li>
 *   <li>{@link hr.fer.ppj.codegen.structs.NestedStructArraySizeExtractor} - Nested struct array size extraction</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.structs;
