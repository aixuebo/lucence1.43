package org.apache.lucene.search;

/**
 * Copyright 2002-2004 The Apache Software Foundation
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

import java.util.HashSet;
import java.util.Iterator;

import org.apache.lucene.index.IndexReader;

/** The abstract base class for queries.
    <p>Instantiable subclasses are:
    <ul>
    <li> {@link TermQuery} 最基础的查询,满足某一个具体term的查询
    <li> {@link BooleanQuery}  最高级的查询,满足各种条件的组合查询
    
    <li> {@link PhraseQuery} 查询匹配文档是包含一组term,并且该term是按照一定顺序出现的,这种情况匹配短语 应该比匹配单个词 要赋予更高的权重
    <li> {@link PhrasePrefixQuery} 主要用来进行同义词查询的
    
    //也是类似rangeQuery,只是模糊匹配,不同的term应该权重是不同的,因此比rangeQuery多设置了个权重
    <li> {@link MultiTermQuery} //FuzzyQuery和WildcardQuery的基础类,属于满足多个词匹配模式的查询
    <li> {@link FuzzyQuery} //通过编辑距离的相似度进行匹配,匹配最相似的term
    <li> {@link WildcardQuery} //使用通配符？和*进行匹配,这个查询会很慢,他需要迭代所有的term,因为*可以替代所有元素,所以遍历所有的term肯定会很慢的,为了提高效率,应该不允许以*开头进行匹配
    
    <li> {@link PrefixQuery} //匹配任何文章中包含term前缀的即可,其实该词和rangeQuery没什么太大区别,只是区间范围是由前缀控制,而 rangeQuery是由开始-结束 term控制
    <li> {@link RangeQuery} //由开始--结束 term控制查询范围,属于booleanQuery--包含内部具体的termQuery操作
    <li> {@link org.apache.lucene.search.spans.SpanQuery}
    </ul>
    <p>A parser for queries is contained in:
    <ul>
    <li>{@link org.apache.lucene.queryParser.QueryParser QueryParser}
    </ul>
*/
public abstract class Query implements java.io.Serializable, Cloneable {
  private float boost = 1.0f;                     // query boost factor

  /** Sets the boost for this query clause to <code>b</code>.  Documents
   * matching this clause will (in addition to the normal weightings) have
   * their score multiplied by <code>b</code>.
   * 每一个查询query匹配,都会有一个优先级,该优先级会用于打分
   * 因为最终结果是由多个query决定的,肯定希望query层面有一个优先级
   * 默认是1,表示所有query都是同一个优先级
   */
  public void setBoost(float b) { boost = b; }

  /** Gets the boost for this clause.  Documents matching
   * this clause will (in addition to the normal weightings) have their score
   * multiplied by <code>b</code>.   The boost is 1.0 by default.
   */
  public float getBoost() { return boost; }

  /** Prints a query to a string, with <code>field</code> as the default field
   * for terms.  <p>The representation used is one that is readable by
   * {@link org.apache.lucene.queryParser.QueryParser QueryParser}
   * (although, if the query was created by the parser, the printed
   * representation may not be exactly what was parsed).
   */
  public abstract String toString(String field);

  /** Prints a query to a string. */
  public String toString() {
    return toString("");
  }

  /** Expert: Constructs an appropriate Weight implementation for this query.
   *  构建一个适当的权重实现
   * <p>Only implemented by primitive queries, which re-write to themselves.
   */
  protected Weight createWeight(Searcher searcher) {
    throw new UnsupportedOperationException();
  }

  /** Expert: Constructs an initializes a Weight for a top-level query. */
  public Weight weight(Searcher searcher)
    throws IOException {
    Query query = searcher.rewrite(this);
    Weight weight = query.createWeight(searcher);
    float sum = weight.sumOfSquaredWeights();
    float norm = getSimilarity(searcher).queryNorm(sum);
    weight.normalize(norm);
    return weight;
  }

  /** Expert: called to re-write queries into primitive queries. */
  public Query rewrite(IndexReader reader) throws IOException {
    return this;
  }

  /** Expert: called when re-writing queries under MultiSearcher.
   *
   * <p>Only implemented by derived queries, with no
   * {@link #createWeight(Searcher)} implementatation.
   */
  public Query combine(Query[] queries) {
    throw new UnsupportedOperationException();
  }


  /** Expert: merges the clauses of a set of BooleanQuery's into a single
   * BooleanQuery.
   *
   *<p>A utility for use by {@link #combine(Query[])} implementations.
   */
  public static Query mergeBooleanQueries(Query[] queries) {
    HashSet allClauses = new HashSet();
    for (int i = 0; i < queries.length; i++) {
      BooleanClause[] clauses = ((BooleanQuery)queries[i]).getClauses();
      for (int j = 0; j < clauses.length; j++) {
        allClauses.add(clauses[j]);
      }
    }

    BooleanQuery result = new BooleanQuery();
    Iterator i = allClauses.iterator();
    while (i.hasNext()) {
      result.add((BooleanClause)i.next());
    }
    return result;
  }

  /** Expert: Returns the Similarity implementation to be used for this query.
   * Subclasses may override this method to specify their own Similarity
   * implementation, perhaps one that delegates through that of the Searcher.
   * By default the Searcher's Similarity implementation is returned.*/
  public Similarity getSimilarity(Searcher searcher) {
    return searcher.getSimilarity();
  }

  /** Returns a clone of this query. */
  public Object clone() {
    try {
      return (Query)super.clone();
    } catch (CloneNotSupportedException e) {
      throw new RuntimeException("Clone not supported: " + e.getMessage());
    }
  }
}
