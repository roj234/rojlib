# (大概)高性能的字符串全文匹配方案
    至少比mysql like快...
    根据数据的不同，快0.7-200倍
    而且不像FULLTEXT它并不会丢数据
    如果分词性能大概更好？
* roj.collect.MatchMapString&lt;V>
* roj.collect.MatchMap&lt;K,V>

```java
import roj.collect.MatchMapString;

class Example {
	public static void main(String[] args) {
		MatchMapString<Integer> test = new MatchMapString<>();
		test.put("myTest", 123);

		// 顺序无关
		test.matchUnordered("tseymT", 0); // 返回: myTest
		// 顺序有关
		test.matchOrdered("myet", 0);
	}
}
```
## 0是干嘛的
 * MATCH_SHORTER 匹配更短的, 也就是 成功匹配过* 但是在key更靠后的位置匹配失败的Entry
 * MATCH_LONGER 匹配更长的, 也就是成功匹配，但是还未完整匹配的Entry
 * MATCH_CONSISTENT key不能拆分(比如上面可以拆成my.e.t所以能成功匹配), 仅能在matchOrdered中使用

成功匹配的定义:
 * matchUnordered中如你所想
 * matchOrdered是按顺序的key中有某个字符匹配了Entry的key的最后一项  
如果是aaa这种的那么优先匹配靠前的: aa -> [0,1] | aaa -> [0,1,2]