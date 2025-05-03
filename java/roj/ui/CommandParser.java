package roj.ui;

import roj.collect.IntList;
import roj.collect.IntMap;
import roj.collect.MyHashMap;
import roj.collect.SimpleList;
import roj.concurrent.TaskExecutor;
import roj.config.ParseException;
import roj.config.Word;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/11/20 15:05
 */
public class CommandParser {
	public static final Word EOF = new Word().init(Word.EOF, 0, "");

	public TaskExecutor executor;

	private String context;
	private List<Word> words;
	private int i, maxI;
	private final IntList stack = new IntList();
	private List<IntMap.Entry<CommandNode>> dynamicHighlighter, dynamicHighlighterMax;

	private final MyHashMap<String, Object> map = new MyHashMap<>();

	public CommandParser(TaskExecutor executor) { this.executor = executor; }

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
		if (w.type() != Word.STRING) throw error("参数不是转义字符串");
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
		if (i >= words.size()) throw error("参数数量不足");
		maxI = Math.max(maxI, i);
		return words.get(i++);
	}
	public Word peekWord() { return peekWord(true); }
	public Word peekWord(boolean EOFisNull) { return i >= words.size() ? null : words.get(i).type() == Word.EOF&&EOFisNull ? null : words.get(i); }

	public boolean hasEOF() { return words.get(words.size()-1).type() == Word.EOF; }
	public boolean isEndAtWordStart() { return i >= words.size()-1 && hasEOF(); }
	public boolean isEndAtWordEnd() { return i >= words.size()-1 && !hasEOF(); }
	public boolean isRealEOF() { return i >= words.size(); }

	public <T> T argument(String name, Class<T> type) { return type.cast(map.get(name)); }
	public void putArgument(String name, Object value) { map.put(name, value); }

	public ParseException error(String reason) { return ParseException.noTrace(context, reason, words.get(i-1).pos()); }

	public void pushStack(CommandNode node) {
		if (dynamicHighlighter != null) {
			maxI = Math.max(maxI, i);
			dynamicHighlighter.add(new IntMap.Entry<>(i, node));
			if (dynamicHighlighter.size() > dynamicHighlighterMax.size()) {
				dynamicHighlighterMax.clear();
				dynamicHighlighterMax.addAll(dynamicHighlighter);
			}
		}
		stack.add(i);
	}
	public void popStack() {
		if (dynamicHighlighter != null) dynamicHighlighter.remove(dynamicHighlighter.size()-1);
		i = stack.remove(stack.size()-1);
	}

	public void setDynamicHighlighter() {
		this.dynamicHighlighter = new SimpleList<>();
		this.dynamicHighlighterMax = new SimpleList<>();
	}
	public List<IntMap.Entry<CommandNode>> getDynamicHighlighterMax() {return dynamicHighlighterMax;}

	public int getI() {return i;}
	public int getMaxI() { return maxI; }

	protected final CommandContext createContext() { return new CommandContext(context, map); }
	public void wrapExecute(Command command) {
		var ctx = createContext();
		if (executor != null) executor.submit(() -> {command.exec(ctx);});
		else {
			try {
				command.exec(ctx);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void failedOn(String name, Argument<?> arg) {}
}