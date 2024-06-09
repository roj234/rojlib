package roj.test;

import roj.compiler.runtime.Generator;
import roj.compiler.runtime.ReturnStack;

/**
 * @author Roj234
 * @since 2024/6/9 0009 21:42
 */
public class GeneratorTest {
	public static Generator<String> generatorTest() {
		return new Generator<>() {
			public void invoke() {
				ReturnStack<?> stack = __pos();
				switch (stack.getI()) {
					case 0:
						__yield(1).put("a");
						return;
					case 1:
						__yield(2).put("b");
						return;
					case 2:
						__yield(-1).put("c");
						return;
				}
			}
		};
	}

	public static void main(String[] args) {
		Generator<String> generator = generatorTest();
		while (generator.hasNext()) {
			String next = generator.next();
			System.out.println(next);
		}
	}
}