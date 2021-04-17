/*
 * This file is a part of MI
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2021 Roj234
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ilib.util;

import java.io.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * NBT IO class
 *
 * @see <a href="https://github.com/udoprog/c10t/blob/master/docs/NBT.txt">Online NBT specification</a>
 */
public class MyNBT {
    private final NBTType type;
    private NBTType listNBTType = null;
    private final String name;
    private Object value;

    /**
     * Create a new List or Compound NBT tag.
     *
     * @param type  either List or Compound
     * @param name  name for the new tag or null to create an unnamed tag.
     * @param value list of tags to add to the new tag.
     */
    public MyNBT(NBTType type, String name, MyNBT[] value) {
        this(type, name, (Object) value);
    }

    /**
     * Create a new List with an empty list. Use {@link MyNBT#addTag(MyNBT)} to add tags later.
     *
     * @param name        name for this tag or null to create an unnamed tag.
     * @param listNBTType type of the elements in this empty list.
     */
    public MyNBT(String name, NBTType listNBTType) {
        this(NBTType.LIST, name, listNBTType);
    }

    /**
     * Create a new NBT tag.
     *
     * @param type  any value from the {@link NBTType} enum.
     * @param name  name for the new tag or null to create an unnamed tag.
     * @param value an object that fits the tag type or a {@link NBTType} to create an empty List with this list type.
     */
    public MyNBT(NBTType type, String name, Object value) {
        switch (type) {
            case END:
                if (value != null)
                    throw new IllegalArgumentException();
                break;
            case BYTE:
                if (!(value instanceof Byte))
                    throw new IllegalArgumentException();
                break;
            case SHORT:
                if (!(value instanceof Short))
                    throw new IllegalArgumentException();
                break;
            case INT:
                if (!(value instanceof Integer))
                    throw new IllegalArgumentException();
                break;
            case LONG:
                if (!(value instanceof Long))
                    throw new IllegalArgumentException();
                break;
            case FLOAT:
                if (!(value instanceof Float))
                    throw new IllegalArgumentException();
                break;
            case DOUBLE:
                if (!(value instanceof Double))
                    throw new IllegalArgumentException();
                break;
            case BYTE_ARRAY:
                if (!(value instanceof byte[]))
                    throw new IllegalArgumentException();
                break;
            case STRING:
                if (!(value instanceof String))
                    throw new IllegalArgumentException();
                break;
            case LIST:
                if (value instanceof NBTType) {
                    this.listNBTType = (NBTType) value;
                    value = new MyNBT[0];
                } else {
                    if (!(value instanceof MyNBT[]))
                        throw new IllegalArgumentException();
                    this.listNBTType = (((MyNBT[]) value)[0]).getNBTType();
                }
                break;
            case COMPOUND:
                if (!(value instanceof MyNBT[]))
                    throw new IllegalArgumentException();
                break;
            case INT_ARRAY:
                if (!(value instanceof int[]))
                    throw new IllegalArgumentException();
                break;
            default:
                throw new IllegalArgumentException();
        }
        this.type = type;
        this.name = name;
        this.value = value;
    }

    public NBTType getNBTType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object newValue) {
        switch (type) {
            case END:
                if (value != null)
                    throw new IllegalArgumentException();
                break;
            case BYTE:
                if (!(value instanceof Byte))
                    throw new IllegalArgumentException();
                break;
            case SHORT:
                if (!(value instanceof Short))
                    throw new IllegalArgumentException();
                break;
            case INT:
                if (!(value instanceof Integer))
                    throw new IllegalArgumentException();
                break;
            case LONG:
                if (!(value instanceof Long))
                    throw new IllegalArgumentException();
                break;
            case FLOAT:
                if (!(value instanceof Float))
                    throw new IllegalArgumentException();
                break;
            case DOUBLE:
                if (!(value instanceof Double))
                    throw new IllegalArgumentException();
                break;
            case BYTE_ARRAY:
                if (!(value instanceof byte[]))
                    throw new IllegalArgumentException();
                break;
            case STRING:
                if (!(value instanceof String))
                    throw new IllegalArgumentException();
                break;
            case LIST:
                if (value instanceof NBTType) {
                    this.listNBTType = (NBTType) value;
                    value = new MyNBT[0];
                } else {
                    if (!(value instanceof MyNBT[]))
                        throw new IllegalArgumentException();
                    this.listNBTType = (((MyNBT[]) value)[0]).getNBTType();
                }
                break;
            case COMPOUND:
                if (!(value instanceof MyNBT[]))
                    throw new IllegalArgumentException();
                break;
            case INT_ARRAY:
                if (!(value instanceof int[]))
                    throw new IllegalArgumentException();
                break;
            default:
                throw new IllegalArgumentException();
        }

        value = newValue;
    }

    public NBTType getListNBTType() {
        return listNBTType;
    }

    /**
     * Add a tag to a List or a Compound.
     */
    public void addTag(MyNBT tag) {
        if (type != NBTType.LIST && type != NBTType.COMPOUND)
            throw new RuntimeException();
        MyNBT[] subtags = (MyNBT[]) value;

        int index = subtags.length;

        //For Compund entries, we need to add the tag BEFORE the end,
        //or the new tag gets placed after the End, messing up the data.
        //End MUST be kept at the very end of the Compound.
        if (type == NBTType.COMPOUND) index--;
        insertTag(tag, index);
    }

    /**
     * Add a tag to a List or a Compound at the specified index.
     */
    public void insertTag(MyNBT tag, int index) {
        if (type != NBTType.LIST && type != NBTType.COMPOUND)
            throw new RuntimeException();
        MyNBT[] subtags = (MyNBT[]) value;
        if (subtags.length > 0)
            if (type == NBTType.LIST && tag.getNBTType() != getListNBTType())
                throw new IllegalArgumentException();
        if (index > subtags.length)
            throw new IndexOutOfBoundsException();
        MyNBT[] newValue = new MyNBT[subtags.length + 1];
        System.arraycopy(subtags, 0, newValue, 0, index);
        newValue[index] = tag;
        System.arraycopy(subtags, index, newValue, index + 1, subtags.length - index);
        value = newValue;
    }

    /**
     * Remove a tag from a List or a Compound at the specified index.
     *
     * @return the removed tag
     */
    public MyNBT removeTag(int index) {
        if (type != NBTType.LIST && type != NBTType.COMPOUND)
            throw new RuntimeException();
        MyNBT[] subtags = (MyNBT[]) value;
        MyNBT victim = subtags[index];
        MyNBT[] newValue = new MyNBT[subtags.length - 1];
        System.arraycopy(subtags, 0, newValue, 0, index);
        index++;
        System.arraycopy(subtags, index, newValue, index - 1, subtags.length - index);
        value = newValue;
        return victim;
    }

    /**
     * Remove a tag from a List or a Compound. If the tag is not a child of this tag then nested tags are searched.
     *
     * @param tag tag to look for
     */
    public void removeSubTag(MyNBT tag) {
        if (type != NBTType.LIST && type != NBTType.COMPOUND)
            throw new RuntimeException();
        if (tag == null)
            return;
        MyNBT[] subtags = (MyNBT[]) value;
        for (int i = 0; i < subtags.length; i++) {
            if (subtags[i] == tag) {
                removeTag(i);
                return;
            } else {
                if (subtags[i].type == NBTType.LIST || subtags[i].type == NBTType.COMPOUND) {
                    subtags[i].removeSubTag(tag);
                }
            }
        }
    }

    /**
     * Find the first nested tag with specified name in a Compound.
     *
     * @param name the name to look for. May be null to look for unnamed tags.
     * @return the first nested tag that has the specified name.
     */
    public MyNBT findTagByName(String name) {
        return findNextTagByName(name, null);
    }

    /**
     * Find the first nested tag with specified name in a List or Compound after a tag with the same name.
     *
     * @param name  the name to look for. May be null to look for unnamed tags.
     * @param found the previously found tag with the same name.
     * @return the first nested tag that has the specified name after the previously found tag.
     */
    public MyNBT findNextTagByName(String name, MyNBT found) {
        if (type != NBTType.LIST && type != NBTType.COMPOUND)
            return null;
        MyNBT[] subtags = (MyNBT[]) value;
        for (MyNBT subtag : subtags) {
            if ((subtag.name == null && name == null) || (subtag.name != null && subtag.name.equals(name))) {
                return subtag;
            } else {
                MyNBT newFound = subtag.findTagByName(name);
                if (newFound != null)
                    if (newFound != found) {
                        return newFound;
                    }
            }
        }
        return null;
    }

    /**
     * Read a tag and its nested tags from an InputStream.
     *
     * @param is stream to read from, like a FileInputStream
     * @return NBT tag or structure read from the InputStream
     * @throws IOException if there was no valid NBT structure in the InputStream or if another IOException occurred.
     */
    public static MyNBT readFrom(InputStream is) throws IOException {
        DataInputStream dis = new DataInputStream(new GZIPInputStream(is));
        byte type = dis.readByte();
        MyNBT tag;

        if (type == 0) {
            tag = new MyNBT(NBTType.END, null, null);
        } else {
            tag = new MyNBT(NBTType.values()[type], dis.readUTF(), readPayload(dis, type));
        }

        dis.close();

        return tag;
    }

    private static Object readPayload(DataInputStream dis, byte type) throws IOException {
        switch (type) {
            case 0:
                return null;
            case 1:
                return dis.readByte();
            case 2:
                return dis.readShort();
            case 3:
                return dis.readInt();
            case 4:
                return dis.readLong();
            case 5:
                return dis.readFloat();
            case 6:
                return dis.readDouble();
            case 7:
                int length = dis.readInt();
                byte[] ba = new byte[length];
                dis.readFully(ba);
                return ba;
            case 8:
                return dis.readUTF();
            case 9:
                byte lt = dis.readByte();
                int ll = dis.readInt();
                MyNBT[] lo = new MyNBT[ll];
                for (int i = 0; i < ll; i++) {
                    lo[i] = new MyNBT(NBTType.values()[lt], null, readPayload(dis, lt));
                }
                if (lo.length == 0)
                    return NBTType.values()[lt];
                else
                    return lo;
            case 10:
                byte stt;
                MyNBT[] tags = new MyNBT[0];
                do {
                    stt = dis.readByte();
                    String name = null;
                    if (stt != 0) {
                        name = dis.readUTF();
                    }
                    MyNBT[] newTags = new MyNBT[tags.length + 1];
                    System.arraycopy(tags, 0, newTags, 0, tags.length);
                    newTags[tags.length] = new MyNBT(NBTType.values()[stt], name, readPayload(dis, stt));
                    tags = newTags;
                } while (stt != 0);
                return tags;
            case 11:
                int len = dis.readInt();
                int[] ia = new int[len];
                for (int i = 0; i < len; i++)
                    ia[i] = dis.readInt();
                return ia;

        }
        return null;
    }

    /**
     * Read a tag and its nested tags from an InputStream.
     *
     * @param os stream to write to, like a FileOutputStream
     * @throws IOException if this is not a valid NBT structure or if any IOException occurred.
     */
    public void writeTo(OutputStream os) throws IOException {
        GZIPOutputStream gzos;
        DataOutputStream dos = new DataOutputStream(gzos = new GZIPOutputStream(os));
        dos.writeByte(type.ordinal());
        if (type != NBTType.END) {
            dos.writeUTF(name);
            writePayload(dos);
        }
        gzos.flush();
        gzos.close();
    }

    private void writePayload(DataOutputStream dos) throws IOException {
        switch (type) {
            case END:
                break;
            case BYTE:
                dos.writeByte((Byte) value);
                break;
            case SHORT:
                dos.writeShort((Short) value);
                break;
            case INT:
                dos.writeInt((Integer) value);
                break;
            case LONG:
                dos.writeLong((Long) value);
                break;
            case FLOAT:
                dos.writeFloat((Float) value);
                break;
            case DOUBLE:
                dos.writeDouble((Double) value);
                break;
            case BYTE_ARRAY:
                byte[] ba = (byte[]) value;
                dos.writeInt(ba.length);
                dos.write(ba);
                break;
            case STRING:
                dos.writeUTF((String) value);
                break;
            case LIST:
                MyNBT[] list = (MyNBT[]) value;
                dos.writeByte(getListNBTType().ordinal());
                dos.writeInt(list.length);
                for (MyNBT tt : list) {
                    tt.writePayload(dos);
                }
                break;
            case COMPOUND:
                MyNBT[] subtags = (MyNBT[]) value;
                for (MyNBT st : subtags) {
                    NBTType type = st.getNBTType();
                    dos.writeByte(type.ordinal());
                    if (type != NBTType.END) {
                        dos.writeUTF(st.getName());
                        st.writePayload(dos);
                    }
                }
                break;
            case INT_ARRAY:
                int[] ia = (int[]) value;
                dos.writeInt(ia.length);
                for (int item : ia) dos.writeInt(item);
                break;

        }
    }

    /**
     * Print the NBT structure to System.out
     */
    public void print() {
        print(this, 0);
    }

    private String getNBTTypeString(NBTType type) {
        if (type == null)
            return null;
        return type.name();
    }

    private void indent(int indent) {
        for (int i = 0; i < indent; i++) {
            System.out.print("   ");
        }
    }

    private void print(MyNBT t, int indent) {
        NBTType type = t.getNBTType();
        if (type == NBTType.END)
            return;
        String name = t.getName();
        indent(indent);
        System.out.print(getNBTTypeString(t.getNBTType()));
        if (name != null)
            System.out.print("(\"" + t.getName() + "\")");
        if (type == NBTType.BYTE_ARRAY) {
            byte[] b = (byte[]) t.getValue();
            System.out.println(": [" + b.length + " bytes]");
        } else if (type == NBTType.LIST) {
            MyNBT[] subtags = (MyNBT[]) t.getValue();
            System.out.println(": " + subtags.length + " entries of type " + getNBTTypeString(t.getListNBTType()));
            for (MyNBT st : subtags) {
                print(st, indent + 1);
            }
            indent(indent);
            System.out.println("}");
        } else if (type == NBTType.COMPOUND) {
            MyNBT[] subtags = (MyNBT[]) t.getValue();
            System.out.println(": " + (subtags.length - 1) + " entries");
            indent(indent);
            System.out.println("{");
            for (MyNBT st : subtags) {
                print(st, indent + 1);
            }
            indent(indent);
            System.out.println("}");
        } else if (type == NBTType.INT_ARRAY) {
            int[] i = (int[]) t.getValue();
            System.out.println(": [" + i.length * 4 + " bytes]");

        } else {
            System.out.println(": " + t.getValue());
        }
    }

}
