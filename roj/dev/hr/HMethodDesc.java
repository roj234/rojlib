package roj.dev.hr;

import roj.mapper.util.Desc;

/**
 * @author Roj234
 * @since 2023/1/14 0014 1:54
 */
final class HMethodDesc extends Desc {
	char id;
	HMethod method;
	boolean inherited;

	HMethodDesc(String owner, String name, String param, int flags) {
		super(owner, name, param, flags);
	}
}
