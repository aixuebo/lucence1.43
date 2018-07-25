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

//精准短语匹配
final class ExactPhraseScorer extends PhraseScorer {

  ExactPhraseScorer(Weight weight, TermPositions[] tps, int[] positions, Similarity similarity,
                    byte[] norms) throws IOException {
    super(weight, tps, positions, similarity, norms);
  }

  //查找该doc中包含短语的次数,即短语词频
  //进入该方法的前提是所有的doc都相同,即所有的term都在改doc存在,因此只需要计算term组成短语的词频即可
  protected final float phraseFreq() throws IOException {
    // sort list with pq
    for (PhrasePositions pp = first; pp != null; pp = pp.next) {//从term最小的位置开始查找
      pp.firstPosition();
      pq.put(pp);				  // build pq from list
    }
    pqToList();					  // rebuild list from pq 重新排序

    int freq = 0;
    do {					  // find position w/ all terms
      while (first.position < last.position) {	  // scan forward in first
			do {
			  if (!first.nextPosition())
			    return (float)freq;
			} while (first.position < last.position);//找到position都相同的位置
		firstToLast();//第一个移动到最后一个位置
      }
      freq++;					  // all equal: a match 匹配一个短语,则累加1
    } while (last.nextPosition());//只要有下一个term位置就不断循环
  
    return (float)freq;
  }
}
