package roj.dev.hr;

import roj.mapper.util.Desc;

/**
 * @author Roj234
 * @since 2023/1/14 0014 1:54
 */
final class HFieldDesc extends Desc {
	HField value;

	HFieldDesc(String owner, String name, String param, int flags) {
		super(owner, name, param, flags);
	}
}
