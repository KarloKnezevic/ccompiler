package hr.fer.ppj.codegen.frisc.frame;

import java.util.List;

/**
 * Parameter layout information for a single function.
 *
 * @param params ordered parameter metadata
 * @param totalSize total byte size of the parameter area
 */
public record ParamLayout(List<ParamInfo> params, int totalSize) {
}
