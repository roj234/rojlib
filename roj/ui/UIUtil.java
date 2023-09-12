package roj.ui;

import roj.io.IOUtil;
import roj.text.TextUtil;
import roj.util.Helpers;
import roj.util.OS;

import javax.annotation.Nonnull;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.io.*;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Roj234
 * @since 2021/5/29 18:40
 */
public final class UIUtil {
	public static final BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

	public static int selectOneFile(List<File> files, String name1) throws IOException {
		if (files.size() == 1) return 0;
		CmdUtil.info("有多个 " + name1 + " , 请选择(输入编号)");

		for (int i = 0, forgeVersionsSize = files.size(); i < forgeVersionsSize; i++) {
			String name = files.get(i).getName();
			int k = name.lastIndexOf(File.separatorChar);
			CmdUtil.fg(CmdUtil.Color.WHITE, (i & 1) == 1);
			System.out.println(i + ". " + (k == -1 ? name : name.substring(0, k)));
			CmdUtil.reset();
		}

		return getNumberInRange(0, files.size());
	}

	/**
	 * @param min 最小值(包括)
	 * @param max 最大值(不包括)
	 */
	public static int getNumberInRange(int min, int max) throws IOException {
		String s;
		int i;
		do {
			do {
				CmdUtil.info("您的选择: ", false);
				CmdUtil.fg(CmdUtil.Color.YELLOW, true);
				s = in.readLine();
				CmdUtil.reset();
				if (s == null) System.exit(-2);
				if (TextUtil.isNumber(s) == 0) break;
				CmdUtil.warning("输入的不是数字!", true);
			} while (true);
			try {
				i = Integer.parseInt(s);
				if (i >= min && i < max) {
					break;
				}
			} catch (NumberFormatException ignored) {
			}
			CmdUtil.warning("输入的数太大或者太小!", true);
		} while (true);
		return i;
	}

	public static File readFile(String name) throws IOException {
		CmdUtil.info("请键入, 粘贴或者拖动" + name + "到这里并按下回车!");
		File file;
		do {
			CmdUtil.fg(CmdUtil.Color.YELLOW, true);
			String s = in.readLine();
			CmdUtil.reset();
			if (s == null) System.exit(-2);
			if (s.startsWith("\"") && s.endsWith("\"")) s = s.substring(1, s.length() - 1);
			file = new File(s);
			if (file.exists()) return file;
			CmdUtil.warning("文件不存在，请重试!", true);
		} while (!file.exists());
		return file;
	}

	public static boolean readBoolean(String info) throws IOException {
		boolean enableAt;
		do {
			CmdUtil.info(info, false);
			CmdUtil.fg(CmdUtil.Color.YELLOW, true);
			String s = in.readLine();
			if (s == null) System.exit(-2);
			CmdUtil.reset();
			Boolean b = null;
			switch (s.toLowerCase(Locale.ROOT)) {
				case "y": case "yes": case "ok":
				case "t": case "true": case "是":
					b = true;
					break;
				case "n": case "no": case "not":
				case "false": case "cancel":
				case "f": case "否":
					b = false;
					break;
			}
			if (b != null) {
				enableAt = b;
				break;
			}
			CmdUtil.warning("不是true或false");
		} while (true);
		return enableAt;
	}

	public static String userInput(String info, Function<String, Boolean> verifier) throws IOException {
		CmdUtil.info(info, false);
		String string;
		do {
			CmdUtil.fg(CmdUtil.Color.YELLOW, true);
			string = in.readLine();
			if (string == null) System.exit(-2);
			if (verifier.apply(string) == Boolean.TRUE) {
				break;
			}
			System.out.println("输入不正确!");
			CmdUtil.reset();
		} while (true);
		return string.trim();
	}

	public static String userInput(String info) throws IOException {
		CmdUtil.info(info, false);
		CmdUtil.fg(CmdUtil.Color.YELLOW, true);
		String string = in.readLine();
		CmdUtil.reset();
		if (string == null) System.exit(-2);
		return string.trim();
	}

	public static void setLogo(JFrame frame, String fileName) {
		InputStream stream = UIUtil.class.getClassLoader().getResourceAsStream(fileName);
		if (stream != null) {
			try {
				frame.setIconImage(ImageIO.read(stream));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void systemLook() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}
	}

	@Nonnull
	public static DropTarget dropFilePath(Component comp, Consumer<File> callback, boolean append) {
		return new DropTarget(comp, new DropTargetAdapter() {
			@Override
			public void drop(DropTargetDropEvent dtde) {
				dtde.acceptDrop(3);
				Transferable t = dtde.getTransferable();
				for (DataFlavor flavor : t.getTransferDataFlavors()) {
					if (flavor.getMimeType().startsWith("application/x-java-file-list")) {
						try {
							List<File> data = Helpers.cast(t.getTransferData(flavor));
							if (append) {
								for (File file : data) {
									if (comp instanceof JTextComponent)
										insert((JTextComponent) comp, file.getAbsolutePath().concat("\n"));
									if (callback != null) callback.accept(file);
								}
							} else {
								String path = data.get(0).getAbsolutePath();
								if (comp instanceof JTextComponent)
									((JTextComponent) comp).setText(path);
								if (callback != null) callback.accept(data.get(0));
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
			}
		});
	}

	@Nonnull
	public static Document insert(JTextComponent component, String value) {
		Document doc = component.getDocument();
		try {
			doc.insertString(doc.getLength(), value, null);
		} catch (BadLocationException e) {}
		return doc;
	}

	private static File lastPath = new File(".");
	public static File fileSaveTo(String title, String defaultFileName) { return fileSaveTo(title, defaultFileName, null, false); }
	public static File fileSaveTo(String title, String defaultFileName, Component pos) { return fileSaveTo(title, defaultFileName, pos, false); }
	public static File fileSaveTo(String title, String defaultFileName, Component pos, boolean folder) {
		JFileChooser jfc = getFileChooser();
		jfc.setDialogTitle(title);
		jfc.setFileSelectionMode(folder?JFileChooser.DIRECTORIES_ONLY:JFileChooser.FILES_ONLY);
		jfc.setSelectedFile(new File(lastPath, defaultFileName));

		int status = jfc.showSaveDialog(pos);
		if (status != JFileChooser.APPROVE_OPTION) return null;
		lastPath = jfc.getCurrentDirectory();

		return jfc.getSelectedFile();
	}

	public static File fileLoadFrom(String title) { return fileLoadFrom(title, null, JFileChooser.FILES_ONLY); }
	public static File fileLoadFrom(String title, Component pos) { return fileLoadFrom(title, pos, JFileChooser.FILES_ONLY); }
	public static File fileLoadFrom(String title, Component pos, int mode) {
		JFileChooser jfc = getFileChooser();
		jfc.setDialogTitle(title);
		jfc.setFileSelectionMode(mode);

		int status = jfc.showOpenDialog(pos);
		if (status != JFileChooser.APPROVE_OPTION) return null;
		lastPath = jfc.getCurrentDirectory();

		return jfc.getSelectedFile();
	}
	public static File[] filesLoadFrom(String title, Component pos, int mode) {
		JFileChooser jfc = getFileChooser();
		jfc.setDialogTitle(title);
		jfc.setMultiSelectionEnabled(true);
		jfc.setFileSelectionMode(mode);

		int status = jfc.showOpenDialog(pos);
		if (status != JFileChooser.APPROVE_OPTION) return null;
		lastPath = jfc.getCurrentDirectory();

		return jfc.getSelectedFiles();
	}

	private static JFileChooser getFileChooser() {
		return new JFileChooser(lastPath) {
			@Override
			protected JDialog createDialog(Component parent) throws HeadlessException {
				JDialog dialog = super.createDialog(parent);
				myadd(dialog);
				return dialog;
			}

			private void myadd(Component comp) {
				// shit
				if (comp.getClass().getName().contains("FilePane")) {
					dropFilePath(comp, (x) -> {
						File path = getCurrentDirectory();
						File target = new File(path, x.getName());
						try {
							// C:\
							if (OS.CURRENT == OS.WINDOWS && x.getAbsolutePath().startsWith(path.getAbsolutePath().substring(0, 3))) {
								x.renameTo(target);
							} else {
								IOUtil.copyFile(x, target);
							}
						} catch (Exception e) {
							e.printStackTrace();
						} finally {
							rescanCurrentDirectory();
						}
					}, true);
				} else if (comp instanceof JTextComponent) {
					dropFilePath(comp, this::setSelectedFile, false);
				}

				if (comp instanceof Container) {
					Container c = (Container) comp;
					synchronized (c.getTreeLock()) {
						for (int i = 0; i < c.getComponentCount(); i++) {
							myadd(c.getComponent(i));
						}
					}
				}
			}
		};
	}

	@Deprecated
	public static void center(Window frame) {
		Dimension dim = Toolkit.getDefaultToolkit().getScreenSize();
		Rectangle bounds = frame.getBounds();
		frame.setBounds((dim.width - bounds.width) / 2, (dim.height - bounds.height) / 2, bounds.width, bounds.height);
	}

	public static char[] readPassword() {
		return System.console().readPassword("");
	}

	public static void pause() {
		try {
			userInput("按回车继续");
		} catch (IOException ignored) {}
	}
}
