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

import org.apache.lucene.index.IndexReader;
import java.io.IOException;
import java.util.BitSet;


/**
 * A query that applies a filter to the results of another query.
 * 一个查询query,配合一个filter过滤器,对query的结果进行过滤
 * <p>Note: the bits are retrieved from the filter each time this
 * query is used in a search - use a CachingWrapperFilter to avoid
 * regenerating the bits every time.
 *
 * <p>Created: Apr 20, 2004 8:58:29 AM
 *
 * @author  Tim Jones
 * @since   1.4
 * @version $Id: FilteredQuery.java,v 1.5 2004/06/18 09:52:25 ehatcher Exp $
 * @see     CachingWrapperFilter
 */
public class FilteredQuery
extends Query {

  Query query;
  Filter filter;

  /**
   * Constructs a new query which applies a filter to the results of the original query.
   * Filter.bits() will be called every time this query is used in a search.
   * @param query  Query to be filtered, cannot be <code>null</code>.
   * @param filter Filter to apply to query results, cannot be <code>null</code>.
   */
  public FilteredQuery (Query query, Filter filter) {
    this.query = query;
    this.filter = filter;
  }

  /**
   * Returns a Weight that applies the filter to the enclosed query's Weight.
   * This is accomplished by overriding the Scorer returned by the Weight.
   */
  protected Weight createWeight (final Searcher searcher) {
    final Weight weight = query.createWeight (searcher);
    return new Weight() {

      // pass these methods through to enclosed query's weight
      public float getValue() { return weight.getValue(); }
      public float sumOfSquaredWeights() throws IOException { return weight.sumOfSquaredWeights(); }
      public void normalize (float v) { weight.normalize(v); }
      public Explanation explain (IndexReader ir, int i) throws IOException { return weight.explain (ir, i); }

      // return this query
      public Query getQuery() { return FilteredQuery.this; }

      // return a scorer that overrides the enclosed query's score if
      // the given hit has been filtered out.
      public Scorer scorer (IndexReader indexReader) throws IOException {
        final Scorer scorer = weight.scorer (indexReader);
        final BitSet bitset = filter.bits (indexReader);//创建过滤器内容
        return new Scorer (query.getSimilarity (searcher)) {

          // pass these methods through to the enclosed scorer 通过query获取下一个匹配的文档
          public boolean next() throws IOException { return scorer.next(); }
          public int doc() { return scorer.doc(); }
          public boolean skipTo (int i) throws IOException { return scorer.skipTo(i); }

          // if the document has been filtered out, set score to 0.0 如果该文档属于filter需要的,则返回该文档得分,不属于filter的,则配置为0分
          public float score() throws IOException {
            return (bitset.get(scorer.doc())) ? scorer.score() : 0.0f;
          }

          // add an explanation about whether the document was filtered
          public Explanation explain (int i) throws IOException {
            Explanation exp = scorer.explain (i);
            if (bitset.get(i)) //表示该doc是允许通过的
              exp.setDescription ("allowed by filter: "+exp.getDescription());
            else
              exp.setDescription ("removed by filter: "+exp.getDescription());
            return exp;
          }
        };
      }
    };
  }

  /** Rewrites the wrapped query.对query进行重构,然后添加对应的filter */
  public Query rewrite(IndexReader reader) throws IOException {
    Query rewritten = query.rewrite(reader);
    if (rewritten != query) {
      FilteredQuery clone = (FilteredQuery)this.clone();
      clone.query = rewritten;
      return clone;
    } else {
      return this;
    }
  }

  public Query getQuery() {
    return query;
  }

  /** Prints a user-readable version of this query. */
  public String toString (String s) {
    return "filtered("+query.toString(s)+")->"+filter;
  }

  /** Returns true iff <code>o</code> is equal to this. */
  public boolean equals(Object o) {
    if (o instanceof FilteredQuery) {
      FilteredQuery fq = (FilteredQuery) o;
      return (query.equals(fq.query) && filter.equals(fq.filter));
    }
    return false;
  }

  /** Returns a hash code value for this object. */
  public int hashCode() {
    return query.hashCode() ^ filter.hashCode();
  }
}
