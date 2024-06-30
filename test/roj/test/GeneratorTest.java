package roj.test;

import roj.compiler.runtime.Generator;

/**
 * @author Roj234
 * @since 2024/6/9 0009 21:42
 */
public class GeneratorTest {
	public static Generator<String> generatorTest() {
		yield "a";
		yield "b";
		yield "c";
	}

	public static void main(String[] args) {
		Generator<String> generator = generatorTest();
		while (generator.hasNext()) {
			String next = generator.next();
			System.out.println(next);
		}
	}
}