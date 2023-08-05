package roj.ui.terminal;

import roj.collect.SimpleList;
import roj.concurrent.TaskPool;
import roj.config.ParseException;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.text.CharList;
import roj.ui.AnsiString;
import roj.ui.CLIUtil;

import java.util.List;

/**
 * 指令系统的使用方式完全借鉴自Minecraft
 * <pre> {@code
 * 		CliConsole.initialize();
 * 		CommandConsole c = new CommandConsole("\u001b[33m田所浩二@AIPC> ");
 * 		CliConsole.setConsole(c);
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

	protected final ArgumentContext ctx = new ArgumentContext(TaskPool.Common());
	protected Tokenizer wr = new Tokenizer();
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

		if (words.isEmpty()) words.add(new Word().init(Word.EOF, 0, ""));
		return words;
	}
	@Override
	protected AnsiString highlight(String input) {
		AnsiString root = new AnsiString("");

		wr.init(input);
		int prevI = 0;
		while (wr.hasNext()) {
			Word w;
			try {
				w = wr.next();
				if (w.type() == Word.EOF) break;
			} catch (ParseException e) {
				return root.append(new AnsiString(input.substring(prevI)).color16(CLIUtil.RED));
			}

			if (w.pos() > prevI) root.append(new AnsiString(input.substring(prevI, w.pos())));
			switch (w.type()) {
				case Word.LITERAL: root.append(new AnsiString(w.val()).color16(CLIUtil.YELLOW+60)); break;
				case Word.CHARACTER: root.append(new AnsiString(input.substring(w.pos(), wr.index)).color16(CLIUtil.GREEN)); break;
				case Word.STRING: root.append(new AnsiString(input.substring(w.pos(), wr.index)).color16(CLIUtil.GREEN+60)); break;
				case Word.INTEGER: case Word.LONG: root.append(new AnsiString(w.val()).color16(getNumberColor(w.val()))); break;
				case Word.DOUBLE: case Word.FLOAT: root.append(new AnsiString(w.val()).color16(CLIUtil.BLUE+60)); break;
				default: root.append(new AnsiString(w.val()).color16(CLIUtil.WHITE+60));
			}

			prevI = wr.index;
		}
		if (prevI < input.length()) root.append(new AnsiString(input.substring(prevI)));

		return root;
	}
	private static int getNumberColor(String val) {
		if (val.length() > 1) {
			String v = val.toLowerCase();
			if (v.startsWith("0b")) return CLIUtil.CYAN;
			if (v.startsWith("0x")) return CLIUtil.YELLOW;
			if (v.startsWith("0")) return CLIUtil.PURPLE+60;
		}
		return CLIUtil.CYAN+60;
	}

	protected List<CommandNode> nodes = new SimpleList<>();
	public void register(CommandNode node) { nodes.add(node); }
	public boolean unregister(String name) {
		for (int i = nodes.size()-1; i >= 0; i--) {
			CommandNode node = nodes.get(i);
			if (node instanceof CommandNode.LiteralNode && ((CommandNode.LiteralNode) node).getName().equals(name)) {
				nodes.remove(i);
				return true;
			}
		}
		return false;
	}

	@Override
	protected void complete(CharList input, int cursor, List<Completion> out) {
		String cmd = input.toString(0, cursor);
		List<Word> words = parse(cmd);
		if (words == null) return;

		for (int i = 0; i < nodes.size(); i++) {
			CommandNode node = nodes.get(i);
			ctx.init(cmd, words);
			try {
				node.apply(ctx, out);
			} catch (ParseException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected final boolean evaluate(String cmd) {
		return execute(cmd, true);
	}
	public boolean executeCommand(String cmd) {
		TaskPool executor = ctx.executor;
		ctx.executor = null;
		try {
			return execute(cmd, false);
		} finally {
			ctx.executor = executor;
		}
	}
	protected boolean execute(String cmd, boolean print) {
		List<Word> words = parse(cmd);
		if (words == null) return false;

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
		}

		if (print) printCommand();
		if (pe != null) pe.printStackTrace();
		else System.out.println("指令未匹配任何参数组合,从左至右最多的部分匹配是:"+ctx.getMaxI());

		return true;
	}

	public void dumpNodes() {
		CharList sb = new CharList();
		for (CommandNode node : nodes) {
			node.dump(sb, 0);
		}
		System.out.println(sb);
	}
}
