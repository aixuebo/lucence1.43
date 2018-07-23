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

final class BooleanScorer extends Scorer {
  private SubScorer scorers = null;
  private BucketTable bucketTable = new BucketTable(this);

  private int maxCoord = 1;
  private float[] coordFactors = null;

  private int requiredMask = 0;//报告第几个bit是否是1,1表示该序号对应的query在require属性是true
  private int prohibitedMask = 0;//报告第几个bit是否是1,1表示该序号对应的query在prohibit属性是true
  private int nextMask = 1;//多少个query,则产生多少个bit位置,去容纳所有的query的bit标识符

  BooleanScorer(Similarity similarity) {
    super(similarity);
  }

  //链表,每一个query组成的一个链表集合
  static final class SubScorer {
    public Scorer scorer;
    public boolean done;
    public boolean required = false;//要求
    public boolean prohibited = false;//禁止
    public HitCollector collector;
    public SubScorer next;//下一个query在链表中的打分对象

    public SubScorer(Scorer scorer, boolean required, boolean prohibited,
		     HitCollector collector, SubScorer next)
      throws IOException {
      this.scorer = scorer;
      this.done = !scorer.next();
      this.required = required;
      this.prohibited = prohibited;
      this.collector = collector;
      this.next = next;
    }
  }

  //参数boolean 有可能都是false,即该query可以命中,也可以不命中
  final void add(Scorer scorer, boolean required, boolean prohibited)
    throws IOException {
    int mask = 0;
    if (required || prohibited) {
      if (nextMask == 0)
	throw new IndexOutOfBoundsException
	  ("More than 32 required/prohibited clauses in query.");
      mask = nextMask;
      nextMask = nextMask << 1; //nextMask * 2^1 即nextMask * 2,即每一次nextMask的值为2 4 8 16 32 64 128等方式增加
    } else
      mask = 0;

    if (!prohibited)
      maxCoord++;//最大的必要的query数量增加1

    if (prohibited) //说明是不要的
      prohibitedMask |= mask;			  // update prohibited mask
    else if (required)
      requiredMask |= mask;			  // update required mask

    scorers = new SubScorer(scorer, required, prohibited,
			    bucketTable.newCollector(mask), scorers);
  }

  //初始化一个总query中,满足多少个query的得分
  private final void computeCoordFactors() throws IOException {
    coordFactors = new float[maxCoord];//
    for (int i = 0; i < maxCoord; i++)//循环每一个满足query的可能性
      coordFactors[i] = getSimilarity().coord(i, maxCoord-1);//比如第一个参数为5,第二个参数为7表示一共有7个子query,其中该文档命中5个query,那么他的权重是多少
  }

  private int end;
  private Bucket current;

  public int doc() { return current.doc; }

  public boolean next() throws IOException {
    boolean more;
    do {
      while (bucketTable.first != null) {         // more queued
        current = bucketTable.first;
        bucketTable.first = current.next;         // pop the queue

        // check prohibited & required
        if ((current.bits & prohibitedMask) == 0 && //找到有prohibite选项的,则不会返回true,即刨除该doc
            (current.bits & requiredMask) == requiredMask) {//所有的query都是命中为require的,则选择该doc
          return true;
        }
      }

      // refill the queue 填充队列
      more = false;
      end += BucketTable.SIZE;//每次读取一个缓冲区元素
      for (SubScorer sub = scorers; sub != null; sub = sub.next) {//循环每一个查询条件
        Scorer scorer = sub.scorer;//第一个查询条件
        while (!sub.done && scorer.doc() < end) {//每一个查询条件都不断的查找文档,知道end文档序号为止,即每一个文档都要查询一次,这两个for循环很耗费性能,相当于多少个查询条件，我就要索引多少次
        	//每次scorer.doc()都会返回匹配该query的文档id和文档得分
          sub.collector.collect(scorer.doc(), scorer.score());
          sub.done = !scorer.next();
        }
        if (!sub.done) {
          more = true;//说明一直都没有新的元素加入进来
        }
      }
    } while (bucketTable.first != null | more);

    return false;
  }

  public float score() throws IOException {
    if (coordFactors == null)
      computeCoordFactors();
    return current.score * coordFactors[current.coord];//该文档的分数*查询命中的权重
  }

  //该对象表示一个doc文档
  static final class Bucket {
    int	doc = -1;				  // tells if bucket is valid  文档id
    float	score;				  // incremental score 文档得分
    int	bits;					  // used for bool constraints 哪个bit位置是1,表示该1的位置是命中的第几个query,比如第1和3位置是1,说明命中的query是第1和3两个---原则上1的bit位置=coord数量
    int	coord;					  // count of terms in score  累加命中了多少个query
    Bucket 	next;				  // next valid bucket 下一个文档
  }

  /** A simple hash table of document scores within a range. */
  static final class BucketTable {
    public static final int SIZE = 1 << 10;//2^10=1024,即一次可以缓存读取1024个doc
    public static final int MASK = SIZE - 1;

    final Bucket[] buckets = new Bucket[SIZE];
    Bucket first = null;			  // head of valid list
  //
    private BooleanScorer scorer;

    public BucketTable(BooleanScorer scorer) {
      this.scorer = scorer;
    }

    public final int size() { return SIZE; }

    public HitCollector newCollector(int mask) {
      return new Collector(mask, this);
    }
  }

  static final class Collector extends HitCollector {
    private BucketTable bucketTable;
    private int mask;
    public Collector(int mask, BucketTable bucketTable) {
      this.mask = mask;
      this.bucketTable = bucketTable;
    }
    
    //收集好该文档的id 以及 某一个query的得分
    public final void collect(final int doc, final float score) {
      final BucketTable table = bucketTable;
      final int i = doc & BucketTable.MASK;//计算该doc应该在缓存中第几个元素位置存储
      Bucket bucket = table.buckets[i];
      if (bucket == null)
	table.buckets[i] = bucket = new Bucket();
      
    if (bucket.doc != doc) {			  // invalid bucket 说明重新设置该文档
		bucket.doc = doc;			  // set doc
		bucket.score = score;			  // initialize score
		bucket.bits = mask;			  // initialize mask
		bucket.coord = 1;			  // initialize coord 累加命中了多少个query
	
		bucket.next = table.first;		  // push onto valid list
		table.first = bucket;
    } else {					  // valid bucket
		bucket.score += score;			  // increment score
		bucket.bits |= mask;			  // add bits in mask
		bucket.coord++;				  // increment coord
      }
    }
  }

  public boolean skipTo(int target) throws IOException {
    throw new UnsupportedOperationException();
  }

  public Explanation explain(int doc) throws IOException {
    throw new UnsupportedOperationException();
  }

  public String toString() {
    StringBuffer buffer = new StringBuffer();
    buffer.append("boolean(");
    for (SubScorer sub = scorers; sub != null; sub = sub.next) {
      buffer.append(sub.scorer.toString());
      buffer.append(" ");
    }
    buffer.append(")");
    return buffer.toString();
  }


}
