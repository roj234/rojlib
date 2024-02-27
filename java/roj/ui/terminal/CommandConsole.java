package roj.ui.terminal;

import roj.collect.SimpleList;
import roj.concurrent.TaskExecutor;
import roj.concurrent.TaskHandler;
import roj.config.ParseException;
import roj.config.Tokenizer;
import roj.config.Word;
import roj.text.CharList;
import roj.ui.AnsiString;
import roj.ui.CLIBoxRenderer;
import roj.ui.Terminal;

import java.util.List;
import java.util.Objects;

/**
 * 指令系统的使用方式完全借鉴自Minecraft
 * <pre> {@code
 * 		CommandConsole c = new CommandConsole("\u001b[33m田所浩二@AIPC> ");
 * 		CLIUtil.setConsole(c);
 *
 * 		c.register(literal("open")
 * 			.then(argument("file", Argument.file())
 * 				.executes(ctx -> {
 * 					File file = ctx.argument("file", File.class);
 * 					System.out.println("打开文件 "+file);
 *              })));
 * }</code>
 * @author Roj234
 * @since 2023/11/20 0020 15:50
 */
public class CommandConsole extends DefaultConsole {
	public CommandConsole(String prompt) { super(prompt); }

	protected final List<CommandNode> nodes = new SimpleList<>();
	public CommandConsole register(CommandNode node) { nodes.add(node); return this; }
	public boolean unregister(CommandNode node) { return nodes.remove(node); }
	public boolean unregister(String name) {
		for (int i = nodes.size()-1; i >= 0; i--) {
			CommandNode node = nodes.get(i);
			if (Objects.equals(name, node.getName())) {
				nodes.remove(i);
				return true;
			}
		}
		return false;
	}

	public void sortCommands() {
		nodes.sort(CommandNode.sorter);
		for (CommandNode node : nodes) {
			node.sorted(true);
		}
	}
	public List<CommandNode> nodes() {return nodes;}
	public CharList dumpNodes(CharList sb, int depth) {
		for (CommandNode node : nodes)
			node.dump(sb.padEnd(' ', depth), depth);
		return sb;
	}

	@Override
	protected void printHelp() {
		CLIBoxRenderer.DEFAULT.render(new String[][]{
			new String[] { "Roj234的指令终端 帮助", "注册的指令", "快捷键" },
			new String[] { dumpNodes(new CharList(), 0).toStringAndFree(), KEY_SHORTCUT }
		});
	}

	private static final TaskExecutor Dispatcher = new TaskExecutor();
	static {
		Dispatcher.setName("RojLib - 指令执行线程");
		Dispatcher.start();
	}
	public static Thread getDefaultDispatcher() {return Dispatcher;}

	public CommandParser ctx = new CommandParser(Dispatcher);
	public boolean commandEcho = true;
	protected final Tokenizer wr = new Tokenizer() {
		@Override
		protected Word onInvalidNumber(int flag, int i, String reason) throws ParseException { return readLiteral(); }
	};
	private List<Word> parse(String cmd) {
		List<Word> words = new SimpleList<>();

		wr.init(cmd);
		while (wr.hasNext()) {
			try {
				words.add(wr.next().copy());
			} catch (ParseException e) {
				e.setStackTrace(new StackTraceElement[0]);
				e.printStackTrace();
				return null;
			}
		}

		if (words.isEmpty()) words.add(CommandParser.EOF);
		return words;
	}
	@Override
	protected AnsiString highlight(CharList input) {
		AnsiString root = new AnsiString("");

		wr.init(input);
		int prevI = 0;
		while (wr.hasNext()) {
			Word w;
			try {
				w = wr.next();
				if (w.type() == Word.EOF) break;
			} catch (ParseException e) {
				return root.append(new AnsiString(input.substring(prevI)).color16(Terminal.RED));
			}

			if (w.pos() > prevI) root.append(new AnsiString(input.substring(prevI, w.pos())));
			switch (w.type()) {
				case Word.LITERAL: root.append(new AnsiString(w.val()).color16(Terminal.YELLOW+ Terminal.HIGHLIGHT)); break;
				case Word.STRING: root.append(new AnsiString(input.substring(w.pos(), wr.index)).color16(Terminal.GREEN+ Terminal.HIGHLIGHT)); break;
				case Word.INTEGER: case Word.LONG: root.append(new AnsiString(w.val()).color16(getNumberColor(w.val()))); break;
				case Word.DOUBLE: case Word.FLOAT: root.append(new AnsiString(w.val()).color16(Terminal.BLUE+ Terminal.HIGHLIGHT)); break;
				default: root.append(new AnsiString(w.val()).color16(Terminal.WHITE+ Terminal.HIGHLIGHT));
			}

			prevI = wr.index;
		}
		if (prevI < input.length()) root.append(new AnsiString(input.substring(prevI)));

		return root;
	}
	private static int getNumberColor(String val) {
		if (val.length() > 1) {
			String v = val.toLowerCase();
			if (v.startsWith("0b")) return Terminal.CYAN;
			if (v.startsWith("0x")) return Terminal.YELLOW;
			if (v.startsWith("0")) return Terminal.PURPLE+ Terminal.HIGHLIGHT;
		}
		return Terminal.CYAN+ Terminal.HIGHLIGHT;
	}

	@Override
	protected void complete(String prefix, List<Completion> out) {
		List<Word> words = parse(prefix);
		if (words == null) return;

		for (int i = 0; i < nodes.size(); i++) {
			CommandNode node = nodes.get(i);
			ctx.init(prefix, words);
			try {
				node.apply(ctx, out);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected final boolean evaluate(String cmd) { return execute(cmd, commandEcho); }
	public boolean executeSync(String cmd) {
		TaskHandler executor = ctx.executor;
		ctx.executor = null;
		try {
			return execute(cmd, false);
		} finally {
			ctx.executor = executor;
		}
	}
	protected boolean execute(String cmd, boolean print) {
		List<Word> words = parse(cmd.trim());
		if (words == null) return false;

		int maxI = 0;
		ParseException pe = null;
		for (int i = 0; i < nodes.size(); i++) {
			CommandNode node = nodes.get(i);
			ctx.init(cmd, words);
			try {
				// 保证printCommand先于异步任务执行
				synchronized (ctx) {
					if (node.apply(ctx, null)) {
						if (print) printCommand();
						return true;
					}
				}
			} catch (ParseException e) {
				pe = e;
			}
			maxI = Math.max(maxI, ctx.getMaxI());
		}

		if (print) printCommand();
		if (pe != null) pe.printStackTrace();
		else System.out.println("输入未完整匹配任何指令,最多部分匹配到"+maxI);

		return true;
	}

	public void setCommandEcho(boolean echo) { commandEcho = echo; }
}