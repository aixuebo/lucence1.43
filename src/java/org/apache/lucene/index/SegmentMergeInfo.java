package org.apache.lucene.index;

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

final class SegmentMergeInfo {
  Term term;//最上面的term
  int base;//该segment的docId的第一个序号,保证所有segment的序号都不重复,并且连贯
  TermEnum termEnum;//该segment的term迭代器
  IndexReader reader;//segment的索引reader对象
  TermPositions postings;//SegmentTermPositions对象
  int[] docMap = null;				  // maps around deleted docs  每一个doc文档的唯一序号,真实的序号是base+该文档数组对应的数字

  /**
  * 参数b是docid的基础序号
  * 参数 te是segment上的term迭代器集合
  * r 表示索引的reader对象
  **/
  SegmentMergeInfo(int b, TermEnum te, IndexReader r)
    throws IOException {
    base = b;
    reader = r;
    termEnum = te;
    term = te.term();//最上面的第一个词
    postings = reader.termPositions();

    // build array which maps document numbers around deletions 
    if (reader.hasDeletions()) {//如果有删除的docid
      int maxDoc = reader.maxDoc();
      docMap = new int[maxDoc];
      int j = 0;//新的id序号   真实的id是base+j
      for (int i = 0; i < maxDoc; i++) {
        if (reader.isDeleted(i))
          docMap[i] = -1;//因为该文档被删除了,因此使用-1代替他的序号，即他不需要序号了
        else
          docMap[i] = j++; //让他的id连贯起来
      }
    }
  }

  //不断的获取下一个term
  final boolean next() throws IOException {
    if (termEnum.next()) {
      term = termEnum.term();
      return true;
    } else {
      term = null;
      return false;
    }
  }

  final void close() throws IOException {
    termEnum.close();
    postings.close();
  }
}

