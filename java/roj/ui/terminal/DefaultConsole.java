package roj.ui.terminal;

import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.concurrent.timing.ScheduleTask;
import roj.concurrent.timing.Scheduler;
import roj.config.data.CInt;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.ui.*;

import java.awt.datatransfer.*;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.regex.Matcher;

import static java.awt.event.KeyEvent.*;
import static roj.ui.Terminal.*;

/**
 * @author Roj234
 * @since 2023/11/18 0018 16:42
 */
public class DefaultConsole implements Console {
	private static final int MAX_INPUT = 500;

	protected final CharList prompt = new CharList(100);
	private String realPrompt;

	private String staticHighlight;
	private AnsiString highlight;
	private int prefixLen, prefixCLen;
	private int cursorMoveBound = 24;

	private final CharList input = new CharList();
	private int cursor, scrollLeft;
	private final MyBitSet invisible = new MyBitSet();
	private String lastInput;

	private int historyPos;
	private final List<String> history = new SimpleList<>();
	private String currentHistory;

	private final List<Completion> tabs = new SimpleList<>();
	private int tabCursor = -1, tabId;

	private boolean _history = true, _echo = true;
	private Runnable interruptHandler;
	private List<IntFunction<Boolean>> keyHandler = Collections.emptyList();

	private boolean _isDirty;

	public DefaultConsole(String prompt) {
		setPrompt(prompt);
		staticHighlight = "\u001b[97m"; // light white
	}

	public String getPrompt() { return realPrompt; }
	public void setPrompt(String prompt) {
		this.realPrompt = prompt;
		this.prompt.clear();
		this.prompt.append("\u001b[0m").append(prompt);

		prefixLen = prompt.length()+4;
		prefixCLen = Terminal.getStringWidth(prompt);

		if (Terminal.hasBottomLine(this.prompt)) doRender();
	}
	public void setDefaultHighlight(String postfix) { staticHighlight = postfix; }
	public void setCursorMoveBound(int cmb) { cursorMoveBound = cmb; }

	public void registered() {doRender();}
	public void unregistered() {
		Terminal.removeBottomLine(prompt, true);
		Terminal.removeBottomLine(tooltip, true);
	}
	public void idleCallback() {
		if (_isDirty) {_isDirty = false;doRender();}
	}

	private void checkAnsi() {
		invisible.clear();
		Matcher m = ANSI_ESCAPE.matcher(input);

		int i = 0;
		while (m.find(i)) {
			invisible.addRange(m.start(), m.end());
			i = m.end();
		}
	}
	private void setComplete(int id) {
		if (tabCursor < 0) {
			tabCursor = cursor;
			if (lastInput == null)
				lastInput = input.toString();
		}

		Completion c = tabs.get(id);
		if (c.description != null) {
			c.description.writeLimited(tooltip(), new CInt(Terminal.windowWidth), true);
			displayTooltip(5000);
		} else {
			displayTooltip(-1);
		}

		tabId = id;
		input.clear();
		input.append(lastInput);
		input.replace(tabCursor+c.offset, tabCursor, c.completion.writeAnsi(IOUtil.getSharedCharBuf()).append("\u001b[0m").append(staticHighlight));
		if (!isAutoComplete) cursor = tabCursor+c.offset + c.completion.length();

		checkAnsi();
	}
	private boolean endCompletion(boolean insert) {
		if (lastInput == null) return false;

		input.clear();
		input.append(lastInput);
		lastInput = null;

		if (highlight != null) {
			highlight = null;
			if (tabCursor < 0) {
				checkAnsi();
				return false;
			}
		}

		assert tabCursor >= 0;

		Completion c = tabs.get(tabId);
		AnsiString str = c.completion;
		if (insert) {
			input.replace(tabCursor+c.offset, tabCursor, str.writeRaw(IOUtil.getSharedCharBuf()));
			if (isAutoComplete) cursor = tabCursor+c.offset + str.length();
			afterInput();
		} else {
			if (cursor > tabCursor) {
				if (cursor <= tabCursor+str.length()) cursor = tabCursor;
				else cursor -= str.length();
			}
		}

		checkAnsi();

		tabs.clear();
		tabId = 0;
		tabCursor = -1;
		isAutoComplete = false;
		return true;
	}
	private void afterInput() { historyPos = history.size(); }
	private void setFromHistory(String history) {
		input.clear(); input.append(history);
		cursor = input.length();
		highlight = null;
	}

	public void keyEnter(int keyCode, boolean isVirtualKey) {
		if (!isVirtualKey) {
			endCompletion(false);

			if (input.length() > MAX_INPUT) { beep(); return; }

			input.insert(cursor, (char)keyCode);
			cursor ++;

			if (autoComplete) {
				complete(lastInput != null ? lastInput.substring(0, cursor) : input.substring(0, cursor), tabs);
				if (tabs.size() > 0) {
					isAutoComplete = true;
					setComplete(0);
				}
			}

			afterInput();
		} else {
			switch (keyCode) {
				case VK_CTRL|VK_B:
					var clipboard = GuiUtil.CLIPBOARD;
					if (clipboard != null)clipboard.setContents(new StringSelection(Terminal.stripAnsi(new CharList(input)).toStringAndFree()), null);
					else System.err.println("您的环境没有剪贴板，复制失败");
				return;
				case VK_CTRL|VK_V:
					clipboard = GuiUtil.CLIPBOARD;
					if (clipboard != null) {
						DataFlavor stringFlavor = DataFlavor.stringFlavor;
						if (clipboard.isDataFlavorAvailable(stringFlavor)) {
							try {
								String text = filterText(clipboard.getData(stringFlavor).toString());
								endCompletion(false);
								input.insert(cursor, text);
								cursor += text.length();
								afterInput();
							} catch (UnsupportedFlavorException | IOException e) {
								e.printStackTrace();
							}
						}
					} else System.err.println("您的环境没有剪贴板，粘贴失败");
				break;
				case VK_CTRL|VK_UP: scrollLeft++; break;
				case VK_CTRL|VK_DOWN: scrollLeft--; break;
				case VK_CTRL|VK_LEFT: cursor = 0; break;
				case VK_CTRL|VK_RIGHT: cursor = input.length() - invisible.size(); break;

				case VK_F1: printHelp(); return;
				case VK_F4:
					autoComplete = !autoComplete;
					tooltip().append(autoComplete ?
						"\u001b[92m+即时补全" :
						"\u001b[91m-即时补全");
					displayTooltip(1000);
				return;
				case VK_ESCAPE:
					if (!endCompletion(false)) return;
				break;
				case VK_ENTER:
					if (endCompletion(true)) break;

					String cmd = input.toString();
					if (!evaluate(cmd)) return;

					input.clear();

					if (_history && !cmd.isEmpty()) {
						history.remove(cmd);
						while (history.size() > 255) history.remove(0);
						history.add(cmd);
					}

					cursor = scrollLeft = 0;
					afterInput();
				break;
				case VK_UP:
					if (!_echo) return;

					if (tabs.size() > 0) {
						setComplete((tabId == 0 ? tabs.size() : tabId) - 1);
						break;
					}

					if (historyPos > 0) {
						if (historyPos == history.size()) {
							endCompletion(false);
							currentHistory = input.toString();
						}

						setFromHistory(history.get(--historyPos));
					} else {
						return; // no update
					}
				break;
				case VK_DOWN:
					if (!_echo) return;

					if (tabs.size() > 0) {
						setComplete((tabId+1) % tabs.size());
						break;
					}

					if (historyPos == history.size()-1) {
						if (currentHistory != null) {
							setFromHistory(currentHistory);
							currentHistory = null;
						}
						historyPos++;
					} else if (historyPos < history.size()) {
						setFromHistory(history.get(++historyPos));
					} else {
						return;
					}
				break;
				case VK_LEFT:
					if (_echo && cursor > 0) cursor--;
					else return;
				break;
				case VK_RIGHT:
					if (_echo && cursor < input.length() - invisible.size()) cursor++;
					else return;
				break;
				case VK_TAB:
					if (!_echo || input.length() > MAX_INPUT) { beep(); return; }

					if (tabs.size() > 0) {
						endCompletion(true);
						break;
					}

					complete(lastInput != null ? lastInput.substring(0, cursor) : input.substring(0, cursor), tabs);
					if (tabs.size() > 0) {
						if (!isAutoComplete) {
							String prev = null;
							int lca = 0;
							int offset = 1;
							for (Completion tab : tabs) {
								if (tab.offset != offset) {
									if (offset != 1) {
										lca = 0;
										break;
									}
									offset = tab.offset;
								}

								String rawStr = tab.completion.toString();
								if (prev == null) {
									prev = rawStr;
									lca = prev.length();
								} else {
									lca = Math.min(rawStr.length(), lca);
									for (int i = 0; i < lca; i++) {
										if (prev.charAt(i) != rawStr.charAt(i)) {
											lca = i;
											break;
										}
									}
									if (lca == 0) break;
								}
							}

							if (lca > -offset) {
								tabs.clear();

								if (lastInput != null) {
									input.clear();
									input.append(lastInput);
									lastInput = null;
									highlight = null;
								}
								input.insert(cursor, prev, -offset, lca);
								cursor += lca + offset;
								afterInput();
								checkAnsi();

								break;
							}
						}
						setComplete(0);
					}
					else return;
				break;
				case VK_BACK_SPACE:
					boolean isDeletingTabComplete = tabCursor >= 0 && (cursor == 0 || (cursor > tabCursor && cursor <= tabCursor+tabs.get(tabId).completion.length()));
					if (endCompletion(false) && isDeletingTabComplete) break;

					if (cursor == 0) return;
					input.delete(--cursor);
					afterInput();
				break;
				default:
					for (int i = 0; i < keyHandler.size(); i++) {
						var result = keyHandler.get(i).apply(keyCode);
						if (result != null) {
							if (result) _isDirty = true;
							return;
						}
					}

					if (keyCode == (VK_CTRL|VK_C)) {
						var r = interruptHandler;
						if (r == null) System.exit(0);
						else r.run();
					}
					return;
			}
		}

		_isDirty = true;
	}

	private static String filterText(String string) {
		int i = string.lastIndexOf('\n');
		if (i >= 0) string = string.substring(i+1);
		return string.replace('\t', ' ');
	}

	protected final void printCommand() {
		String pp = prompt.toString();

		prompt.setLength(prefixLen);
		prompt.append("\u001b[0m");

		System.out.println(pp);
	}
	protected synchronized final void doRender() {
		assert Terminal.getConsole() == this;
		if (!ANSI_OUTPUT) return;

		if (!_echo) {
			prompt.setLength(prefixLen);
			Terminal.renderBottomLine(prompt, true, prefixCLen+1);
			return;
		}

		if (tabCursor < 0 && highlight == null) {
			highlight = highlight(input);
			if (highlight != null) {
				lastInput = input.toString();
				input.clear();
				highlight.writeAnsi(input);
				checkAnsi();
			}
		}

		int absCursor = getDisplayWidth(cursor, false);
		int relCursor = absCursor-getDisplayWidth(scrollLeft, true);
		int maxWidth = computeMaxWidth();

		reComputeWidth: {
			if (relCursor >= maxWidth) {
				scrollLeft = Math.max(relCursor-maxWidth+1, scrollLeft+cursorMoveBound);
				scrollLeft = invisible.nextFalse(scrollLeft);
			} else if (relCursor <= 0) {
				scrollLeft = Math.max(0, invisible.prevFalse(scrollLeft+Math.min(-cursorMoveBound, relCursor)));
			} else break reComputeWidth;

			relCursor = absCursor-getDisplayWidth(scrollLeft, true);
			maxWidth = computeMaxWidth();
		}

		prompt.setLength(prefixLen);

		// POST: write
		if (scrollLeft > 0) {
			prompt.append("\u001b[5;41;97m+").append("\u001b[0m"); // + before, 红底白字闪烁
			relCursor++;
		}

		int end = limitWidth(scrollLeft, maxWidth);
		if (scrollLeft > end) {
			Terminal.directWrite("RojLib Warning: [VT]找到无效的ANSI转义.\n");
			scrollLeft = 0;
		}
		prompt.append(staticHighlight).append(input, scrollLeft, end);

		if (end < input.length()) prompt.append("\u001b[1;5;41;97m+"); // + after
		prompt.append("\u001b[0m");

		Terminal.renderBottomLine(prompt, true, prefixCLen+relCursor+1);
	}
	private int computeMaxWidth() {
		int maxWidth = Terminal.windowWidth-prefixCLen;
		if (scrollLeft > 0) maxWidth--;
		if (limitWidth(scrollLeft, maxWidth) < input.length()) maxWidth--;
		return maxWidth;
	}
	private int getDisplayWidth(int visLen, boolean countInvisible) {
		if (visLen == 0) return 0;

		int width = 0;
		for (int i = 0; i < input.length(); i++) {
			if (!invisible.contains(i)) {
				width += Terminal.getCharWidth(input.charAt(i));
				if (--visLen == 0) break;
			} else if (countInvisible) {
				if (--visLen == 0) break;
			}
		}
		return width;
	}
	private int limitWidth(int i, int maxWidth) {
		if (maxWidth == 0) return i;

		int width = 0;
		for (; i < input.length(); i++) {
			if (!invisible.contains(i)) {
				width += Terminal.getCharWidth(input.charAt(i));
				if (width >= maxWidth) return i;
			}
		}
		return input.length();
	}

	private boolean autoComplete, isAutoComplete;
	private final CharList tooltip = new CharList();
	private ScheduleTask removeTooltip;
	protected final CharList tooltip() { tooltip.clear(); return tooltip; }
	protected final void displayTooltip(int timeout) {
		if (removeTooltip != null) removeTooltip.cancel();
		if (timeout < 0) {
			Terminal.removeBottomLine(tooltip, true);
			return;
		}

		Terminal.renderBottomLine(tooltip, true, 0);
		removeTooltip = Scheduler.getDefaultScheduler().delay(() -> displayTooltip(-1), timeout);
	}

	public static final String KEY_SHORTCUT = """
		F1: 帮助
		F4: 开关即时补全
		Tab: 补全 ENTER确认 ESC取消
		Ctrl+A: 全选(WIP)
		Ctrl+B: 复制输入
		Ctrl+C: 退出
		Ctrl+V: 粘贴
		Ctrl+←: 光标移至开头
		Ctrl+→: 光标移至结尾""";
	protected void printHelp() {
		CLIBoxRenderer.DEFAULT.render(new String[][]{
			new String[] { "Roj234的终端模拟器 帮助", "简介", "快捷键" },
			new String[] {"""
怎么，其实又是一个JLine不会用的人啊/doge 可惜这里不能放图，哈哈哈
有兴趣可以看看我是如何计算字符显示长度的
非常邪门的写法，但是很好用
https://unix.stackexchange.com/questions/245013/get-the-display-width-of-a-string-of-characters""",
				KEY_SHORTCUT }
		});
	}
	protected AnsiString highlight(CharList input) {return null;}
	protected void complete(String prefix, List<Completion> out) {}
	protected boolean evaluate(String cmd) {return true;}

	public void setInputEcho(boolean echo) {this._echo = echo; _isDirty = true;}
	public void setInputHistory(boolean history) {this._history = history;}
	@Deprecated
	public void onKeyboardInterrupt(Runnable o) {interruptHandler = o;}
	public void onVirtualKey(IntFunction<Boolean> h) {
		if (keyHandler.isEmpty()) keyHandler = new SimpleList<>();
		keyHandler.add(h);
	}
	public void removeKeyHandler(IntFunction<Boolean> h) {keyHandler.remove(h);}
}