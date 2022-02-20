package roj.misc;

import roj.text.ACalendar;
import roj.text.TextUtil;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * @author solo6975
 * @since 2022/3/5 17:32
 */
public class BinaryCompare {
    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("BinaryCompare <a> <b>");
            return;
        }
        File a = new File(args[0]);
        echoFileInfo(a);
        File b = new File(args[1]);
        echoFileInfo(b);
        RandomAccessFile ain = new RandomAccessFile(a, "r");
        RandomAccessFile bin = new RandomAccessFile(b, "r");

        byte[] blA = new byte[1111];
        byte[] blB = new byte[1111];
        int offset=0;
        while (true) {
            int lenA = ain.read(blA);

            int lenB = bin.read(blB);

            int offA = -1;
            for (int i = 0; i < blA.length; i++) {
                if (blA[i] != blB[i]) {
                    offA = i;
                    break;
                }
            }
            if (offA < 0) continue;
            // simulate as blA,Offset = 0
            int offB = findDelta(blA, blB, offA, Math.min(lenA, lenB));
            int dEnd = blA.length;
            for (int i = blA.length - 1 - offB; i >= offB; i--) {
                if (blA[i] != blB[i + offB]) {
                    dEnd = i + offB;
                    break;
                }
            }

            // 估计还要蛮久, fuck math!
            System.out.println("db=" + offA + " de=" + dEnd + " of=" + offB);
            System.out.println("偏移量(按A记) " + Integer.toHexString(offset + offA) + " 至 " + Integer.toHexString(offset + dEnd) + ": ");
            System.out.println("============ File A ============");
            //ain.seek(Math.max(0, ain.getFilePointer() - blA.length + dBegin - 50));
            //ain.read(blA, 0, Math.min(dEnd + 50, blA.length));
            System.out.println(TextUtil.dumpBytes(blA, offA, dEnd));
            System.out.println("============ File B ============");
            //bin.seek(Math.max(0, ain.getFilePointer() - blA.length + dBegin - 50 + off1));
            //bin.read(blA, 0, Math.min(dEnd + 50, blA.length));
            bin.seek(bin.getFilePointer() + offB - offA);
            System.out.println(TextUtil.dumpBytes(blB, offA, offB));

            if (lenA < blA.length) break;
            if (lenB < blB.length) break;
            offset += 1024;
        }
    }

    private static int findDelta(byte[] a, byte[] b, int i, int len) {
        long minDelta = Long.MAX_VALUE;
        int minLength = len;
        int j = 0;
        while (j < len) {
            long dt = computeDelta(a, i, b, j, Math.min(len - i, len - j));
            if (dt < minDelta) {
                minLength = j;
            }
            j++;
        }
        return minLength;
    }

    private static long computeDelta(byte[] a, int aOff, byte[] b, int bOff, int len) {
        long delta = 0;
        while (len-- > 0) {
            delta += Math.abs(a[aOff++] - b[bOff++]);
        }
        return delta;
    }

    private static void echoFileInfo(File a) {
        System.out.println("名: " + a.getName());
        System.out.println("修改日期: " + new ACalendar().formatDate("Y-m-d H:i:s.x", a.lastModified()));
        System.out.println("大小: " + TextUtil.scaledNumber(a.length()));
        System.out.println("=============================================");
    }
}
