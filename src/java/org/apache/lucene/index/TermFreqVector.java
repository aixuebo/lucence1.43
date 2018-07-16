package org.apache.lucene.index;

/** Provides access to stored term vector of 
 *  a document field.
 *  该对象表示一个document文档中每一个field中所有term的信息，快速找到一个field中的term
 */
public interface TermFreqVector {
  /**
   * 
   * @return The field this vector is associated with.
   * 该document下哪个field
   */ 
  public String getField();
  
  /** 
   * @return The number of terms in the term vector.
   * 该document-field下有多少个term
   */
  public int size();

  /** 
   * @return An Array of term texts in ascending order.
   * 所有的term集合,已经排序好了
   */
  public String[] getTerms();


  /** Array of term frequencies. Locations of the array correspond one to one
   *  to the terms in the array obtained from <code>getTerms</code>
   *  method. Each location in the array contains the number of times this
   *  term occurs in the document or the document field.
   */
  public int[] getTermFrequencies();
  

  /** Return an index in the term numbers array returned from
   *  <code>getTerms</code> at which the term with the specified
   *  <code>term</code> appears. If this term does not appear in the array,
   *  return -1.
   *  快速找到给定term所对应的排序后的序号
   */
  public int indexOf(String term);


  /** Just like <code>indexOf(int)</code> but searches for a number of terms
   *  at the same time. Returns an array that has the same size as the number
   *  of terms searched for, each slot containing the result of searching for
   *  that term number.
   *
   *  @param terms array containing terms to look for
   *  @param start index in the array where the list of terms starts
   *  @param len the number of terms in the list
   *  快速找到一组集合term,返回每一个term在索引文件中的序号
   */
  public int[] indexesOf(String[] terms, int start, int len);

}
