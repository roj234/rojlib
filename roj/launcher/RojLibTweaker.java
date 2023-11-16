package roj.launcher;

import roj.collect.SimpleList;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/8/4 0004 17:06
 */
public class RojLibTweaker implements ITweaker {
	String[] args1;

	@Override
	public String[] initialize(String[] args, ClassWrapper loader) {
		args1 = args;
		loader.registerTransformer(new ReflectionHook());
		return args;
	}

	@Override
	public void addArguments(List<String> args) {
		((SimpleList<String>)args).addAll(args1);
	}
}
