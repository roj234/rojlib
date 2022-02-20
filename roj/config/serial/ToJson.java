package roj.config.serial;

import roj.config.word.AbstLexer;

/**
 * @author Roj233
 * @since 2022/2/19 19:14
 */
public class ToJson extends StreamSerializer {
    public ToJson() {
        this(0);
    }

    public ToJson(int indent) {
        this.indent = new char[indent];
        for (int i = 0; i < indent; i++) {
            this.indent[i] = ' ';
        }
    }

    private final char[] indent;

    @Override
    protected void listNext() {
        sb.append(",");
        indent(level);
    }

    @Override
    protected void endLevel() {
        indent(level);
        sb.append((flag & 12) == LIST ? ']' : '}');
    }

    @Override
    public void valueMap() {
        push(MAP | NEXT);
        sb.append('{');
    }

    @Override
    public void valueList() {
        push(LIST);
        sb.append('[');
        indent(level);
    }

    @Override
    public void key(String key) {
        if ((flag & 12) != MAP) throw new IllegalStateException("Not map");
        if ((flag & NEXT) == 0) {
            if ((flag & END) == 0) throw new IllegalStateException("!EOF");
            sb.append(",");
            flag &= ~END;
        } else {
            flag &= ~NEXT;
        }
        indent(level);
        AbstLexer.addSlashes(key, sb.append('"')).append("\":");
        if (indent.length > 0) sb.append(' ');
    }

    private void indent(int x) {
        if (indent.length > 0) {
            sb.append('\n');
            while (x-- > 0) sb.append(indent);
        }
    }
}
