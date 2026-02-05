package hr.fer.ppj.codegen.frisc.frame;

import hr.fer.ppj.ir.types.IrType;

/**
 * Metadata for a single parameter slot.
 *
 * @param type parameter type
 * @param offset byte offset within the parameter area
 */
public record ParamInfo(IrType type, int offset) {
}
