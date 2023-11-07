package roj.io;

public class CorruptedInputException extends java.io.IOException {
	private static final long serialVersionUID = 3L;

	public CorruptedInputException() { super("data is corrupt"); }
	public CorruptedInputException(String s) { super(s); }
}
