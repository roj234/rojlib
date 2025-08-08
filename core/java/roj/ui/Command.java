package roj.ui;

/**
 * @author Roj234
 * @since 2023/11/20 15:05
 */
@FunctionalInterface
public interface Command {
	void exec(CommandContext ctx) throws Exception;
}