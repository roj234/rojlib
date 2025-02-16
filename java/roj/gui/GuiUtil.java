package roj.gui;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import roj.RojLib;
import roj.compiler.plugins.annotations.Attach;
import roj.io.IOUtil;
import roj.reflect.ReflectionUtils;
import roj.reflect.litasm.FastJNI;
import roj.text.TextReader;
import roj.text.TextUtil;
import roj.util.Helpers;
import roj.util.NativeException;
import roj.util.OS;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

import static roj.reflect.Unaligned.U;

/**
 * @author Roj234
 * @since 2021/5/29 18:40
 */
public final class GuiUtil {
	private static Clipboard clipboard;
	static {
		try {
			clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
		} catch (HeadlessException ignored) {}
	}
	@Nullable public static Clipboard getClipboard() {return clipboard;}
	@Nullable public static String getClipboardText() {
		if (clipboard != null) {
			DataFlavor stringFlavor = DataFlavor.stringFlavor;
			if (clipboard.isDataFlavorAvailable(stringFlavor)) {
				try {
					return clipboard.getData(stringFlavor).toString();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}

		return null;
	}
	public static boolean setClipboardText(String text) {
		if (clipboard == null) return false;
		clipboard.setContents(new StringSelection(text), null);
		return true;
	}

	public static boolean isGui() {return clipboard != null;}

	public static void systemLook() {
		int dpi;
		try {
			dpi = Toolkit.getDefaultToolkit().getScreenResolution();
		} catch (HeadlessException ex) {
			return;
		}

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

		Object peer = U.getObject(window, U.objectFieldOffset(Component.class.getDeclaredField("peer")));
		long hwnd = U.getLong(peer, U.objectFieldOffset(ReflectionUtils.getField(peer.getClass(), "hwnd")));

		long flags = GetWindowLong(hwnd, -20/*GWL_EXSTYLE*/);
		flags |= 524320/*WS_EX_LAYERED|WS_EX_TRANSPARENT*/;
		SetWindowLong(hwnd, -20, flags);
	}

	//static {Intrinsics.linkNative("USER32");}
	@FastJNI
	private static native long GetWindowLong(long hwnd, int dwType);
	@FastJNI
	private static native void SetWindowLong(long hwnd, int dwType, long flags);

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

	public static void setDefaultPathToDesktop() {
		if (OS.CURRENT != OS.WINDOWS) return;
		try {
			Process proc = Runtime.getRuntime().exec("reg query \"HKCU\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Explorer\\Shell Folders\" /v \"Desktop\"");
			try (var tx = TextReader.auto(proc.getInputStream())) {
				tx.skipLines(2);
				var folder = new File(TextUtil.split(tx.readLine(), "    ").get(3));
				if (folder.isDirectory()) lastPath = folder;
			} finally {
				proc.destroy();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	public static File lastPath = new File("");
	@Nullable
	public static File fileSaveTo(String title, String defaultFileName) { return fileSaveTo(title, defaultFileName, null, false); }
	@Nullable
	public static File fileSaveTo(String title, String defaultFileName, Component pos) { return fileSaveTo(title, defaultFileName, pos, false); }
	@Nullable
	public static File fileSaveTo(String title, String defaultFileName, Component pos, boolean folder) {
		JFileChooser jfc = newMyFileChooser();
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
		JFileChooser jfc = newMyFileChooser();
		jfc.setDialogTitle(title);
		jfc.setFileSelectionMode(mode);

		int status = jfc.showOpenDialog(pos);
		if (status != JFileChooser.APPROVE_OPTION) return null;
		lastPath = jfc.getCurrentDirectory();

		return jfc.getSelectedFile();
	}
	@Nullable
	public static File[] filesLoadFrom(String title, Component pos, int mode) {
		JFileChooser jfc = newMyFileChooser();
		jfc.setDialogTitle(title);
		jfc.setMultiSelectionEnabled(true);
		jfc.setFileSelectionMode(mode);

		int status = jfc.showOpenDialog(pos);
		if (status != JFileChooser.APPROVE_OPTION) return null;
		lastPath = jfc.getCurrentDirectory();

		return jfc.getSelectedFiles();
	}

	public static JFileChooser newMyFileChooser() {
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
	public static FileFilter newExtensionFilter(String extension, String briefDesc) {
		String desc = briefDesc+" (*."+extension+")";
		return new FileFilter() {
			@Override public boolean accept(File f) {return f.isDirectory() || IOUtil.extensionName(f.getName()).equals(extension);}
			@Override public String getDescription() {return desc;}
		};
	}

	@Attach("remove")
	public static void removeComponent(Component component) {
		component.getParent().remove(component);
	}
}