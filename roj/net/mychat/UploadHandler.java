package roj.net.mychat;

import roj.concurrent.task.AbstractExecutionTask;
import roj.crypt.Base64;
import roj.crypt.SM3;
import roj.io.FileUtil;
import roj.io.IOUtil;
import roj.math.MathUtils;
import roj.net.http.serv.FormPostHandler;
import roj.net.http.serv.Request;
import roj.net.http.serv.RequestHandler;
import roj.text.UTFCoder;
import roj.util.ByteList;
import roj.util.FastThreadLocal;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.DigestException;
import java.util.Iterator;
import java.util.Map;

/**
 * @author Roj233
 * @since 2022/3/16 19:46
 */
public class UploadHandler extends FormPostHandler {
    private final int uid;
    private final boolean image;

    private UTFCoder uc;
    private SM3 sm3;

    public File[] files;
    public String[] errors;

    private FileOutputStream fos;
    private ByteList tmp;
    protected int i;

    public UploadHandler(Request req, int count, int uid, boolean image) {
        super(req);
        files = new File[count];
        this.uid = uid;
        this.image = image;
    }

    @Override
    public void init(Request req) {
        super.init(req);
        uc = IOUtil.SharedCoder.get();
        uc.baseChars = Base64.B64_URL_SAFE;

        Map<String, Object> ctx = RequestHandler.LocalShared.get().ctx;
        if (!ctx.containsKey("SM3U")) {
            ctx.put("SM3U", sm3 = new SM3());
        } else {
            sm3 = (SM3) ctx.get("SM3U");
        }
    }

    @Override
    public void onSuccess() {
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
    protected void onKey(CharSequence key) {
        close0();

        int i = this.i = MathUtils.parseInt(key);
        if (i < 0 || i > files.length) throw new ArrayIndexOutOfBoundsException();
        try {
            fos = new FileOutputStream(files[i] = getFile());
        } catch (IOException e) {
            if (errors == null) errors = new String[files.length];
            errors[i] = e.getMessage();
        }
    }

    private File getFile() throws IOException {
        sm3.reset();

        ByteList bb = uc.byteBuf;
        bb.clear();
        bb.putLong(System.nanoTime())
          .putLong(System.currentTimeMillis())
          .putInt(files.length).putInt(files.hashCode())
          .putInt(i);

        File file;
        int i = 0;
        do {
            if (i++ > 5) {
                throw new IOException("????????????");
            }

            sm3.update(bb.list, 0, bb.wIndex());
            try {
                sm3.digest(bb.list, 0, 32);
            } catch (DigestException ignored) {}
            bb.wIndex(16);
            bb.putInt(uid);

            file = new File(image ? Server.imgDir : Server.attDir, uc.encodeBase64(bb));
        } while (file.isFile());
        return file;
    }

    @Override
    protected void onValue(ByteBuffer buf) {
        if (fos == null) return;
        try {
            if (hasArray) {
                try {
                    fos.write(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
                    sm3.update(buf.array(), buf.arrayOffset() + buf.position(), buf.remaining());
                } catch (IOException e) {
                    if (errors == null) errors = new String[files.length];
                    errors[i] = e.getMessage();
                }
                buf.position(buf.limit());
            } else {
                buf.mark();
                fos.getChannel().write(buf);
                buf.reset();

                ByteList tmp = uc.byteBuf;
                tmp.clear();
                tmp.put(buf);
                sm3.update(tmp.list, 0, tmp.wIndex());
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
                ByteList tmp = uc.byteBuf;
                sm3.digest(tmp.list, 0, 32);
                tmp.wIndex(16);

                String hashFileName = uc.encodeBase64(tmp);

                File old = new File(image ? Server.imgDir : Server.attDir, hashFileName);
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
                        FileUtil.copyFile(cur, old);
                        if (!cur.delete()) throw new IOException("Unable delete old file " + cur);
                    }
                    files[i] = old;
                }
            } catch (DigestException ignored) {
            } catch (IOException e) {
                if (errors == null) errors = new String[files.length];
                errors[i] = e.getMessage();
                return;
            }

            if (!image) return;

            try {
                // ?????????????????????stream???
                ImageInputStream ins = ImageIO.createImageInputStream(files[i]);
                Iterator<ImageReader> readers = ImageIO.getImageReaders(ins);
                if (!readers.hasNext()) {
                    ins.close();
                    if (errors == null) errors = new String[files.length];
                    errors[i] = "?????????????????????";
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
                    errors[i] = "????????????";
                    return;
                } else if (size < 262144) {
                    if (fmt.equals("png")) {
                        // ????????????
                        r.dispose();
                        ins.close();
                        return;
                    } else if (fmt.equals("bmp")) {
                        // ?????? 256*256 ???????????????(PNG)
                        fmt = "png";
                    } else {
                        // < 256KB?????????????????????
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

                Server.POOL.pushTask(new ConvertImage(r, ins, fmt, files[i]));
            } catch (Throwable e) {
                e.printStackTrace();
                if (errors == null) errors = new String[files.length];
                errors[i] = e.getMessage();
            }
        }
    }

    private static class ConvertImage extends AbstractExecutionTask {
        static final FastThreadLocal<BufferedImage> img = new FastThreadLocal<>();

        private final ImageReader v$r;
        private final ImageInputStream v$in;
        private final String v$fmt;
        private final File v$file;

        public ConvertImage(ImageReader v$r, ImageInputStream v$in, String v$fmt, File v$file) {
            this.v$r = v$r;
            this.v$in = v$in;
            this.v$fmt = v$fmt;
            this.v$file = v$file;
        }

        @Override
        public void run() throws Throwable {
            Object[] dh = FastThreadLocal.getDataHolder(img.seqNum);

            BufferedImage dst = (BufferedImage) dh[img.seqNum];
            if (dst == null || dst.getWidth() < v$r.getWidth(0) || dst.getHeight() < v$r.getHeight(0)) {
                dh[img.seqNum] = dst = new BufferedImage(v$r.getWidth(0),
                                                         v$r.getHeight(0),
                                                         BufferedImage.TYPE_3BYTE_BGR);
            }
            dst = dst.getSubimage(0, 0, v$r.getWidth(0), v$r.getHeight(0));

            ImageReadParam param = new ImageReadParam();
            param.setSourceBands(new int[] {0,1,2});
            param.setDestination(dst);

            BufferedImage img;
            try {
                img = v$r.read(0, param);
            } finally {
                v$r.dispose();
                v$in.close();
            }

            ImageIO.write(img, v$fmt, v$file);
        }
    }
}
