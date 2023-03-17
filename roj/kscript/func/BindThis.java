package roj.kscript.func;

import roj.kscript.api.ArgList;
import roj.kscript.api.IObject;
import roj.kscript.type.KType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

/**
 * No description provided
 *
 * @author Roj234
 * @version 0.1
 * @since 2021/4/26 22:51
 */
public class BindThis extends KFunction {
	final IObject $this;
	final KFunction origFn;

	public BindThis(IObject $this, KFunction original) {
		this.$this = $this;
		this.origFn = original;
		set(original.source, original.name, original.clazz);
	}

	public static BindThis Function_proto_bind(KFunction fn, ArgList args) {
		return new BindThis(args.get(0).asObject(), fn);
	}

	@Override
	public KType invoke(@Nonnull IObject $this, ArgList param) {
		return origFn.invoke(this.$this, param);
	}

	public static class AppendArguments extends ArgList {

		@Nullable
		@Override
		public KFunction caller() {
			return null;
		}

		@Override
		public void trace(List<StackTraceElement> collector) {

		}
	}
}
