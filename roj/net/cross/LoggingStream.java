package roj.net.cross;

import roj.collect.RingBuffer;
import roj.text.ACalendar;
import roj.ui.DelegatedPrintStream;

import java.io.PrintStream;

/**
 * @author Roj233
 * @since 2022/1/13 0:48
 */
public class LoggingStream extends DelegatedPrintStream {
    public static RingBuffer<String> logger;

    private final ACalendar   cl;
    private final PrintStream out;

    public LoggingStream(boolean log) {
        super(2000);
        out = log ? System.out : null;
        cl = new ACalendar();
    }

    @Override
    protected void newLine() {
        String t = cl.formatDate("[H:i:s] ", System.currentTimeMillis()) + sb;
        logger.addLast(t);
        sb.clear();
        if (out != null) {
            out.println(t);
        }
    }
}
