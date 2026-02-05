/**
 * FRISC code generation backend.
 *
 * <p>This package provides the entry point and shared types for translating the
 * PPJ IR into FRISC assembly. The public surface area is intentionally small:
 * {@link hr.fer.ppj.codegen.frisc.FriscCodeGenerator} orchestrates parsing,
 * lowering, and emission while preserving a stable output format.
 *
 * <p>Design goals:
 * <ul>
 *   <li>Deterministic, byte-for-byte stable assembly output.</li>
 *   <li>Clear separation between IR parsing, lowering, and emission.</li>
 *   <li>Strict adherence to the FRISC ABI, calling convention, and formatting rules.</li>
 * </ul>
 */
package hr.fer.ppj.codegen.frisc;
