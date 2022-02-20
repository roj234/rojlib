package roj.config.serial;

import roj.config.JSONParser.JSONLexer;
import roj.config.ParseException;
import roj.config.word.WordPresets;

/**
 * @author Roj233
 * @since 2022/2/19 20:50
 */
public class FromJson extends StreamDeserializer {
    public static void main(String[] args) throws ParseException {
        FromJson st = new FromJson();
        st.wr.init("{\"1\":\"2\",\"2\":\"3\"}");
        st.forMap();
        for (int i = 0; i < 2; i++) {
            System.out.println(st.key());
            System.out.println(st.forInt());
        }
    }

    static final int
            left_l_bracket = 13,
            right_l_bracket = 14,
            left_m_bracket = 15,
            right_m_bracket = 16,
            comma = 17,
            colon = 18;

    protected FromJson() {
        super(new JSONLexer());
    }

    @Override
    protected boolean checkString() throws ParseException {
        return poll().type() == WordPresets.STRING;
    }

    @Override
    protected void listNext() throws ParseException {
        if (poll().type() != comma) throw unexcept(",");
    }

    @Override
    protected void endLevel() throws ParseException {
        boolean list = (flag & 12) == LIST;
        if (poll().type() != (list ? right_m_bracket : right_l_bracket)) throw unexcept(list ? "]" : "}");
    }

    @Override
    public void forMap() throws ParseException {
        if (poll().type() != left_l_bracket) throw unexcept("{");
        push(MAP | NEXT);
    }

    @Override
    public void forList() throws ParseException {
        if (poll().type() != left_m_bracket) throw unexcept("[");
        push(LIST);
    }

    @Override
    public String key() throws ParseException {
        if ((flag & 12) != MAP) throw new IllegalStateException("Not map");
        if ((flag & NEXT) == 0) {
            if ((flag & END) == 0) throw new IllegalStateException("!EOF");
            listNext();
            flag &= ~END;
        } else {
            flag &= ~NEXT;
        }
        String key = poll().val();
        if (poll().type() != colon) throw unexcept(":");
        return key;
    }
}
