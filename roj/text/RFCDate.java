package roj.text;

import roj.math.MathUtils;

/**
 * @author Roj233
 * @since 2022/3/12 16:13
 */
public class RFCDate {
    @SuppressWarnings("fallthrough")
    public static long parse(CharSequence seq) {
        // Tue, 25 Feb 2022 21:48:10 GMT
        String str = seq.subSequence(0, 3).toString();
        String[] x = ACalendar.UTCWEEK;
        int week = 0;
        while (week < x.length) {
            if (str.equals(x[week++])) {
                break;
            }
        }
        if (week == 8) throw new IllegalArgumentException("Invalid week " + str);
        if (seq.charAt(3) != ',' || seq.charAt(4) != ' ')
            throw new IllegalArgumentException("分隔符错误");
        int day = MathUtils.parseInt(seq, 5, 7, 10);
        if (seq.charAt(7) != ' ') throw new IllegalArgumentException("分隔符错误");

        str = seq.subSequence(8, 11).toString();
        x = ACalendar.UTCMONTH;
        int month = 0;
        while (month < x.length) {
            if (str.equals(x[month++])) {
                break;
            }
        }
        if (month == 12) throw new IllegalArgumentException("Invalid month " + str);

        if (seq.charAt(11) != ' ') throw new IllegalArgumentException("分隔符错误");

        int year = MathUtils.parseInt(seq, 12, 16, 10);
        if (seq.charAt(16) != ' ') throw new IllegalArgumentException("分隔符错误");
        int h = MathUtils.parseInt(seq, 17, 19, 10);
        if (seq.charAt(19) != ':') throw new IllegalArgumentException("分隔符错误");
        int m = MathUtils.parseInt(seq, 20, 22, 10);
        if (seq.charAt(22) != ':') throw new IllegalArgumentException("分隔符错误");
        int s = MathUtils.parseInt(seq, 23, 25, 10);
        if (seq.charAt(25) != ' ') throw new IllegalArgumentException("分隔符错误");

        if (h > 23) throw new IllegalArgumentException("你一天" + h + "小时");
        if (m > 59) throw new IllegalArgumentException("你一小时" + m + "分钟");
        if (s > 59) throw new IllegalArgumentException("你一分钟" + s + "秒");

        long a = (ACalendar.daySinceAD(year, month, day, null) -
                ACalendar.GREGORIAN_OFFSET_DAY) * 86400000L
               + h * 3600000 + m * 60000 + s * 1000;

        int i = -1;
        switch (seq.charAt(26)) {
            case '-':
                i = 1;
            case '+':
                i *= MathUtils.parseInt(seq, 27, 30, 10);
                int d = i % 100;
                if (d < -59 || d > 59) throw new IllegalArgumentException("你一小时" + d + "分钟");
                a += 60000 * d;

                d = i / 100;
                if (d < -23 || d > 23) throw new IllegalArgumentException("你一天" + d + "小时");
                a += 3600000 * d;

                break;
            case 'G':
                if (seq.charAt(27) != 'M' || seq.charAt(28) != 'T')
                    throw new IllegalArgumentException("分隔符错误");
                break;
            default:
                throw new IllegalArgumentException("分隔符错误");
        }

        return a;
    }
}
