package org.apache.lucene.search;

/**
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import org.apache.lucene.index.*;

//短语中每一个term对应一个该对象
final class PhrasePositions {
  int doc;					  // current doc 此时读取的docid
  int position;					  // position in doc 此时读取的doc下文档的位置
  int count;					  // remaining pos in this doc 此时doc下该term剩余词频数量
  
  int offset;					  // position in phrase 该term在短语中的位置
  TermPositions tp;				  // stream of positions 可以next方法获取该term在下一个doc的词频,以及nextPosition方法返回在同一个doc内的每一个词位置
  
  PhrasePositions next;				  // used to make lists //链表,对应下一个term的对象

  PhrasePositions(TermPositions t, int o) {
    tp = t;
    offset = o;
  }

  final boolean next() throws IOException {	  // increments to next doc
    if (!tp.next()) {//说明该term在所有doc都读取完成了
      tp.close();				  // close stream
      doc = Integer.MAX_VALUE;			  // sentinel value
      return false;
    }
    doc = tp.doc();//读取目前的docid
    position = 0;//此时位置是0
    return true;
  }

  final boolean skipTo(int target) throws IOException {
    if (!tp.skipTo(target)) {//跳跃到指定doc上
      tp.close();				  // close stream
      doc = Integer.MAX_VALUE;			  // sentinel value
      return false;
    }
    doc = tp.doc();
    position = 0;
    return true;
  }


  //切换一个新文档后执行该方法
  final void firstPosition() throws IOException {
    count = tp.freq();				  // read first pos 读取该doc的词频
    nextPosition();//读取该doc下该term的每一个位置
  }

  //读取同一个doc下该term的位置
  final boolean nextPosition() throws IOException {
    if (count-- > 0) {				  // read subsequent pos's
      position = tp.nextPosition() - offset;//让每一个term都转换成短语的term,
      /**
       * 比如hello world sky ---在同一个文档2中存在三个term,每一个term的位置如下:
       * hello  6 10 18
       * world 9 11 19
       * sky 10 12 21
       * 而hello world sky的offset分别为0 1 2
       * 因此转换后
       * hello  6 10 18
       * world 8 10 18
       * sky 8 10 19
       * 因此hello world sky 找到三个单词的位置都相同的即可,即只有10满足条件,即虽然都出现三次,但是短语形式出现的只有一次
       */
      return true;
    } else
      return false;
  }
}
