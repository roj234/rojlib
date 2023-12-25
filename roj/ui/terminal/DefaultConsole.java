package roj.ui.terminal;

import roj.collect.MyBitSet;
import roj.collect.SimpleList;
import roj.concurrent.timing.ScheduleTask;
import roj.concurrent.timing.Scheduler;
import roj.io.IOUtil;
import roj.math.MutableInt;
import roj.text.CharList;
import roj.text.GB18030;
import roj.text.UTF8MB4;
import roj.text.UnsafeCharset;
import roj.ui.AnsiString;
import roj.ui.CLIBoxRenderer;
import roj.ui.CLIUtil;
import roj.ui.Console;
import roj.util.ByteList;

import java.awt.*;
import java.awt.datatransfer.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;

import static java.awt.event.KeyEvent.*;
import static roj.ui.CLIUtil.ANSI_ESCAPE;
import static roj.ui.CLIUtil.VK_CTRL;

/**
 * @author Roj234
 * @since 2023/11/18 0018 16:42
 */
public class DefaultConsole implements Console {
	private static final int MAX_INPUT = 500;

	protected final CharList prompt = new CharList(100);

	private String staticHighlight;
	private AnsiString highlight;
	private int prefixLen, prefixCLen;
	private int cursorMoveBound = 24;

	private static final UnsafeCharset CE = GB18030.is(Charset.defaultCharset()) ? GB18030.CODER : UTF8MB4.CODER;
	private final ByteList byteDec = new ByteList(8);

	private final CharList input = new CharList();
	private int cursor, scrollLeft;
	private final MyBitSet invisible = new MyBitSet();
	private String lastInput;

	private int historyPos;
	private final List<String> history = new SimpleList<>();
	private String currentHistory;

	private final List<Completion> tabs = new SimpleList<>();
	private int tabCursor = -1, tabId;

	public DefaultConsole(String prompt) {
		setPrompt(prompt);
		staticHighlight = "\u001b[97m"; // light white
	}
	public void setPrompt(String prompt) {
		this.prompt.clear();
		this.prompt.append("\u001b[0m").append(prompt);

		prefixLen = prompt.length()+4;
		prefixCLen = CLIUtil.getStringWidth(prompt);

		if (CLIUtil.hasBottomLine(this.prompt)) doRender();
	}
	public void setDefaultHighlight(String postfix) { staticHighlight = postfix; }
	public void setCursorMoveBound(int cmb) { cursorMoveBound = cmb; }

	public void registered() {
		CLIUtil.enableQuickEditMode();
		doRender();
	}
	public void unregistered() { CLIUtil.removeBottomLine(prompt, true); }

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
			lastInput = input.toString();
		}

		Completion s = tabs.get(id);
		if (s.description != null) {
			s.description.writeLimited(tooltip(), new MutableInt(CLIUtil.windowWidth), true);
			displayTooltip(5000);
		} else {
			displayTooltip(-1);
		}

		tabId = id;
		input.clear();
		input.append(lastInput);
		input.insert(tabCursor, s.completion.writeAnsi(IOUtil.getSharedCharBuf()).append("\u001b[0m").append(staticHighlight));
		if (!isAutoComplete)
			cursor = tabCursor + s.completion.length();

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

		AnsiString str = tabs.get(tabId).completion;
		if (insert) {
			input.insert(tabCursor, str.toString());
			if (isAutoComplete)
				cursor = tabCursor + str.length();
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
		if (keyEnter_Pre(keyCode, isVirtualKey)) return;
		if (!isVirtualKey) {
			endCompletion(false);

			if (input.length() > MAX_INPUT) { beep(); return; }

			CharList charDec = IOUtil.getSharedCharBuf();
			CE.decodeLoop(byteDec.put(keyCode), byteDec.readableBytes(), charDec, 99, true);
			byteDec.compact();
			input.insert(cursor, charDec);
			cursor += charDec.length();

			if (autoComplete) {
				complete(input, cursor, tabs);
				if (tabs.size() > 0) {
					isAutoComplete = true;
					setComplete(0);
				}
			}

			afterInput();
		} else if ((keyCode&VK_CTRL) != 0) {
			keyCode ^= VK_CTRL;
			switch (keyCode) {
				case VK_A: break;
				case VK_V:
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					DataFlavor stringFlavor = DataFlavor.stringFlavor;
					if (clipboard.isDataFlavorAvailable(stringFlavor)) {
						try {
							String text = filterText(clipboard.getData(stringFlavor).toString());
							input.insert(cursor, text);
							cursor += text.length();
							afterInput();
						} catch (UnsupportedFlavorException | IOException e) {
							e.printStackTrace();
						}
						//break;
					}
				break;
				case VK_B:
					if (ctrlCForCopy) System.exit(0);
				break;
				case VK_C:
					if (!ctrlCForCopy) System.exit(0);
					else {
						Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(CLIUtil.stripAnsi(new CharList(input)).toStringAndFree()), null);
					}
				break;
				case VK_UP: scrollLeft++; break;
				case VK_DOWN: scrollLeft--; break;
				case VK_LEFT: cursor = 0; break;
				case VK_RIGHT: cursor = input.length() - invisible.size(); break;
				default: return;
			}
		} else {
			switch (keyCode) {
				case VK_F1: printHelp(); return;
				case VK_F2:
					ctrlCForCopy = !ctrlCForCopy;
					tooltip().append(ctrlCForCopy ?
						"\u001b[91m现在Ctrl+C可以用来复制选中的文本，使用Ctrl+B中断程序" :
						"\u001b[92m现在Ctrl+C用来中断程序");
					displayTooltip(1000);
				return;
				case VK_F3:
					autoHighlight = !autoHighlight;
					tooltip().append(autoHighlight ?
						"\u001b[92m已启用动态语法高亮" :
						"\u001b[91m已禁用动态语法高亮");
					displayTooltip(1000);
				return;
				case VK_F4:
					autoComplete = !autoComplete;
					tooltip().append(autoComplete ?
						"\u001b[92m已启用即时补全" :
						"\u001b[91m已禁用即时补全");
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

					history.remove(cmd);
					while (history.size() > 255) history.remove(0);
					history.add(cmd);

					cursor = scrollLeft = 0;
					afterInput();
				break;
				case VK_UP:
					if (tabs.size() > 0) {
						setComplete((tabId == 0 ? tabs.size() : tabId) - 1);
						break;
					}

					if (historyPos > 0) {
						if (historyPos == history.size()) {
							currentHistory = input.toString();
						}

						setFromHistory(history.get(--historyPos));
					} else {
						return; // no update
					}
				break;
				case VK_DOWN:
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
					if (cursor > 0) cursor--;
					else return;
				break;
				case VK_RIGHT:
					if (cursor < input.length() - invisible.size()) cursor++;
					else return;
				break;
				case VK_TAB:
					if (input.length() > MAX_INPUT) { beep(); return; }
					/*if (tabs.size() > 1) {
						setComplete((tabId+1) % tabs.size());
						break;
					}*/
					if (endCompletion(true)) break;
					complete(input, cursor, tabs);
					if (tabs.size() > 0) setComplete(0);
					else return;
				break;
				case VK_BACK_SPACE:
					boolean isDeletingTabComplete = tabCursor >= 0 && (cursor == 0 || (cursor > tabCursor && cursor <= tabCursor+tabs.get(tabId).completion.length()));
					if (endCompletion(false) && isDeletingTabComplete) break;

					if (cursor == 0) return;
					input.delete(--cursor);
					afterInput();
				break;
				default: return;
			}
		}

		doRender();
	}
	private static void beep() { CLIUtil.sysOut.write(7); CLIUtil.sysOut.flush(); }

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
	protected final void doRender() {
		if (autoHighlight && tabCursor < 0 && highlight == null) {
			lastInput = input.toString();
			highlight = highlight(lastInput);
			if (highlight == null) {
				autoHighlight = false;
				lastInput = null;
				tooltip().append("不支持语法高亮");
				displayTooltip(1000);
			} else {
				assert highlight.length() == lastInput.length() : "input length mismatch";
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
			} else if (relCursor < 0) {
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
			System.err.println("[DefaultConsole]Invalid ANSI sequence detected.");
			scrollLeft = 0;
		}
		prompt.append(staticHighlight).append(input, scrollLeft, end);

		if (end < input.length()) prompt.append("\u001b[1;5;41;97m+"); // + after
		prompt.append("\u001b[0m");

		CLIUtil.renderBottomLine(prompt, true, prefixCLen+relCursor+1);
	}
	private int computeMaxWidth() {
		int maxWidth = CLIUtil.windowWidth-prefixCLen;
		if (scrollLeft > 0) maxWidth--;
		if (limitWidth(scrollLeft, maxWidth) < input.length()) maxWidth--;
		return maxWidth;
	}
	private int getDisplayWidth(int visLen, boolean countInvisible) {
		if (visLen == 0) return 0;

		int width = 0;
		for (int i = 0; i < input.length(); i++) {
			if (!invisible.contains(i)) {
				width += CLIUtil.getCharWidth(input.charAt(i));
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
				width += CLIUtil.getCharWidth(input.charAt(i));
				if (width >= maxWidth) return i;
			}
		}
		return input.length();
	}

	private boolean autoHighlight = true, ctrlCForCopy, autoComplete, isAutoComplete;
	private final CharList tooltip = new CharList();
	private ScheduleTask removeTooltip;
	protected final CharList tooltip() { tooltip.clear(); return tooltip; }
	protected final void displayTooltip(int timeout) {
		if (removeTooltip != null) removeTooltip.cancel();
		if (timeout < 0) {
			CLIUtil.removeBottomLine(tooltip, true);
			return;
		}

		CLIUtil.renderBottomLine(tooltip, true, 0);
		removeTooltip = Scheduler.getDefaultScheduler().delay(() -> displayTooltip(-1), timeout);
	}

	protected boolean keyEnter_Pre(int key, boolean vk) { return false; }
	protected void printHelp() {
		CLIBoxRenderer.DEFAULT.render(new String[][]{
			new String[] { "Roj234的终端模拟器 帮助", "简介", "快捷键" },
			new String[] {
				"怎么，其实又是一个JLine不会用的人啊/doge 可惜这里不能放图，哈哈哈\n" +
					"有兴趣可以看看我是如何计算字符显示长度的\n" +
					"早期版本：c<16 => 0, c<255 => 1, else => 2\n" +
					"然而：https://unix.stackexchange.com/questions/245013/get-the-display-width-of-a-string-of-characters\n" +
					"\n" +
					"做什么都不简单。。。还很邪道",
				"F1: 查看帮助\n" +
					"F2: 切换Ctrl+C功能\n" +
					"F3: 开关语法高亮\n" +
					"F4: 开关即时补全\n" +
					"Ctrl+A: 全选\n" +
					"Ctrl+B: 中断程序(F2)\n" +
					"Ctrl+C: 中断程序或复制(F2)\n" +
					"Ctrl+V: 粘贴\n" +
					"Ctrl+←: 光标移至开头\n" +
					"Ctrl+→: 光标移至结尾\n" +
					"ESC: 取消补全\n" +
					"ENTER: 确认补全或执行指令\n" +
					"↑: 上一条历史或补全候选\n" +
					"↓: 下一条历史或补全候选, 或回到当前输入\n" +
					"Tab: 在当前位置补全代码" }
		});
	}
	protected AnsiString highlight(String input) { return null; }
	protected void complete(CharList input, int cursor, List<Completion> out) {}
	protected boolean evaluate(String cmd) { return true; }
}
