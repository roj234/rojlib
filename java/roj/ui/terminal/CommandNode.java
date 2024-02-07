package roj.ui.terminal;

import org.jetbrains.annotations.Nullable;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.text.CharList;

import java.util.List;

/**
 * @author Roj234
 * @since 2023/11/20 0020 15:03
 */
public abstract class CommandNode {
	private final List<CommandNode> children = new SimpleList<>();
	private CommandImpl impl;

	@Nullable
	public String getName() { return null; }

	public static CommandNode literal(String name) { return new LiteralNode(name); }
	public static CommandNode argument(String name, Argument<?> argument) { return new ArgumentNode(name, argument); }

	public CharList dump(CharList sb, int depth) {
		depth += 2;

		if (impl == null) {
			if (children.size() == 1) return children.get(0).dump(sb.append(' '), depth);
			else if (children.size() == 0) return sb.append("[无可执行]");
		} else if (children.isEmpty()) {
			return sb.append('\n');
		}

		sb.append(":\n");
		if (impl != null) sb.padEnd(' ', depth).append("[无参数]\n");
		for (CommandNode child : children) {
			child.dump(sb.padEnd(' ', depth), depth);
		}
		return sb;
	}

	public CommandNode executes(CommandImpl e) {
		if (impl != null) throw new IllegalStateException("Already have executor");
		impl = e;
		return this;
	}

	public abstract boolean apply(ArgumentContext ctx, List<Completion> completions) throws ParseException;
	final boolean doApply(ArgumentContext ctx, List<Completion> completions) throws ParseException {
		if (ctx.peekWord() == null) {
			if (completions == null && impl != null) {
				ctx.wrapExecute(impl);
				return true;
			}
		}

		ParseException pe = null;
		for (int i = 0; i < children.size(); i++) {
			ctx.pushStack();
			try {
				if (children.get(i).apply(ctx, completions)) {
					return true;
				}
			} catch (ParseException e) {
				pe = e;
			}
			ctx.popStack();
		}

		if (pe != null) throw pe;
		return false;
	}

	public CommandNode then(CommandNode node) {
		children.add(node);
		return this;
	}

	static final class LiteralNode extends CommandNode {
		private final String name;
		LiteralNode(String name) { this.name = name; }

		public String getName() { return name; }

		@Override
		public CharList dump(CharList sb, int depth) { return super.dump(sb.append(name), depth); }

		@Override
		public boolean apply(ArgumentContext ctx, List<Completion> completions) throws ParseException {
			if (ctx.isEOF()) {
				if (completions != null) completions.add(new Completion(name));
				return false;
			}

			if (ctx.peekWord() == null) return false;

			String s = ctx.peekWord().val();
			if (!s.equals(name)) {
				if (completions != null && ctx.isWordEdge() && name.startsWith(s)) completions.add(new Completion(name.substring(s.length())));
				return false;
			}
			ctx.nextUnquotedString();

			return doApply(ctx, completions);
		}
	}
	static final class ArgumentNode extends CommandNode {
		private final String name;
		private final Argument<?> argument;
		ArgumentNode(String name, Argument<?> argument) { this.name = name; this.argument = argument; }

		@Override
		public CharList dump(CharList sb, int depth) {
			return super.dump(sb.append('<').append(name).append('>').append(':').append(argument.type()), depth);
		}

		@Override
		public boolean apply(ArgumentContext ctx, List<Completion> completions) throws ParseException {
			if (ctx.isEOF()) {
				if (completions != null) argument.example(completions);
				else {
					try {
						Object o = argument.parse(ctx, null);
						ctx.putArgument(name, o);
						return doApply(ctx, null);
					} catch (Exception e) {
						ctx.failedOn(name, argument);
						return false;
					}
				}
				return false;
			}

			boolean shouldPop = true;
			ctx.pushStack();
			Object o;
			block:
			try {
				o = argument.parse(ctx, completions);

				if (o == null) {
					if (completions != null && ctx.isEOF()) {
						argument.example(completions);
					}
					break block;
				}

				ctx.putArgument(name, o);
			} catch (ParseException e) {
				ctx.popStack();
				shouldPop = false;

				if (completions == null) throw e;
			}

			try {
				return doApply(ctx, completions);
			} finally {
				if (shouldPop) ctx.popStack();
			}
		}
	}
}