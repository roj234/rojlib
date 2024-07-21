package roj.ui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.RojLib;
import roj.compiler.plugins.annotations.Attach;
import roj.io.IOUtil;
import roj.reflect.ReflectionUtils;
import roj.util.Helpers;
import roj.util.NativeException;
import roj.util.OS;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

import static roj.reflect.ReflectionUtils.u;

/**
 * @author Roj234
 * @since 2021/5/29 18:40
 */
public final class GuiUtil {
	private static Clipboard clipboard;
	@Nullable
	public static Clipboard getClipboard() {
		if (clipboard != null) return clipboard;

		Clipboard c;
		try {
			c = Toolkit.getDefaultToolkit().getSystemClipboard();
		} catch (Exception e) {
			c = null;
		}
		return clipboard = c;
	}

	public static void systemLook() {
		int dpi;
		try {
			dpi = Toolkit.getDefaultToolkit().getScreenResolution();
		} catch (HeadlessException ex) {
			return;
		}
		System.out.println("Dpi="+dpi);
		int size = 12;
		if (Math.round(size * 72F / dpi) < 8) {
			size = Math.round(8 * dpi / 72F);
		}

		if (size < 12) System.setProperty("swing.useSystemFontSettings","false");

		try {
			UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
		} catch (Exception e) {
			System.err.println("RojLib Warning: Windows皮肤不可用，您可能会感受到绝对定位的痛苦");
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			} catch (Exception ex) {
				Helpers.athrow(ex);
			}
		}
	}

	public static void setLogo(JFrame frame, String fileName) {
		InputStream stream = GuiUtil.class.getClassLoader().getResourceAsStream(fileName);
		if (stream != null) {
			try {
				frame.setIconImage(ImageIO.read(stream));
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	public static void setClickThrough(Window window) throws Exception {
		if (!RojLib.hasNative(RojLib.WIN32)) throw new NativeException("不支持所请求的操作");

		Object peer = u.getObject(window, u.objectFieldOffset(Component.class.getDeclaredField("peer")));
		long hwnd = u.getLong(peer, u.objectFieldOffset(ReflectionUtils.getField(peer.getClass(), "hwnd")));

		long flags = nGetWindowLong(hwnd, -20/*GWL_EXSTYLE*/);
		flags |= 524320/*WS_EX_LAYERED|WS_EX_TRANSPARENT*/;
		nSetWindowLong(hwnd, -20, flags);
	}
	public static long getConsoleWindow() {return nGetConsoleWindow();}
	private static native long nGetWindowLong(long hwnd, int dwType);
	private static native void nSetWindowLong(long hwnd, int dwType, long flags);
	private static native long nGetConsoleWindow();

	@NotNull
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

	@NotNull
	public static Document insert(JTextComponent component, String value) {
		Document doc = component.getDocument();
		try {
			doc.insertString(doc.getLength(), value, null);
		} catch (BadLocationException e) {}
		return doc;
	}

	private static File lastPath = new File("");
	@Nullable
	public static File fileSaveTo(String title, String defaultFileName) { return fileSaveTo(title, defaultFileName, null, false); }
	@Nullable
	public static File fileSaveTo(String title, String defaultFileName, Component pos) { return fileSaveTo(title, defaultFileName, pos, false); }
	@Nullable
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

	@Nullable
	public static File fileLoadFrom(String title) { return fileLoadFrom(title, null, JFileChooser.FILES_ONLY); }
	@Nullable
	public static File fileLoadFrom(String title, Component pos) { return fileLoadFrom(title, pos, JFileChooser.FILES_ONLY); }
	@Nullable
	public static File fileLoadFrom(String title, Component pos, int mode) {
		JFileChooser jfc = getFileChooser();
		jfc.setDialogTitle(title);
		jfc.setFileSelectionMode(mode);

		int status = jfc.showOpenDialog(pos);
		if (status != JFileChooser.APPROVE_OPTION) return null;
		lastPath = jfc.getCurrentDirectory();

		return jfc.getSelectedFile();
	}
	@Nullable
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

				if (comp instanceof Container c) {
					synchronized (c.getTreeLock()) {
						for (int i = 0; i < c.getComponentCount(); i++) {
							myadd(c.getComponent(i));
						}
					}
				}
			}
		};
	}

	@Attach("remove")
	public static void removeComponent(Component component) {
		component.getParent().remove(component);
	}
}