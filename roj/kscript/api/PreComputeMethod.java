package roj.kscript.api;

import roj.kscript.type.KType;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2020/10/14 22:45
 */
@FunctionalInterface
public interface PreComputeMethod {
	PreComputeMethod NULL = args -> null;

	KType handle(List<KType> args);
}
