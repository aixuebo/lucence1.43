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
import java.util.Vector;

import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermPositions;
import org.apache.lucene.index.IndexReader;

/** A Query that matches documents containing a particular sequence of terms.
  This may be combined with other terms with a {@link BooleanQuery}.
  查询匹配文档是包含一组term,并且该term是按照一定顺序出现的,这种情况匹配短语 应该比匹配单个词 要赋予更高的权重
  */
public class PhraseQuery extends Query {
  private String field;
  private Vector terms = new Vector();//存储一组词  元素是Term
  private Vector positions = new Vector();//存储每一个词对应的序号位置   元素是Integer
  private int slop = 0;

  /** Constructs an empty phrase query. */
  public PhraseQuery() {}

  /** Sets the number of other words permitted between words in query phrase.
   * 表示在短语中被允许插入多少个其他无用的词也可以被接受
    If zero, then this is an exact phrase search.  For larger values this works
    like a <code>WITHIN</code> or <code>NEAR</code> operator.
    如果该值是0,表示精准匹配,如果该阈值很大,则类似工作像within 或者 near操作,即表示近似

    <p>The slop is in fact an edit-distance, where the units correspond to
    moves of terms in the query phrase out of position.  For example, to switch
    the order of two words requires two moves (the first move places the words
    atop one another), so to permit re-orderings of phrases, the slop must be
    at least two.
    实际上slop是编辑距离,移动query的词语的编辑距离

    <p>More exact matches are scored higher than sloppier matches, thus search
    results are sorted by exactness.

    <p>The slop is zero by default, requiring exact matches.*/
  public void setSlop(int s) { slop = s; }
  /** Returns the slop.  See setSlop(). */
  public int getSlop() { return slop; }

  /**
   * Adds a term to the end of the query phrase.
   * The relative position of the term is the one immediately after the last term added.
   */
  public void add(Term term) {
    int position = 0;
    if(positions.size() > 0)
        position = ((Integer) positions.lastElement()).intValue() + 1;
    
    add(term, position);
  }
  
  /**
   * Adds a term to the end of the query phrase.
   * The relative position of the term within the phrase is specified explicitly.
   * This allows e.g. phrases with more than one term at the same position
   * or phrases with gaps (e.g. in connection with stopwords).
   * 
   * @param term
   * @param position
   * 添加短语中的term和对应的位置
   */
  public void add(Term term, int position) {
      if (terms.size() == 0)
          field = term.field();
      else if (term.field() != field)
          throw new IllegalArgumentException("All phrase terms must be in the same field: " + term);
      
      terms.addElement(term);
      positions.addElement(new Integer(position));
  }

  /** Returns the set of terms in this phrase. */
  public Term[] getTerms() {
    return (Term[])terms.toArray(new Term[0]);
  }
  
  /**
   * Returns the relative positions of terms in this phrase.
   * 返回每一个term对应的位置
   */
  public int[] getPositions() {
      int[] result = new int[positions.size()];
      for(int i = 0; i < positions.size(); i++)
          result[i] = ((Integer) positions.elementAt(i)).intValue();
      return result;
  }

  private class PhraseWeight implements Weight {
    private Searcher searcher;
    private float value;
    private float idf;
    private float queryNorm;
    private float queryWeight;

    public PhraseWeight(Searcher searcher) {
      this.searcher = searcher;
    }

    public String toString() { return "weight(" + PhraseQuery.this + ")"; }

    public Query getQuery() { return PhraseQuery.this; }
    public float getValue() { return value; }

    public float sumOfSquaredWeights() throws IOException {
      idf = getSimilarity(searcher).idf(terms, searcher);
      queryWeight = idf * getBoost();             // compute query weight
      return queryWeight * queryWeight;           // square it
    }

    public void normalize(float queryNorm) {
      this.queryNorm = queryNorm;
      queryWeight *= queryNorm;                   // normalize query weight
      value = queryWeight * idf;                  // idf for document 
    }

    public Scorer scorer(IndexReader reader) throws IOException {
      if (terms.size() == 0)			  // optimize zero-term case
        return null;

      //每一个term对应一个TermPositions对象
      TermPositions[] tps = new TermPositions[terms.size()];//可以next方法获取该term在下一个doc的词频,以及nextPosition方法返回在同一个doc内的每一个词位置
      for (int i = 0; i < terms.size(); i++) {
        TermPositions p = reader.termPositions((Term)terms.elementAt(i));
        if (p == null)
          return null;
        tps[i] = p;
      }

      if (slop == 0)				  // optimize exact case  精准匹配
        return new ExactPhraseScorer(this, tps, getPositions(), getSimilarity(searcher),
                                     reader.norms(field));
      else
        return
          new SloppyPhraseScorer(this, tps, getPositions(), getSimilarity(searcher), slop,
                                 reader.norms(field));
      
    }

    public Explanation explain(IndexReader reader, int doc)
      throws IOException {

      Explanation result = new Explanation();
      result.setDescription("weight("+getQuery()+" in "+doc+"), product of:");

      StringBuffer docFreqs = new StringBuffer();
      StringBuffer query = new StringBuffer();
      query.append('\"');
      for (int i = 0; i < terms.size(); i++) {
        if (i != 0) {
          docFreqs.append(" ");
          query.append(" ");
        }

        Term term = (Term)terms.elementAt(i);

        docFreqs.append(term.text());
        docFreqs.append("=");
        docFreqs.append(searcher.docFreq(term));

        query.append(term.text());
      }
      query.append('\"');

      Explanation idfExpl =
        new Explanation(idf, "idf(" + field + ": " + docFreqs + ")");
      
      // explain query weight
      Explanation queryExpl = new Explanation();
      queryExpl.setDescription("queryWeight(" + getQuery() + "), product of:");

      Explanation boostExpl = new Explanation(getBoost(), "boost");
      if (getBoost() != 1.0f)
        queryExpl.addDetail(boostExpl);
      queryExpl.addDetail(idfExpl);
      
      Explanation queryNormExpl = new Explanation(queryNorm,"queryNorm");
      queryExpl.addDetail(queryNormExpl);
      
      queryExpl.setValue(boostExpl.getValue() *
                         idfExpl.getValue() *
                         queryNormExpl.getValue());

      result.addDetail(queryExpl);
     
      // explain field weight
      Explanation fieldExpl = new Explanation();
      fieldExpl.setDescription("fieldWeight("+field+":"+query+" in "+doc+
                               "), product of:");

      Explanation tfExpl = scorer(reader).explain(doc);
      fieldExpl.addDetail(tfExpl);
      fieldExpl.addDetail(idfExpl);

      Explanation fieldNormExpl = new Explanation();
      byte[] fieldNorms = reader.norms(field);
      float fieldNorm =
        fieldNorms!=null ? Similarity.decodeNorm(fieldNorms[doc]) : 0.0f;
      fieldNormExpl.setValue(fieldNorm);
      fieldNormExpl.setDescription("fieldNorm(field="+field+", doc="+doc+")");
      fieldExpl.addDetail(fieldNormExpl);

      fieldExpl.setValue(tfExpl.getValue() *
                         idfExpl.getValue() *
                         fieldNormExpl.getValue());
      
      result.addDetail(fieldExpl);

      // combine them
      result.setValue(queryExpl.getValue() * fieldExpl.getValue());

      if (queryExpl.getValue() == 1.0f)
        return fieldExpl;

      return result;
    }
  }

  protected Weight createWeight(Searcher searcher) {
    if (terms.size() == 1) {			  // optimize one-term case
      Term term = (Term)terms.elementAt(0);
      Query termQuery = new TermQuery(term);
      termQuery.setBoost(getBoost());
      return termQuery.createWeight(searcher);
    }
    return new PhraseWeight(searcher);
  }


  /** Prints a user-readable version of this query. */
  public String toString(String f) {
    StringBuffer buffer = new StringBuffer();
    if (!field.equals(f)) {
      buffer.append(field);
      buffer.append(":");
    }

    buffer.append("\"");
    for (int i = 0; i < terms.size(); i++) {
      buffer.append(((Term)terms.elementAt(i)).text());
      if (i != terms.size()-1)
	buffer.append(" ");
    }
    buffer.append("\"");

    if (slop != 0) {
      buffer.append("~");
      buffer.append(slop);
    }

    if (getBoost() != 1.0f) {
      buffer.append("^");
      buffer.append(Float.toString(getBoost()));
    }

    return buffer.toString();
  }

  /** Returns true iff <code>o</code> is equal to this. */
  public boolean equals(Object o) {
    if (!(o instanceof PhraseQuery))
      return false;
    PhraseQuery other = (PhraseQuery)o;
    return (this.getBoost() == other.getBoost())
      && (this.slop == other.slop)
      &&  this.terms.equals(other.terms)
      && this.positions.equals(other.positions);
  }

  /** Returns a hash code value for this object.*/
  public int hashCode() {
    return Float.floatToIntBits(getBoost())
      ^ Float.floatToIntBits(slop)
      ^ terms.hashCode()
      ^ positions.hashCode();
  }

}
