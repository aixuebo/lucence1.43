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
import org.apache.lucene.util.BitVector;
import org.apache.lucene.store.InputStream;

//描述一个term在所有document维度下的情况
class SegmentTermDocs implements TermDocs {
  protected SegmentReader parent;
  private InputStream freqStream;
  private int count;//目前处理到第几个文档了
  private int df;//该term出现在多少文档中
  private BitVector deletedDocs;//删除的文档集合
  int doc = 0;//此时处理的文档documentid
  int freq;//该term在该document的field出现的词频

  private int skipInterval;
  private int numSkips;//最多有多少层跳跃表
  private int skipCount;
  private InputStream skipStream;
  private int skipDoc;
  private long freqPointer;
  private long proxPointer;
  private long skipPointer;//返回跳跃表开始的位置
  private boolean haveSkipped;

  //传入索引的reader对象,可以读取索引的所有信息
  SegmentTermDocs(SegmentReader parent)
          throws IOException {
    this.parent = parent;
    this.freqStream = (InputStream) parent.freqStream.clone();
    this.deletedDocs = parent.deletedDocs;
    this.skipInterval = parent.tis.getSkipInterval();
  }

  public void seek(Term term) throws IOException {
    TermInfo ti = parent.tis.get(term);
    seek(ti);
  }

  public void seek(TermEnum termEnum) throws IOException {
    TermInfo ti;
    
    // use comparison of fieldinfos to verify that termEnum belongs to the same segment as this SegmentTermDocs
    if (termEnum instanceof SegmentTermEnum && ((SegmentTermEnum) termEnum).fieldInfos == parent.fieldInfos)          // optimized case
      ti = ((SegmentTermEnum) termEnum).termInfo();
    else                                          // punt case
      ti = parent.tis.get(termEnum.term());
      
    seek(ti);
  }

  //重新检索该term
  void seek(TermInfo ti) throws IOException {
    count = 0;//重新设置一个term,因此出现的文档数量为0
    if (ti == null) {
      df = 0;
    } else {
      df = ti.docFreq;//term出现在多少个文档中
      doc = 0;
      skipDoc = 0;
      skipCount = 0;
      numSkips = df / skipInterval;//最多有多少层跳跃表
      freqPointer = ti.freqPointer;
      proxPointer = ti.proxPointer;
      skipPointer = freqPointer + ti.skipOffset;//返回跳跃表开始的位置
      freqStream.seek(freqPointer);//设置该文档对应的词频位置
      haveSkipped = false;
    }
  }

  public void close() throws IOException {
    freqStream.close();
    if (skipStream != null)
      skipStream.close();
  }

  //此时处理的文档id以及词频
  public final int doc() { return doc; }
  public final int freq() { return freq; }

  protected void skippingDoc() throws IOException {
  }

  //找到该term的下一个文档的词频、docid等信息
  public boolean next() throws IOException {
    while (true) {
      if (count == df)
        return false;

      int docCode = freqStream.readVInt();
      doc += docCode >>> 1;			  // shift off low bit 文档号码
      if ((docCode & 1) != 0)			  // if low bit is set 该文档词频
        freq = 1;				  // freq is one
      else
        freq = freqStream.readVInt();		  // else read freq 该文档词频

      count++;//该term出现的文档次数累加1

      if (deletedDocs == null || !deletedDocs.get(doc)) //说明该doc不是删除的,因此说明找到该文档了，则退出
        break;
      skippingDoc();
    }
    return true;
  }

  /** Optimized implementation.
   * 一次性多读取一些内容,将读取的内容存储到参数的数组中
   * 返回接下来每一个属于该term的docid集合,以及每一个doc中该term出现的词频
   **/
  public int read(final int[] docs, final int[] freqs)
          throws IOException {
    final int length = docs.length;
    int i = 0;
    while (i < length && count < df) {//只要没有超过总doc文档数,就一直循环

      // manually inlined call to next() for speed
      final int docCode = freqStream.readVInt();//获取词频 以及文档id
      doc += docCode >>> 1;			  // shift off low bit
      if ((docCode & 1) != 0)			  // if low bit is set
        freq = 1;				  // freq is one
      else
        freq = freqStream.readVInt();		  // else read freq
      count++;

      if (deletedDocs == null || !deletedDocs.get(doc)) {//说明该doc没有被删除
        docs[i] = doc;//read的值附加到参数集合中
        freqs[i] = freq;
        ++i;
      }
    }
    return i;
  }

  /** Overridden by SegmentTermPositions to skip in prox stream. 
   * 跳跃到位置文件的制定位置
   **/
  protected void skipProx(long proxPointer) throws IOException {}

  /** Optimized implementation. */
  public boolean skipTo(int target) throws IOException {
    if (df >= skipInterval) {                      // optimized case

      if (skipStream == null)
        skipStream = (InputStream) freqStream.clone(); // lazily clone

      if (!haveSkipped) {                          // lazily seek skip stream
        skipStream.seek(skipPointer);//设置到跳跃表位置
        haveSkipped = true;
      }

      // scan skip data
      int lastSkipDoc = skipDoc;
      long lastFreqPointer = freqStream.getFilePointer();
      long lastProxPointer = -1;
      int numSkipped = -1 - (count % skipInterval);

      while (target > skipDoc) {
    	  //更新last等信息,说明跳跃表有作用了
        lastSkipDoc = skipDoc;
        lastFreqPointer = freqPointer;
        lastProxPointer = proxPointer;
        
        if (skipDoc != 0 && skipDoc >= doc)
          numSkipped += skipInterval;
        
        if(skipCount >= numSkips) //说明已经到跳跃表最后了
          break;

        //本次的位置 是为了lastFreqPointer等服务的
        skipDoc += skipStream.readVInt();//跳跃表的docid
        freqPointer += skipStream.readVInt();//docid所在的词频文件位置
        proxPointer += skipStream.readVInt();//docid所在的位置文件位置

        skipCount++;//跳跃表的数量
      }
      //获取最后一个last位置,从该位置开始往后面依次读取数据
      
      // if we found something to skip, then skip it
      if (lastFreqPointer > freqStream.getFilePointer()) {
        freqStream.seek(lastFreqPointer);//跳跃到词频文件的制定位置
        skipProx(lastProxPointer);//跳跃到位置文件的指定位置

        doc = lastSkipDoc;
        count += numSkipped;
      }

    }

    // done skipping, now just scan
    do {
      if (!next())//接下来读取下一个doc
        return false;
    } while (target > doc);
    return true;
  }

}
