package roj.config;

import roj.math.MathUtils;
import roj.text.CharList;

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
    private final int index;

    private int line = -2, linePos;
    private CharSequence lineContent;

    /**
     * Constructs a ParseException with the specified detail message and
     * offset.
     * A detail message is a String that describes this particular exception.
     *
     * @param reason       the detail message
     * @param index the position where the error is found while parsing.
     */
    public ParseException(CharSequence all, String reason, int index, Throwable cause) {
        super(reason, cause, true, DEBUG);
        this.index = index;
        this.lineContent = all;
    }

    public int getIndex() {
        return index;
    }

    public int getLine() {
        return line;
    }

    public int getLineOffset() {
        return linePos;
    }

    @Override
    public void printStackTrace(PrintStream s) {
        Exception e = new Exception(toString());
        e.setStackTrace(getStackTrace());
        e.printStackTrace(s);
    }

    @Override
    public void printStackTrace(PrintWriter s) {
        Exception e = new Exception(toString());
        e.setStackTrace(getStackTrace());
        e.printStackTrace(s);
    }

    @Override
    public String getMessage() {
        return !(getCause() instanceof ParseException) ? super.getMessage() : getCause().getMessage();
    }

    public String getLineContent() {
        return lineContent.toString();
    }

    private void lineParser() {
        if(this.line != -2) return;

        CharList chars = new CharList(20);

        CharSequence keys = this.lineContent;

        int target = index;
        if(target > keys.length() || target < 0) {
            this.line = 0;
            this.linePos = 8;
            this.lineContent = "<ERROR: INVALID OFFSET>";
            return;
        }

        int line = 1, linePos = 0;
        int i = 0;

        for (; i < target; i++) {
            char c1 = keys.charAt(i);
            switch (c1) {
                case '\r':
                    if(i + 1 < keys.length() && keys.charAt(i + 1) == '\n') // \r\n
                        i++;
                case '\n':
                    linePos = 0;
                    line++;
                    chars.clear();
                    break;
                default:
                    linePos++;
                    chars.append(c1);
            }
        }

        o:
        for (; i < keys.length(); i++) {
            char c1 = keys.charAt(i);
            switch (c1) {
                case '\r':
                case '\n':
                    break o; // till this line end
                default:
                    chars.append(c1);
            }
        }

        this.line = line;
        this.linePos = linePos - 1;
        this.lineContent = chars.toString();
    }

    @Override
    public String toString() {
        String msg = getMessage() == null ? (getCause() == null ? "无消息" : getCause().toString()) : getMessage();

        lineParser();

        String line = getLineContent();

        StringBuilder k = new StringBuilder().append("解析错误:\r\n  Line ").append(this.line).append(": ");

        if (line.length() > 512) {
            k.append("偏移量 ").append(this.linePos);
        } else {
            k.append(line).append("\r\n");
            int off = this.linePos + 9 + MathUtils.digitCount(this.line);
            for (int i = 0; i < off; i++) {
                k.append('-');
            }

            for (int i = line.length() - 1; i >= 0; i--) {
                if (line.charAt(i) > 255)
                    k.append('-');
            }
            k.append('^');
        }

        return k.append("\r\n原因: ").append(msg).append("\r\n").toString();
    }
}
