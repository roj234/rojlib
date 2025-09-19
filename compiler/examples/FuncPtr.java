import *

public class Test {
   static int plus(int x, int y) {
      return x + y;
   }

	public static void main(String[] args) {
      F<int, int, int> lambda = Test::plus;
      System.out.println(lambda(1, 2));
	}
}
