/**
 * This file is a part of MI <br>
 * (L) Copyleft 2020-20XX 版权没有，仿冒不究,如有雷同,纯属活该
 * <p>
 * File version : 不知道...
 * Author: R__
 * Filename: GIFDecoder.java
 */
package roj.util;

import roj.io.IOUtil;
import roj.text.CharList;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/*function hexRGBA(hex) {
    var a = hex >>> 24;
    var r = hex >>> 16 ^ (a << 8);
    var g = hex >>> 8 ^ (r << 8) ^ (a << 16);
    var b = hex ^ (a << 24) ^ (r << 16) ^ g << 8;
    return [r,g,b,a];
}*/

public class GIFDecoder {
    public static final byte ARGB = 0;
    public static final byte RGB = 1;
    public static final byte RGBA = 2;

    public static byte COLOR_TYPE = ARGB;

    public static void decodeAndSave(InputStream in, String cacheFolder) {
        try (InputStream s = in) {
            Gif gif = decode(IOUtil.readFully(s));

            File file = new File(cacheFolder);
            if (!file.exists()) {
                file.mkdirs();
            }
            List<BufferedImage> list = gif.toImageList(false);

            int i = 0;
            for (BufferedImage img : list) {
                ImageIO.write(img, "png", new File(file, i++ + ".png"));
            }
        } catch (IOException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
        }
    }

    public static Gif decode(final byte[] out) throws IOException {
        ByteReader r = new ByteReader(out);
        Gif gif = new Gif();

        {
            CharList cl = new CharList(10);
            ByteReader.decodeUTF(6, cl, r.readBytesDelegated(6));
            if (!cl.equals("GIF87a") && !cl.equals("GIF89a"))
                throw new IOException("Illegal header " + cl);
        }

        int colorTableSize = 0;

        {
            //r.index++;
            // Block LSD
            gif.width = r.readUShortR();
            gif.height = r.readUShortR();
            gif.wh = gif.width * gif.height;
            byte flag = r.readByte();
            gif.sorted = isBitTrue(flag, 3);
            //int exceptedColorResolution = getSpecifyBit(flag, 4, 6);
            // 期待的显示器色深
            if (isBitTrue(flag, 7)) { // globalColorTable flag
                colorTableSize = getSpecifyBit(flag, 0, 2);
                colorTableSize = 1 << colorTableSize + 1;
            }
            gif.bgColorIndex = r.readUByte();
            short whPercent0 = r.readUByte();
            if (whPercent0 != 0)
                gif.whPercent = (((double) whPercent0) + 15.0d) / 64.0d;
        }

        if (colorTableSize != 0) {
            // Block GCT
            parseColorTable(gif.globalColorTable = new int[colorTableSize], r);
        }

        while (true) {
            if (r.index >= r.length()) {
                throw new IOException("Unexpected end of input.");
            }
            byte blockId = r.readByte();
            switch (blockId) {
                case 0x21: // extension
                    readExtension(gif, r);
                    break;
                case 0x2C: // descriptor
                    readDescriptor(gif, r);
                    break;
                case 0x3B: // end
                    return gif;
                default:
                    throw new IOException("Unknown blockId: " + blockId);
            }
        }
    }

    public static void parseColorTable(int[] colorTable, ByteReader rd) {
        int size = colorTable.length;
        if (size == 0)
            return;
        for (int i = 0; i < size; i++) {
            int r = rd.readUByte();
            int g = rd.readUByte();
            int b = rd.readUByte();
            int a = 0xFF;
            colorTable[i] = mergeARGB(r, g, b, a);
        }
    }

    public static int mergeARGB(int r, int g, int b, int a) {
        switch (COLOR_TYPE) {
            case ARGB:
                return a << 24 | r << 16 | g << 8 | b;
            case RGB:
                return r << 16 | g << 8 | b;
            case RGBA:
                return r << 24 | g << 16 | b << 8 | a;
        }
        return 0;
    }

    public static void readExtension(Gif gif, ByteReader r) throws IOException {
        short ext = r.readUByte();
        switch (ext) {
            case 0xFE:
                System.out.println("Comment:" + new String(skipSubBlock(r, new ByteList())));
                break;
            case 0xFF:
                readAppExtensions(gif, r);
                break;
            case 0x01: // skip it
                gif.endFrame();
                System.err.println("[GIFDecoder - PictureTextExtension]: No one *ever* uses this.\n    If you use it, deal with parsing it YOURSELF.");
                //r.readByte(); // Always 12
                //r.readBytes(12);
                r.index += 13;
                skipSubBlock(r);
                break;
            case 0xF9:
                if (gif.currFrame != null) {
                    throw new IOException("Frame end not found!");
                }
                gif.beginFrame();
                readGraphicController(gif, r);
                break;
            default:
                throw new IOException("Unknown tag: " + ext);
        }

    }

    public static int skipSubBlock(ByteReader r) {
        int startIndex = r.index;
        short subBlockLen = r.readUByte();
        while (subBlockLen != 0 && !r.isFinished()) {
            r.index += subBlockLen;
            subBlockLen = r.readUByte();
            startIndex++;
        }
        return r.index - startIndex - 1;
    }

    public static byte[] skipSubBlock(ByteReader r, ByteList buf) {
        byte b = r.readByte();
        while (b != 0) {
            buf.add(b);
            b = r.readByte();
        }

        return buf.toByteArray();
    }

    public static void readGraphicController(Gif gif, ByteReader r) {
        Frame frame = gif.currFrame;
        r.index++;
        byte flag = r.readByte();
        frame.disposalMethod = (flag & 0x1C) >>> 2;
        frame.transparent = isBitTrue(flag, 0);
        frame.delay = r.readUShortR();
        frame.transpantColorIndex = r.readUByte();
        r.index++;
    }

    public static void readAppExtensions(Gif gif, ByteReader r) {
        short len = r.readUByte();
        gif.appName = new String(r.readBytes(8));
        gif.appCode = new String(r.readBytes(3));
        //if(len > 11)
        skipSubBlock(r);
    }

    public static void readDescriptor(Gif gif, ByteReader r) {
        gif.beginFrame();
        Frame frame = gif.currFrame;
        frame.offsetX = r.readUShortR();
        frame.offsetY = r.readUShortR();
        frame.width = r.readUShortR();
        frame.height = r.readUShortR();
        byte flag = r.readByte();
        frame.sorted = isBitTrue(flag, 5);
        frame.interlace = isBitTrue(flag, 6);
        if (isBitTrue(flag, 7)) {
            frame.localColorTable = new int[1 << getSpecifyBit(flag, 0, 2) + 1];
            parseColorTable(frame.localColorTable, r);
        }

        frame.minCodeSize = r.readByte();

        int thisIndex = r.index;
        int subBlockDataSize = skipSubBlock(r);
        r.index = thisIndex;

        frame.imgData = skipSubBlock(r, new ByteList(subBlockDataSize + 2));

        gif.endFrame();
    }

    public static boolean isBitTrue(int i, int bit) {
        return bit == 0 ? ((i & 1) == 1) : ((i & (1 << bit)) >> bit) == 1;
    }

    public static int getSpecifyBit(int n, int fromInclude, int toInclude) {
        int k = 0;
        for (int i = fromInclude; i <= toInclude; i++) {
            k |= (1 << i);
        }
        return (n & k) >>> fromInclude;
    }

    static final class LZWInflater {
        public final int[][] dict = new int[4096][1];
        public int length;

        public int minCodeSize;
        public int clearCode;
        public int endOfInfoCode;
        public int codeSize;

        public final void add(int last, int code) {
            int[] lastVal = dict[last];
            int nowVal = dict[code][0];
            int[] newArr = new int[lastVal.length + 1];
            System.arraycopy(lastVal, 0, newArr, 0, lastVal.length);
            newArr[newArr.length - 1] = nowVal;
            dict[length++] = newArr;
        }

        public int bitPos = 0;
        public byte[] byteData = null;

        public final int next() {
            int code = 0;
            for (int i = 0; i < codeSize; i++) {
                if ((byteData[bitPos >> 3] & (1 << (bitPos & 7))) != 0) {
                    code |= 1 << i;
                }
                bitPos++;
            }
            return code;
        }

        public final void clear() {
            this.codeSize = minCodeSize + 1;
            this.length = clearCode + 2;
            for (int i = 0; i < clearCode; i++) {
                this.dict[i] = new int[]{i};
            }
            this.dict[clearCode] = new int[0];
            this.dict[endOfInfoCode] = null;
        }

        public final void init(Frame frame, int[] color, byte[] byteData) {
            int numColors = color.length;

            this.minCodeSize = frame.minCodeSize;
            this.clearCode = 1 << minCodeSize;
            this.endOfInfoCode = clearCode + 1;
            this.codeSize = minCodeSize + 1;
            this.byteData = byteData;
            this.bitPos = 0;
            this.length = 0;

            if (frame.transparent && frame.transpantColorIndex < numColors) {
                this.dict[frame.transpantColorIndex][0] = 0;
            }
        }

        public String toString() {
            return "LZWDecoder: \n   Dict length: " + clearCode + ' ' + minCodeSize + "\n   Stream length: " + byteData.length;
        }
    }

    public static class Gif {
        public int width;
        public int height;
        int wh;
        boolean sorted;
        boolean noPrevFrame = true;
        short bgColorIndex;
        int[] globalColorTable = _EMPTY;
        double whPercent = 1.0d;

        public String appName;
        public String appCode;

        Frame currFrame = null;
        public List<Frame> frames = new ArrayList<>(40);

        void beginFrame() {
            if (currFrame != null)
                return;//throw new RuntimeException("Frame is not end.");
            currFrame = new Frame();
            frames.add(currFrame);
        }

        void endFrame() {
            if (currFrame == null)
                throw new RuntimeException("Frame is not begin.");
            if (currFrame.disposalMethod == 3)
                noPrevFrame = false;
            currFrame = null;
        }

        static final int[] _EMPTY = new int[0];


        public List<int[]> toColorArray() {
            List<int[]> frameAlignedData = new ArrayList<>((int) (frames.size() / 0.7) + 1);
            LZWInflater inf = new LZWInflater();
            int[] prevFrame = null;
            for (Frame frame : frames) {
                decodeGif(frame, inf);
                prevFrame = expandFrame(frame, prevFrame, frameAlignedData);
            }
            this.currFrame = null;
            //this.frames.clear();
            //this.frames = null;
            this.globalColorTable = _EMPTY;
            return frameAlignedData;
        }

        public List<BufferedImage> toImageList(boolean transparent) {
            List<BufferedImage> imageList = new ArrayList<>((int) (frames.size() / 0.7) + 1);
            LZWInflater inf = new LZWInflater();
            BufferedImage prevFrame = null;
            for (Frame frame : frames) {
                decodeGif(frame, inf);
                prevFrame = expandFrame(frame, prevFrame, imageList, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
            }
            this.globalColorTable = _EMPTY;
            return imageList;
        }


        final void decodeGif(Frame frame, LZWInflater inf) {
            if (frame.pixels == null) {
                int[] color = frame.localColorTable == _EMPTY ? globalColorTable : frame.localColorTable;
                inf.init(frame, color, frame.imgData);
                int[] pixels = imageDecode(frame, color, inf);
                if (frame.interlace) {
                    pixels = deinterlace(pixels, frame);
                }
                frame.setImgData(pixels);
            } else {
                System.out.println("Warn: gif already decoded.");
            }
        }

        final void drawImage(int[] canvas, int[] frame, int offsetX, int offsetY, int frameWidth, int frameHeight, boolean doTransparent) {
            //System.out.println("frame size: " + frame.length);
            for (int canvasX = offsetX, frameX = 0, dx = offsetX + frameWidth; canvasX < dx; canvasX++, frameX++) {
                for (int canvasY = offsetY, frameY = 0, dy = offsetY + frameHeight; canvasY < dy; canvasY++, frameY++) {
                    int px = frame[frameY * frameWidth + frameX];
                    if (doTransparent && px == 0)
                        continue;
                    canvas[canvasY * width + canvasX] = px;
                }
            }
        }

        final void clearRect(int[] array, int offsetX, int offsetY, int frameWidth, int frameHeight) {
            for (int y = offsetY, dy = Math.min(this.height, offsetY + frameHeight); y < dy; y++) {
                for (int x = offsetX, dx = Math.min(this.width, offsetX + frameWidth); x < dx; x++) {
                    array[y * width + x] = getBackgroundColor();
                }
            }
        }


        final int[] expandFrame(Frame frame, int[] prevFrame, List<int[]> list) {
            int[] currPrevFrame = new int[wh];
            if (frame.disposalMethod == 3) {
                System.arraycopy(prevFrame, 0, currPrevFrame, 0, this.wh);
            }

            int[] argbArray = (prevFrame == null) ? new int[wh] : prevFrame;
            drawImage(argbArray, frame.pixels, frame.offsetX, frame.offsetY, frame.width, frame.height, frame.transparent);
            list.add(argbArray);

            if (frame.disposalMethod != 3) {
                System.arraycopy(argbArray, 0, currPrevFrame, 0, this.wh);
            }
            if (frame.disposalMethod == 2) {
                clearRect(currPrevFrame, frame.offsetX, frame.offsetY, frame.width, frame.height);
            }
            return argbArray;
        }

        final BufferedImage expandFrame(Frame frame, BufferedImage prevFrame, List<BufferedImage> list, int type) {
            BufferedImage currPrevFrameImg = new BufferedImage(this.width, this.height, type);
            int[] currPrevFrame = ((DataBufferInt) currPrevFrameImg.getRaster().getDataBuffer()).getData();

            BufferedImage output = prevFrame == null ? new BufferedImage(this.width, this.height, type) : prevFrame;
            int[] argbArray = ((DataBufferInt) output.getRaster().getDataBuffer()).getData();

            if (frame.disposalMethod == 3) {
                System.arraycopy(argbArray, 0, currPrevFrame, 0, this.wh);
            }

            drawImage(argbArray, frame.pixels, frame.offsetX, frame.offsetY, frame.width, frame.height, frame.transparent);
            output.flush();
            list.add(output);

            if (frame.disposalMethod != 3) {
                System.arraycopy(argbArray, 0, currPrevFrame, 0, this.wh);
            }
            if (frame.disposalMethod == 2) {
                clearRect(currPrevFrame, frame.offsetX, frame.offsetY, frame.width, frame.height);
            }
            return currPrevFrameImg;
        }

        final int[] imageDecode(Frame frame, int[] color, LZWInflater codes) {
            int clearCode = codes.clearCode;
            int endOfInfoCode = codes.endOfInfoCode;
            int transparent = frame.transparent ? frame.transpantColorIndex : -1;
            IntBuffer output = new IntBuffer(clearCode);
            int code = 0, prevCode;
            //System.out.println(codes);
            while (true) {
                prevCode = code;
                code = codes.next();

                if (code == clearCode) {
                    codes.clear();
                    continue;
                } else if (code == endOfInfoCode) {
                    break;
                }

                if (code < codes.length) {
                    if (prevCode != clearCode) {
                        codes.add(prevCode, code);
                    }
                } else {
                    if (code != codes.length)
                        throw new ArrayIndexOutOfBoundsException("Invalid LZW code." + code + "/" + codes.length);
                    codes.add(prevCode, prevCode);
                }
                for (int i : codes.dict[code]) {
                    if (i == transparent)
                        output.add(0);
                    else
                        output.add(color[i]);
                }
                //System.out.println("233");

                if (codes.length == (1 << codes.codeSize) && codes.codeSize < 12) {
                    // If we're at the last code and codeSize is 12, the next code will be a clearCode, and it'll be 12 bits long.
                    codes.codeSize++;
                }
            }

            // I don't know if this is technically an error, but some GIFs do it.
            //if (Math.ceil(pos / 8) !== data.length) throw new Error('Extraneous LZW bytes.');
            //System.out.println(output.size);
            return output.toIntArray();
        }

        final int[] deinterlace(int[] src, Frame frame) {
            int w = frame.width, h = frame.height, wh = w * h;
            int[] dest = new int[src.length];

            int set2Y = h + 7 >>> 3;
            int set3Y = set2Y + (h + 3 >>> 3);
            int set4Y = set3Y + (h + 1 >>> 2);

            int set2 = w * set2Y,
                    set3 = w * set3Y,
                    set4 = w * set4Y;

            int w2 = w << 1,
                    w4 = w2 << 1,
                    w8 = w4 << 1;

            int from = 0, to = 0;
            for (; from < set2; from += w, to += w8) {
                System.arraycopy(src, from, dest, to, w);
            }
            for (to = w4; from < set3; from += w, to += w8) {
                System.arraycopy(src, from, dest, to, w);
            }
            for (to = w2; from < set4; from += w, to += w4) {
                System.arraycopy(src, from, dest, to, w);
            }
            for (to = w; from < wh; from += w, to += w2) {
                System.arraycopy(src, from, dest, to, w);
            }
            return dest;
        }

        public final int getBackgroundColor() {
            if (globalColorTable != _EMPTY) {
                return globalColorTable[bgColorIndex];
            }
            Frame frame = this.frames.get(0);
            if (frame.localColorTable != _EMPTY)
                return frame.localColorTable[bgColorIndex];
            return 0;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder("GIF File X: \n      Width: ").append(width).append("\n      Height: ").append(height).append("\n      Sorted: ").append(sorted).append("\n      Background: ").append(getBackgroundColor()).append("\n      Application Name: ").append(appName).append("\n      Application (itself) identifier: ").append(appCode).append("\n");
            for (Frame frame : frames) {
                sb.append(frame).append('\n');
            }
            return sb.toString();
        }
    }

    public static class IntBuffer {
        public int[] buffer;
        public int cap;
        public int size;

        public IntBuffer(int initialSize) {
            this.buffer = new int[initialSize];
            this.cap = initialSize;
            this.size = 0;
        }

        public IntBuffer() {
            this(8192);
        }

        public IntBuffer append(int[] arr) {
            checkSize(size + arr.length);
            System.arraycopy(arr, 0, buffer, size, arr.length);
            size += arr.length;
            return this;
        }

        public IntBuffer add(int i) {
            checkSize(size + 1);
            buffer[size++] = i;
            return this;
        }

        public void checkSize(int size) {
            if (size > cap) {
                int oldCap = cap;
                cap += (int) Math.max(this.size * 1.5d, size - cap + 1);
                int[] newBuffer = new int[cap];
                System.arraycopy(buffer, 0, newBuffer, 0, oldCap);
                buffer = null;
                System.gc();
                buffer = newBuffer;
            }
        }

        public int[] toIntArray() {
            int[] result = new int[size];
            System.arraycopy(buffer, 0, result, 0, size);
            return result;
        }
    }

    public static class Frame {
        public int offsetX;
        public int offsetY;
        public int width;
        public int height;

        int disposalMethod;
        boolean transparent;
        boolean sorted;
        boolean interlace;
        public int delay;
        int[] localColorTable = Gif._EMPTY;

        Short transpantColorIndex;
        Byte minCodeSize;
        byte[] imgData;

        public int[] pixels;

        public void setImgData(int[] pixels) {
            this.imgData = null;
            this.transpantColorIndex = null;
            this.minCodeSize = null;
            this.localColorTable = Gif._EMPTY;
            this.pixels = pixels;
        }

        public String toString() {
            return "   Frame X: \n      Left: " + offsetX + "\n      Top: " + offsetY + "\n      Width: " + width + "\n      Height: " + height + "\n      Disposal method: " + disposalMethod + "\n      Transpant: " + transparent + "\n      Interlaced: " + interlace + "\n      Delay: " + delay + "\n      Decoded image ARGB data: " + imgData.length;
        }
    }
}