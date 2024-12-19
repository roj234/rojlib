package roj.ui.terminal;

import org.jetbrains.annotations.Nullable;
import roj.collect.SimpleList;
import roj.config.ParseException;
import roj.text.CharList;
import roj.ui.AnsiString;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2023/11/20 0020 15:03
 */
public abstract class CommandNode {
	private final List<CommandNode> children = new SimpleList<>();
	private Command impl;

	@Nullable
	public String getName() { return null; }

	//public static CommandNode root(List<CommandNode> name) { return new Literal(name); }
	public static CommandNode literal(String name) { return new Literal(name); }
	public static CommandNode argument(String name, Argument<?> argument) { return new ArgumentNode(name, argument); }
	public static CommandNode redirect(CommandNode node) { return new Redirect(node); }

	public CharList dump(CharList sb, int depth) {
		if (impl == null) {
			if (children.size() == 1) return children.get(0).dump(sb.append(' '), depth);
			else if (children.size() == 0) return sb.append("[**错误:不可执行**]\n");
		} else if (children.isEmpty()) return sb.append('\n');

		depth += 2;

		sb.append(":\n");
		if (impl != null) sb.padEnd(' ', depth).append("[无参数]\n");
		for (CommandNode child : children) {
			child.dump(sb.padEnd(' ', depth), depth);
		}
		return sb;
	}

	public CommandNode executes(Command e) {
		if (impl != null) throw new IllegalStateException("Already have executor");
		impl = e;
		return this;
	}

	public abstract boolean apply(CommandParser ctx, List<Completion> completions) throws ParseException;
	final boolean doApply(CommandParser ctx, List<Completion> completions) throws ParseException {
		if (ctx.peekWord() == null) {
			if (completions == null && impl != null) {
				ctx.wrapExecute(impl);
				return true;
			}
		}

		ParseException pe = null;
		for (int i = 0; i < children.size(); i++) {
			CommandNode node = children.get(i);
			ctx.pushStack();
			try {
				if (node.apply(ctx, completions)) {
					return true;
				}
			} catch (ParseException e) {
				pe = e;
			}

			int pos = ctx.getI();
			ctx.popStack();

			if (node.fastFail && (completions == null || pos != ctx.getI())) break;
		}

		if (pe != null) throw pe;
		return false;
	}

	public List<CommandNode> getChildren() { return children; }
	public Command getCommand() { return impl; }
	public CommandNode getRedirect() { return null; }

	static final Comparator<CommandNode> sorter = (o1, o2) -> {
		String n1 = o1.getName();
		String n2 = o2.getName();
		if (n1 == null) return n2 == null ? 0 : 1;
		if (n2 == null) return -1;
		int i = n1.compareTo(n2);
		if (i == 0) throw new IllegalStateException("指令名称重复:"+n1);
		return i;
	};
	public CommandNode sorted(boolean recursion) {
		if (recursion) {
			for (var child : children) child.sorted(true);
		}
		children.sort(sorter);

		return this;
	}

	public CommandNode then(CommandNode node) {
		children.add(node);
		return this;
	}

	private boolean fastFail;
	public CommandNode fastFail() { fastFail = true; return this; }

	public static final class Redirect extends CommandNode {
		private CommandNode redirect;
		Redirect(CommandNode redirect) {this.redirect = redirect;}

		@Override
		public String getName() { return redirect.getName(); }
		@Override
		public boolean apply(CommandParser ctx, List<Completion> completions) throws ParseException { return redirect.apply(ctx, completions);}
		@Override
		public CommandNode getRedirect() { return redirect; }
		public void setRedirect(CommandNode redirect) { this.redirect = redirect; }
	}
	private static final class Literal extends CommandNode {
		private final String name;
		Literal(String name) {
			if (name.trim() != name || name.isEmpty()) throw new IllegalArgumentException("literal参数不能包含空格或为空");
			this.name = name;
		}

		public String getName() { return name; }

		@Override
		public CharList dump(CharList sb, int depth) { return super.dump(sb.append(name), depth); }

		@Override
		public boolean apply(CommandParser ctx, List<Completion> completions) throws ParseException {
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
	public static final class ArgumentNode extends CommandNode {
		private final String name;
		private final Argument<?> argument;
		ArgumentNode(String name, Argument<?> argument) { this.name = Objects.requireNonNull(name); this.argument = Objects.requireNonNull(argument); }

		public String getArgumentName() { return name; }
		public Argument<?> getArgument() { return argument; }

		@Override
		public CharList dump(CharList sb, int depth) {
			return super.dump(sb.append('<').append(name).append('>').append(':').append(argument.type()), depth);
		}

		@Override
		public boolean apply(CommandParser ctx, List<Completion> completions) throws ParseException {
			if (ctx.isEOF()) {
				if (completions != null) {
					makeExample(completions);
				} else {
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
						makeExample(completions);
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

		private void makeExample(List<Completion> completions) {
			int size = completions.size();
			argument.example(completions);
			if (completions.size() == size) {
				var tip = new Completion("<"+name+">");
				tip.isTip = true;
				completions.add(tip);
			}

			var paramDesc = new AnsiString("参数名称：<"+name+">，类型："+argument.type());
			for (; size < completions.size(); size++) {
				Completion completion = completions.get(size);
				if (completion.description == null)
					completion.description = paramDesc;
			}
		}
	}
}