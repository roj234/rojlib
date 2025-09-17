package roj.ui;

import org.jetbrains.annotations.Nullable;
import roj.collect.ArrayList;
import roj.collect.IntMap;
import roj.concurrent.Executor;
import roj.concurrent.TaskPool;
import roj.text.CharList;
import roj.text.ParseException;
import roj.text.Token;
import roj.text.Tokenizer;
import roj.text.logging.Logger;
import roj.util.Helpers;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 指令系统的使用方式完全借鉴自Minecraft
 * <pre> {@code
 * 		CommandConsole c = new CommandConsole("\u001b[33m田所浩二@AIPC> ");
 * 		Terminal.setConsole(c);
 *
 * 		c.register(literal("open")
 * 			.then(argument("file", Argument.file())
 * 				.executes(ctx -> {
 * 					File file = ctx.argument("file", File.class);
 * 					System.out.println("打开文件 "+file);
 *              })));
 * }</code>
 * @author Roj234
 * @since 2023/11/20 15:50
 */
public class Shell extends Terminal {
	public Shell(String prompt) { super(prompt); }

	protected final List<CommandNode> commands = new ArrayList<>();
	public Shell register(CommandNode node) { commands.add(node); return this; }
	public boolean unregister(CommandNode node) { return commands.remove(node); }
	public boolean unregister(String name) {
		for (int i = commands.size()-1; i >= 0; i--) {
			CommandNode node = commands.get(i);
			if (Objects.equals(name, node.getName())) {
				commands.remove(i);
				return true;
			}
		}
		return false;
	}

	public void sortCommands() {
		commands.sort(CommandNode.sorter);
		for (CommandNode node : commands) {
			node.sorted(true);
		}
	}
	public List<CommandNode> nodes() {return commands;}
	public CharList dumpNodes(CharList sb, int depth) {
		for (CommandNode node : commands)
			node.dump(sb.padEnd(' ', depth), depth);
		return sb;
	}

	public CommandNode findNode(String... name) {
		int i = 0;
		List<CommandNode> nodes = commands;

		loop:
		while (true) {
			for (CommandNode node : nodes) {
				if (name[i].equals(node.getName())) {
					if (++i == name.length)
						return node;

					nodes = node.getChildren();
					i++;
					continue loop;
				}
			}
			return null;
		}
	}

	@Override
	protected void printHelp() {
		Box.DEFAULT.render(new String[][]{
			new String[] { "指令帮助", dumpNodes(new CharList(), 0).toStringAndFree() }
		});
	}

	public boolean commandEcho = true;

	private static final Logger LOGGER = Logger.getLogger("指令终端");

	private Executor executor = TaskPool.common();
	private final CommandArgList argList = new CommandArgList(this);

	private final Tokenizer tk = new Tokenizer();
	private List<Token> tokenize(String cmd, boolean highlighting) {
		List<Token> tokens = new ArrayList<>();

		tk.init(cmd);
		while (tk.hasNext()) {
			try {
				tokens.add(tk.next().copy());
			} catch (ParseException e) {
				if (highlighting) return tokens;

				e.setStackTrace(new StackTraceElement[0]);
				e.printStackTrace();
				return null;
			}
		}

		if (tokens.isEmpty()) tokens.add(CommandArgList.EOF);
		return tokens;
	}
	@Override
	protected Text highlight(CharList input, int cursor) {
		Text root = new Text("");
		var cmdNow = input.substring(0, cursor < 0 ? input.length() : cursor);
		var argList = new CommandArgList(null);

		List<Token> tokens = tokenize(cmdNow, true);
		int maxI = -1;
		List<IntMap.Entry<CommandNode>> dynamicHighlighterMax = Collections.emptyList();
		boolean maxIsSuccess = false;
		if (tokens != null && !tokens.isEmpty()) {
			for (int i = 0; i < commands.size(); i++) {
				CommandNode node = commands.get(i);
				argList.init(cmdNow, tokens);
				argList.setDynamicHighlighter();
				boolean isSuccess;
				try {
					isSuccess = node.apply(argList, null);
				} catch (ParseException ignored) {
					isSuccess = false;
				} catch (Exception e) {
					e.printStackTrace();
					isSuccess = false;
				}
				maxI = Math.max(maxI, argList.getMaxI()-(isSuccess?0:1));
				var newMax = argList.getDynamicHighlighterMax();
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

		tk.init(cmdNow);
		int prevI = 0;
		while (tk.hasNext()) {
			if (wordId > maxI) currentColor = 0xFF0000;
			else while (wordId >= wordIdNext && highlightId < dynamicHighlighterMax.size()) {
                wordIdNext = dynamicHighlighterMax.get(highlightId).getIntKey();
                currentColor = dynamicHighlighterMax.get(highlightId).getValue().color();
                highlightId++;
            }

			Token w;
			try {
				w = tk.next();
				if (w.type() == Token.EOF) break;
			} catch (ParseException e) {
				return root.append(new Text(input.substring(prevI)).color16(Tty.RED));
			}

			if (w.pos() > prevI) root.append(new Text(cmdNow.substring(prevI, w.pos())));
			root.append(w.type() == Token.STRING ? new Text(cmdNow.substring(w.pos(), tk.index)).colorRGB(currentColor) : new Text(w.text()).colorRGB(currentColor));

			prevI = tk.index;
			wordId++;
		}
		if (prevI < input.length()) root.append(new Text(input.substring(prevI)));

		return root;
	}

	@Override
	protected void complete(String prefix, List<Completion> out, boolean oninput) {
		List<Token> tokens = tokenize(prefix, false);
		if (tokens == null) return;

		for (int i = 0; i < commands.size(); i++) {
			CommandNode node = commands.get(i);
			argList.init(prefix, tokens);
			try {
				node.apply(argList, out);
			} catch (Throwable e) {
				LOGGER.warn("补全命令"+tokens+"时发生异常", e);
			}
		}
	}

	@Override
	protected final boolean evaluate(String cmd) { return execute(cmd, commandEcho); }
	public boolean executeSync(String cmd) {
		Executor prev = executor;
		executor = null;
		try {
			return execute(cmd, false);
		} finally {
			executor = prev;
		}
	}
	private boolean execute(String cmd, boolean print) {
		Tty.removeBottomLine(tooltip());

		List<Token> tokens = tokenize(cmd, false);
		if (tokens == null) return false;

		ParseException pe = null;
		for (int i = 0; i < commands.size(); i++) {
			CommandNode node = commands.get(i);
			argList.init(cmd, tokens);
			try {
				// 保证printCommand先于异步任务执行
				synchronized (argList) {
					if (node.apply(argList, null)) {
						if (print) printCommand();
						return true;
					}
				}
			} catch (ParseException e) {
				pe = e;
			} catch (Throwable e) {
				LOGGER.warn("解析命令"+cmd+"时发生异常", e);
			}
		}

		if (pe != null) pe.printStackTrace();
		else Tty.beep();
		return false;
	}

	public void setCommandEcho(boolean echo) { commandEcho = echo; }

	public void doExec(Command command) {
		if (executor != null) {
			executor.executeUnsafe(() -> {
				Tty.popHandler();
				synchronized (argList) {
					realExec(command);
				}
				Tty.pushHandler(this);
			});
		} else {
			realExec(command);
		}
	}
	private void realExec(Command command) {
		try {
			command.exec(argList.createContext());
		} catch (Exception e) {
			LOGGER.warn("指令执行出错", e);
		}
	}

	public void setExecutor(Executor executor) {this.executor = executor;}

	/**
	 * 从用户输入中读取符合参数要求的对象.
	 * 如果输入Ctrl+C，返回null
	 */
	@Nullable
	@SuppressWarnings("unchecked")
	@Deprecated(forRemoval = true)
	public <T> T readSync(Argument<T> arg) {
		var ref = new Object[1];
		CommandNode node = CommandNode.argument("", arg).executes(ctx -> {
			synchronized (ref) {
				ref[0] = ctx.argument("", Object.class);
				ref.notify();
			}
		});
		register(node);

		// 真是不优雅……
		var t = Thread.currentThread();
		onKeyboardInterrupt(t::interrupt);
		executor = null;

		Tty.pushHandler(this);
		try {
			synchronized (ref) {ref.wait();}
		} catch (InterruptedException e) {
			return Helpers.maybeNull();
		} finally {
			onKeyboardInterrupt(null);
			unregister(node);
			Tty.popHandler();
		}

		return (T) ref[0];
	}
}