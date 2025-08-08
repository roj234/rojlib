package roj.ui;

import roj.collect.HashMap;
import roj.collect.IntList;
import roj.collect.IntMap;
import roj.collect.ArrayList;
import roj.config.ParseException;
import roj.config.Token;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/11/20 15:05
 */
public class CommandArgList {
	public static final Token EOF = new Token().init(Token.EOF, 0, "");

	private final Shell owner;

	private String command;
	private List<Token> tokens;
	private int i, maxI;
	private final IntList stack = new IntList();
	private List<IntMap.Entry<CommandNode>> dynamicHighlighter, dynamicHighlighterMax;

	private final HashMap<String, Object> map = new HashMap<>();

	public CommandArgList(Shell owner) { this.owner = owner; }

	public void init(String context, List<Token> tokens) {
		this.command = context;
		this.tokens = tokens;
		this.i = this.maxI = 0;
		this.stack.clear();
		this.map.clear();
	}

	public String nextString() throws ParseException { return nextWord().text(); }
	public String nextUnquotedString() throws ParseException {
		Token w = nextWord();
		if (w.type() != Token.LITERAL) throw error("参数不是字面量");
		return w.text();}
	public String nextQuotedString() throws ParseException {
		Token w = nextWord();
		if (w.type() != Token.STRING) throw error("参数不是转义字符串");
		return w.text();
	}
	public int nextInt() throws ParseException {
		Token w = nextWord();
		if (w.type() != Token.INTEGER) throw error("参数不是整型");
		return w.asInt();
	}
	public long nextLong() throws ParseException {
		Token w = nextWord();
		if (w.type() != Token.INTEGER && w.type() != Token.LONG) throw error("参数不是长整型");
		return w.asLong();
	}
	public double nextDouble() throws ParseException {
		Token w = nextWord();
		if (w.type() != Token.INTEGER && w.type() != Token.LONG && w.type() != Token.DOUBLE) throw error("参数不是实数");
		return w.asDouble();
	}
	public Token nextWord() throws ParseException {
		if (i >= tokens.size()) throw error("参数数量不足");
		maxI = Math.max(maxI, i);
		return tokens.get(i++);
	}
	public Token peekWord() { return i >= tokens.size() ? null : tokens.get(i).type() == Token.EOF ? null : tokens.get(i); }

	public boolean hasEOF() { return tokens.get(tokens.size()-1).type() == Token.EOF; }
	public boolean isEndAtWordStart() { return i >= tokens.size()-1 && hasEOF(); }
	public boolean isEndAtWordEnd() { return i >= tokens.size()-1 && !hasEOF(); }
	public boolean isRealEOF() { return i >= tokens.size(); }

	public void setArgument(String name, Object value) { map.put(name, value); }

	public ParseException error(String reason) { return ParseException.noTrace(command, reason, tokens.get(i-1).pos()); }

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
		this.dynamicHighlighter = new ArrayList<>();
		this.dynamicHighlighterMax = new ArrayList<>();
	}
	public List<IntMap.Entry<CommandNode>> getDynamicHighlighterMax() {return dynamicHighlighterMax;}

	public int getI() {return i;}
	public int getMaxI() { return maxI; }

	protected final CommandContext createContext() { return new CommandContext(command, map); }
	public void exec(Command command) {if (owner != null) owner.doExec(command);}

	public void failedOn(String name, Argument<?> arg) {}
}