package roj.plugins.ci.plugin;

import roj.asmx.Context;
import roj.collect.SimpleList;
import roj.config.data.CEntry;
import roj.plugins.ci.Compiler;

import java.io.File;
import java.util.List;

/**
 * @author Roj234
 * @since 2023/1/19 8:16
 */
public interface Processor {
	String name();
	default void init(CEntry config) {}
	default int beforeCompile(Compiler compiler, SimpleList<String> options, List<File> files, ProcessEnvironment pc) {return 2;}
	List<Context> process(List<Context> classes, ProcessEnvironment ctx);
}