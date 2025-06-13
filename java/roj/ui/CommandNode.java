package roj.ui;

import org.jetbrains.annotations.Nullable;
import roj.collect.ArrayList;
import roj.config.ParseException;
import roj.text.CharList;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * @author Roj234
 * @since 2023/11/20 15:03
 */
public abstract class CommandNode {
	private final List<CommandNode> children = new ArrayList<>();
	private Command impl;
	private Text comment;

	@Nullable
	public String getName() { return null; }

	public static CommandNode literal(String name) { return new Literal(name); }
	public static CommandNode argument(String name, Argument<?> argument) { return new ArgumentNode(name, argument); }
	public static CommandNode redirect(CommandNode node) { return new Redirect(node); }

	public CharList dump(CharList sb, int depth) {
		if (impl == null) {
			if (children.size() == 1) {
				children.get(0).dump(sb.append(' '), depth);
				if (comment != null) {
					sb.set(sb.length()-1, ' ');
					comment.writeAnsi(sb).append('\n');
				}
				return sb;
			}
			else if (children.size() == 0) return sb.append("[**错误:不可执行**]\n");
		} else if (children.isEmpty()) {
			if (comment != null) comment.writeAnsi(sb.append(' '));
			return sb.append('\n');
		}

		depth += 2;

		sb.append(':');
		if (comment != null) comment.writeAnsi(sb.append(' '));
		sb.append('\n');
		if (impl != null) sb.padEnd(' ', depth).append("[无参数]\n");
		for (CommandNode child : children) {
			child.dump(sb.padEnd(' ', depth), depth);
		}
		return sb;
	}

	public CommandNode executes(Command command) {
		if (impl != null) throw new IllegalStateException("Already have command");
		impl = command;
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
			ctx.pushStack(this);
			try {
				if (node.apply(ctx, completions)) {
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

	public CommandNode then(CommandNode node) {children.add(node);return this;}
	public CommandNode comment(String comment) {this.comment = Text.of(comment+"\033[0m").color16(Terminal.BLACK+Terminal.HIGHLIGHT);return this;}
	public CommandNode comment(Text comment) {this.comment = comment;return this;}

	boolean breakOn;
	public CommandNode breakOn() { breakOn = true; return this; }

	public int color() {return 0xFFFFFF;}

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
			if (ctx.isEndAtWordStart()) {
				if (completions != null) completions.add(new Completion(name));
				return false;
			}

			if (ctx.peekWord() == null) return false;

			String s = ctx.peekWord().val();
			if (!s.equals(name)) {
				if (completions != null && ctx.isEndAtWordEnd() && name.startsWith(s)) completions.add(new Completion(name.substring(s.length())));
				return false;
			}
			ctx.nextUnquotedString();

			return doApply(ctx, completions) || (completions != null && breakOn);
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

		public int color() {return argument.color();}

		@Override
		public boolean apply(CommandParser ctx, List<Completion> completions) throws ParseException {
			if (ctx.isEndAtWordStart()) {
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
			ctx.pushStack(this);
			Object o;
			block:
			try {
				o = argument.parse(ctx, completions);

				if (o == null) {
					if (completions != null && ctx.isEndAtWordStart()) {
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

			var paramDesc = new Text("参数名称：<"+name+">，类型："+argument.type());
			for (; size < completions.size(); size++) {
				Completion completion = completions.get(size);
				if (completion.description == null)
					completion.description = paramDesc;
			}
		}
	}
}