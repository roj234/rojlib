package roj.config;

import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.TextUtil;
import roj.text.Tokenizer;

import java.io.IOException;

import static roj.config.node.MapValue.CONFIG_TOPLEVEL;

/**
 * @author Roj234
 * @since 2023/3/27 22:38
 */
public final class IniSerializer implements ValueEmitter {
	private CharList writer;
	public IniSerializer() {}

	public IniSerializer to(CharList writer) {this.writer = writer;return this;}

	private int depth;
	private boolean hasTop;

	public final void emit(boolean b) {writer.append(b).append('\n');}
	public final void emit(int i) {writer.append(i).append('\n');}
	public final void emit(long i) {writer.append(i).append('\n');}
	public final void emit(float i) {writer.append(i).append('\n');}
	public final void emit(double i) {writer.append(i).append('\n');}
	public final void emit(String s) {
		if (IniParser.literalSafe(s)) writer.append(s);
		else Tokenizer.escape(writer.append('"'), s).append('"');
		writer.append('\n');
	}
	public final void emitNull() {writer.append("null\n");}

	@Override
	public void comment(String comment) {
		int i = 0;
		while (i < comment.length()) {
			writer.append(';');
			i = TextUtil.gAppendToNextCRLF(comment, i, writer);
			writer.append('\n');
		}
	}

	// *no state check*
	public final void emitKey(String key) {
		if (depth == 1) {
			if (key.equals(CONFIG_TOPLEVEL)) {
				if (hasTop) throw new IllegalArgumentException("TopLevel必须是第一个:"+key);
				return;
			}
			hasTop = true;
			writer.append("\n[");
			if (IniParser.literalSafe(key)) writer.append(key);
			else Tokenizer.escape(writer, key);
			writer.append("]\n");
		} else if (depth == 2) {
			if (IniParser.literalSafe(key)) writer.append(key);
			else Tokenizer.escape(writer, key);
			writer.append('=');
		} else {
			throw new IllegalArgumentException("INI不支持两级以上的映射");
		}

	}

	public final void emitList() {
		if (depth != 1) throw new IllegalArgumentException("Can not serialize LIST to INI/"+depth);
		depth++;
	}
	public final void emitMap() {
		if (depth > 1) throw new IllegalArgumentException("Can not serialize MAP to INI/"+depth);
		depth++;
	}
	public final void pop() {
		depth--;
	}

	public final IniSerializer reset() {
		depth = 0;
		hasTop = false;
		return this;
	}

	public final CharList getValue() {reset();return writer;}
	public String toString() {return writer.toString();}

	@Override public void close() throws IOException { if (writer instanceof AutoCloseable x) IOUtil.closeSilently(x);}
}