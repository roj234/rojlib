package roj.compiler.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.asm.type.IType;
import roj.asm.type.Type;
import roj.text.ParseException;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * @author Roj234
 * @since 2025/09/19 21:23
 */
public interface LambdaCompiler {
	AtomicReference<Supplier<LambdaCompiler>> FACTORY = new AtomicReference<>();
	static @NotNull LambdaCompiler getInstance() {return Objects.requireNonNull(FACTORY.get(), "No implementation found").get();}
	static void setFactory(Supplier<LambdaCompiler> factory) {FACTORY.set(Objects.requireNonNull(factory));}

	default <T> T compile(String source, Class<T> type, String... argumentNames) throws ParseException, LinkageError {return compile(source, null, type, argumentNames);}
	@SuppressWarnings("unchecked")
	default <T> T compile(String source, @Nullable String fileName, Class<T> type, String... argumentNames) throws ParseException, LinkageError {return (T) compile(source, fileName, Type.getType(type), argumentNames);}
	Object compile(String source, @Nullable String fileName, @Nullable IType genericType, String... argumentNames) throws ParseException, LinkageError;
}
