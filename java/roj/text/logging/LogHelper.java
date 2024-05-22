package roj.text.logging;

import org.jetbrains.annotations.Nullable;
import roj.reflect.DirectAccessor;

import java.util.List;

/**
 * @author Roj234
 * @since 2024/7/1 0001 15:07
 */
interface LogHelper {
	LogHelper INSTANCE = DirectAccessor.builder(LogHelper.class).access(Throwable.class, "suppressedExceptions", "getSuppressed", null).delegate(Throwable.class, "getOurStackTrace", "getStackTrace").build();
	@Nullable
	List<Throwable> getSuppressed(Throwable t);
	StackTraceElement[] getStackTrace(Throwable t);
}