import *

// 封闭代数类型 (不能'创建'实例)
sealed enum ErrorCode(int) {
	OK(0),
	ERROR(-1);

	boolean isOK() { return this == OK; }
}

// 开放代数类型
enum MyInt(int) {
	ZERO(0);

	static MyInt plus(MyInt self, MyInt other) {
		return self + 1;
	}

	int to_int() {
		return this;
	}
}

public class Test {
	public static void main(String[] args) {
		ErrorCode x = null;
		x = 0;
		x = ErrorCode.OK;

		switch(x) {
			case OK -> {
				System.out.println("OK");
			}
			case ERROR -> {
				System.out.println("ERROR");
			}
		}
		
		MyInt adt = 1;
		adt = new MyInt(1);
		adt = adt.plus(1);
		int val = adt.to_int();
		System.out.println(val);
	}
}
