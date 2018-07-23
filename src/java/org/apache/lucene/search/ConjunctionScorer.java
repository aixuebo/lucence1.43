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
import java.util.*;

/** Scorer for conjunctions, sets of queries, all of which are required. 
 *  scorers集合满足的query必须都要符合匹配要求
 *  一种优化方式,适用于所有的查询条件都是require的
 **/
final class ConjunctionScorer extends Scorer {
  private LinkedList scorers = new LinkedList();
  private boolean firstTime = true;//true表示尚未初始化
  private boolean more = true;
  private float coord;

  public ConjunctionScorer(Similarity similarity) {
    super(similarity);
  }

  final void add(Scorer scorer) throws IOException {
    scorers.addLast(scorer);//所有的query条件,每一个query占用一个元素
  }

  //返回第一个或者最后一个query
  private Scorer first() { return (Scorer)scorers.getFirst(); }
  private Scorer last() { return (Scorer)scorers.getLast(); }

  public int doc() { return first().doc(); }

  public boolean next() throws IOException {
    if (firstTime) {
      init();
    } else if (more) {
      more = last().next();                       // trigger further scanning
    }
    return doNext();
  }
  
  //获取下一个所有query中都相同的doc,即都存在的文档
  private boolean doNext() throws IOException {
    while (more && first().doc() < last().doc()) { // find doc w/ all clauses 退出该循环,说明first到last中的所有doc都是相同的
      more = first().skipTo(last().doc());      // skip first upto last 跳跃到最后一个文档
      scorers.addLast(scorers.removeFirst());   // move first to last 将第一个元素,追击到最后一个位置上,保持队列一直是满的
    }
    return more;                                // found a doc with all clauses
  }

  public boolean skipTo(int target) throws IOException {
    Iterator i = scorers.iterator();
    while (more && i.hasNext()) {
      more = ((Scorer)i.next()).skipTo(target);
    }
    if (more)
      sortScorers();                              // re-sort scorers
    return doNext();
  }

  public float score() throws IOException {
    float score = 0.0f;                           // sum scores
    Iterator i = scorers.iterator();//计算该文档在每一个doc中的分数之和
    while (i.hasNext())
      score += ((Scorer)i.next()).score();
    score *= coord;//计算和*基础系数分,获取该doc的分数
    return score;
  }

  //初始化
  private void init() throws IOException {
    more = scorers.size() > 0;

    // compute coord factor
    coord = getSimilarity().coord(scorers.size(), scorers.size());//总共有多少个query,因为所有query都是require的,因此两个参数相同

    // move each scorer to its first entry
    Iterator i = scorers.iterator();//循环每一个query查询子条件
    while (more && i.hasNext()) {//如果有任意一个子条件没有下一个匹配的doc文档,则退出while
      more = ((Scorer)i.next()).next();//判断是不是所有的子条件都有下一个匹配的doc文档
    }
    if (more) //如果是false说明没有匹配的文档了
      sortScorers();                              // initial sort of list

    firstTime = false;//初始化结束
  }

  private void sortScorers() throws IOException {
    // move scorers to an array
    Scorer[] array = (Scorer[])scorers.toArray(new Scorer[scorers.size()]);
    scorers.clear();                              // empty the list

    // note that this comparator is not consistent with equals!每一个匹配的文档按照doc排序
    Arrays.sort(array, new Comparator() {         // sort the array
        public int compare(Object o1, Object o2) {
          return ((Scorer)o1).doc() - ((Scorer)o2).doc();
        }
        public boolean equals(Object o1, Object o2) {
          return ((Scorer)o1).doc() == ((Scorer)o2).doc();
        }
      });
    
    for (int i = 0; i < array.length; i++) {
      scorers.addLast(array[i]);                  // re-build list, now sorted 排序后的集合
    }
  }

  public Explanation explain(int doc) throws IOException {
    throw new UnsupportedOperationException();
  }

}
