package roj.config.mapper;

import org.jetbrains.annotations.Nullable;
import roj.config.ValueEmitter;

import java.io.IOException;

/**
 * An object writer that serializes an instance of type {@code T} by traversing its structure
 * and emitting values/events via a {@link ValueEmitter}.
 *
 * <p><b>Push-based serialization</b>: the writer visits the object's fields/properties
 * and calls {@code emit*} methods on the provided emitter.</p>
 *
 * <p>Example usage:
 * <pre><code>
 * ValueEmitter emitter = new JsonSerializer().to(TextWriter.to(outputStream));
 * ObjectWriter&lt;MyObject&gt; writer = ObjectMapper.SAFE.writer(MyObject.class);
 * writer.write(emitter, myObjectInstance);
 * emitter.close();
 * </code></pre></p>
 *
 * @param <T> the type of object to write
 * @see ValueEmitter
 * @see ObjectReader
 * @author Roj234
 * @since 2025/9/22 18:52
 */
public interface ObjectWriter<T> {
	/**
	 * Writes the given object {@code value} to the {@link ValueEmitter} by emitting its structure
	 * and values. The writer traverses the object recursively, calling appropriate emit methods
	 * (e.g., {@link ValueEmitter#emitList()}, {@link ValueEmitter#emit(String)}).
	 *
	 * <p>Implementations should handle nulls via {@link ValueEmitter#emitNull()}.</p>
	 *
	 * @param emitter the emitter to receive the object's data
	 * @param value the object to write (may be null)
	 * @throws IOException if an emission error occurs (e.g., underlying I/O failure)
	 */
	void write(ValueEmitter emitter, @Nullable T value);
}