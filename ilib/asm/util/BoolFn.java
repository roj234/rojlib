package ilib.asm.util;

import roj.util.ByteList;

/**
 * @author solo6975
 * @since 2022/3/29 22:23
 */
@FunctionalInterface
public interface BoolFn {
	boolean accept(ByteList data);

	default BoolFn andThen(BoolFn other) {
		return (data) -> accept(data) & other.accept(data);
	}
}
