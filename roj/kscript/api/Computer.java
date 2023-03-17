package roj.kscript.api;

import roj.kscript.type.KType;

import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/5/27 23:06
 */
@FunctionalInterface
public interface Computer {
	KType compute(List<KType> arguments);
}
