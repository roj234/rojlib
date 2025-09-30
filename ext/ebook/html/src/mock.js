
/**
 * 数据准备函数，用于模拟生成狐狸和狗相关的差异数据。
 * @param {number} dataCount - 生成数据的重复次数。
 * @returns {{original: string, modified: string, diffList: {type: string, originalRange: number[], modifiedRange: number[]}[]}}
 *          包含原始字符串、修改后的字符串以及差异列表的对象。
 */
function mockFoxDog(dataCount) {
    /** @type {string} */
    let original = "";
    /** @type {string} */
    let modified = "";
    /** @type {{type: string, originalRange: number[], modifiedRange: number[]}[]} */
    let diffList = [];

	let baseL = 0, baseR = 0;
	for (let i = 0; i < dataCount; i++) {
		var left = "快速棕毛狐狸了懒狗\n";
		var right = "敏捷的棕毛狐狸跃过了懒\n";
		original += left;
		modified += right;
		diffList.push(
			{ type: 'replace', originalRange: [baseL + 0, baseL + 2], modifiedRange: [baseR + 0, baseR + 3] },
			{ type: 'equal', originalRange: [baseL + 2, baseL + 6], modifiedRange: [baseR + 3, baseR + 7] },
			{ type: 'insert', originalRange: [baseL + 6, baseL + 6], modifiedRange: [baseR + 7, baseR + 9] },
			{ type: 'delete', originalRange: [baseL + 8, baseL + 9], modifiedRange: [baseR + 11, baseR + 11] },
			{ type: 'equal', originalRange: [baseL + 9, baseL + 10], modifiedRange: [baseR + 11, baseR + 12] }
		);
		baseL += left.length;
		baseR += right.length;

		var same = "很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa很长的字符串--aaa\nfsdfsdfg\nggggggadasdas";
		original += same;
		modified += same;
		diffList.push({
						type: 'equal', originalRange: [baseL, baseL + same.length], modifiedRange: [baseR, baseR + same.length]
				});
		baseL += same.length;
		baseR += same.length;


		var left = "多行差异\n渲染测试\nASDF\n";
		var right = "多行差异\n渲染测试\nASDF\n";
		original += left;
		modified += right;
		diffList.push(
			{ type: 'equal', originalRange: [baseL, baseL + 1], modifiedRange: [baseR, baseR + 1] },
			{ type: 'replace', originalRange: [baseL + 1, baseL + 12], modifiedRange: [baseR + 1, baseR + 12] },
			{ type: 'equal', originalRange: [baseL + 12, baseL + 15], modifiedRange: [baseR + 12, baseR + 15] }
		);
		baseL += left.length;
		baseR += right.length;
	}

	return {original, modified, diffList};
}

export {mockFoxDog};