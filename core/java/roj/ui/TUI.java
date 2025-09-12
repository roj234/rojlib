package roj.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.collect.BitSet;
import roj.text.CharList;
import roj.ui.Tty.Cursor;
import roj.ui.Tty.Screen;
import roj.util.function.Flow;

import java.awt.event.KeyEvent;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.function.Function;

import static java.awt.event.KeyEvent.VK_C;
import static roj.ui.Tty.VK_CTRL;

/**
 * @author Roj234
 * @since 2025/4/29 0:04
 */
public abstract class TUI implements KeyHandler {
	private static final boolean THEME2 = true;
	private static final boolean UNICODE = Tty.isFullUnicode();

	private static final String LINE = "│", END = "└";
	public static final String RESET = "\033[0m";
	private static final String BLACK = "\033["+(Tty.BLACK+Tty.HIGHLIGHT)+"m";
	private static final String BLACK_LINE = BLACK+LINE+"  "+RESET;
	private static final String BLACK_END = BLACK+END;

	private static final String SELECTED = THEME2 ? "●" : "\033["+Tty.GREEN+"m>\033[0m", UNSELECTED = THEME2 ? "○" : " ";
	private static final String SELECTED_MULTI = UNICODE ? "◼" : "[+]", UNSELECTED_MULTI = UNICODE ? "◻" : "[ ]", HOVERED_MULTI = UNICODE ? "◻" : "[•]";

	private static final String BLUE = "\033["+(Tty.CYAN)+"m";
	private static final String PENDING = BLUE+"◇ "+RESET;
	private static final String PENDING_LINE = BLUE+LINE+"  ";

	private static final String
			SUCCESS = "\033["+Tty.GREEN+"m◆",
			FAILURE = "\033["+Tty.RED+"m▲",
			WARNING = "\033["+Tty.YELLOW+"m▲",
			ERROR = "\033["+Tty.RED+"m■",
			INFO = "\033["+Tty.CYAN+"m●";

	public static void start(Text message) {
		var sb = new CharList().append(BLACK+"┌  "+RESET);
		message.writeAnsi(sb, "\n"+BLACK+LINE+" "+RESET);
		sb.append("\n"+BLACK+LINE+" "+RESET+"\n");
		Tty.write(sb);
	}
	public static void end(Text message) {
		var sb = new CharList().append(BLACK+"└  "+RESET);
		message.writeAnsi(sb, "\n"+BLACK+LINE+" "+RESET);
		Tty.write(sb.append('\n'+RESET));
	}

	private static int writeWithTrace(Text message, String icon) {
		var sb = new CharList().append(icon).append(" "+RESET);
		message.writeAnsi(sb, "\n"+BLACK+LINE+" "+RESET);
		sb.append("\n"+BLACK+LINE+" "+RESET+"\n");
		Tty.write(sb);
		return Tty.splitByWidth(sb.toString(), Tty.getColumns()).size();
	}

	/**
	 * 输入之前step返回的数值即可撤销这个step
	 */
	public static void stepUndo(int lh) {Tty.write(Cursor.moveVertical(-lh)+Screen.clearLineAfter);}
	public static int stepSuccess(Text message) {return writeWithTrace(message, SUCCESS);}
	public static int stepWarn(Text message) {return writeWithTrace(message, WARNING);}
	public static int stepInfo(Text message) {return writeWithTrace(message, INFO);}
	public static int stepError(Text message) {return writeWithTrace(message, ERROR);}

	public static Text text(String s) {return Text.of(s);}

	//region 格式化文本输入 input/password
	public static String input(String info) {return input(text(info), Argument.rest()).trim();}
	public static String input(Text message) {return input(message, Argument.rest()).trim();}
	public static <T> T input(Text message, Argument<T> argument) {return input(message, null, "", argument, true);}
	public static <T> T inputOpt(Text message, Argument<T> argument) {return input(message, null, "", argument, false);}
	public static <T> T input(Text message, String initialValue, Argument<T> argument) {return input(message, null, initialValue, argument, true);}
	public static <T> T input(Text message,
							  @Nullable Text placeholder,
							  @NotNull String initialValue,
							  Argument<T> argument, boolean required) {

		var callback = new Input<>(message, initialValue, argument);
		callback.placeholder = placeholder;
		callback.required = required;
		Tty.pushHandler(callback);
		return callback.getValue();
	}
	public static <T> T password(Text message, Argument<T> argument) {
		var callback = new Input<>(message, "", argument);
		callback.placeholder = text("密码不会显示"+Tty.reset).bgColor16(Tty.BLACK+Tty.HIGHLIGHT);
		callback.setInputEcho(false);
		Tty.pushHandler(callback);
		return callback.getValue();
	}
	private static class Input<T> extends Shell {
		final Text message;
		final Argument<T> argument;
		T value;
		final Object lock = new Object();
		Text placeholder;
		boolean required;

		@SuppressWarnings("unchecked")
		public Input(Text message, @NotNull String initialValue, Argument<T> argument) {
			super("\n"+PENDING_LINE+RESET);
			setExecutor(null);
			setCommandEcho(false);
			setInputHistory(false);
			setAutoComplete(true);
			input.append(initialValue);

			this.message = message;
			this.argument = argument;

			register(CommandNode.argument("", argument).executes(ctx -> {
				synchronized (lock) {
					value = (T) ctx.argument("", Object.class);
					lock.notify();
				}
			}));

			var t = Thread.currentThread();
			onKeyboardInterrupt(t::interrupt);
		}

		@Override
		public void registered() {
			var sb = new CharList();
			message.writeAnsi(sb.append(PENDING));
			lineHeight = 3 + Tty.splitByWidth(sb.toString(), Tty.getColumns()).size();
			Tty.write(sb.append("\n"+PENDING_LINE+RESET+"\n"+PENDING_LINE+"\n"+END+"\n"));
		}

		int lineHeight;

		@Override
		protected void renderLine(int cursor) {
			if (input.length() == 0 && placeholder != null) placeholder.writeAnsi(line);

			if (!echo) line.padEnd("*", input.length());

			CharList sb = new CharList().append(Cursor.moveVertical(-lineHeight)).append(line).append(Screen.clearLineAfter).append("\n\n\n");
			Tty.write(sb);
		}

		@Override
		public void keyEnter(int keyCode, boolean isVirtualKey) {
			super.keyEnter(keyCode, isVirtualKey);
		}

		@Nullable
		public T getValue() {
			try {
				synchronized (lock) {lock.wait();}
			} catch (InterruptedException e) {
				if (required) {
					Tty.write(Cursor.moveVertical(-lineHeight)+FAILURE+"\n"+Screen.clearAfter+choice()+BLACK_END+"  \033["+Tty.RED+"m操作被用户取消！\033[0m\n");
					throw new CancellationException();
				}
			} finally {
				Tty.popHandler();
			}

			Tty.write(Cursor.moveVertical(-lineHeight)+SUCCESS+"\n"+Screen.clearLineAfter+choice());
			return value;
		}

		String choice() {
			CharList sb = new CharList().append(BLACK_LINE);
			if (value != null) {
				sb.append(echo ? value : "*".repeat(value.toString().length()));
			} else {
				sb.append("Nothing");
			}
			return sb.append("\n"+BLACK_LINE+"\n").toString();
		}
	}
	//endregion
	//region Yes/No选择 confirm
	public static boolean confirm(Text message) {
		var callback = new TUI() {
			int width;

			@Override
			public void registered() {
				lineHeight = 4;

				var sb = new CharList().append(PENDING);
				var before = PENDING_LINE+RESET+UNSELECTED+" Yes / ";
				width = Tty.getStringWidth(before);

				message.writeAnsi(sb).append(RESET+"\n").append(before).append(SELECTED+" No\n"+PENDING_LINE+"\n"+END+"\n\033[0m");
				Tty.write(sb);
			}

			@Override
			@SuppressWarnings("fallthrough")
			public void keyEnter(int keyCode, boolean isVirtualKey) {
				switch (keyCode) {
					case KeyEvent.VK_ESCAPE, VK_CTRL | VK_C: cancel(); Tty.popHandler();break;
					case KeyEvent.VK_Y, KeyEvent.VK_N: setValue(keyCode == KeyEvent.VK_Y ? 1 : 0); //fallthrough
					case KeyEvent.VK_ENTER: confirm(); Tty.popHandler();break;
					case KeyEvent.VK_TAB, KeyEvent.VK_SPACE: setValue(value ^ 1);break;
					case KeyEvent.VK_LEFT, KeyEvent.VK_UP: setValue(1);break;
					case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN: setValue(0);break;
					default: Tty.beep();break;
				}
			}

			String choice() {return BLACK_LINE+(value!=0?"Yes":"No")+"\033[0J\n"+BLACK_LINE+"\n";}

			private void setValue(int newValue) {
				if (newValue == value) return;
				value = newValue;

				Tty.write("\033[3F\033[4G"+(newValue!=0? SELECTED : UNSELECTED)+Cursor.toHorizontal(width)+(newValue==0? SELECTED : UNSELECTED)+"\n\n\n\033[3D");
			}
		};

		Tty.pushHandler(callback);
		return callback.getValue() != 0;
	}
	//endregion
	//region 单选框 radio
	public static <T> T radio(Text message, List<T> choices, Function<T, Completion> toString) {
		return choices.get(radio(message, Flow.of(choices).map(toString).toArray(Completion[]::new)));
	}
	public static int radio(Text message, Completion... choices) {return radio(message, 0, choices);}
	public static int radio(Text message, int def, Completion... choices) {
		if (choices.length == 1) return 0;

		var callback = new Select(message, choices);
		callback.value = def;
		Tty.pushHandler(callback);
		return callback.getValue();
	}
	private static class Select extends TUI {
		final Text message;
		final Completion[] choices;

		public Select(Text message, Completion... choices) {
			this.message = message;
			this.choices = choices;
		}

		@Override
		public void registered() {
			var sb = new CharList();
			message.writeAnsi(sb.append(PENDING));
			writeChoice(sb.append(RESET), value);

			Tty.write(sb.append("\n"+PENDING_LINE+"\n"+END+"\n"));
		}

		void writeChoice(CharList sb, int current) {
			int pos = sb.length();

			for (int i = 0; i < choices.length; i++) {
				appendSelectInfo(sb, i, current);

				var choice = choices[i];
				choice.completion.writeAnsi(sb).append(RESET);
				if (i == current && choice.description != null) {
					sb.append(" (");
					choice.description.writeAnsi(sb).append(RESET).append(')');
				}
				sb.append(Screen.clearLineAfter);
			}

			applyHeightChange(sb, pos);
		}
		void appendSelectInfo(CharList sb, int i, int selected) {
			sb.append("\n"+PENDING_LINE+RESET).append(i == selected ? SELECTED : UNSELECTED).append(' ');
		}

		@Override
		public void keyEnter(int keyCode, boolean isVirtualKey) {
			switch (keyCode) {
				case VK_CTRL | VK_C -> {
					cancel();
					Tty.popHandler();
				}
				case KeyEvent.VK_ENTER -> {
					confirm();
					Tty.popHandler();
				}
				case KeyEvent.VK_TAB -> setValue((value+1) % choices.length);
				case KeyEvent.VK_LEFT, KeyEvent.VK_UP -> setValue(value == 0 ? value : value-1);
				case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> setValue(value == choices.length-1 ? value : value+1);
				default -> Tty.beep();
			}
		}

		String choice() {return BLACK_LINE+choices[value].completion+"\n"+BLACK_LINE+"\n";}

		private void setValue(int newValue) {
			if (newValue == value) return;
			value = newValue;

			CharList sb = new CharList().append(Cursor.moveVertical(-lineHeight));
			writeChoice(sb, newValue);
			Tty.write(sb.append("\n\n\n"));
		}
	}
	//endregion
	//region 复选框 checkbox
	public static int checkbox(Text message, Completion... choices) {return checkbox(message, 0, choices);}
	public static int checkbox(Text message, int def, Completion... choices) {
		var callback = new Select(message, choices) {
			int selected;

			@Override
			public void registered() {
				var sb = new CharList();
				message.writeAnsi(sb.append(PENDING));
				writeChoice(sb.append(RESET), selected);

				Tty.write(sb.append("\n"+PENDING_LINE+"\n"+END+"\n"));
			}

			@Override
			void appendSelectInfo(CharList sb, int i, int hovered) {
				sb.append("\n"+PENDING_LINE);
				boolean isSelected = (value & 1 << i) != 0;
				if (isSelected) {
					if (i != hovered) sb.append(RESET);
					sb.append(SELECTED_MULTI);
					if (i == hovered) sb.append(RESET);
				} else {
					if (i == hovered) {
						sb.append(HOVERED_MULTI+RESET);
					} else {
						sb.append(RESET+UNSELECTED_MULTI);
					}
				}
				sb.append(' ');
			}

			@Override
			public void keyEnter(int keyCode, boolean isVirtualKey) {
				switch (keyCode) {
					case VK_CTRL | VK_C -> {
						cancel();
						Tty.popHandler();
					}
					case KeyEvent.VK_ENTER -> {
						confirm();
						Tty.popHandler();
					}
					case KeyEvent.VK_SPACE -> {
						value ^= 1<<selected;
						toggleSelection();
					}
					case KeyEvent.VK_TAB -> setValue((selected+1) % choices.length);
					case KeyEvent.VK_LEFT, KeyEvent.VK_UP -> setValue(selected == 0 ? selected : selected-1);
					case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> setValue(selected == choices.length-1 ? selected : selected+1);
					default -> Tty.beep();
				}
			}

			@Override
			String choice() {
				CharList sb = new CharList().append(BLACK_LINE);
				if (value != 0) {
					for (int i = 0; i < choices.length; i++) {
						if ((value&(1 << i)) != 0) {
							sb.append(choices[i].completion);
							sb.append(", ");
						}
					}

					sb.setLength(sb.length()-2);
				} else {
					sb.append("Nothing");
				}
				return sb.append("\n"+BLACK_LINE+"\n").toString();
			}

			private void toggleSelection() {
				var sb = new CharList().append(Cursor.moveVertical(-lineHeight));
				writeChoice(sb, selected);
				Tty.write(sb.append("\n\n\n"));
			}

			private void setValue(int newValue) {
				if (newValue == selected) return;
				selected = newValue;
				toggleSelection();
			}
		};
		callback.value = def;
		Tty.pushHandler(callback);
		return callback.getValue();
	}
	//endregion
	//region 进度条 progress
	public static Progress progress(Text title, int extraLines) {
		CharList sb = new CharList().append(PENDING);
		title.writeAnsi(sb).append(RESET+'\n');
		Tty.write(sb);
		return new Progress(title, extraLines);
	}
	public static class Progress extends TUI {
		public Progress(Text title, int extraLines) {
			this.title = title;
			this.extraLines = new Text[extraLines];
		}

		@Override
		public void keyEnter(int keyCode, boolean isVirtualKey) {
			if (keyCode == (VK_CTRL|VK_C)) interrupted = true;
		}

		boolean interrupted;
		public boolean isInterrupted() {return interrupted;}

		int progressWidth = 20;

		boolean animation;
		float progress;
		Text title, before, after;
		Text[] extraLines;

		public void setTitle(Text title) {this.title = title;}
		public void setProgress(float progress) {this.progress = progress;}
		public float getProgress() {return progress;}

		public void setBeforeProgress(Text before) {this.before = before;}

		public void setAfterProgress(Text title) {this.after = title;}
		public void setExtraLine(int line, Text text) {extraLines[line] = text;}
		public void setExtraLines(int count) {this.extraLines = new Text[count];}

		public void setAnimation(boolean animation) {this.animation = animation;}

		public void draw() {
			var sb = new CharList();
			sb.append(Cursor.moveVertical(-lineHeight));
			int len = sb.length();

			int filled = Math.round(progress * progressWidth);
			sb.append(PENDING_LINE+RESET);
			if (before != null) before.writeAnsi(sb).append(RESET).append(' ');
			sb.append("[");
			if (animation) Tty.TextEffect.sonic("=".repeat(filled), sb);
			else sb.padEnd('=', filled);
			sb.padEnd(' ', progressWidth - filled).append("]"+RESET);
			if (after != null) after.writeAnsi(sb.append(' ')).append(RESET);

			sb.append('\n');
			for (int i = 0; i < extraLines.length; i++) {
				Text extra = extraLines[i];
				sb.append(PENDING_LINE);
				if (extra != null) extra.writeAnsi(sb.append(RESET)).append(RESET);
				else sb.append(Screen.clearLineAfter);
				sb.append('\n');
			}

			boolean b = lineHeight != 0;

			applyHeightChange(sb, len);

			sb.append(b ? "\n\n" : PENDING_LINE+"\n"+END+"\n");
			Tty.write(sb);
		}

		public void success() {withMessage(null, SUCCESS);}
		public void warning(Text message) {withMessage(message, WARNING);}
		public void error(Text message) {withMessage(message, ERROR);}

		private void withMessage(Text message, String head) {
			var sb = new CharList().append(Cursor.moveVertical(-lineHeight - 1)).append(head).append("\n"+Screen.clearAfter+BLACK_LINE);
			if (message != null) message.writeAnsi(sb, "\n"+BLACK_LINE).append("\n"+BLACK_LINE);
			Tty.write(sb.append('\n'));
		}

		@Override String choice() {return "";}
	}
	//endregion
	public static void pause() {
		char c = key(null, new CharList("按任意键继续"));
		if (c == 0) System.exit(-1);
	}
	//region 任意键无需回车 key
	/**
	 * @see #key(BitSet, CharList)
	 */
	public static char key(String chars) {return key(chars, "");}
	public static char key(String chars, String prefix) {return key(BitSet.from(chars), new CharList(prefix));}

	/**
	 * 从标准输入中读取一个被chars允许的字符.
	 * 如果输入的字符不允许，发出哔哔声
	 * 如果输入Ctrl+C，返回\0
	 * @param prefix 提示用户的前缀
	 */
	public static char key(BitSet chars, CharList prefix) {
		var ref = new char[1];
		KeyHandler c = (keyCode, isVirtualKey) -> {
			synchronized (ref) {
				ref[0] = (char) keyCode;
				ref.notifyAll();
			}
		};

		Tty.pushHandler(c);
		if (prefix != null) Tty.renderBottomLine(prefix, false, Tty.getStringWidth(prefix)+1);
		try {
			while (true) {
				synchronized (ref) {
					ref.wait();

					char ch = ref[0];
					if (chars == null || chars.contains(ch)) return ch;
					if (ch == (VK_CTRL|VK_C)) return 0;
				}
				Tty.beep();
			}
		} catch (InterruptedException e) {
			return 0;
		} finally {
			if (prefix != null) Tty.removeBottomLine(prefix);
			Tty.popHandler();
		}
	}
	//endregion
	int lineHeight;

	boolean done;
	boolean cancel;
	int value;

	public int getValue() {
		synchronized (this) {
			while (!done) {
				try {
					wait();
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}
		if (cancel) throw new CancellationException();

		return value;
	}

	abstract String choice();
	final void confirm() {
		Tty.write(Cursor.moveVertical(-lineHeight)+SUCCESS+"\n"+Screen.clearAfter+choice());
		done = true;
		synchronized (this) {notifyAll();}
	}
	final void cancel() {
		Tty.write(Cursor.moveVertical(-lineHeight)+FAILURE+"\n"+Screen.clearAfter+choice()+BLACK_END+"  \033["+Tty.RED+"m操作被用户取消！\033[0m\n");
		//System.exit(1);
		cancel = true;
		done = true;
		synchronized (this) {notifyAll();}
	}

	final void applyHeightChange(CharList sb, int pos) {
		var prevLineHeight = lineHeight;
		lineHeight = Tty.splitByWidth(sb.toString(), Tty.getColumns()).size() + 2;
		if (lineHeight < prevLineHeight) sb.append("\033[").append(prevLineHeight - lineHeight).append("M");
		if (lineHeight > prevLineHeight && prevLineHeight != 0) {
			sb.insert(pos, Cursor.moveVertical(1)+"\033["+(lineHeight-prevLineHeight)+"L"+Cursor.moveVertical(-1));
		}
	}
}
