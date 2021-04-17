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
    private final short type;

    private String word;

    private final int line, lineOffset;

    public Word(int type, int line, int lineOffset, String word) {
        this.type = (short) type;
        this.line = line;
        this.lineOffset = lineOffset;
        this.word = word;
    }

    public Word(int line, int lineOffset) {
        this.type = WordPresets.EOF;
        this.line = line;
        this.lineOffset = lineOffset;
        this.word = "/EOF";
    }

    @Override
    public String toString() {
        return "Token{#" + type + "@'" + word + '\'' + '}';
    }

    public String val() {
        return word;
    }

    public int getLine() {
        return line;
    }

    public int getLineOffset() {
        return lineOffset;
    }

    public Word number() {
        int v;
        switch (this.type) {
            case WordPresets.HEX:
                v = MathUtils.parseInt(false, this.word, 16);
                //this.type = Keyword.INTEGER;
                break;
            case WordPresets.BINARY:
                v = MathUtils.parseInt(false, this.word, 2);
                //this.type = Keyword.INTEGER;
                break;
            case WordPresets.OCTAL:
                v = MathUtils.parseInt(false, this.word, 8);
                //this.type = Keyword.INTEGER;
                break;
            case WordPresets.INTEGER:
                v = MathUtils.parseInt(false, this.word, 10);
                break;
            default:
                return this;
        }
        this.word = Integer.toString(v);

        return this;
    }

    public short type() {
        switch (type) {
            case WordPresets.OCTAL:
            case WordPresets.BINARY:
            case WordPresets.HEX:
                return WordPresets.INTEGER;
            default:
                return type;
        }
    }

    public Word negative() {
        word = new CharList(word.length() + 1).append('-').append(word).toString();
        return this;
    }
}
