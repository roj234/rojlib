package roj.config;

import roj.math.MathUtils;

import java.io.PrintStream;
import java.io.PrintWriter;

/**
 * Signals that an error has been reached unexpectedly
 * while parsing.
 *
 * @see Exception
 */
public final class ParseException extends Exception {
    private static final long serialVersionUID = 2703218443322787634L;
    private static final boolean DEBUG = true;

    /**
     * The zero-based character offset into the string being parsed at which
     * the error was found during parsing.
     */
    private final int textOff, line, lineOffset;

    /**
     * Constructs a ParseException with the specified detail message and
     * offset.
     * A detail message is a String that describes this particular exception.
     *
     * @param s       the detail message
     * @param textOff the position where the error is found while parsing.
     */
    public ParseException(String s, int textOff, int line, int lineOffset) {
        super(s);
        this.textOff = textOff;
        this.line = line;
        this.lineOffset = lineOffset;
    }

    public ParseException(String s, int textOff, int line, int lineOffset, Throwable cause) {
        super(s, cause, true, DEBUG);
        this.textOff = textOff;
        this.line = line;
        this.lineOffset = lineOffset;
    }

    public ParseException(Throwable e) {
        super(null, e, true, false);
        this.textOff = this.line = this.lineOffset = -2;
    }

    public ParseException(int textOff, int line, int lineOffset, String s, ParseException e) {
        super(s, e, true, false);
        this.textOff = textOff;
        this.line = line;
        this.lineOffset = lineOffset;
    }

    public int getTextOff() {
        return textOff;
    }

    public int getLine() {
        return line;
    }

    public int getLineOffset() {
        return lineOffset;
    }

    @Override
    public void printStackTrace() {
        //if ((getCause() instanceof ParseException)) {
            Exception e = new Exception(toString());
            e.setStackTrace(getStackTrace());
            e.printStackTrace();
        //} else {
        //    super.printStackTrace();
        //}
    }

    @Override
    public void printStackTrace(PrintStream s) {
        //if ((getCause() instanceof ParseException)) {
            Exception e = new Exception(toString());
            e.setStackTrace(getStackTrace());
            e.printStackTrace(s);
        //} else {
        //    super.printStackTrace(s);
        //}
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        //if ((getCause() instanceof ParseException)) {
            Exception e = new Exception(toString());
            e.setStackTrace(getStackTrace());
            e.printStackTrace(s);
        //} else {
        //    super.printStackTrace(s);
        //}
    }

    @Override
    public String getMessage() {
        return !(getCause() instanceof ParseException) ? super.getMessage() : getCause().getMessage();
    }

    @Override
    public String getLocalizedMessage() {
        return !(getCause() instanceof ParseException) ? super.getLocalizedMessage() : getCause().getLocalizedMessage();
    }

    public String getLineContent() {
        return getCause() != null ? super.getMessage() : null;
    }

    @Override
    public String toString() {
        if (!(getCause() instanceof ParseException)) {
            String message = getMessage();
            return message != null ? message : super.toString();
        }

        String line = getLineContent();
        StringBuilder k = new StringBuilder().append("解析错误:\r\n  Line ").append(this.line).append(": ");
        if (line.length() > 512) {
            k.append("偏移量 ").append(lineOffset);
        } else {
            k.append(line).append("\r\n");
            int off = lineOffset + 9 + MathUtils.digitCount(this.line);
            for (int i = 0; i < off; i++) {
                k.append('-');
            }

            for (int i = line.length() - 1; i >= 0; i--) {
                if (line.charAt(i) > 255)
                    k.append('-');
            }
            k.append('^');
        }

        return k.append("\r\n原因: ").append(getCause().getLocalizedMessage()).append("\r\n").toString();
    }
}
