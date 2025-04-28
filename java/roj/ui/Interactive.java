package roj.ui;

import roj.text.CharList;
import roj.ui.Terminal.Cursor;
import roj.ui.Terminal.Screen;

import java.awt.event.KeyEvent;

import static java.awt.event.KeyEvent.VK_C;
import static roj.ui.Terminal.VK_CTRL;

/**
 * @author Roj234
 * @since 2025/4/29 0029 0:04
 */
public abstract class Interactive implements Console {
	private static final boolean THEME2 = true;
	private static final boolean UNICODE = Terminal.isFullUnicode();

	private static final String LINE = "│", END = "└";
	public static final String RESET = "\033[0m";
	private static final String BLACK = "\033["+(Terminal.BLACK+Terminal.HIGHLIGHT)+"m";
	private static final String BLACK_LINE = BLACK+LINE+"  "+RESET;
	private static final String BLACK_END = BLACK+END;

	private static final String SELECTED = THEME2 ? "●" : "\033["+Terminal.GREEN+"m>\033[0m", UNSELECTED = THEME2 ? "○" : " ";
	private static final String SELECTED_MULTI = UNICODE ? "◼" : "[+]", UNSELECTED_MULTI = UNICODE ? "◻" : "[ ]", HOVERED_MULTI = UNICODE ? "◻" : "[•]";

	private static final String BLUE = "\033["+(Terminal.CYAN)+"m";
	private static final String PENDING = BLUE+"◇ "+RESET;
	private static final String PENDING_LINE = BLUE+LINE+"  ";

	private static final String
			SUCCESS = "\033["+Terminal.GREEN+"m◆",
			FAILURE = "\033["+Terminal.RED+"m▲",
			WARNING = "\033["+Terminal.YELLOW+"m▲",
			ERROR = "\033["+Terminal.RED+"m■",
			INFO = "\033["+Terminal.CYAN+"m●";

	public static void start(AnsiString message) {
		var sb = new CharList().append(BLACK+"┌  "+RESET);
		message.writeAnsi(sb, "\n"+BLACK+LINE+" "+RESET);
		sb.append("\n"+BLACK+LINE+" "+RESET+"\n");
		Terminal.directWrite(sb);
	}
	public static void end(AnsiString message) {
		var sb = new CharList().append(BLACK+"└  "+RESET);
		message.writeAnsi(sb, "\n"+BLACK+LINE+" "+RESET);
		Terminal.directWrite(sb.append('\n'+RESET));
	}

	private static int writeWithTrace(AnsiString message, String icon) {
		var sb = new CharList().append(icon).append(" "+RESET);
		message.writeAnsi(sb, "\n"+BLACK+LINE+" "+RESET);
		sb.append("\n"+BLACK+LINE+" "+RESET+"\n");
		Terminal.directWrite(sb);
		return Terminal.splitByWidth(sb.toString(), Terminal.windowWidth).size();
	}

	/**
	 * 输入之前step返回的数值即可撤销这个step
	 */
	public static void stepUndo(int lh) {Terminal.directWrite(Cursor.moveVertical(-lh)+Screen.clearLineAfter);}
	public static int stepSuccess(AnsiString message) {return writeWithTrace(message, SUCCESS);}
	public static int stepWarn(AnsiString message) {return writeWithTrace(message, WARNING);}
	public static int stepInfo(AnsiString message) {return writeWithTrace(message, INFO);}
	public static int stepError(AnsiString message) {return writeWithTrace(message, ERROR);}

	public static boolean confirm(AnsiString message) {
		var prev = Terminal.getConsole();
		var callback = new Interactive() {
			int width;

			@Override
			public void registered() {
				lineHeight = 4;

				var sb = new CharList().append(PENDING);
				var before = PENDING_LINE+RESET+UNSELECTED+" Yes / ";
				width = Terminal.getStringWidth(before);

				message.writeAnsi(sb).append(RESET+"\n").append(before).append(SELECTED+" No\n"+PENDING_LINE+"\n"+END+"\n\033[0m");
				Terminal.directWrite(sb);
			}

			@Override
			@SuppressWarnings("fallthrough")
			public void keyEnter(int keyCode, boolean isVirtualKey) {
				switch (keyCode) {
					case KeyEvent.VK_ESCAPE, VK_CTRL | VK_C: cancel(); Terminal.setConsole(prev);break;
					case KeyEvent.VK_Y, KeyEvent.VK_N: setValue(keyCode == KeyEvent.VK_Y ? 1 : 0); //fallthrough
					case KeyEvent.VK_ENTER: confirm(); Terminal.setConsole(prev);break;
					case KeyEvent.VK_TAB, KeyEvent.VK_SPACE: setValue(value ^ 1);break;
					case KeyEvent.VK_LEFT, KeyEvent.VK_UP: setValue(1);break;
					case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN: setValue(0);break;
					default: Terminal.beep();break;
				}
			}

			String choice() {return BLACK_LINE+(value!=0?"Yes":"No")+"\033[0J\n"+BLACK_LINE+"\n";}

			private void setValue(int newValue) {
				if (newValue == value) return;
				value = newValue;

				Terminal.directWrite("\033[3F\033[4G"+(newValue!=0? SELECTED : UNSELECTED)+Cursor.toHorizontal(width)+(newValue==0? SELECTED : UNSELECTED)+"\n\n\n\033[3D");
			}
		};

		Terminal.setConsole(callback);
		return callback.getValue() != 0;
	}
	public static int radio(AnsiString message, Completion... choices) {return radio(message, 0, choices);}
	public static int radio(AnsiString message, int def, Completion... choices) {
		if (choices.length == 1) return 0;

		var prev = Terminal.getConsole();
		var callback = new Select(prev, message, choices);
		callback.value = def;
		Terminal.setConsole(callback);
		return callback.getValue();
	}
	private static class Select extends Interactive {
		final Console prev;
		final AnsiString message;
		final Completion[] choices;

		public Select(Console prev, AnsiString message, Completion... choices) {
			this.message = message;
			this.prev = prev;
			this.choices = choices;
		}

		@Override
		public void registered() {
			var sb = new CharList();
			message.writeAnsi(sb.append(PENDING));
			writeChoice(sb.append(RESET), value);

			Terminal.directWrite(sb.append("\n"+PENDING_LINE+"\n"+END+"\n"));
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
					Terminal.setConsole(prev);
				}
				case KeyEvent.VK_ENTER -> {
					confirm();
					Terminal.setConsole(prev);
				}
				case KeyEvent.VK_TAB -> setValue((value+1) % choices.length);
				case KeyEvent.VK_LEFT, KeyEvent.VK_UP -> setValue(value == 0 ? value : value-1);
				case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> setValue(value == choices.length-1 ? value : value+1);
				default -> Terminal.beep();
			}
		}

		String choice() {return BLACK_LINE+choices[value].completion+"\n"+BLACK_LINE+"\n";}

		private void setValue(int newValue) {
			if (newValue == value) return;
			value = newValue;

			CharList sb = new CharList().append(Cursor.moveVertical(-lineHeight));
			writeChoice(sb, newValue);
			Terminal.directWrite(sb.append("\n\n\n"));
		}
	}
	public static int checkbox(AnsiString message, Completion... choices) {return checkbox(message, 0, choices);}
	public static int checkbox(AnsiString message, int def, Completion... choices) {
		var prev = Terminal.getConsole();
		var callback = new Select(prev, message, choices) {
			int selected;

			@Override
			public void registered() {
				var sb = new CharList();
				message.writeAnsi(sb.append(PENDING));
				writeChoice(sb.append(RESET), selected);

				Terminal.directWrite(sb.append("\n"+PENDING_LINE+"\n"+END+"\n"));
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
						Terminal.setConsole(prev);
					}
					case KeyEvent.VK_ENTER -> {
						confirm();
						Terminal.setConsole(prev);
					}
					case KeyEvent.VK_SPACE -> {
						value ^= 1<<selected;
						toggleSelection();
					}
					case KeyEvent.VK_TAB -> setValue((selected+1) % choices.length);
					case KeyEvent.VK_LEFT, KeyEvent.VK_UP -> setValue(selected == 0 ? selected : selected-1);
					case KeyEvent.VK_RIGHT, KeyEvent.VK_DOWN -> setValue(selected == choices.length-1 ? selected : selected+1);
					default -> Terminal.beep();
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
				Terminal.directWrite(sb.append("\n\n\n"));
			}

			private void setValue(int newValue) {
				if (newValue == selected) return;
				selected = newValue;
				toggleSelection();
			}
		};
		callback.value = def;
		Terminal.setConsole(callback);
		return callback.getValue();
	}

	public static Progress progress(AnsiString title, int extraLines) {
		CharList sb = new CharList().append(PENDING);
		title.writeAnsi(sb).append(RESET+'\n');
		Terminal.directWrite(sb);
		return new Progress(title, extraLines);
	}
	public static class Progress extends Interactive {
		public Progress(AnsiString title, int extraLines) {
			this.title = title;
			this.extraLines = new AnsiString[extraLines];
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
		AnsiString title, before, after;
		AnsiString[] extraLines;

		public void setTitle(AnsiString title) {this.title = title;}
		public void setProgress(float progress) {this.progress = progress;}
		public float getProgress() {return progress;}

		public void setBeforeProgress(AnsiString before) {this.before = before;}

		public void setAfterProgress(AnsiString title) {this.after = title;}
		public void setExtraLine(int line, AnsiString text) {extraLines[line] = text;}
		public void setExtraLines(int count) {this.extraLines = new AnsiString[count];}

		public void setAnimation(boolean animation) {this.animation = animation;}

		public void draw() {
			var sb = new CharList();
			sb.append(Cursor.moveVertical(-lineHeight));
			int len = sb.length();

			int filled = Math.round(progress * progressWidth);
			sb.append(PENDING_LINE+RESET);
			if (before != null) before.writeAnsi(sb).append(RESET).append(' ');
			sb.append("[");
			if (animation) Terminal.Color.sonic("=".repeat(filled), sb);
			else sb.padEnd('=', filled);
			sb.padEnd(' ', progressWidth - filled).append("]"+RESET);
			if (after != null) after.writeAnsi(sb.append(' ')).append(RESET);

			sb.append('\n');
			for (int i = 0; i < extraLines.length; i++) {
				AnsiString extra = extraLines[i];
				sb.append(PENDING_LINE);
				if (extra != null) extra.writeAnsi(sb.append(RESET)).append(RESET);
				else sb.append(Screen.clearLineAfter);
				sb.append('\n');
			}

			boolean b = lineHeight != 0;

			applyHeightChange(sb, len);

			sb.append(b ? "\n\n" : PENDING_LINE+"\n"+END+"\n");
			Terminal.directWrite(sb);
		}

		public void success() {withMessage(null, SUCCESS);}
		public void warning(AnsiString message) {withMessage(message, WARNING);}
		public void error(AnsiString message) {withMessage(message, ERROR);}

		private void withMessage(AnsiString message, String head) {
			var sb = new CharList().append(Cursor.moveVertical(-lineHeight - 1)).append(head).append("\n"+Screen.clearAfter+BLACK_LINE);
			if (message != null) message.writeAnsi(sb, "\n"+BLACK_LINE).append("\n"+BLACK_LINE);
			Terminal.directWrite(sb.append('\n'));
		}

		@Override String choice() {return "";}
	}

	int lineHeight;

	boolean done;
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

		return value;
	}

	abstract String choice();
	final void confirm() {
		Terminal.directWrite(Cursor.moveVertical(-lineHeight)+SUCCESS+"\n"+Screen.clearAfter+choice());
		done = true;
		synchronized (this) {notifyAll();}
	}
	final void cancel() {
		Terminal.directWrite(Cursor.moveVertical(-lineHeight)+FAILURE+"\n"+Screen.clearAfter+choice()+BLACK_END+"  \033["+Terminal.RED+"m操作被用户取消！\033[0m\n");
		System.exit(1);
	}

	final void applyHeightChange(CharList sb, int pos) {
		var prevLineHeight = lineHeight;
		lineHeight = Terminal.splitByWidth(sb.toString(), Terminal.windowWidth).size() + 2;
		if (lineHeight < prevLineHeight) sb.append("\033[").append(prevLineHeight - lineHeight).append("M");
		if (lineHeight > prevLineHeight && prevLineHeight != 0) {
			sb.insert(pos, Cursor.moveVertical(1)+"\033["+(lineHeight-prevLineHeight)+"L"+Cursor.moveVertical(-1));
		}
	}
}
