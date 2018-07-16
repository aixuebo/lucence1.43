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

import org.apache.lucene.index.TermDocs;

final class TermScorer extends Scorer {
  private Weight weight;
  private TermDocs termDocs;
  private byte[] norms;
  private float weightValue;
  private int doc;//此时正在处理的doc文档

  private final int[] docs = new int[32];	  // buffered doc numbers 缓存的有该term的doc文档集合
  private final int[] freqs = new int[32];	  // buffered term freqs  //缓存的每一个doc对应的词频
  private int pointer;//处理docs缓存已经第几个位置了
  private int pointerMax;//docs缓存一共多少条有效的数据元素

  private static final int SCORE_CACHE_SIZE = 32;
  private float[] scoreCache = new float[SCORE_CACHE_SIZE];//缓存一部分分数

  TermScorer(Weight weight, TermDocs td, Similarity similarity,
             byte[] norms) throws IOException {
    super(similarity);
    this.weight = weight;
    this.termDocs = td;
    this.norms = norms;
    this.weightValue = weight.getValue();

    for (int i = 0; i < SCORE_CACHE_SIZE; i++)
      scoreCache[i] = getSimilarity().tf(i) * weightValue;
  }

  public int doc() { return doc; }

  //初始化下一个doc文档id
  public boolean next() throws IOException {
    pointer++;
    if (pointer >= pointerMax) {//说明缓存不足了
      pointerMax = termDocs.read(docs, freqs);    // refill buffer  读取该term对应的doc和词频集合
      if (pointerMax != 0) {
        pointer = 0;//继续从0开始迭代
      } else {//说明没有包含该term的doc了
        termDocs.close();			  // close stream
        doc = Integer.MAX_VALUE;		  // set to sentinel value
        return false;
      }
    } 
    doc = docs[pointer];
    return true;
  }

  //计算该docid的得分
  public float score() throws IOException {
    int f = freqs[pointer];//找到该doc对应的词频
    float raw =                                   // compute tf(f)*weight
      f < SCORE_CACHE_SIZE			  // check cache
      ? scoreCache[f]                             // cache hit
      : getSimilarity().tf(f)*weightValue;        // cache miss

    return raw * Similarity.decodeNorm(norms[doc]); // normalize for field
  }

  //直接跳转到参数对应的docid上面
  public boolean skipTo(int target) throws IOException {
    // first scan in cache 先从缓存中查找
    for (pointer++; pointer < pointerMax; pointer++) {
      if (docs[pointer] >= target) {//判断缓存的docid是不是比参数docid大,如果大,则说明找到了该target docid了
        doc = docs[pointer];
        return true;
      }
    }

    // not found in cache, seek underlying stream  直接跳转到docid位置
    boolean result = termDocs.skipTo(target);
    if (result) {//说明存在
      pointerMax = 1;//让下一次可以重新填充缓存空间
      pointer = 0;
      docs[pointer] = doc = termDocs.doc();//返回docid
      freqs[pointer] = termDocs.freq();//设置词频
    } else {//说明不存在
      doc = Integer.MAX_VALUE;
    }
    return result;
  }

  public Explanation explain(int doc) throws IOException {
    TermQuery query = (TermQuery)weight.getQuery();
    Explanation tfExplanation = new Explanation();
    int tf = 0;
    while (pointer < pointerMax) {
      if (docs[pointer] == doc)
        tf = freqs[pointer];
      pointer++;
    }
    if (tf == 0) {
      while (termDocs.next()) {
        if (termDocs.doc() == doc) {
          tf = termDocs.freq();
        }
      }
    }
    termDocs.close();
    tfExplanation.setValue(getSimilarity().tf(tf));
    tfExplanation.setDescription("tf(termFreq("+query.getTerm()+")="+tf+")");
    
    return tfExplanation;
  }

  public String toString() { return "scorer(" + weight + ")"; }

}
