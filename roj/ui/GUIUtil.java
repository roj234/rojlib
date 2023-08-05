package roj.ui;

import roj.io.IOUtil;
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
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.function.Consumer;

/**
 * @author Roj234
 * @since 2021/5/29 18:40
 */
public final class GUIUtil {
	public static void systemLook() {
		try {
			UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | UnsupportedLookAndFeelException e) {
			e.printStackTrace();
		}
	}

	public static void setLogo(JFrame frame, String fileName) {
		InputStream stream = GUIUtil.class.getClassLoader().getResourceAsStream(fileName);
		if (stream != null) {
			try {
				frame.setIconImage(ImageIO.read(stream));
			} catch (IOException e) {
				e.printStackTrace();
			}
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
}
