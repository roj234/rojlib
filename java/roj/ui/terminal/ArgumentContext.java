package roj.ui.terminal;

import roj.collect.IntList;
import roj.collect.MyHashMap;
import roj.concurrent.TaskPool;
import roj.config.ParseException;
import roj.config.word.Word;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/11/20 0020 15:05
 */
public class ArgumentContext {
	public static final Word EOF = new Word().init(Word.EOF, 0, "");

	public TaskPool executor;

	private String context;
	private List<Word> words;
	private int i, maxI;
	private final IntList stack = new IntList();

	private final MyHashMap<String, Object> map = new MyHashMap<>();

	public ArgumentContext(TaskPool executor) { this.executor = executor; }

	public void init(String context, List<Word> words) {
		this.context = context;
		this.words = words;
		this.i = this.maxI = 0;
		this.stack.clear();
		this.map.clear();
	}

	public String nextString() throws ParseException { return nextWord().val(); }
	public String nextUnquotedString() throws ParseException {
		Word w = nextWord();
		if (w.type() != Word.LITERAL) throw error("参数不是字面量");
		return w.val();}
	public String nextQuotedString() throws ParseException {
		Word w = nextWord();
		if (w.type() != Word.STRING && w.type() != Word.CHARACTER) throw error("参数不是转义字符串");
		return w.val();
	}
	public int nextInt() throws ParseException {
		Word w = nextWord();
		if (w.type() != Word.INTEGER) throw error("参数不是整型");
		return w.asInt();
	}
	public long nextLong() throws ParseException {
		Word w = nextWord();
		if (w.type() != Word.INTEGER && w.type() != Word.LONG) throw error("参数不是长整型");
		return w.asLong();
	}
	public double nextDouble() throws ParseException {
		Word w = nextWord();
		if (w.type() != Word.INTEGER && w.type() != Word.LONG && w.type() != Word.DOUBLE) throw error("参数不是实数");
		return w.asDouble();
	}
	public Word nextWord() throws ParseException {
		maxI = Math.max(maxI, i);
		if (i >= words.size()) throw error("参数数量不足");
		return words.get(i++);
	}
	public Word peekWord() { return peekWord(true); }
	public Word peekWord(boolean EOFisNull) { return i >= words.size() ? null : words.get(i).type() == Word.EOF&&EOFisNull ? null : words.get(i); }

	public boolean hasEOF() { return words.get(words.size()-1).type() == Word.EOF; }
	public boolean isEOF() { return i >= words.size()-1 && hasEOF(); }
	public boolean isWordEdge() { return i >= words.size()-1 && !hasEOF(); }

	public <T> T argument(String name, Class<T> type) { return type.cast(map.get(name)); }
	public void putArgument(String name, Object value) { map.put(name, value); }

	public ParseException error(String reason) { return ParseException.noTrace(context, reason, words.get(i-1).pos()); }

	public void pushStack() { stack.add(i); }
	public void popStack() { i = stack.remove(stack.size()-1); }

	public int getMaxI() { return maxI; }

	protected final CommandContext createContext() { return new CommandContext(map); }
	public void wrapExecute(CommandImpl command) {
		CommandContext ctx = createContext();
		if (executor != null) executor.pushTask(() -> {
			synchronized (this) {}
			command.accept(ctx);
		});
		else {
			try {
				command.accept(ctx);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void failedOn(String name, Argument<?> arg) {}
}