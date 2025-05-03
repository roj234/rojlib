package roj.http.h2;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * @author Roj234
 * @since 2024/7/13 2:23
 */
public interface H2FlowControl {
	default void initSetting(H2Setting setting) {}
	void dataReceived(H2Connection connection, @NotNull H2Stream stream, int bytes) throws IOException;
}