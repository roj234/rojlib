package roj.ui.terminal;

import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.config.word.Tokenizer;
import roj.config.word.Word;
import roj.reflect.ReflectionUtils;
import roj.text.TextUtil;
import roj.ui.CLIUtil;
import roj.ui.Console;

import java.util.Arrays;
import java.util.List;

import static roj.ui.terminal.CommandNode.argument;

/**
 * @author Roj234
 * @since 2024/1/20 0020 1:29
 */
public class SimpleCliParser {
	private final List<CommandNode> nodes = new SimpleList<>();
	public SimpleCliParser add(CommandNode node) { nodes.add(node); return this; }

	public static CommandImpl nullImpl() { return NULLIMPL; }
	private static final CommandImpl NULLIMPL = ctx -> {};

	private static void add(String s, List<Word> words, int i) throws ParseException {
		if (s.startsWith("\"")) {
			words.add(new Word().init(Word.STRING, i, Tokenizer.removeSlashes(s)));
		} else {
			int number = TextUtil.isNumber(s);
			if (number == 0) {
				words.add(new Word.L(i, Long.parseLong(s), s));
			} else if (number == 1) {
				words.add(new Word.D(i, Double.parseDouble(s), s));
			} else {
				words.add(new Word().init(Word.LITERAL, i, s));
			}
		}
	}

	public CommandContext parse(String[] args, boolean allowUserInput) throws ParseException {
		List<Word> words = new SimpleList<>();
		for (int i = 0; i < args.length; i++) add(args[i], words, i);
		words.add(ArgumentContext.EOF);

		List<String> mynames = new SimpleList<>();
		List<Argument<?>> myargs = new SimpleList<>();
		CommandContext[] cc = new CommandContext[1];
		ArgumentContext ac = new ArgumentContext(null) {
			@Override
			public void failedOn(String name, Argument<?> arg) { mynames.add(name); myargs.add(arg); }
			@Override
			public void wrapExecute(CommandImpl command) { cc[0] = createContext(); }
		};

		CommandConsole c = null;
		CommandImpl cb = null;

		while (true) {
			mynames.clear(); myargs.clear();

			for (int i = 0; i < nodes.size(); i++) {
				CommandNode node = nodes.get(i);
				ac.init("", words);
				try {
					if (node.apply(ac, null))
						return cc[0];
				} catch (ParseException ignored) {}
			}

			if (mynames.isEmpty() || !allowUserInput) return null;

			Console prev = CLIUtil.getConsole();
			if (c == null) {
				System.out.println("位于命令管道位置 "+words.size()+" 的 "+ReflectionUtils.getCallerClass(2).getSimpleName()+" "+TextUtil.join(Arrays.asList(args), " ")+"\n请为以下参数提供值:");

				Thread t = Thread.currentThread();
				c = new CommandConsole("");
				c.setInputHistory(false);
				c.ctx.executor = null;
				cb = ctx1 -> {
					synchronized (words) {
						words.remove(words.size()-1);
						add(ctx1.argument("arg", Object.class).toString(), words, words.size());
						words.add(ArgumentContext.EOF);

						words.notify();
						CLIUtil.setConsole(prev);
					}
				};
				c.onKeyboardInterrupt(t::interrupt);
			}

			c.setPrompt(mynames.size() > 1 ? "Argument["+(words.size()-1)+"]:" : mynames.get(0)+":");
			for (Argument<?> arg : myargs) c.register(argument("arg", arg).executes(cb));

			CLIUtil.setConsole(c);
			try {
				synchronized (words) { words.wait(); }
			} catch (InterruptedException e) {
				System.out.println("KeyboardInterrupt");
				return null;
			} finally {
				CLIUtil.setConsole(prev);
			}
		}
	}
}