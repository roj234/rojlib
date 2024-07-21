package roj.plugins.ci;

import roj.collect.MyHashSet;
import roj.util.ByteList;

import java.io.File;
import java.util.List;
import java.util.Set;

/**
 * @author Roj234
 * @since 2024/6/24 17:24
 */
public interface Compiler {
	static Compiler getInstance(String type, String basePath) {
		Set<String> ignores = new MyHashSet<>();
		FMDMain.readTextList(ignores::add, "忽略的编译错误码");
		return new JCompiler(ignores, basePath);
	}

	void showErrorCode(boolean show);
	// TODO Lavac
	List<? extends Compiled> compile(List<String> options, List<File> files);

	interface Compiled {
		String getName();
		ByteList getData();
	}
}