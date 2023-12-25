package roj.ui.terminal;

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

	public static CommandNode literal(String name) { return new LiteralNode(name); }
	public static CommandNode argument(String name, Argument<?> argument) { return new ArgumentNode(name, argument); }

	public CharList dump(CharList sb, int depth) {
		if (impl == null) {
			if (children.size() == 1) return children.get(0).dump(sb.append(' '), depth);
		} else if (children.isEmpty()) {
			return sb.append('\n');
		}

		sb.append(":\n");
		if (impl != null) sb.padEnd(' ', depth).append("<Execute>\n");
		for (CommandNode child : children) {
			child.dump(sb.padEnd(' ', depth), depth+2).append('\n');
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
		ParseException pe = null;
		ctx.pushStack();
		try {
			for (int i = 0; i < children.size(); i++) {
				try {
					if (children.get(i).apply(ctx, completions)) {
						return true;
					}
				} catch (ParseException e) {
					pe = e;
				}
			}
		} finally {
			ctx.popStack();
		}

		if (ctx.peekWord() == null) {
			if (impl != null) {
				if (completions == null) ctx.wrapExecute(impl);
				return true;
			}
		}

		if (pe != null) throw pe;
		return false;
	}

	public CommandNode then(CommandNode node) {
		children.add(node);
		return this;
	}

	static final class LiteralNode extends CommandNode {
		private final String literal;
		LiteralNode(String name) { literal = name; }

		@Override
		public CharList dump(CharList sb, int depth) {
			sb.append('\'').append(literal).append('\'');
			return super.dump(sb, depth);
		}

		@Override
		public boolean apply(ArgumentContext ctx, List<Completion> completions) throws ParseException {
			if (ctx.isEOF()) {
				if (completions != null) completions.add(new Completion(literal));
				return false;
			}

			String s = ctx.nextUnquotedString();
			if (!s.equals(literal)) {
				if (completions != null && literal.startsWith(s)) completions.add(new Completion(literal.substring(s.length())));
				return false;
			}

			return doApply(ctx, completions);
		}
	}
	static final class ArgumentNode extends CommandNode {
		private final String name;
		private final Argument<?> argument;
		ArgumentNode(String name, Argument<?> argument) { this.name = name; this.argument = argument; }

		@Override
		public CharList dump(CharList sb, int depth) {
			sb.append(name).append(':').append(argument.getClass().getSimpleName());
			return super.dump(sb, depth);
		}

		@Override
		public boolean apply(ArgumentContext ctx, List<Completion> completions) throws ParseException {
			if (ctx.isEOF()) {
				if (completions != null) argument.example(completions);
				return false;
			}

			boolean shouldPop = false;
			ctx.pushStack();
			ctx.clearRunsOut();
			Object o;
			block:
			try {
				o = argument.parse(ctx, completions!=null);
				shouldPop = true;

				if (o == null) {
					if (ctx.runsOut()) {
						ctx.popStack();
						ctx.pushStack();
						argument.complete(ctx, completions);
					}
					break block;
				}

				ctx.putArgument(name, o);
			} catch (ParseException e) {
				ctx.popStack();

				if (completions == null) throw e;
				if (ctx.runsOut()) argument.complete(ctx, completions);
			}

			try {
				return doApply(ctx, completions);
			} finally {
				if (shouldPop) ctx.popStack();
			}
		}
	}
}
