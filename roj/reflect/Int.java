package roj.reflect;

import roj.text.TextUtil;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2020/10/17 18:31
 */
final class Int {
    static sun.misc.Unsafe U;
    static sun.misc.JavaLangAccess JLA;

    static {
        try {
            Field f = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (sun.misc.Unsafe) f.get(null);
            JLA = sun.misc.SharedSecrets.getJavaLangAccess();
        } catch (Throwable ignored) {}
    }

    static final class UFA extends IFieldAccessor {
        static final List<String> arr = TextUtil.splitStringF(new ArrayList<>(9), "BOOL,BYTE,SHORT,CHAR,INT,LONG,FLOAT,DOUBLE,OBJECT", ',');
        /**
         * 内存地址相对instance起始偏移量
         */
        final long offset;
        final byte type;

        /**
         * 检测类型转换 <Br>
         * JVM内部没有类型检查 <br>
         * 只要index对上了
         * ...
         */
        public boolean checkCast = true;

        /**
         * 警告: 修改这个字段的只可能会破坏concurrent!
         */
        public boolean isVolatile;

        UFA(@Nonnull Field field) {
            super(field);
            this.offset = J8Util.getFieldOffset(field);
            if (this.offset == -1) {
                throw new IllegalArgumentException("Field offset error " + field);
            }
            if (isStatic) {
                this.instance = U.staticFieldBase(field);
            }
            Class<?> type = field.getType();
            if (type == Boolean.TYPE) {
                this.type = 0;
            } else if (type == Byte.TYPE) {
                this.type = 1;
            } else if (type == Short.TYPE) {
                this.type = 2;
            } else if (type == Character.TYPE) {
                this.type = 3;
            } else if (type == Integer.TYPE) {
                this.type = 4;
            } else if (type == Long.TYPE) {
                this.type = 5;
            } else if (type == Float.TYPE) {
                this.type = 6;
            } else {
                this.type = (byte) ((type == Double.TYPE) ? 7 : 8);
            }
            this.isVolatile = Modifier.isVolatile(field.getModifiers());
        }

        @Override
        public void setInstance(@Nonnull Object instance) {
            super.setInstance(instance);
            if(!isStatic)
                checkObjectType(instance);
        }

        @Override
        public void clearInstance() {
            if (isStatic)
                return;
            this.instance = null;
        }

        private void checkObjectType(Object obj) {
            if (!field.getDeclaringClass().isInstance(obj)) // include null
                throw new IllegalArgumentException("Cannot set instance to not instance of " + field.getDeclaringClass().getName());
        }

        private void checkType(byte required) {
            if (this.type != required)
                throw new IllegalArgumentException("Type " + arr.get(this.type) + " cannot cast to Type " + arr.get(required));
        }

        @Override
        public Object getObject() {
            checkType((byte) 8);
            checkAccess();
            return isVolatile ? U.getObjectVolatile(instance, offset) : U.getObject(instance, offset);
        }

        @Override
        public boolean getBoolean() {
            checkType((byte) 0);
            checkAccess();
            return isVolatile ? U.getBooleanVolatile(instance, offset) : U.getBoolean(instance, offset);
        }

        @Override
        public byte getByte() {
            checkType((byte) 1);
            checkAccess();
            return isVolatile ? U.getByteVolatile(instance, offset) : U.getByte(instance, offset);
        }

        @Override
        public char getChar() {
            checkType((byte) 2);
            checkAccess();
            return isVolatile ? U.getCharVolatile(instance, offset) : U.getChar(instance, offset);
        }

        @Override
        public short getShort() {
            checkType((byte) 3);
            checkAccess();
            return isVolatile ? U.getShortVolatile(instance, offset) : U.getShort(instance, offset);
        }

        @Override
        public int getInt() {
            checkType((byte) 4);
            checkAccess();
            return isVolatile ? U.getIntVolatile(instance, offset) : U.getInt(instance, offset);
        }

        @Override
        public long getLong() {
            checkType((byte) 5);
            checkAccess();
            return isVolatile ? U.getLongVolatile(instance, offset) : U.getLong(instance, offset);
        }

        @Override
        public float getFloat() {
            checkType((byte) 6);
            checkAccess();
            return isVolatile ? U.getFloatVolatile(instance, offset) : U.getFloat(instance, offset);
        }

        @Override
        public double getDouble() {
            checkType((byte) 7);
            checkAccess();
            return isVolatile ? U.getDoubleVolatile(instance, offset) : U.getDouble(instance, offset);
        }

        @Override
        public void setObject(Object obj) {
            if (type != 8) {
                switch (type) {
                    case 0:
                        setBoolean((Boolean) obj);
                        break;
                    case 1:
                        setByte((Byte) obj);
                        break;
                    case 2:
                        setChar((Character) obj);
                        break;
                    case 3:
                        setShort((Short) obj);
                        break;
                    case 4:
                        setInt((Integer) obj);
                        break;
                    case 5:
                        setLong((Long) obj);
                        break;
                    case 6:
                        setFloat((Float) obj);
                        break;
                    case 7:
                        setDouble((Double) obj);
                        break;
                }
                return;
            }
            checkType((byte) 8);
            checkAccess();
            if (checkCast && obj != null && !field.getType().isInstance(obj))
                throw new IllegalArgumentException(obj.getClass().getName() + " cannot cast to " + field.getType().getName());
            if (isVolatile) {
                U.putObjectVolatile(instance, offset, obj);
            } else {
                U.putObject(instance, offset, obj);
            }
        }

        @Override
        public void setBoolean(boolean value) {
            checkType((byte) 0);
            checkAccess();
            if (isVolatile) {
                U.putBooleanVolatile(instance, offset, value);
            } else {
                U.putBoolean(instance, offset, value);
            }
        }

        @Override
        public void setByte(byte value) {
            checkType((byte) 1);
            checkAccess();
            if (isVolatile) {
                U.putByteVolatile(instance, offset, value);
            } else {
                U.putByte(instance, offset, value);
            }
        }

        @Override
        public void setChar(char value) {
            checkType((byte) 2);
            checkAccess();
            if (isVolatile) {
                U.putCharVolatile(instance, offset, value);
            } else {
                U.putChar(instance, offset, value);
            }
        }

        @Override
        public void setShort(short value) {
            checkType((byte) 3);
            checkAccess();
            if (isVolatile) {
                U.putShortVolatile(instance, offset, value);
            } else {
                U.putShort(instance, offset, value);
            }
        }

        @Override
        public void setInt(int value) {
            checkType((byte) 4);
            checkAccess();
            if (isVolatile) {
                U.putIntVolatile(instance, offset, value);
            } else {
                U.putInt(instance, offset, value);
            }
        }

        @Override
        public void setLong(long value) {
            checkType((byte) 5);
            checkAccess();
            if (isVolatile) {
                U.putLongVolatile(instance, offset, value);
            } else {
                U.putLong(instance, offset, value);
            }
        }

        @Override
        public void setFloat(float value) {
            checkType((byte) 6);
            checkAccess();
            if (isVolatile) {
                U.putFloatVolatile(instance, offset, value);
            } else {
                U.putFloat(instance, offset, value);
            }
        }

        @Override
        public void setDouble(double value) {
            checkType((byte) 7);
            checkAccess();
            if (isVolatile) {
                U.putDoubleVolatile(instance, offset, value);
            } else {
                U.putDouble(instance, offset, value);
            }
        }
    }
}
