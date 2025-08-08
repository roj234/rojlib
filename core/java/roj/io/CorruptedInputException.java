package roj.io;

public class CorruptedInputException extends java.io.IOException {
	public CorruptedInputException() { super("data is corrupt"); }
	public CorruptedInputException(String s) { super(s); }
	public CorruptedInputException(String s, Exception e) { super(s,e); }
}