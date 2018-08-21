package org.apache.lucene.search.spans;

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

import java.util.List;
import java.util.ArrayList;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.util.PriorityQueue;

class NearSpans implements Spans {
  private SpanNearQuery query;

  private List ordered = new ArrayList();         // spans in query order
  private int slop;                               // from query
  private boolean inOrder;                        // from query

  private SpansCell first;                        // linked list of spans 优先队列中的第一个对象
  private SpansCell last;                         // sorted by doc only 优先队列中的最后一个对象

  private int totalLength;                        // sum of current lengths

  private CellQueue queue;                        // sorted queue of spans
  private SpansCell max;                          // max element in queue

  private boolean more = true;                    // true iff not done
  private boolean firstTime = true;               // true before first next()

  private class CellQueue extends PriorityQueue {
    public CellQueue(int size) {
      initialize(size);
    }
    
    protected final boolean lessThan(Object o1, Object o2) {
      SpansCell spans1 = (SpansCell)o1;
      SpansCell spans2 = (SpansCell)o2;
      if (spans1.doc() == spans2.doc()) {
        if (spans1.start() == spans2.start()) {
          if (spans1.end() == spans2.end()) {
            return spans1.index > spans2.index;
          } else {
            return spans1.end() < spans2.end();
          }
        } else {
          return spans1.start() < spans2.start();
        }
      } else {
        return spans1.doc() < spans2.doc();
      }
    }
  }


  /** Wraps a Spans, and can be used to form a linked list. */
  private class SpansCell implements Spans {
    private Spans spans;
    private SpansCell next;
    private int length = -1;
    private int index;

    public SpansCell(Spans spans, int index) {
      this.spans = spans;
      this.index = index;
    }

    public boolean next() throws IOException {
      if (length != -1)                           // subtract old length
        totalLength -= length;

      boolean more = spans.next();                // move to next

      if (more) {
        length = end() - start();                 // compute new length
        totalLength += length;                    // add new length to total

        if (max == null || doc() > max.doc() ||   // maintain max
            (doc() == max.doc() && end() > max.end()))
          max = this;
      }

      return more;
    }

    public boolean skipTo(int target) throws IOException {
      if (length != -1)                           // subtract old length
        totalLength -= length;

      boolean more = spans.skipTo(target);        // skip

      if (more) {
        length = end() - start();                 // compute new length
        totalLength += length;                    // add new length to total

        if (max == null || doc() > max.doc() ||   // maintain max
            (doc() == max.doc() && end() > max.end()))
          max = this;
      }

      return more;
    }

    public int doc() { return spans.doc(); }
    public int start() { return spans.start(); }
    public int end() { return spans.end(); }

    public String toString() { return spans.toString() + "#" + index; }
  }

  public NearSpans(SpanNearQuery query, IndexReader reader)
    throws IOException {
    this.query = query;
    this.slop = query.getSlop();
    this.inOrder = query.isInOrder();

    SpanQuery[] clauses = query.getClauses();     // initialize spans & list
    queue = new CellQueue(clauses.length);
    for (int i = 0; i < clauses.length; i++) {
      SpansCell cell =                            // construct clause spans
        new SpansCell(clauses[i].getSpans(reader), i);
      ordered.add(cell);                          // add to ordered
    }
  }

  public boolean next() throws IOException {
    if (firstTime) {
      initList(true);
      listToQueue();                              // initialize queue
      firstTime = false;
    } else if (more) {
      more = min().next();                        // trigger further scanning
      if (more)
        queue.adjustTop();                        // maintain queue
    }

    while (more) {

      boolean queueStale = false;

      if (min().doc() != max.doc()) {             // maintain list
        queueToList();
        queueStale = true;
      }

      // skip to doc w/ all clauses

      while (more && first.doc() < last.doc()) {
        more = first.skipTo(last.doc());          // skip first upto last
        firstToLast();                            // and move it to the end
        queueStale = true;
      }

      if (!more) return false;

      // found doc w/ all clauses

      if (queueStale) {                           // maintain the queue
        listToQueue();
        queueStale = false;
      }

      if (atMatch())
        return true;
      
      // trigger further scanning
      if (inOrder && checkSlop()) {
        /* There is a non ordered match within slop and an ordered match is needed. */
        more = firstNonOrderedNextToPartialList();
        if (more) {
          partialListToQueue();                            
        }
      } else {
        more = min().next();
        if (more) {
          queue.adjustTop();                      // maintain queue
        }
      }
    }
    return false;                                 // no more matches
  }

  public boolean skipTo(int target) throws IOException {
    if (firstTime) {                              // initialize
      initList(false);
      for (SpansCell cell = first; more && cell!=null; cell=cell.next) {
        more = cell.skipTo(target);               // skip all
      }
      if (more) {
        listToQueue();
      }
      firstTime = false;
    } else {                                      // normal case
      while (more && min().doc() < target) {      // skip as needed
        more = min().skipTo(target);
        if (more)
          queue.adjustTop();
      }
    }
    if (more) {

      if (atMatch())                              // at a match?
        return true;

      return next();                              // no, scan
    }

    return false;
  }

  private SpansCell min() { return (SpansCell)queue.top(); }

  public int doc() { return min().doc(); }
  public int start() { return min().start(); }
  public int end() { return max.end(); }


  public String toString() {
    return "spans("+query.toString()+")@"+
      (firstTime?"START":(more?(doc()+":"+start()+"-"+end()):"END"));
  }

  private void initList(boolean next) throws IOException {
    for (int i = 0; more && i < ordered.size(); i++) {
      SpansCell cell = (SpansCell)ordered.get(i);
      if (next)
        more = cell.next();                       // move to first entry
      if (more) {
        addToList(cell);                          // add to list
      }
    }
  }

  //按照顺序组装成队列---新添加到放到队尾
  private void addToList(SpansCell cell) {
    if (last != null) {			  // add next to end of list 新添加的放到队尾即可
      last.next = cell;
    } else {//说明是第一次添加,因此first为cell
    	first = cell;//first就第一次时候赋值
    }
    last = cell;//设置最后一个为队尾对象--每次更新最后一个元素即可
    cell.next = null;//因为刚刚添加元素,还没有next属性,因此要设置为null
  }

  //第一个迁移到最后一个位置
  private void firstToLast() {
    last.next = first;			  // move first to end of list  移动第一个到最后一个位置,最后一个位置就是last的下一个---设置一个链表关系
    last = first;//设置最后一个即是first
    first = first.next;//第一个就是原来第一个的下一个
    last.next = null;//此时的next 就是原始第一个的next,因为已经没有意义了,因此清空
  }

  //将优先队列按照顺序组装成链表
  private void queueToList() {
    last = first = null;
    while (queue.top() != null) {
      addToList((SpansCell)queue.pop());
    }
  }
  
  private boolean firstNonOrderedNextToPartialList() throws IOException {
    /* Creates a partial list consisting of first non ordered and earlier.
     * Returns first non ordered .next().
     */
    last = first = null;
    int orderedIndex = 0;
    while (queue.top() != null) {
      SpansCell cell = (SpansCell)queue.pop();
      addToList(cell);
      if (cell.index == orderedIndex) {
        orderedIndex++;
      } else {
        return cell.next();
        // FIXME: continue here, rename to eg. checkOrderedMatch():
        // when checkSlop() and not ordered, repeat cell.next().
        // when checkSlop() and ordered, add to list and repeat queue.pop()
        // without checkSlop(): no match, rebuild the queue from the partial list.
        // When queue is empty and checkSlop() and ordered there is a match.
      }
    }
    throw new RuntimeException("Unexpected: ordered");
  }

  //将链表中数据 存储到优先队列中
  private void listToQueue() {
    queue.clear(); // rebuild queue
    partialListToQueue();
  }

  private void partialListToQueue() {
    for (SpansCell cell = first; cell != null; cell = cell.next) {//从头到尾的遍历即可
      queue.put(cell);                      // add to queue from list
    }
  }

  private boolean atMatch() {
    return (min().doc() == max.doc())
          && checkSlop()
          && (!inOrder || matchIsOrdered());
  }
  
  private boolean checkSlop() {
    int matchLength = max.end() - min().start();
    return (matchLength - totalLength) <= slop;
  }

  private boolean matchIsOrdered() {
    int lastStart = -1;
    for (int i = 0; i < ordered.size(); i++) {
      int start = ((SpansCell)ordered.get(i)).start();
      if (!(start > lastStart))
        return false;
      lastStart = start;
    }
    return true;
  }
}
