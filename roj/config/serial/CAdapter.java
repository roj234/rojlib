package roj.config.serial;

import roj.config.ParseException;
import roj.config.Parser;

/**
 * @author Roj234
 * @since 2023/3/19 0019 18:53
 */
public interface CAdapter<T> extends CVisitor {
	default T read(Parser<?> parser, CharSequence text) throws ParseException {
		return read(parser,text,0);
	}
	default T read(Parser<?> parser, CharSequence text, int flag) throws ParseException {
		reset();
		parser.parse(this,text,flag);
		if (!finished()) throw new IllegalStateException("数据结构有误");
		return result();
	}

	T result();
	boolean finished();
	void reset();

	void write(CVisitor c, T t);

	default T deepcopy(T t) {
		reset();
		write(this, t);
		return result();
	}
}
