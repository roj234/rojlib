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
package roj.text;

import roj.collect.LongBitSet;
import roj.math.MathUtils;

import javax.annotation.Nullable;
import java.util.TimeZone;

/**
 * Replacement? of {@link java.util.Calendar}
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/6/16 2:48
 */
public class ACalendar {
    public static void main(String[] args) {
        int[] a = get1(args.length > 0 ? Long.parseLong(args[0]) : System.currentTimeMillis());
        System.out.println(a[YEAR] + "年" + a[MONTH] + "月" + a[DAY] + "日 " + a[HOUR] + "：" + a[MINUTE] + "：" + a[SECOND] + "." + a[MILLISECOND] + " 星期" + a[DAY_OF_WEEK] + " 闰年" + a[REN_YEAR]);
    }

    public static final int
            YEAR        = 0,
            MONTH       = 1,
            DAY         = 2,
            HOUR        = 3,
            MINUTE      = 4,
            SECOND      = 5,
            MILLISECOND = 6,
            DAY_OF_WEEK = 7,
            REN_YEAR    = 8,

            TOTAL       = 9;

    private final int[] buf = new int[TOTAL];
    private final long[] cache = new long[4];
    private final TimeZone timezone;

    public ACalendar() {
        this(TimeZone.getDefault());
    }

    public ACalendar(TimeZone timezone) {
        this.timezone = timezone;
    }

    public int[] get() {
        return get(System.currentTimeMillis());
    }

    public int[] get(long unix) {
        return get(unix + timezone.getOffset(unix), buf, cache);
    }

    public static int[] get1() {
        return get(System.currentTimeMillis(), new int[TOTAL], null);
    }

    public static int[] get1(long unix) {
        return get(unix, new int[TOTAL], null);
    }

    public static int[] get(long date, int[] buf, long[] cache) {
        if(buf.length < 7)
            throw new ArrayIndexOutOfBoundsException(6);

        // TIMEZONE OFFSET
        // date += 8 * 3600 * 1000;

        date = His(date, buf);

        date += GREGORIAN_OFFSET_DAY;

        if(date < MINIMUM_GREGORIAN_DAY)
            throw new ArithmeticException("ACalendar does not support time < 1582/8/15");

        int y = buf[YEAR] = yearSinceUnix(date);
        long days = daySinceAD(y, 1, 1, null);
        boolean renYear = isRenYear(y);

        int daysSinceY = (int)(date - days);

        long daysAfterFeb = days + 31 + 28 + (renYear ? 1 : 0);
        if (date >= daysAfterFeb) {
            daysSinceY += renYear ? 1 : 2;
        }

        int m = 12 * daysSinceY + 373;
        if (m > 0) {
            m /= 367;
        } else {
            m = floorDiv(m, 367);
        }

        long monthDays = days + SUMMED_DAYS[m] + (m >= 3 && renYear ? 1 : 0);
        buf[DAY] = (int)(date - monthDays) + 1;

        if(m == 0) {
            // fail safe
            buf[YEAR]--;
            buf[MONTH] = 12;
            buf[DAY]++;
        } else {
            buf[MONTH] = m;
        }

        buf[DAY_OF_WEEK] = dayOfWeek(date - 1);

        buf[REN_YEAR] = renYear ? 1 : 0;

        return buf;
    }

    public static long His(long date, int[] buf) {
        if(date > 0) {
            buf[MILLISECOND] = (int) (date % 1000);
            date /= 1000;

            buf[SECOND] = (int) (date % 60);
            date /= 60;

            buf[MINUTE] = (int) (date % 60);
            date /= 60;

            buf[HOUR] = (int) (date % 24);
            date /= 24;
        } else {
            date = divModLss(date, 1000, buf);
            buf[MILLISECOND] = buf[0];

            date = divModLss(date, 60, buf);
            buf[SECOND] = buf[0];

            date = divModLss(date, 60, buf);
            buf[MINUTE] = buf[0];

            date = divModLss(date, 24, buf);
            buf[HOUR] = buf[0];
        }

        return date;
    }

    public static int yearSinceUnix(long date) {
        int years = 400 * (int)(date / 146097L);
        int datei = (int)(date % 146097L);

        int r100;
        years += 100 * (r100 = datei / 36524);
        datei = datei % 36524;

        years += 4 * (datei / 1461);

        years += (datei % 1461) / 365;

        if (r100 != 4 && years != 4) {
            ++years;
        }

        return years;
    }

    /***
     * 计算公元零年后过去的日子
     * @return 天数
     */
    public static long daySinceAD(int year, int month, int day, @Nullable long[] cache) {
        boolean firstDay = month == 1 && day == 1;
        if (cache != null && cache[2] == year) {
            return cache[3] + (firstDay ? 0 : dayOfYear(year, month, day) - 1);
        } else {
            int i = year - 1970;
            if (i >= 0 && i < CACHED_YEARS.length) {
                long yearTS = CACHED_YEARS[i];
                if (cache != null) {
                    cache[2] = year;
                    cache[3] = yearTS;
                }

                return firstDay ? yearTS : yearTS + dayOfYear(year, month, day) - 1L;
            }

            long longYr = (long)year - 1L;
            long date = day + longYr * 365 + ((367 * month - 362) / 12) // raw year + month + day
                        + longYr / 4L - longYr / 100L + longYr / 400L; // ren days

            if (month > 2) { // feb offset
                date -= isRenYear(year) ? 1L : 2L;
            }

            if (cache != null && firstDay) {
                cache[2] = year;
                cache[3] = date;
            }

            return date;
        }
    }

    public static boolean isRenYear(int year) {
        return (year & 3) == 0 && (year % 100 != 0 || year % 400 == 0);
    }

    public static long dayOfYear(int year, int month, int day) {
        return (long)day + (long)(SUMMED_DAYS[month] + (month > 2 && isRenYear(year) ? 1 : 0));
    }

    public static int dayOfWeek(long day) {
        return (int)(day % 7L) + 1;
    }

    /**
     * 1970 to 2030
     */
    static final int[] CACHED_YEARS = new int[]{719163, 719528, 719893, 720259, 720624, 720989, 721354,
                                                 721720, 722085, 722450, 722815, 723181, 723546, 723911,
                                                 724276, 724642, 725007, 725372, 725737, 726103, 726468,
                                                 726833, 727198, 727564, 727929, 728294, 728659, 729025,
                                                 729390, 729755, 730120, 730486, 730851, 731216, 731581,
                                                 731947, 732312, 732677, 733042, 733408, 733773, 734138,
                                                 734503, 734869, 735234, 735599, 735964, 736330, 736695,
                                                 737060, 737425, 737791, 738156, 738521, 738886, 739252,
                                                 739617, 739982, 740347, 740713, 741078, 741443, 741808,
                                                 742174, 742539, 742904, 743269, 743635, 744000, 744365};
    /**
     * 每月的天数
     */
    static final int[] SUMMED_DAYS  = new int[]{-30, 0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};

    public static final int GREGORIAN_OFFSET_DAY  = 719163; // Fixed day of 1970/1/ 1 (Gregorian)
    public static final int MINIMUM_GREGORIAN_DAY = 577736; // Fixed day of 1582/8/15

    public static int floorDiv(int a, int b) {
        return a >= 0 ? a / b : (a + 1) / b - 1;
    }

    public static long divMod(int a, int b) {
        if(a >= 0) {
            return (long) (a % b) << 32 | (long) (a / b);
        } else {
            int div = (a + 1) / b - 1;
            int mod = a - b * div;
            return ((((long) mod) << 32) & 0xFFFFFFFF00000000L) | (((long) div) & 0xFFFFFFFFL);
        }
    }

    public static long divModLss(long a, int b, int[] buf) {
        if(a >= 0) {
            return (a % b) << 54 | (a / b);
        } else {
            long div = (a + 1) / b - 1;
            buf[0] = (int) (a - b * div);
            return div;
        }
    }

    public static String pad(int number, int min) {
        int len = MathUtils.digitCount(number);
        min -= len;
        if(min > 0) {
            StringBuilder sb = new StringBuilder(min + len);
            for (int i = 0; i < min; i++) {
                sb.append('0');
            }
            return sb.append(number).toString();
        } else {
            return Integer.toString(number);
        }
    }

    public static void pad(StringBuilder sb, int number, int min) {
        for (int i = min - MathUtils.digitCount(number) - 1; i >= 0; i--) {
            sb.append('0');
        }
        sb.append(number);
    }

    private static final LongBitSet PATTERN = LongBitSet.from("LYydjlwNmntaAgGhHisOPcU");

    public String formatDate(String format, long stamp) {
        int[] date = get(stamp);
        StringBuilder sb = new StringBuilder(format.length());
        char c;
        for (int i = 0; i < format.length(); i++) {
            switch (c = format.charAt(i)) {
                case 'L':
                    sb.append(date[REN_YEAR]);
                    break;
                case 'Y':
                    sb.append(date[YEAR]);
                    break;
                case 'y':
                    sb.append(date[YEAR]).delete(sb.length() - 4, sb.length() - 2);
                    break;
                case 'd':
                    pad(sb, date[DAY], 2);
                    break;
                case 'j':
                    sb.append(date[DAY]);
                    break;
                case 'l':
                    sb.append("星期").append(MathUtils.CHINA_NUMERIC[date[DAY_OF_WEEK]]);
                    break;
                case 'w':
                    sb.append(date[DAY_OF_WEEK]);
                    break;
                case 'N':
                    sb.append(date[DAY_OF_WEEK] + 1);
                    break;
                case 'm':
                    pad(sb, date[MONTH] + 1, 2);
                    break;
                case 'n':
                    sb.append(date[MONTH]);
                    break;
                case 't': // 本月有几天
                    int mth = date[MONTH];
                    if (mth++ == 1) {
                        sb.append(28 + date[REN_YEAR]);
                    } else {
                        sb.append(((mth & 1) != 0) == mth < 8 ? 31 : 30);
                    }
                    break;
                case 'a':
                    sb.append(date[HOUR] > 11 ? "pm" : "am");
                    break;
                case 'A':
                    sb.append(date[HOUR] > 11 ? "PM" : "AM");
                    break;
                case 'g': // am/pm时间
                    int h = date[HOUR] % 12;
                    sb.append(h == 0 ? 12 : h);
                    break;
                case 'G':
                    sb.append(date[HOUR]);
                    break;
                case 'h':
                    h = date[HOUR] % 12;
                    pad(sb, h == 0 ? 12 : h, 2);
                    break;
                case 'H':
                    pad(sb, date[HOUR], 2);
                    break;
                case 'i':
                    pad(sb, date[MINUTE], 2);
                    break;
                case 's':
                    pad(sb, date[SECOND], 2);
                    break;
                case 'O': // timezone offset 2
                    tzoff(stamp, sb);
                    break;
                case 'P':
                    sb.insert(tzoff(stamp, sb) + 2, ':');
                    break;
                case 'c':
                    sb.append(date[YEAR]).append('-')
                      .append(date[MONTH] + 1).append('-')
                      .append(date[DAY]).append('T')
                      .append(date[HOUR]).append(':')
                      .append(date[MINUTE]).append(':')
                      .append(date[SECOND])
                      .insert(tzoff(stamp, sb) + 2, ':');
                    break;
                case 'U':
                    sb.append(stamp / 1000);
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }
        return sb.toString();
    }

    private int tzoff(long stamp, StringBuilder sb) {
        int pos = sb.length();
        sb.append('+');
        int h;
        pad(sb, h = timezone.getOffset(stamp) / 60 * 1000, 4);
        if (h > 0) {
            sb.setCharAt(pos, '-');
        }
        return pos;
    }

    private static final int[]    TIME_DT     = {60, 1800, 3600, 86400, 604800, 2592000, 15552000};
    private static final int[]    TIME_FACTOR = {60, 1, 60, 24, 7, -2592000, 6};
    private static final String[] TIME_NAME   = {" 秒前", " 分前", "半小时前", " 小时前", " 天前", " 周前", " 月前"};

    public String prettyTime(long unix) {
        long diff = System.currentTimeMillis() - unix;
        if (diff == 0)
            return "现在";
        if (diff < 0)
            return Long.toString(diff);
        double val = diff;
        boolean flag = false;
        for (int i = 0; i < TIME_DT.length; i++) {
            int time = TIME_DT[i];
            if (diff < time) {
                return flag ? TIME_NAME[i] : new StringBuilder().append(Math.round(val)).append(TIME_NAME[i]).toString();
            }
            int dt = TIME_FACTOR[i];
            flag = dt == 1;
            if(dt < 0) {
                val = (double) time / (-dt);
            } else {
                val /= dt;
            }

        }
        return formatDate("Y-m-d H:i:s", unix);
    }
}
