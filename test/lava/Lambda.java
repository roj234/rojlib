import *

public class Test {
	static int plus(int a, int b) { return a + b; }

	public static void main(String[] args) {
		BiFunction<Integer, Integer, Integer> a;
		a = Test::plus;
		System.out.println(a.apply(1, 2) == (Integer) 3);
		int arg = 1;
		a = (x, y) -> plus(x + arg, y);
		System.out.println(a.apply(1, 2) == 4);
	}
}
