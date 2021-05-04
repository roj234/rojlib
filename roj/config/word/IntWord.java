package roj.config.word;

/**
 * This file is a part of MI <br>
 * 版权没有, 仿冒不究,如有雷同,纯属活该 <br>
 *
 * @author solo6975
 * @since 2021/5/3 22:35
 */
public class IntWord extends Word {
    public int number;

    public IntWord(int index, String val, int i) {
        reset(WordPresets.INTEGER, index, val);
        this.number = i;
    }

    @Override
    public IntWord number() {
        return this;
    }
}
