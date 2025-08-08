package roj.plugins.mychat;

import org.jetbrains.annotations.NotNull;
import roj.concurrent.FastThreadLocal;
import roj.concurrent.TaskPool;
import roj.crypt.BufferedDigest;
import roj.crypt.CryptoFactory;
import roj.http.Headers;
import roj.http.server.HSConfig;
import roj.http.server.MultipartParser;
import roj.http.server.Request;
import roj.io.IOUtil;
import roj.net.ChannelCtx;
import roj.text.TextUtil;
import roj.util.ByteList;
import roj.util.DynByteBuf;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/3/16 19:46
 */
public class UploadHandler extends MultipartParser {
	private final int uid;
	private final boolean image;

	private BufferedDigest sm3;

	public File[] files;
	public String[] errors;

	private FileOutputStream fos;
	protected int i;

	public UploadHandler(Request req, int count, int uid, boolean image) {
		super(req);
		files = new File[count];
		this.uid = uid;
		this.image = image;
	}

	@Override
	public void init(String req) {
		super.init(req);

		Map<String, Object> ctx = HSConfig.getInstance().ctx;
		if (!ctx.containsKey("SM3U")) {
			ctx.put("SM3U", sm3 = CryptoFactory.SM3());
		} else {
			sm3 = (BufferedDigest) ctx.get("SM3U");
		}
	}

	@Override
	public void onSuccess(DynByteBuf rest) {
		close0();
	}

	@Override
	public void onComplete() throws IOException {
		close0();
		for (File file : files) {
			if (file != null) {
				file.delete();
			}
		}
	}

	@Override
	protected @NotNull Object begin(ChannelCtx ctx, Headers header) throws IOException {
		close0();

		String name = header.getHeaderValue("content-disposition", "name");
		if (name == null) throw new UnsupportedOperationException("没有content-disposition.name,如果需要处理隐式的text/plain或特殊的头，请覆盖此方法");

		int i = this.i = TextUtil.parseInt(name);
		if (i < 0 || i > files.length) throw new ArrayIndexOutOfBoundsException();
		try {
			fos = new FileOutputStream(files[i] = getFile());
		} catch (IOException e) {
			if (errors == null) errors = new String[files.length];
			errors[i] = e.getMessage();
		}

		return fos;
	}

	private File getFile() throws IOException {
		sm3.reset();

		ByteList bb = IOUtil.getSharedByteBuf();
		File file;
		int i = 0;
		do {
			if (i++ > 5) throw new IOException("哈希碰撞");

			bb.clear();

			bb.putLong(System.nanoTime()).putLong(System.currentTimeMillis()).putInt(files.length).putInt(files.hashCode()).putInt(i);
			sm3.update(bb);

			bb.clear();
			sm3.digest(bb);
			bb.wIndex(16);

			bb.putInt(uid);

			file = new File(ChatManager.attDir, bb.base64UrlSafe());
		} while (file.isFile());
		return file;
	}

	@Override
	protected void data(ChannelCtx ctx, DynByteBuf buf) {
		if (fos == null) return;
		try {
			if (buf.hasArray()) {
				try {
					fos.write(buf.array(), buf.arrayOffset() + buf.rIndex, buf.readableBytes());
					sm3.update(buf.array(), buf.arrayOffset() + buf.rIndex, buf.readableBytes());
				} catch (IOException e) {
					if (errors == null) errors = new String[files.length];
					errors[i] = e.getMessage();
				}
				buf.rIndex = buf.wIndex();
			} else {
				fos.getChannel().write(buf.nioBuffer());
				sm3.update(buf);
			}
		} catch (IOException e) {
			if (errors == null) errors = new String[files.length];
			errors[i] = e.getMessage();
			try {
				fos.close();
			} catch (IOException ignored) {}
			fos = null;
		}
	}

	private void close0() {
		if (fos != null) {
			try {
				fos.close();
			} catch (IOException e) {
				if (errors == null) errors = new String[files.length];
				errors[i] = e.getMessage();
			}

			fos = null;
			if (files[i] == null) return;

			try {
				ByteList tmp = IOUtil.getSharedByteBuf();
				sm3.digest(tmp);
				tmp.wIndex(16);

				String hashFileName = tmp.base64UrlSafe();

				File old = new File(ChatManager.attDir, hashFileName);
				File cur = files[i];
				if (old.isFile()) {
					if (old.length() == cur.length()) {
						if (cur.delete()) {
							files[i] = old;
							return;
						} else {
							System.out.println("Failed to delete duplicate file: " + cur);
						}
					}
				} else {
					if (!cur.renameTo(old)) {
						IOUtil.copyFile(cur, old);
						if (!cur.delete()) throw new IOException("Unable delete old file " + cur);
					}
					files[i] = old;
				}
			} catch (IOException e) {
				if (errors == null) errors = new String[files.length];
				errors[i] = e.getMessage();
				return;
			}

			if (!image) return;

			try {
				// 傻逼操作，不能stream么
				ImageInputStream ins = ImageIO.createImageInputStream(files[i]);
				Iterator<ImageReader> readers = ImageIO.getImageReaders(ins);
				if (!readers.hasNext()) {
					ins.close();
					if (errors == null) errors = new String[files.length];
					errors[i] = "未知的图片类型";
					return;
				}

				ImageReader r = readers.next();
				r.setInput(ins);

				String fmt = r.getFormatName();
				int size = r.getWidth(0) * r.getHeight(0);
				if (size > 8294400) {
					// 4K size
					ins.close();
					if (errors == null) errors = new String[files.length];
					errors[i] = "图片过大";
					return;
				} else if (size < 262144) {
					if (fmt.equals("png")) {
						// 不做处理
						r.dispose();
						ins.close();
						return;
					} else if (fmt.equals("bmp")) {
						// 小于 256*256 使用高质量(PNG)
						fmt = "png";
					} else {
						// < 256KB的动图不做处理
						if (fmt.equals("gif")) {
							r.dispose();
							ins.close();
							return;
						}
						fmt = "jpg";
					}
				} else {
					fmt = "jpg";
				}

				// todo stream
				String v$fmt = fmt;
				File v$file = files[i];
				TaskPool.common().executeUnsafe(() -> {
					BufferedImage dst = img.get();
					if (dst == null || dst.getWidth() < r.getWidth(0) || dst.getHeight() < r.getHeight(0)) {
						dst = new BufferedImage(r.getWidth(0), r.getHeight(0), BufferedImage.TYPE_3BYTE_BGR);
						img.set(dst);
					}
					dst = dst.getSubimage(0, 0, r.getWidth(0), r.getHeight(0));

					ImageReadParam param = new ImageReadParam();
					param.setSourceBands(new int[] {0, 1, 2});
					param.setDestination(dst);

					BufferedImage img;
					try {
						img = r.read(0, param);
					} finally {
						r.dispose();
						ins.close();
					}

					ImageIO.write(img, v$fmt, v$file);
				});
			} catch (Throwable e) {
				e.printStackTrace();
				if (errors == null) errors = new String[files.length];
				errors[i] = e.getMessage();
			}
		}
	}
	static final FastThreadLocal<BufferedImage> img = new FastThreadLocal<>();
}