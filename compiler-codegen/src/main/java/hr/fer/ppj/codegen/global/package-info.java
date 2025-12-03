/**
 * Global variable declaration code generation utilities.
 * 
 * <p>This package contains utilities for extracting information from parse trees
 * related to global variable declarations, including:
 * <ul>
 *   <li>Initializer value extraction</li>
 *   <li>Array size extraction</li>
 *   <li>Parse tree traversal for declaration information</li>
 * </ul>
 * 
 * <p><b>Grammar Rules Handled:</b>
 * <ul>
 *   <li>{@code <vanjska_deklaracija>} - External declarations</li>
 *   <li>{@code <deklaracija>} - Variable declarations</li>
 *   <li>{@code <inicijalizator>} - Initializers</li>
 *   <li>{@code <lista_izraza_pridruzivanja>} - Initializer lists</li>
 * </ul>
 * 
 * <p><b>Key Classes:</b>
 * <ul>
 *   <li>{@link hr.fer.ppj.codegen.global.InitializerExtractor} - Extracts initializer values</li>
 *   <li>{@link hr.fer.ppj.codegen.global.ArraySizeExtractor} - Extracts array sizes</li>
 * </ul>
 * 
 * <p><b>FRISC Data Section:</b>
 * <ul>
 *   <li>Initialized arrays: {@code DW %D value1, %D value2, ...}</li>
 *   <li>Uninitialized arrays: {@code `DS %D size}</li>
 *   <li>Element size: 4 bytes for both int and char arrays</li>
 * </ul>
 * 
 * @author <a href="https://karloknezevic.github.io/">Karlo Knežević</a>
 */
package hr.fer.ppj.codegen.global;

