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

abstract class PhraseScorer extends Scorer {
  private Weight weight;
  protected byte[] norms;
  protected float value;

  private boolean firstTime = true;
  private boolean more = true;
  protected PhraseQueue pq;//优先队列
  protected PhrasePositions first, last;//存储每一个term的读取位置,即如何读取term的位置,判断他们是否是短语

  private float freq;//查找该doc中包含短语的次数,即短语词频


  /**
   * 
   * @param weight
   * @param tps 可以next方法获取该term在下一个doc的词频,以及nextPosition方法返回在同一个doc内的每一个词位置
   * @param positions 每一个term在短语中的位置
   * @param similarity
   * @param norms
   */
  PhraseScorer(Weight weight, TermPositions[] tps, int[] positions, Similarity similarity,
               byte[] norms) {
    super(similarity);
    this.norms = norms;
    this.weight = weight;
    this.value = weight.getValue();

    // convert tps to a list
    for (int i = 0; i < tps.length; i++) {//循环每一个term对应所有doc中的位置
      PhrasePositions pp = new PhrasePositions(tps[i], positions[i]);//获取term
      if (last != null) {			  // add next to end of list
        last.next = pp;//next表示下一个term,比如pp是第2个term,而last就是第一个term,因此last.next就是说第一个term要知道第二个term是谁
      } else
        first = pp;//第一次进来,赋予first为第一个term
      last = pp;
    }

    pq = new PhraseQueue(tps.length);             // construct empty pq

  }

  public int doc() { return first.doc; }

  public boolean next() throws IOException {
    if (firstTime) {//第一次要初始化
      init();//初始化
      firstTime = false;
    } else if (more) {
      more = last.next();                         // trigger further scanning
    }
    return doNext();
  }
  
  // next without initial increment
  private boolean doNext() throws IOException {
    while (more) {//不断查找短语所在doc
      while (more && first.doc < last.doc) {      // find doc w/ all the terms 说明不是所有的doc都包含相同的term,因此first所在的doc不会出现短语
        more = first.skipTo(last.doc);            // skip first upto last,让first跳跃到last对应的doc位置上,期间的所有doc都不会出现该短语
        firstToLast();                            // and move it to the end,first让他到最后一个位置上
      }

      if (more) {//至此说明找到了包含所有短语term的doc
        // found a doc with all of the terms 查找该doc中包含短语的次数,即短语词频
        freq = phraseFreq();                      // check for phrase
        if (freq == 0.0f)                         // no match 没有匹配
          more = last.next();                     // trigger further scanning 继续扫描,因为first-last的doc都是相同的,谁执行next都是可以的
        else
          return true;                            // found a match
      }
    }
    return false;                                 // no more matches
  }

  public float score() throws IOException {
    //System.out.println("scoring " + first.doc);
    float raw = getSimilarity().tf(freq) * value; // raw score
    return raw * Similarity.decodeNorm(norms[first.doc]); // normalize
  }

  public boolean skipTo(int target) throws IOException {
    for (PhrasePositions pp = first; more && pp != null; pp = pp.next) {
      more = pp.skipTo(target);
    }
    if (more)
      sort();                                     // re-sort
    return doNext();
  }

  //查找该doc中包含短语的次数,即短语词频
  //进入该方法的前提是所有的doc都相同,即所有的term都在改doc存在,因此只需要计算term组成短语的词频即可
  protected abstract float phraseFreq() throws IOException;

  private void init() throws IOException {
    for (PhrasePositions pp = first; more && pp != null; pp = pp.next) //more=false,说明没有下一个doc了,退出for循环,说明短语肯定不用再找了,因为该短语中的一个term所在的doc已经都循环完成了,其他term对应的doc一定没有改term,没有必要再查找了
      more = pp.next();//不断获取下一个doc,如果没有下一个doc了,则more=flse
    if(more)
      sort();//对数据排序
  }
  
  private void sort() {
    pq.clear();
    for (PhrasePositions pp = first; pp != null; pp = pp.next)
      pq.put(pp);//优先队列排序
    pqToList();
  }

  //按照排序后,对PhrasePositions重新调整next的顺序
  protected final void pqToList() {
    last = first = null;
    while (pq.top() != null) {
      PhrasePositions pp = (PhrasePositions) pq.pop();
      if (last != null) {			  // add next to end of list
        last.next = pp;
      } else
        first = pp;
      last = pp;
      pp.next = null;
    }
  }

  /**
   * 让第一个 移动到最后一个位置
   * 步骤如下:
   * 1.原有最后一个,变成倒数第2个,因此next赋予新的值,就是first
   * 2.让最后一个变成第一个(目的)
   * 3.让第二个位置元素 现在变成第一个位置
   * 4.让原来第一个位置的下一个为null,因为他是最后一个了,不在于next了,原来的next第二个已经是第一个位置了
   */
  protected final void firstToLast() {
    last.next = first;			  // move first to end of list 将第一个移动到最后一个
    last = first;//last让他变成第一个,因为第一个目前就是即将变成最后一个
    first = first.next;//第一个就是原来的第二个
    last.next = null;//因为last已经是原来的第一个了,因此原来第一个next不再是关注第二个了,因此没有next
  }

  public Explanation explain(final int doc) throws IOException {
    Explanation tfExplanation = new Explanation();

    while (next() && doc() < doc) {}

    float phraseFreq = (doc() == doc) ? freq : 0.0f;
    tfExplanation.setValue(getSimilarity().tf(phraseFreq));
    tfExplanation.setDescription("tf(phraseFreq=" + phraseFreq + ")");

    return tfExplanation;
  }

  public String toString() { return "scorer(" + weight + ")"; }

}
