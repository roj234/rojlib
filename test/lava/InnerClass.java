import *

public class Test {
	class OutsideRef {
		int f1 = 1;
	}
	class Inherit extends OutsideRef {
		int f2 = 2;

		public Inherit() {
			super();
		}
	}

	static void println(String x) {System.out.println(x);}

	public static void main(String[] args) {
    var ref = new Inherit();
		println(Lavac.getCompileTime());
	}
}
