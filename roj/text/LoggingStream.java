package roj.text;

import roj.collect.RingBuffer;
import roj.ui.DelegatedPrintStream;

import java.io.PrintStream;

/**
 * @author Roj233
 * @since 2022/1/13 0:48
 */
public class LoggingStream extends DelegatedPrintStream {
    public static RingBuffer<String> logger;

    private String timeFormat;
    private final ACalendar   cl;
    private final PrintStream out;

    public LoggingStream(boolean log) {
        super(2000);
        out = log ? System.out : null;
        cl = new ACalendar();
        timeFormat = "[H:i:s] ";
    }

    public void setTimeFormat(String timeFormat) {
        this.timeFormat = timeFormat;
    }

    @Override
    protected void newLine() {
        String t;
        synchronized (sb) {
            t = cl.formatDate(timeFormat, System.currentTimeMillis()).append(sb).toString();
            sb.clear();
        }
        logger.addLast(t);
        if (out != null) {
            out.println(t);
        }
    }
}
