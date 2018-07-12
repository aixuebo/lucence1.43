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

import org.apache.lucene.store.Directory;

/** This stores a monotonically increasing set of <Term, TermInfo> pairs in a
 * Directory.  Pairs are accessed either by Term or by ordinal position the
 * set.  */

final class TermInfosReader {
  private Directory directory;
  private String segment;
  private FieldInfos fieldInfos;

  private ThreadLocal enumerators = new ThreadLocal();
  private SegmentTermEnum origEnum;//原始tis的term内容的迭代器
  private long size;//tis文件中总term数量

  TermInfosReader(Directory dir, String seg, FieldInfos fis)
       throws IOException {
    directory = dir;
    segment = seg;
    fieldInfos = fis;

    //term在tis或者tii中的term迭代器
    origEnum = new SegmentTermEnum(directory.openFile(segment + ".tis"),
                                   fieldInfos, false);
    size = origEnum.size;
    readIndex();
  }

  public int getSkipInterval() {
    return origEnum.skipInterval;
  }

  final void close() throws IOException {
    if (origEnum != null)
      origEnum.close();
  }

  /** Returns the number of term/value pairs in the set. */
  final long size() {
    return size;
  }

  private SegmentTermEnum getEnum() {
    SegmentTermEnum termEnum = (SegmentTermEnum)enumerators.get();
    if (termEnum == null) {
      termEnum = terms();
      enumerators.set(termEnum);
    }
    return termEnum;
  }

  Term[] indexTerms = null;//索引中所有的term
  TermInfo[] indexInfos;//每一个索引中term的info对象
  long[] indexPointers;//每一个索引对一个tis的文件位置

  //初始化索引tii文件内容
  private final void readIndex() throws IOException {
    SegmentTermEnum indexEnum =
      new SegmentTermEnum(directory.openFile(segment + ".tii"),
			  fieldInfos, true);
    try {
      int indexSize = (int)indexEnum.size;

      indexTerms = new Term[indexSize];
      indexInfos = new TermInfo[indexSize];
      indexPointers = new long[indexSize];

      for (int i = 0; indexEnum.next(); i++) {
	indexTerms[i] = indexEnum.term();
	indexInfos[i] = indexEnum.termInfo();
	indexPointers[i] = indexEnum.indexPointer;
      }
    } finally {
      indexEnum.close();
    }
  }

  /** Returns the offset of the greatest index entry which is less than or equal to term.
   找到距离该term最近的索引term
   */
  private final int getIndexOffset(Term term) throws IOException {
    int lo = 0;					  // binary search indexTerms[]
    int hi = indexTerms.length - 1;

    while (hi >= lo) {
      int mid = (lo + hi) >> 1;
      int delta = term.compareTo(indexTerms[mid]);
      if (delta < 0)
	hi = mid - 1;
      else if (delta > 0)
	lo = mid + 1;
      else
	return mid;
    }
    return hi;
  }

  //通过索引文件定位原始文件
  private final void seekEnum(int indexOffset) throws IOException {
    getEnum().seek(indexPointers[indexOffset],//原始文件位置
	      (indexOffset * getEnum().indexInterval) - 1,//该索引是原始文件中第几个term
	      indexTerms[indexOffset], indexInfos[indexOffset]);//原始文件的term和terminfo内容
  }

  /** Returns the TermInfo for a Term in the set, or null. */
  TermInfo get(Term term) throws IOException {
    if (size == 0) return null;

    // optimize sequential access: first try scanning cached enum w/o seeking
    //一种优化
    SegmentTermEnum enumerator = getEnum();
    if (enumerator.term() != null                 // term is at or past current
	&& ((enumerator.prev != null && term.compareTo(enumerator.prev) > 0) //说明该term比前一个term要大
	    || term.compareTo(enumerator.term()) >= 0)) { //该term比后一个term要大
      int enumOffset = (int)(enumerator.position/enumerator.indexInterval)+1;
      if (indexTerms.length == enumOffset	  // but before end of block
	  || term.compareTo(indexTerms[enumOffset]) < 0)
	return scanEnum(term);			  // no need to seek
    }

    // random-access: must seek
    seekEnum(getIndexOffset(term));//通过索引文件找到该term最近的原始文件
    return scanEnum(term);//然后在顺序扫描
  }

  /** Scans within block for matching term. 
   查询tis文件,没有走索引,所以很慢
   找到一个term对应的info内容
  */
  private final TermInfo scanEnum(Term term) throws IOException {
    SegmentTermEnum enumerator = getEnum();
    while (term.compareTo(enumerator.term()) > 0 && enumerator.next()) {} //只要term参数比字典中term大,都一个个pass过掉
    if (enumerator.term() != null && term.compareTo(enumerator.term()) == 0) //说明找到该term了
      return enumerator.termInfo();//返回该term的info对象
    else
      return null;
  }

  /** Returns the nth term in the set. */
  final Term get(int position) throws IOException {
    if (size == 0) return null;

    SegmentTermEnum enumerator = getEnum();
    if (enumerator != null && enumerator.term() != null &&
        position >= enumerator.position &&
	position < (enumerator.position + enumerator.indexInterval)) //说明在一个小范围内,可以一个个迭代查找
      return scanEnum(position);		  // can avoid seek

    seekEnum(position / enumerator.indexInterval); // must seek
    return scanEnum(position);
  }

  //从指定为止开始一个个查找term
  private final Term scanEnum(int position) throws IOException {
    SegmentTermEnum enumerator = getEnum();
    while(enumerator.position < position)//不断的一个个查找term,指导找到为止
      if (!enumerator.next())
	return null;

    return enumerator.term();
  }

  /** Returns the position of a Term in the set or -1. 
   获取该term的内部排序序号
   */
  final long getPosition(Term term) throws IOException {
    if (size == 0) return -1;

    int indexOffset = getIndexOffset(term);//获取该term最近的在索引文件中的序号
    seekEnum(indexOffset);//通过索引文件定位原始文件

    SegmentTermEnum enumerator = getEnum();
    while(term.compareTo(enumerator.term()) > 0 && enumerator.next()) {} //范围已经缩小了,因此可以直接一个个查找

    if (term.compareTo(enumerator.term()) == 0)
      return enumerator.position;
    else
      return -1;
  }

  /** Returns an enumeration of all the Terms and TermInfos in the set. */
  public SegmentTermEnum terms() {
    return (SegmentTermEnum)origEnum.clone();
  }

  /** Returns an enumeration of terms starting at or after the named term. */
  public SegmentTermEnum terms(Term term) throws IOException {
    get(term);
    return (SegmentTermEnum)getEnum().clone();
  }
}
