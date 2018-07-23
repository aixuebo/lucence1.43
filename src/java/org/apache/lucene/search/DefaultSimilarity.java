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

/** Expert: Default scoring implementation. */
public class DefaultSimilarity extends Similarity {
  /** Implemented as <code>1/sqrt(numTerms)</code>. 
   * 该文档的field的词越多,得分越小 
   **/
  public float lengthNorm(String fieldName, int numTerms) {
    return (float)(1.0 / Math.sqrt(numTerms));
  }
  
  /** Implemented as <code>1/sqrt(sumOfSquaredWeights)</code>. */
  public float queryNorm(float sumOfSquaredWeights) {
    return (float)(1.0 / Math.sqrt(sumOfSquaredWeights));
  }

  /** Implemented as <code>sqrt(freq)</code>. 
   *  表示一个term在该document中出现的频次，频次越高，分数应该越高
   *  默认值是开根号
   **/
  public float tf(float freq) {
    return (float)Math.sqrt(freq);
  }
    
  /** Implemented as <code>1 / (distance + 1)</code>. */
  public float sloppyFreq(int distance) {
    return 1.0f / (distance + 1);
  }
    
  /** Implemented as <code>log(numDocs/(docFreq+1)) + 1</code>.
   * term在多少个doc出现过,如果一个term越不常出现,则分数应该越高 
   **/
  public float idf(int docFreq, int numDocs) {
    return (float)(Math.log(numDocs/(double)(docFreq+1)) + 1.0);
  }
    
  /** Implemented as <code>overlap / maxOverlap</code>. 
   * 是一个评分因子，这是一个搜索时的因子，是在搜索的时候起作用
   * 
   * 即不是基于term,而是针对整个query,将query分词成若干个term后,这些term有多少个词在该doc中出现,
   * 一篇包含了越多的不同的term的doc 一定分数更高一些
   * 
   * 参数 overlap: 在该文档中,有多少个不同的term存在
   * maxOverlap:在query中一共有多少个不同的term
   **/
  public float coord(int overlap, int maxOverlap) {
    return overlap / (float)maxOverlap;
  }
}
