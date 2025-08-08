package roj.compiler.jpp.target;

import roj.asm.cp.Constant;

/**
 * @author Roj234
 * @since 2025/3/31 6:20
 */
public interface IRAssembler {
	void movConst2Reg(Constant value, int regTo);

	// even type auto convert?
	void movReg2Reg(int regFrom, int regTo);
	/**
	 * memory[address + offset] = regFrom
	 */
	void movReg2Mem(int regFrom, IRLabel address, int offset);
	/**
	 * memory[memory[address + offset]] = regFrom
	 */
	void movReg2MemPtr(int regFrom, IRLabel pointer, int offset);
	/**
	 * memory[regTo] = regFrom
	 */
	void movReg2RegPtr(int regFrom, int regTo);
	/**
	 * regTo = memory[address + offset]
	 */
	void movMem2Reg(IRLabel address, int offset, int regTo);
	/**
	 * regTo = memory[memory[address + offset]]
	 */
	void movMemPtr2Reg(IRLabel pointer, int offset, int regTo);
	/**
	 * regTo = memory[regFrom]
	 */
	void movRegPtr2Reg(int regFrom, int regTo);

	// not
	void math1(int src, int dst);
	// add sub and or xor
	void math2(int s1, int s2, int dst);
	void mul(int s1, int s2, int dstL, int dstH);
	void div(int s1, int s2, int dstQ, int dstR);

	void compare(int s1, int s2, int cmpType);
	void jump(int flag, IRLabel address, int offset);

	void call(IRLabel address, int offset);
	void ret();

	void push(int reg);
	void pop(int reg);
}
