package roj.math;

/**
 * 四则运算和函数
 * @author Roj234
 * @since 2024/12/23 0023 2:30
 */
public class Calculator {
	private final String str;
	private int pos;
	private char ch;

	public Calculator(String str) {this.str = str;ch = str.charAt(0);}

	private void next() {ch = pos < str.length() - 1 ? str.charAt(++pos) : 0;}
	private boolean nextIf(int c) {
		while (ch == ' ') next();
		if (ch == c) {next();return true;}
		return false;
	}

	private double parseL1() {
		double x = parseL2();
		while (true) {
			if (nextIf('+')) {
				x += parseL2();
			} else if (nextIf('-')) {
				x -= parseL2();
			} else {
				break;
			}
		}
		return x;
	}

	private double parseL2() {
		double x = parseNumber();
		while (true) {
			if (nextIf('*')) {
				x *= parseNumber();
			} else if (nextIf('/')) {
				x /= parseNumber();
			} else {
				break;
			}
		}
		return x;
	}

	private double parseNumber() {
		nextIf('+');
		if (nextIf('-')) return -parseNumber();

		int startPos = pos;
		double x;
		if (nextIf('(')) {
			x = parseL1();
			nextIf(')');
		} else if ((ch >= '0' && ch <= '9') || ch == '.') {
			while ((ch >= '0' && ch <= '9') || ch == '.') {
				next();
			}
			x = Double.parseDouble(str.substring(startPos, pos));
		} else if (ch >= 'a' && ch <= 'z') {
			while (ch >= 'a' && ch <= 'z') {
				next();
			}

			String func = str.substring(startPos, pos);
			if (!nextIf('(')) throw new RuntimeException("missing (");
			if (nextIf(')')) {
				x = switch (func) {
					case "random" -> Math.random();
					default -> throw new RuntimeException("Unknown function: " + func);
				};
			} else {
				x = parseL1();
				if (nextIf(')')) {
					x = switch (func) {
						case "sin" -> Math.sin(x);
						case "cos" -> Math.cos(x);
						case "tan" -> Math.tan(x);
						case "abs" -> Math.abs(x);
						case "exp" -> Math.exp(x);
						case "log" -> Math.log(x);
						default -> throw new RuntimeException("Unknown function: " + func);
					};
				} else {
					if (!nextIf(',')) throw new RuntimeException("missing ,");
					var y = parseL1();
					nextIf(')');
					x = switch (func) {
						case "max" -> Math.max(x, y);
						case "min" -> Math.min(x, y);
						case "pow" -> Math.pow(x, y);
						default -> throw new RuntimeException("Unknown function: " + func);
					};
				}
			}
		} else {
			throw new RuntimeException("Unexpected: "+ch);
		}

		return x;
	}

	public double eval() {
		double x = parseL1();
		if (pos != str.length()-1) throw new RuntimeException("Unexpected: "+ch);
		return x;
	}

	public static void main(final String[] args) {
		System.out.println(new Calculator(args[0]).eval());
	}
}
