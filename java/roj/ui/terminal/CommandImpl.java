package roj.ui.terminal;

/**
 * @author Roj234
 * @since 2023/11/20 0020 15:05
 */
@FunctionalInterface
public interface CommandImpl {
	void accept(CommandContext ctx) throws Exception;
}