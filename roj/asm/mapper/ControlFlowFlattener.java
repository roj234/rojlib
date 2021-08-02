/*
 * This file is a part of MoreItems
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
package roj.asm.mapper;

/**
 * Your description here
 *
 * @author solo6975
 * @version 0.1
 * @since 2021/7/30 22:17
 */
public class ControlFlowFlattener {
    /*
    要想了解什么是控制流平坦化(control flow flatten)，可以找论文"obfuscating c++ programs via control flow flattening"了解。
    基本思想是让所有的基本块都有共同的前驱块，而该前驱块进行基本块的分发，分发用switch语句，依赖于switch变量进行分发。
    先为每个基本块进行值编号，每个基本块就是一个case块，如果基本块有后继块，就替换每个基本块的后继块，新的后继块更新switch变量为后继块的值，然后跳转到switch开始分发处，初始switch变量为入口entry块的值。

    if(a == b) {
       a +=b;
    } else {
      a -=b;
    }

    ===========>

    int k = 0;
    :tmp
    switch(k) {
      case 0:
        if(a == b) {
          k = 1;
          goto :tmp;
        } else {
          k = 2;
          goto :tmp;
        }
      case 1:
        a += b;
      break;
      case 2:
        a -= b;
      break;
    }

   <=============

  二、常量展开
    虽然已经进行了平坦化，但是在更新switch变量时暴露了后继块的case值，因此有必要对这条更新语句进行常量展开。常量展开，可以看成用一个数去生成另一个数的过程。   1、基本运算
     主要是异或、加和减运算：
     //用b生成a
     v=b;
     //v=v^(b^a);
     //v=v-(b-a);
     v=v+(a-b);
   2、预运算
     在进行生成时，先对数值进行一系列的运算：
     v=b;
      //x=b*random1
      v=v*random1;
      //y=x&random2
      v=v&random2;
     //z=y|random3
     v=v|random3;
     //z=((b*random1)&random2)|random3
     //v=v^(a^z);
    //v=v-(z-a);
    v=v+(a-((b*random1)&random2)|random3);
   3、多次迭代
    要想从b生成a,可以先生成中间值c,然后再生成a，即从b→a变成b→c→…→a。可能生成的代码：
    v=b;
    //这里可以先进行预运算
    //v=v^(c^b);
    //v=v-(b-c);
    v=v+(c-b);
    //v=v^(a^c);
    //v=v-(c-a);
    v=v+(a-c);



    ===========>

    int k = 0;
    :tmp
    switch(k) {
      case 0:
        if(a == b) {
          k += 9;
          k ^= 8; // other can also ojbk
          goto :tmp;
        } else {
          k += 6;
          k ^= 4; // == 1+1
          goto :tmp;
        }
      case 1:
        a += b;
      break;
      case 2:
        a -= b;
      break;
    }

   <=============
三、其它
  这里的方法是实验性的。
  1、隐藏case值
   不仅要隐藏后继块的case值，还必须对当前块的case值进行隐藏，即从switch(x)变成switch(f(x))，而f(x)可以是hash、在区间[case最小值，case最大值]有唯一值的数学函数、rsa、离散对数、椭圆曲线离散对数等。

    ====>
    // 哦，这个妙啊
    switch((int) Math.atan(x)) {
       case 0:
       case ...;
    }
    <====
  2、多重分支
    在常量展开b→a时，首先用switch语句生成多个中间值展开成a,然后随机选择一个中间值，用b来展开。
  3、生成函数
    在常量展开时生成的都是一个变量和一个常量进行计算的二元操作符，可以转化为函数。首先记录当前计算前变量的值和计算后的值，生成的函数参数个数不定，每个参数都是当前变量，在函数内部进行常量展开，即用多个当前变量生成计算后的值。

    ====>
    ...
    k = x(k, 3, 5, 0);
    k = y(k, 4, 1, 9);
    ...
    <====



     */
}
