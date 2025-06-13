package roj.ui;

import roj.collect.ArrayList;
import roj.collect.BitSet;
import roj.concurrent.ScheduleTask;
import roj.concurrent.Scheduler;
import roj.config.data.CInt;
import roj.gui.GuiUtil;
import roj.io.IOUtil;
import roj.text.CharList;
import roj.text.LineReader;

import java.util.Collections;
import java.util.List;
import java.util.function.IntFunction;
import java.util.regex.Matcher;

import static java.awt.event.KeyEvent.*;
import static roj.ui.Terminal.*;

/**
 * @author Roj234
 * @since 2023/11/18 16:42
 */
public class VirtualTerminal implements KeyHandler {
	private static final int HISTORY_MAX = 100;
	private static final int INPUT_MAX = 500;

	protected final CharList prompt = new CharList(100);
	private String realPrompt;

	private String staticHighlight;
	private Text highlight;
	private int prefixLen, prefixCLen;
	private int cursorMoveBound;

	private final CharList input = new CharList();
	private int cursor, scrollLeft;
	private final BitSet invisible = new BitSet();
	private String lastInput;

	private int historyPos;
	private final List<String> history = new ArrayList<>();
	private String currentHistory;

	private final List<Completion> tabs = new ArrayList<>();
	private int tabCursor = -1, tabId;

	private boolean _history = true, _echo = true;
	private Runnable interruptHandler;
	private List<IntFunction<Boolean>> keyHandler = Collections.emptyList();

	private boolean _isDirty, _selectAll;

	public VirtualTerminal(String prompt) {
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
	public int getCursorMoveBound() {return cursorMoveBound == 0 ? computeMaxWidth() - 16 : cursorMoveBound;}

	public void registered() {doRender();}
	public void unregistered() {
		Terminal.removeBottomLine(prompt);
		Terminal.removeBottomLine(tooltip);
	}
	public void render() {
		if (_isDirty) {_isDirty = false;doRender();}
	}

	protected CharList input() {return input;}
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
		if (insert && c.isTip) {
			beep();
			tooltip().append("请填充该参数的内容，而不是补全它");
			displayTooltip(1000);
			insert = false;
		} else if (c.description != null) {
			Terminal.removeBottomLine(tooltip);
		}
		Text str = c.completion;
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
	private void afterInput() { historyPos = history.size();_selectAll = false; }
	private void setFromHistory(String history) {
		input.clear(); input.append(history);
		cursor = input.length();
		highlight = null;
	}

	public void keyEnter(int keyCode, boolean isVirtualKey) {
		if (!isVirtualKey) {
			endCompletion(false);

			if (input.length() > INPUT_MAX) { beep(); return; }

			input.insert(cursor, (char)keyCode);
			cursor ++;

			if (autoComplete) {
				complete(lastInput != null ? lastInput.substring(0, cursor) : input.substring(0, cursor), tabs, true);
				if (tabs.size() > 0) {
					isAutoComplete = true;
					setComplete(0);
				}
			}

			afterInput();
		} else {
			switch (keyCode) {
				case VK_CTRL|VK_A -> {
					if (input.length() > 0) _selectAll = true;
				}
				case VK_CTRL|VK_V -> {
					try {
						var text = GuiUtil.getClipboardText();
						if (text != null && !text.isEmpty()) {
							var itr = LineReader.create(text, false);
							for (;;) {
								String line = itr.next();

								endCompletion(false);
								input.insert(cursor, line);
								cursor += line.length();
								afterInput();

								if (!itr.hasNext()) break;
								keyEnter(VK_ENTER, true);
							}
						}
					} catch (Throwable e) {
						System.err.println("您的环境没有剪贴板，粘贴失败");
					}
				}
				case VK_CTRL|VK_UP -> {
					var tmp = invisible.nextFalse(scrollLeft);
					if (tmp < input.length()) scrollLeft = tmp;
				}
				case VK_CTRL|VK_DOWN -> {
					var tmp = invisible.prevFalse(scrollLeft);
					if (tmp >= 0) scrollLeft = tmp;
				}
				case VK_CTRL|VK_LEFT -> cursor = 0;
				case VK_CTRL|VK_RIGHT -> cursor = input.length() - invisible.size();
				case VK_F1 -> {printHelp();return;}
				case VK_F4 -> {
					autoComplete = !autoComplete;
					tooltip().append(autoComplete ? "\u001b[92m+即时补全" : "\u001b[91m-即时补全");
					displayTooltip(1000);
					return;
				}
				case VK_ESCAPE -> {
					if (!endCompletion(false)) return;
				}
				case VK_ENTER -> {
					doRender(); // 适配粘贴多行命令的情况
					if (endCompletion(true)) break;

					String cmd = input.trim().toString();
					boolean ok = evaluate(cmd);
					if (!ok) Terminal.beep();

					input.clear();

					if (_history && !cmd.isEmpty()) {
						history.remove(cmd);
						while (history.size() > HISTORY_MAX) history.remove(0);
						history.add(cmd);
					}

					cursor = scrollLeft = 0;
					afterInput();
				}
				case VK_UP -> {
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
				}
				case VK_DOWN -> {
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
				}
				case VK_LEFT -> {
					if (_echo && cursor > 0) cursor--;
					else return;
				}
				case VK_RIGHT -> {
					if (_echo && cursor < input.length() - invisible.size()) cursor++;
					else return;
				}
				case VK_TAB -> {
					if (!_echo || input.length() > INPUT_MAX) { beep(); return; }

					if (tabs.size() > 0) {
						endCompletion(true);
						break;
					}

					complete(lastInput != null ? lastInput.substring(0, cursor) : input.substring(0, cursor), tabs, false);
					if (tabs.size() > 0) {
						tabs.sort((o1, o2) -> o1.completion.toString().substring(-o1.offset).compareTo(o2.completion.toString().substring(-o2.offset)));
						if (!isAutoComplete) {
							String prev = null;
							int lca = 0;
							int offset = 1;
							for (Completion tab : tabs) {
								if (tab.isTip) {
									lca = offset = 0;
									break;
								}

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
					} else return;
				}
				case VK_BACK_SPACE -> {
					if (_selectAll) {
						endCompletion(false);
						input.clear();
						cursor = 0;
					} else {
						boolean isDeletingTabComplete = tabCursor >= 0 && (cursor == 0 || (cursor > tabCursor && cursor <= tabCursor + tabs.get(tabId).completion.length()));
						if (endCompletion(false) && isDeletingTabComplete) break;

						if (cursor == 0) return;
						input.delete(--cursor);
					}
					afterInput();
				}
				case VK_CTRL|VK_C -> {
					if (_selectAll) {
						if (!GuiUtil.setClipboardText(Terminal.stripAnsi(new CharList(input)).toStringAndFree()))
							System.err.println("您的环境没有剪贴板，复制失败");
						_selectAll = false;
						break;
					}

					var r = interruptHandler;
					if (r != null) r.run();
					else if (!runKeyHandlers(keyCode)) {
						System.exit(0);
					}
				}
				default -> {
					runKeyHandlers(keyCode);
					return;
				}
			}
		}

		_isDirty = true;
	}
	private boolean runKeyHandlers(int keyCode) {
		for (int i = 0; i < keyHandler.size(); i++) {
			var result = keyHandler.get(i).apply(keyCode);
			if (result != null) {
				if (result) _isDirty = true;
				return true;
			}
		}
		return false;
	}

	protected final void printCommand() {
		String pp = prompt.toString();

		prompt.setLength(prefixLen);
		if (!ANSI_OUTPUT) return;
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
				scrollLeft = Math.max(relCursor-maxWidth+1, scrollLeft+getCursorMoveBound());
				scrollLeft = invisible.nextFalse(scrollLeft);
			} else if (relCursor <= 0) {
				scrollLeft = Math.max(0, invisible.prevFalse(scrollLeft+Math.min(-getCursorMoveBound(), relCursor)));
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
		if (_selectAll) prompt.append("\u001b[7m");
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
			Terminal.removeBottomLine(tooltip);
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
		Box.DEFAULT.render(new String[][]{
			new String[] { "终端模拟器", "简介", "快捷键" },
			new String[] {"""
只是一个不会用JLine的人啊/doge 可惜这里不能放图，哈哈哈
有兴趣可以看看我是如何计算字符显示长度的
非常邪门的写法，但是很好用
https://unix.stackexchange.com/questions/245013/get-the-display-width-of-a-string-of-characters""",
				KEY_SHORTCUT }
		});
	}
	protected Text highlight(CharList input) {return null;}
	protected void complete(String prefix, List<Completion> out, boolean oninput) {}
	protected boolean evaluate(String cmd) {return true;}

	public void setInputEcho(boolean echo) {this._echo = echo; _isDirty = true;}
	public void setInputHistory(boolean history) {this._history = history;}
	@Deprecated
	public void onKeyboardInterrupt(Runnable o) {interruptHandler = o;}
	public void onVirtualKey(IntFunction<Boolean> h) {
		if (keyHandler.isEmpty()) keyHandler = new ArrayList<>();
		keyHandler.add(h);
	}
	public void removeKeyHandler(IntFunction<Boolean> h) {keyHandler.remove(h);}
	public void setAutoComplete(boolean autoComplete) {this.autoComplete = autoComplete;}
}