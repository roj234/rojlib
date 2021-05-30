package roj.io;

import roj.collect.MyHashMap;
import roj.text.CharList;
import roj.util.ByteList;
import roj.util.ByteReader;
import roj.util.ByteWriter;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Set;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/4/5 18:14
 */
public class AppendOnlyCache implements Closeable {
    MyHashMap<String, long[]> infoMap = new MyHashMap<>();
    final RandomAccessFile rf;

    static final int MAGIC = 0x27462DE8;

    public AppendOnlyCache(File file) throws IOException {
        rf = new RandomAccessFile(file, "rw");
        if(rf.length() < 4) {
            rf.setLength(4);
            rf.writeInt(MAGIC);
        }
    }

    public void read() throws IOException {
        long len = rf.length();
        if(len < 4) {
            rf.seek(0);
            rf.setLength(4);
            rf.writeInt(MAGIC);
        } else if(len == 4) {
            return;
        }
        rf.seek(4);
        long pos = 4;

        CharList tmp = new CharList(100);
        ByteList bl = new ByteList();
        while (pos < len) {
            int slen = rf.readInt();
            if(slen > 1000) {
                throw new IOException("String length exceed limit 1000");
            }
            bl.ensureCapacity(slen);
            if(rf.read(bl.list, 0, slen) < slen) {
                throw new IOException("Got EOF before read fully");
            }
            bl.pos(slen);
            ByteReader.decodeUTF(-1, tmp, bl);
            String key = tmp.toString();
            tmp.clear();

            pos += 4 + slen;
            slen = rf.readInt();
            infoMap.put(key, new long[] {
                    pos + 4, slen
            });

            pos += 4 + slen;
            rf.seek(pos);
        }
    }

    public boolean contains(String name) {
        return infoMap.containsKey(name);
    }

    public ByteList get(String name, ByteList list) throws IOException {
        long[] arr = infoMap.get(name);
        if(arr == null)
            return null;
        rf.seek(arr[0]);
        list.ensureCapacity((int) arr[1]);
        rf.readFully(list.list, list.offset(), (int)arr[1]);
        list.pos((int) arr[1]);
        return list;
    }

    public byte[] getBytes(String name) throws IOException {
        ByteList bl = get(name, new ByteList());
        if(bl == null)
            return null;
        return bl.getByteArray();
    }

    public String getUTF(String name) throws IOException {
        ByteList bl = get(name, new ByteList());
        if(bl == null)
            return null;
        return ByteReader.readUTF(bl);
    }

    public void append(String name, ByteList list) throws IOException {
        if(infoMap.containsKey(name))
            throw new IOException("Duplicate name, clear() first!");
        if(name.length() > 1000) {
            throw new IOException("String length exceed limit 1000");
        }
        long[] data = new long[] {
                rf.length(), list.pos()
        };
        rf.seek(rf.length());
        rf.setLength(rf.length() + list.pos() + name.length() + 8);

        rf.writeInt(name.length());
        ByteList li = ByteWriter.encodeUTF(name);
        rf.write(li.list, li.offset(), li.pos());

        rf.writeInt(list.pos());
        rf.write(list.list, list.offset(), list.pos());

        infoMap.put(name, data);
    }

    public Set<String> keys() {
        return infoMap.keySet();
    }

    public void clear() throws IOException {
        rf.setLength(4);
        rf.seek(0);
        rf.writeInt(MAGIC);
        infoMap.clear();
    }

    @Override
    public void close() throws IOException {
        rf.close();
    }
}
