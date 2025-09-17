package roj.config.v2;

import roj.collect.*;
import roj.io.IOUtil;
import roj.text.CharList;

import java.util.Arrays;
import java.util.List;
import java.util.function.ToIntFunction;

import static roj.reflect.Unsafe.U;

/**
 * 有限自动机
 * @author Roj234
 * @since 2025/05/20 01:59
 * @see Tokenizer2
 */
public final class DFA {
	/**
	 * 状态偏移量（索引=状态ID，值=在transitions中的结束位置）
	 * <p>状态N的转移区间：[stateBase[N-1] || 0, stateBase[N]) </p>
	 */
	final int[] stateBase;
	/**
	 * 压缩的状态转移数据（按状态分组排序）
	 * <ul>
	 *   <li>高16位：转移字符（char）</li>
	 *   <li>低16位：目标状态（short，有符号）</li>
	 * </ul>
	 * @see Builder#state(int, int) 条目格式
	 */
	final int[] transitions;
	Int2IntMap lookup;

	BitSet literalEnd;
	IntMap<String> stateRepr;

	private DFA(int[] stateBase, int[] stateMap, BitSet lends, IntMap<String> terminalRepr) {
		this.stateBase = stateBase;
		this.transitions = stateMap;
		this.literalEnd = lends;
		this.stateRepr = terminalRepr;

	}

	/**
	 * 获取当前状态下的转移目标状态
	 *
	 * @param state 当前状态ID（必须≥0）
	 * @param input 输入字符（'\0'表示EOF）
	 * @return 转移结果：
	 *         <li>>0：有效转移（下一状态ID）</li>
	 *         <li><0：终止状态（绝对值表示token类型）</li>
	 *         <li>=0：无匹配转移</li>
	 * @throws ArrayIndexOutOfBoundsException 如果state超出范围
	 */
	@Deprecated
	public int update(int state, char input) {
		int start = state == 0 ? 0 : stateBase[state-1];
		int end = stateBase[state];

		// 二分查找
		int low = start;
		int high = end - 1;

		while (low <= high) {
			int mid = (low + high) >>> 1;
			int entry = transitions[mid];

			int c = entry >>> 16;
			if (c == input) {
				return (short) entry;
			} else if (c < input) {
				low = mid + 1;
			} else {
				high = mid - 1;
			}
		}

		return (transitions[start] & 0xFFFF0000) == 0/* '\0' EOF */ ? (short)transitions[start] : -1;
	}

	@Override
	public String toString() {
		return "DFA{" +
				"transitions=" + Arrays.toString(transitions) +
				", stateBase=" + Arrays.toString(stateBase) +
				'}';
	}

	/**
	 * DFA构建器（支持链式调用）
	 *
	 * <h3>使用示例：</h3>
	 * <pre>{@code
	 * DFA dfa = DFA.builder()
	 *     .addTransition(0, "abc", 1)
	 *     .addTerminal(1, TOKEN_ID)
	 *     .build();
	 * }</pre>
	 */
	public static Builder builder() {return new Builder();}

	public String data(int endState) {return stateRepr.get(endState);}

	public static final class Builder {
		private int nextStateId;
		private final List<Int2IntMap> transitions = new ArrayList<>();
		private final Int2IntMap terminal = new Int2IntMap();
		private final IntMap<String> terminalRepr = new IntMap<>();
		private final BitSet lends = new BitSet();

		public Builder() {transitions.add(new Int2IntMap());}

		private Int2IntMap getStateMap(int state) {
			while (transitions.size() <= state) {
				transitions.add(new Int2IntMap());
			}
			return transitions.get(state);
		}

		public Builder add(int startTokenId, String... tokens) {
			for (int i = 0; i < tokens.length; i++) {
				int endState = addTransitions(0, tokens[i]);
				addTerminal(endState, -(startTokenId + i) - 1);
				this.terminalRepr.put(startTokenId+i, tokens[i]);
			}
			return this;
		}

		public Builder addSymbol(int startTokenId, String... tokens) {
			for (int i = 0; i < tokens.length; i++) {
				String str = tokens[i];
				lends.add(str.charAt(0));
				int endState = addTransitions(0, str);
				addTerminal(endState, -(startTokenId+i) - 1);
				this.terminalRepr.put(startTokenId+i, tokens[i]);
			}
			return this;
		}

		public Builder addNumber(boolean withSign) {
			int state = addBatchTransition(0, "0123456789");
			addTerminal(state, Tokenizer2.ST_NUMBER);

			if (withSign) {
				state = addBatchTransition(0, "+-");
				state = addBatchTransition(state, "0123456789");
				addTerminal(state, Tokenizer2.ST_SIGNED_NUMBER);
			}
			return this;
		}

		public Builder addString() {
			addSpecial(Tokenizer2.ST_STRING, "\"", "\"");
			return this;
		}

		public Builder addWhitespace() {
			String whitespace = "\r\n\t\f ";
			lends.addAll(whitespace);
			return addBatchTransition(0, whitespace, 0);
		}

		public Builder addSpecial(int id, String token, Object parameter) {
			int endState = addTransitions(0, token);
			this.terminalRepr.put(endState, parameter.toString());
			addTerminal(endState, id);
			return this;
		}

		/**
		 * 添加单字符转移
		 *
		 * @param fromState 起始状态
		 * @param c         转移字符
		 * @return 自动生成的目标状态ID（新建或复用）
		 */
		public int addTransition(int fromState, char c) {
			Int2IntMap stateMap = getStateMap(fromState);
			int nextState = stateMap.getOrDefaultInt(c, -1);
			if (nextState < 0) {
				nextState = ++nextStateId;
				stateMap.put(c, nextState);
			}
			return nextState;
		}
		/**
		 * 添加自定义目标状态的字符转移
		 *
		 * @param fromState 起始状态
		 * @param c         转移字符
		 * @param toState   指定目标状态
		 * @throws IllegalStateException 字符转移已存在
		 */
		public Builder addTransition(int fromState, char c, int toState) {
			Int2IntMap stateMap = getStateMap(fromState);
			if (stateMap.putIntIfAbsent(c, toState) != toState) {
				throw new IllegalStateException("字符范围冲突: "+c);
			}
			return this;
		}

		/**
		 * 添加自定义目标状态的字符串转移（每个字符生成连续状态）
		 *
		 * @param fromState 起始状态
		 * @param str       转移字符串
		 * @throws IllegalStateException 字符转移已存在
		 */
		public int addTransitions(int fromState, CharSequence str) {
			int state = fromState;

			for (int i = 0; i < str.length(); i++) {
				char c = str.charAt(i);
				state = addTransition(state, c);
			}

			return state;
		}

		/**
		 * 添加多字符转移（每个字符使用相同状态）
		 *
		 * @param fromState 起始状态
		 * @param str       转移字符串
		 * @return 最终目标状态ID
		 */
		public int addBatchTransition(int fromState, CharSequence str) {
			int nextState = ++nextStateId;
			addBatchTransition(fromState, str, nextState);
			return nextState;
		}
		/**
		 * 添加自定义目标状态的多字符转移（每个字符使用相同状态）
		 *
		 * @param fromState 起始状态
		 * @param str       转移字符串
		 * @param toState   指定目标状态
		 * @throws IllegalStateException 字符转移已存在
		 */
		public Builder addBatchTransition(int fromState, CharSequence str, int toState) {
			Int2IntMap stateMap = getStateMap(fromState);

			for (int i = 0; i < str.length(); i++) {
				char c = str.charAt(i);
				if (stateMap.putIntIfAbsent(c, toState) != toState) {
					throw new IllegalStateException("字符范围冲突: "+c);
				}
			}

			return this;
		}

		/**
		 * 添加终止状态标记
		 *
		 * @param state 目标状态
		 * @param value token类型值（必须<-1）
		 * @return 当前构建器（链式调用）
		 * @throws IllegalArgumentException 值>=0
		 */
		public Builder addTerminal(int state, int value) {
			if (value >= -1) throw new IllegalArgumentException("状态"+state+"的值必须在[-32768,-2]之间 "+value);
			this.terminal.put(state, value);
			return this;
		}

		public void addData(int state, String data) {
			this.terminalRepr.put(state, data);
		}

		@SuppressWarnings("unchecked")
		public <T> Builder addTrieTree(TrieTree<T> tree, ToIntFunction<T> mapToEndState) {
			ArrayList<TrieTree.Entry<T>> entries = new ArrayList<>();
			ArrayList<Integer> states = new ArrayList<>();

			entries.add(tree.getRoot());
			states.add(0);

			CharList sb = IOUtil.getSharedCharBuf();

			// DFS
			while (!entries.isEmpty()) {
				var node = entries.pop();
				int state = states.pop();

				// 处理压缩节点（多字符节点）
				if (node.length() > 1) {
					sb.clear();
					node.append(sb);
					int len = sb.length();

					// 逐个处理压缩节点中的字符
					for (int i = 1; i < len; i++) {
						char c = sb.charAt(i);
						state = addTransition(state, c);
					}
				}

				if (node.isLeaf()) addTerminal(state, mapToEndState.applyAsInt(node.getValue()));

				for (TrieEntry child : node) {
					char c = child.firstChar;
					int nextState = addTransition(state, c);

					entries.add((TrieTree.Entry<T>) child);
					states.add(nextState);
				}
			}
			return this;
		}

		public BitSet getLends() {return lends;}

		static int state(int input, int nextState) {return (input << 16) | (nextState & 0xFFFF);}
		public DFA build() {
			int numStates = nextStateId;

			IntList entries = new IntList();

			int[] stateBase = (int[]) U.allocateUninitializedArray(int.class, numStates + 1);
			IntList stateMap = new IntList();

			int state = 0;
			var lookup = new Int2IntMap(numStates);
			for (; state < transitions.size(); state++) {
				entries.clear();
				int endStateId = terminal.getOrDefaultInt(state, 0);
				if (endStateId != 0) {
					lookup.put(state(state, 0), endStateId);
					entries.add(endStateId & 0xFFFF);
				}

				for (var entry : transitions.get(state).selfEntrySet()) {
					lookup.put(state(state, entry.getIntKey()), entry.getIntValue());

					entries.add(state(entry.getIntKey(), entry.getIntValue()));
				}
				entries.sortUnsigned();

				stateMap.addAll(entries);
				stateBase[state] = stateMap.size();
			}

			for (; state <= numStates; state++) {
				int endStateId = terminal.getOrDefaultInt(state, 0);
				if (endStateId == 0) throw new IllegalArgumentException("最后一些状态必须是终止状态");
				stateMap.add(endStateId & 0xFFFF);
				lookup.put(state(state, 0), endStateId);

				stateBase[state] = stateMap.size();
			}

			DFA dfa = new DFA(stateBase, stateMap.toArray(), lends, terminalRepr);
			dfa.lookup = lookup;
			return dfa;
		}
	}
}