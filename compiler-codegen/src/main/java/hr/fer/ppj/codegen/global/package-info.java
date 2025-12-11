/**
 * Global variable declaration code generation utilities.
 *
 * <p>This package contains utilities for extracting information from parse trees related to global
 * variable declarations, including:
 *
 * <ul>
 *   <li>Initializer value extraction
 *   <li>Array size extraction
 *   <li>Parse tree traversal for declaration information
 * </ul>
 *
 * <p><b>Grammar Rules Handled:</b>
 *
 * <ul>
 *   <li>{@code <vanjska_deklaracija>} - External declarations
 *   <li>{@code <deklaracija>} - Variable declarations
 *   <li>{@code <inicijalizator>} - Initializers
 *   <li>{@code <lista_izraza_pridruzivanja>} - Initializer lists
 * </ul>
 *
 * <p><b>Key Classes:</b>
 *
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.global.InitializerExtractor} - Extracts initializer values
 *   <li>{@link hr.fer.ppj.codegen.global.ArraySizeExtractor} - Extracts array sizes
 * </ul>
 *
 * <p><b>FRISC Data Section:</b>
 *
 * <ul>
 *   <li>Initialized arrays: {@code DW %D value1, %D value2, ...}
 *   <li>Uninitialized arrays: {@code `DS %D size}
 *   <li>Element size: 4 bytes for both int and char arrays
 * </ul>
 *
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.global;
