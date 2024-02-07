package roj.asmx.launcher;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/8/4 0004 16:03
 */
public interface ITweaker {
	String[] initialize(String[] args, ClassWrapper loader);
	default void addArguments(List<String> args) {}
}