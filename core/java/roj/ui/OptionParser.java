package roj.ui;

import roj.config.ParseException;
import roj.reflect.Reflection;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.util.HighResolutionTimer;

import java.util.Arrays;

import static roj.ui.CommandNode.literal;

/**
 * @author Roj234
 * @since 2024/1/20 1:29
 */
public class OptionParser {
	private final String tool;
	private final Shell shell;

	public OptionParser() {this(Reflection.getCallerClass(3).getSimpleName());}
	public OptionParser(String tool) {
		this.tool = tool;
		this.shell = new Shell(tool+"> ");
	}

	@Deprecated
	public static Command nullImpl() { return NULLIMPL; }
	private static final Command NULLIMPL = ctx -> {};

	public OptionParser add(CommandNode node) { shell.register(node); return this; }

	public boolean parse(String[] args, boolean allowUserInput) throws ParseException {
		boolean executed = shell.executeSync(TextUtil.join(Arrays.asList(args), " "));
		if (!executed) {
			shell.sortCommands();
			CharList charList = shell.dumpNodes(new CharList().append("Usage: ").append(tool).append("\n"), 4);
			System.out.println(charList.append('\n').toStringAndFree());
			if (allowUserInput) {
				System.out.println(
						"Welcome to "+tool+" CLI [v1.0]\n" +
						"Type 'help' for commands, 'exit' to quit\n\n");
				Tty.pushHandler(shell.register(literal("help").executes(ctx -> shell.printHelp()))
				.register(literal("exit").executes(ctx -> {
					Tty.popHandler();
					System.out.println("Goodbye!");
					System.exit(0);
				})));
				HighResolutionTimer.runThis();
			}
		}
		return executed;
	}
}