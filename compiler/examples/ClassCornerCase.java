
public class Test {
	public static void main(String[] args) {
		System.out.println(Inaccessible.sb.length());
		System.out.println(Accessible.sb.length());
		Accessible.sb type = null;
		System.out.println(type.length());
		Inaccessible.sb type2 = null;
		System.out.println(type2.length());
	}
}

class Inaccessible {
	private static StringBuilder sb = new StringBuilder();
	public static class sb {
		public static int length() { return 1; }
	}
}
class Accessible {
	public static StringBuilder sb = new StringBuilder();
	public static class sb {
		public static int length() { return 1; }
	}
}