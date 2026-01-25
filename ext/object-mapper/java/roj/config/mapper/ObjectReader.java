package roj.config.mapper;

import roj.asm.type.IType;
import roj.ci.annotation.StaticMethod;
import roj.config.ConfigMaster;
import roj.config.Parser;
import roj.config.TextParser;
import roj.config.ValueEmitter;
import roj.config.node.ConfigValue;
import roj.text.ParseException;
import roj.util.DynByteBuf;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;

/**
 * An object reader that deserializes an instance of type {@code T} by receiving emitted values/events
 * via the {@link ValueEmitter} methods it implements. This acts as both a callback receiver and an
 * object builder.
 *
 * <p><b>Push-based deserialization</b>: an external source (e.g., a {@link TextParser}) calls emit methods on this
 * reader to build the object incrementally. Call {@link #get()} after emission to retrieve the built
 * object. Supports reuse via {@link #reset()}.</p>
 *
 * <p>Example usage:
 * <pre><code>
 * ObjectReader&lt;MyObject&gt; reader = ObjectMapper.SAFE.reader(MyObject.class);
 * JsonParser parser = new JsonParser();
 * parser.parse(reader, inputStream);  // Parser calls reader's emit methods
 * MyObject obj = reader.get();
 * reader.close();
 * </code></pre></p>
 *
 * @param <T> the type of object to read/build
 * @see ValueEmitter
 * @see ObjectWriter
 * @author Roj234
 * @since 2023/3/19 18:53
 */
public interface ObjectReader<T> extends ValueEmitter {
	@StaticMethod
	default T read(ConfigValue value) {value.accept(reset());return get();}
	@StaticMethod
	default T read(File in, ConfigMaster parser) throws IOException, ParseException {parser.parser().parse(in, reset());return get();}
	@StaticMethod
	default T read(DynByteBuf in, ConfigMaster parser) throws IOException, ParseException {parser.parser().parse(in, reset());return get();}
	@StaticMethod
	default T read(InputStream in,  ConfigMaster parser) throws IOException, ParseException {parser.parser().parse(in, reset());return get();}
	@StaticMethod
	default T read(CharSequence in, ConfigMaster parser) throws ParseException {
		Parser p = parser.parser();
		if (!(p instanceof TextParser tp)) throw new UnsupportedOperationException(this+"不是文本配置格式");
		tp.parse(in, reset());return get();
	}
	@StaticMethod
	default T read(File in, ConfigMaster factory, Charset charset) throws IOException, ParseException {
		var parser = factory.parser();
		if (parser instanceof TextParser tp) tp.charset = charset;
		parser.parse(in, reset());
		return get();
	}
	@StaticMethod
	default T read(DynByteBuf in, ConfigMaster factory, Charset charset) throws IOException, ParseException {
		var parser = factory.parser();
		if (parser instanceof TextParser tp) tp.charset = charset;
		parser.parse(in, reset());
		return get();
	}
	@StaticMethod
	default T read(InputStream in, ConfigMaster factory, Charset charset) throws IOException, ParseException {
		var parser = factory.parser();
		if (parser instanceof TextParser tp) tp.charset = charset;
		parser.parse(in, reset());
		return get();
	}

	/**
	 * Returns the built object after emissions are complete.
	 * Call this only when {@link #finished()} returns true to avoid partial objects.
	 *
	 * @return the deserialized object (or null if emission indicated null)
	 * @throws IllegalStateException if called before emissions are finished
	 */
	T get();

	/**
	 * Checks if the reading process is complete (all emissions processed, object built).
	 *
	 * @return true if finished and ready for {@link #get()}
	 */
	boolean finished();

	/**
	 * Resets this reader to a clean state for reuse (clears the built object/state).
	 *
	 * @return this reader for chaining
	 */
	ObjectReader<T> reset();

	/**
	 * Returns the corresponding {@link ObjectWriter} for this type, allowing serialization
	 * of the same {@code T}. Useful for round-trip or copy operations.
	 *
	 * @return the writer for type {@code T}
	 */
	ObjectWriter<T> getWriter();

	/**
	 * Creates a deep copy of the given object.
	 *
	 * <p>Note: Copy is recursive, if {@code T} may have circular references, use {@code POOLED} flag in {@link ObjectMapper#reader(IType)} flags.</p>
	 *
	 * @param obj the object to copy
	 * @return a new instance of {@code T} as a copy
	 */
	default T copyOf(T obj) {
		reset();
		getWriter().write(this, obj);
		return get();
	}
}