package roj.asmx.launcher;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/8/4 16:03
 */
public interface ITweaker {
	void init(List<String> args, Bootstrap loader);
}