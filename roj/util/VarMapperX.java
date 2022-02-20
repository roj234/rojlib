package roj.util;

import roj.collect.IntIterator;
import roj.collect.LongBitSet;
import roj.collect.Unioner;
import roj.collect.Unioner.Range;
import roj.collect.Unioner.Region;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 根据变量作用域分配可重用的ID ...... <BR>
 *     同时根据set/get拆分细化变量作用域 <BR>
 *     进而进一步降低所需的ID数目 <BR>
 * @author Roj233
 * @since 2022/2/23 15:27
 */
public class VarMapperX extends VarMapper {
    private final Unioner<Jump> jump = new Unioner<>(10, true, 50);

//    public static void main(String[] args) {
//        VarMapperX<VarX> map = new VarMapperX<>();
//
//        VarX a = new VarX();
//        a.start = -1;
//        map.add(a);
//        map.set(a,0);
//        map.get(a,1);
//        map.set(a,2);
//        map.set(a,3);
//        map.jmp(4, 1);
//        map.set(a, 5);
//        map.set(a, 6);
//        map.get(a, 7);
//
//        map.set(a, 11);
//        map.set(a, 66);
//        map.get(a, 99);
//        int max = map.map(Collections.emptyList());
//        System.out.println(a);
//    }

    public void set(VarX v, int pos) {
        if (v.start < 0) v.start = pos;
        v.end = pos;

        v.S.add(pos);
    }

    public void get(VarX v, int pos) {
        if (v.start < 0) v.start = pos;
        v.end = pos;

        LongBitSet S = v.S;
        if (v.G.length <= S.size()) {
            int[] G = Arrays.copyOf(v.G, S.size()+1);
            for (int i = v.G.length; i <= S.size(); i++) {
                G[i] = -1;
            }
            v.G = G;
        }
        v.G[S.size()] = pos;
    }

    public void getset(VarX v, int pos) {
        if (v.start < 0) v.start = pos;
        v.end = pos;

        LongBitSet S = v.S;
        if (v.G.length <= S.size()) {
            int[] G = Arrays.copyOf(v.G, S.size()+1);
            for (int i = v.G.length; i <= S.size(); i++) {
                G[i] = -1;
            }
            v.G = G;
        }
        v.G[S.size()] = pos;
        v.S.add(pos);
    }

    public void jmp(int from, int to) {
        jump.add(new Jump(from, to));
    }

    @Override
    public void add(IVar v1) {
        tmp.add(v1);
    }

    @Override
    public void clear() {
        tmp.clear();
        union.clear();
        jump.clear();
    }

    public int map(List<? extends IVar> list) {
        Unioner<IVar> union = this.union;
        List<IVar> tmp = this.tmp;
        Unioner<Jump> reverseJump = this.jump;

        if (list != null) {
            tmp.addAll(list);
        }

        // A. 当一变量assign后,此语句下方没有语句能返回上方,则可将其视为新变量
        // 并尝试给一个较小的ID
        for (int i = 0; i < tmp.size(); i++) {
            VarX x = (VarX) tmp.get(i);
            // 只assign了一次或零次(undefined)
            if (x.S.size() < 2) {
                // get过
                if (x.start < x.end) {
                    union.add(x);
                    x.subVars.add(x);
                }
                continue;
            }

            // 拆分
            int lastPos = x.start;
            // 起始Region id
            int rStr = Math.max(0, reverseJump.search(lastPos));
            // set计数
            int k = 0;
            // 上一个变量
            Var v = null;

            Region[] array = reverseJump.dataArray();
            IntIterator itr = x.S.iterator();
            if (x.start != itr.nextInt()) System.err.println("illegal S setting");

            boolean next = true;
            while (next) {
                k++;
                int pos;
                if (itr.hasNext()) {
                    pos = itr.nextInt();
                } else {
                    pos = x.end;
                    next = false;
                }

                // +1: assign"后"
                int rEnd = reverseJump.search(pos +1);
                if(rEnd < 0) continue;

                // B. 若一变量在两次assign间没有get, 当然,它们之间也没有跳转
                // 则前一次assign可以放弃 (当然要保留side-effect)
                checkAssign:
                if (k < x.G.length && x.G[k] == -1) {
                    for (; rStr < rEnd; rStr++) {
                        if (!array[rStr].value().isEmpty()) {
                            break checkAssign;
                        }
                    }

                    if (v != null) {
                        rStr = rEnd;
                        lastPos = pos;
                        continue;
                    } else {
                        System.out.println("v=null && allEmpty");
                    }
                }

                rStr = rEnd;
                if (!array[rEnd].value().isEmpty()) {
                    if (next) continue;
                    else {
                        // 依然有反向跳转且 [待续]
                        System.out.println("? backward jmp");
                    }
                }

                // 如果都能返回上方
                if (lastPos == x.start && pos == x.end) {
                    union.add(x);
                    x.subVars.add(x);
                } else {
                    // 或者这就是新变量
                    v = new Var();
                    v.start = lastPos;
                    v.end = lastPos = x.G[k] < 0 ? pos : x.G[k];

                    union.add(v);
                    x.subVars.add(v);
                }
            }
        }
        tmp.clear();

        return super.map(Collections.emptyList());
    }

    public static class VarX extends Var {
        final LongBitSet S = new LongBitSet();
        int[] G = new int[] {-1,-1};

        public final List<Var> subVars = new ArrayList<>();

        public void reset() {
            for (int i = 0; i < S.size(); i++) {
                G[i] = -1;
            }
            S.clear();
            subVars.clear();
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder()
                .append(super.toString())
                .append(", subVars=[");
            for (int i = 0; i < subVars.size(); i++) {
                sb.append(subVars.get(i) == this ? "<recursion>" : subVars.get(i))
                  .append(',');
            }
            return sb.append("]}").toString();
        }
    }

    static final class Jump implements Range {
        int from, to;

        public Jump(int from, int to) {
            if (from > to) {
                this.from = to;
                this.to   = from;
            } else {
                this.from = from;
                this.to   = to;
            }
        }

        @Override
        public long startPos() {
            return from;
        }

        @Override
        public long endPos() {
            return to;
        }
    }
}
