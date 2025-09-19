import *

public class Test {
	class OutsideRef {
		int f1 = 1;
	}
	class Inherit extends OutsideRef {
		int f2 = 2;

		public Inherit() {
			System.out.println("statement before init??");
			int f2 = 111;
			if (f2 == 114) super();
			else super();
			System.out.println("statement after init??");
		}

		{
			System.out.println("globalInit??");
		}
	}

	static void println(String x) {System.out.println(x);}

	public static void main(String[] args) {
    var ref = new Test().new Inherit();
		println(Lavac.getCompileTime());
	}
}
