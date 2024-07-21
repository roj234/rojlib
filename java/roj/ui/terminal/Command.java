package roj.ui.terminal;

/**
 * @author Roj234
 * @since 2023/11/20 0020 15:05
 */
@FunctionalInterface
public interface Command {
	void exec(CommandContext ctx) throws Exception;
}