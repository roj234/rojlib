package roj.text.logging;

import org.jetbrains.annotations.Nullable;
import roj.reflect.Bypass;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/7/1 15:07
 */
public interface LogHelper {
	LogHelper INSTANCE = Bypass.builder(LogHelper.class).access(Throwable.class, "suppressedExceptions", "getSuppressed", null).delegate(Throwable.class, "getOurStackTrace", "getStackTrace").build();
	@Nullable
	List<Throwable> getSuppressed(Throwable t);
	StackTraceElement[] getStackTrace(Throwable t);

	static void printError(Throwable e) {printError(e, System.err);}
	static void printError(Throwable e, Appendable myOut) {printError(e, myOut, "");}
	static void printError(Throwable e, Appendable myOut, String prefix) {LogWriter.LOCAL.get().printError(e, myOut, prefix);}
}