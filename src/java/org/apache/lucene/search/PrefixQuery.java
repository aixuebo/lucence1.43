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

import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;
import org.apache.lucene.index.IndexReader;

/** A Query that matches documents containing terms with a specified prefix.
 * 匹配任何文章中包含term前缀的即可,其实该词和rangeQuery没什么太大区别,只是区间范围是由前缀控制,而 rangeQuery是由开始-结束 term控制
 **/
public class PrefixQuery extends Query {
  private Term prefix;

  /** Constructs a query for terms starting with <code>prefix</code>. */
  public PrefixQuery(Term prefix) {
    this.prefix = prefix;
  }

  /** Returns the prefix of this query. */
  public Term getPrefix() { return prefix; }

  public Query rewrite(IndexReader reader) throws IOException {
    BooleanQuery query = new BooleanQuery();
    TermEnum enumerator = reader.terms(prefix);
    try {
      String prefixText = prefix.text();
      String prefixField = prefix.field();
      do {
        Term term = enumerator.term();
        if (term != null &&
            term.text().startsWith(prefixText) &&
            term.field() == prefixField) {
          TermQuery tq = new TermQuery(term);	  // found a match
          tq.setBoost(getBoost());                // set the boost
          query.add(tq, false, false);		  // add to query,都是fasle,表示该词语不是必须出现的,也不是必须删除的
          //System.out.println("added " + term);
        } else {
          break;
        }
      } while (enumerator.next());
    } finally {
      enumerator.close();
    }
    return query;
  }

  public Query combine(Query[] queries) {
    return Query.mergeBooleanQueries(queries);
  }

  /** Prints a user-readable version of this query. */
  public String toString(String field) {
    StringBuffer buffer = new StringBuffer();
    if (!prefix.field().equals(field)) {
      buffer.append(prefix.field());
      buffer.append(":");
    }
    buffer.append(prefix.text());
    buffer.append('*');
    if (getBoost() != 1.0f) {
      buffer.append("^");
      buffer.append(Float.toString(getBoost()));
    }
    return buffer.toString();
  }

}
