package roj.config.auto;

import roj.config.ParseException;
import roj.config.Parser;
import roj.config.serial.CVisitor;

/**
 * @author Roj234
 * @since 2023/3/19 0019 18:53
 */
public interface Serializer<T> extends CVisitor {
	default T read(Parser parser, CharSequence text) throws ParseException {
		return read(parser,text,0);
	}
	default T read(Parser parser, CharSequence text, int flag) throws ParseException {
		reset();
		parser.parse(text, flag, this);
		if (!finished()) throw new IllegalStateException("数据结构有误");
		return get();
	}

	T get();
	boolean finished();
	Serializer<T> reset();

	void write(CVisitor c, T t);

	default T deepcopy(T t) {
		reset();
		write(this, t);
		return get();
	}
}