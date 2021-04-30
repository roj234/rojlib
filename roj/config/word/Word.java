package roj.config.word;

import roj.math.MathUtils;
import roj.text.CharList;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 * <p>
 * 单词
 *
 * @author solo6975
 * @since 2020/10/3 19:20
 */
public class Word {
    private short type;
    private String val;
    private int index;

    public Word() {}

    /**
     * 复用对象
     */
    public Word reset(int type, int index, String word) {
        this.type = (short) type;
        this.index = index;
        this.val = word;
        return this;
    }

    public Word(int index) {
        this.type = WordPresets.EOF;
        this.index = index;
        this.val = "/EOF";
    }

    @Override
    public String toString() {
        return "Token{#" + type + "@'" + val + '\'' + '}';
    }

    public String val() {
        return val;
    }

    public int getIndex() {
        return index;
    }

    public Word number() {
        int v;
        switch (this.type) {
            case WordPresets.HEX:
                v = MathUtils.parseInt(false, this.val, 16);
                //this.type = Keyword.INTEGER;
                break;
            case WordPresets.BINARY:
                v = MathUtils.parseInt(false, this.val, 2);
                //this.type = Keyword.INTEGER;
                break;
            case WordPresets.OCTAL:
                v = MathUtils.parseInt(false, this.val, 8);
                //this.type = Keyword.INTEGER;
                break;
            case WordPresets.INTEGER:
                v = MathUtils.parseInt(false, this.val, 10);
                break;
            default:
                return this;
        }
        this.type = WordPresets.INTEGER;
        this.val = Integer.toString(v);

        return this;
    }

    public short type() {
        return type;
    }

    @Deprecated
    public Word negative() {
        val = new CharList(val.length() + 1).append('-').append(val).toString();
        return this;
    }
}
