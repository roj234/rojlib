package roj.ci.plugin;

import roj.collect.ArrayList;
import roj.config.data.CEntry;

import java.io.File;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/1/19 8:16
 */
public interface Processor {
	String name();
	default void init(CEntry config) {}
	default int beforeCompile(ArrayList<String> options, List<File> sources, ProcessEnvironment ctx) {return 2;}
	default void afterCompile(ProcessEnvironment ctx) {}

	default boolean defaultEnabled() {return false;}
}