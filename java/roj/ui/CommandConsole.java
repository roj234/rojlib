package roj.ui;

import roj.collect.IntMap;
import roj.collect.SimpleList;
import roj.concurrent.TaskExecutor;
import roj.concurrent.TaskHandler;
import roj.config.ParseException;
import roj.config.Tokenizer;
import roj.config.Word;
import roj.text.CharList;
import roj.text.logging.Logger;

import java.util.Collections;
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
		AnsiBox.DEFAULT.render(new String[][]{
			new String[] { "Roj234的指令终端 帮助", "注册的指令", "快捷键" },
			new String[] { dumpNodes(new CharList(), 0).toStringAndFree(), KEY_SHORTCUT }
		});
	}

	private static final TaskExecutor Dispatcher = new TaskExecutor();
	private static final Logger LOGGER = Logger.getLogger("指令终端");
	static {
		Dispatcher.setName("RojLib 终端");
		Dispatcher.start();
	}
	public static Thread getDefaultDispatcher() {return Dispatcher;}

	public CommandParser ctx = new CommandParser(Dispatcher);
	public boolean commandEcho = true;
	protected final Tokenizer wr = new Tokenizer() {
		@Override
		protected Word onInvalidNumber(int flag, int i, String reason) throws ParseException { return readLiteral(); }
	};
	private List<Word> parse(String cmd, boolean highlighting) {
		List<Word> words = new SimpleList<>();

		wr.init(cmd);
		while (wr.hasNext()) {
			try {
				words.add(wr.next().copy());
			} catch (ParseException e) {
				if (highlighting) return words;

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
		var cmdNow = input.toString();
		var ctx = new CommandParser(null) {
			@Override public void wrapExecute(Command command) {}
		};

		List<Word> words = parse(cmdNow, true);
		int maxI = -1;
		List<IntMap.Entry<CommandNode>> dynamicHighlighterMax = Collections.emptyList();
		boolean maxIsSuccess = false;
		if (words != null && !words.isEmpty()) {
			for (int i = 0; i < nodes.size(); i++) {
				CommandNode node = nodes.get(i);
				ctx.init(cmdNow, words);
				ctx.setDynamicHighlighter();
				boolean isSuccess;
				try {
					isSuccess = node.apply(ctx, null);
				} catch (ParseException ignored) {
					isSuccess = false;
				} catch (Exception e) {
					e.printStackTrace();
					isSuccess = false;
				}
				maxI = Math.max(maxI, ctx.getMaxI()-(isSuccess?0:1));
				var newMax = ctx.getDynamicHighlighterMax();
				if (newMax.size() >= dynamicHighlighterMax.size() && (isSuccess || !maxIsSuccess)) {
					maxIsSuccess = isSuccess;
					dynamicHighlighterMax = newMax;
				}
			}
		}

		int wordId = 0;
		int highlightId = 0;
		int wordIdNext = -1;
		int currentColor = 0xFFFFFF;

		wr.init(cmdNow);
		int prevI = 0;
		while (wr.hasNext()) {
			if (wordId > maxI) currentColor = 0xFF0000;
			else while (wordId >= wordIdNext && highlightId < dynamicHighlighterMax.size()) {
                wordIdNext = dynamicHighlighterMax.get(highlightId).getIntKey();
                currentColor = dynamicHighlighterMax.get(highlightId).v.color();
                highlightId++;
            }

			Word w;
			try {
				w = wr.next();
				if (w.type() == Word.EOF) break;
			} catch (ParseException e) {
				return root.append(new AnsiString(cmdNow.substring(prevI)).color16(Terminal.RED));
			}

			if (w.pos() > prevI) root.append(new AnsiString(cmdNow.substring(prevI, w.pos())));
			root.append(w.type() == Word.STRING ? new AnsiString(cmdNow.substring(w.pos(), wr.index)).colorRGB(currentColor) : new AnsiString(w.val()).colorRGB(currentColor));

			prevI = wr.index;
			wordId++;
		}
		if (prevI < cmdNow.length()) root.append(new AnsiString(cmdNow.substring(prevI)));

		return root;
	}

	@Override
	protected void complete(String prefix, List<Completion> out, boolean oninput) {
		List<Word> words = parse(prefix, false);
		if (words == null) return;

		for (int i = 0; i < nodes.size(); i++) {
			CommandNode node = nodes.get(i);
			ctx.init(prefix, words);
			try {
				node.apply(ctx, out);
			} catch (Throwable e) {
				LOGGER.warn("补全命令"+words+"时发生异常", e);
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
		Terminal.removeBottomLine(tooltip());

		List<Word> words = parse(cmd.trim(), false);
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
			} catch (Throwable e) {
				LOGGER.warn("解析命令"+words+"时发生异常", e);
				continue;
			}
			maxI = Math.max(maxI, ctx.getMaxI());
		}

		if (pe != null) pe.printStackTrace();
		else Terminal.beep();
		return false;
	}

	public void setCommandEcho(boolean echo) { commandEcho = echo; }
}