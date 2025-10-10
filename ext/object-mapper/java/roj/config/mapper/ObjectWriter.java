package roj.config.mapper;

import org.jetbrains.annotations.Nullable;
import roj.ci.annotation.StaticMethod;
import roj.config.ConfigMaster;
import roj.config.TreeEmitter;
import roj.config.ValueEmitter;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.util.DynByteBuf;
import roj.util.Helpers;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

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

	@StaticMethod default void write(ConfigMaster type, T o, File file) throws IOException {write(type, o, file, "");}
	@StaticMethod default void write(ConfigMaster type, T o, OutputStream out) throws IOException {write(type, o, out, "");}
	@StaticMethod default CharList write(ConfigMaster type, T o, CharList sb) {return write(type, o, sb, "");}
	@StaticMethod default DynByteBuf write(ConfigMaster type, T o, DynByteBuf buf) {return write(type, o, buf, "");}

	@StaticMethod default boolean write(ConfigMaster type, T o, File file, String indent) throws IOException {
		return IOUtil.writeFileEvenMoreSafe(file.getParentFile(), file.getName(), value -> write0(type, o, value, indent));
	}
	@StaticMethod default void write(ConfigMaster type, T o, OutputStream out, String indent) throws IOException {write0(type, o, out, indent);}
	@StaticMethod default CharList write(ConfigMaster type, T o, CharList sb, String indent) {write0(type, o, sb, indent);return sb;}
	@StaticMethod default DynByteBuf write(ConfigMaster type, T o, DynByteBuf buf, String indent) {write0(type, o, buf, indent);return buf;}

	private void write0(ConfigMaster type, T o, Object out, String indent) {
		try {
			if (type.hasSerializer()) {
				try (ValueEmitter v = type.serializer(out, indent)) {
					write(v, o);
				}
			} else {
				var tmp = new TreeEmitter();
				tmp.setProperty(TreeEmitter.ORDERED_MAP, true);
				write(tmp, o);
				type.serialize(out, tmp.get());
			}
		} catch (Exception e) {
			Helpers.athrow(e);
		}
	}
}