package org.apache.lucene.index;
import java.util.*;

/**
 * 该对象表示一个document文档中每一个field中所有term的信息，快速找到一个field中的term
 */
class SegmentTermVector implements TermFreqVector {
  private String field;
  private String terms[];//该field中所有的term词---term已经排序好了
  private int termFreqs[];//每一个term词对应的词频
  
  SegmentTermVector(String field, String terms[], int termFreqs[]) {
    this.field = field;
    this.terms = terms;
    this.termFreqs = termFreqs;
  }

  /**
   * 
   * @return The number of the field this vector is associated with
   */
  public String getField() {
    return field;
  }

  //field:text1/5,text2/4
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append('{');
    sb.append(field).append(": ");
    for (int i=0; i<terms.length; i++) {
      if (i>0) sb.append(", ");
      sb.append(terms[i]).append('/').append(termFreqs[i]);
    }
    sb.append('}');
    return sb.toString();
  }

  public int size() {
    return terms == null ? 0 : terms.length;
  }

  public String [] getTerms() {
    return terms;
  }

  public int[] getTermFrequencies() {
    return termFreqs;
  }

  //二分法定位一个term
  public int indexOf(String termText) {
    int res = Arrays.binarySearch(terms, termText);
    return res >= 0 ? res : -1;
  }

  //查号数组中的term集合,返回该term在索引中term的序号----从数组的第ster位置开始查找
  public int[] indexesOf(String [] termNumbers, int start, int len) {
    // TODO: there must be a more efficient way of doing this.
    //       At least, we could advance the lower bound of the terms array
    //       as we find valid indexes. Also, it might be possible to leverage
    //       this even more by starting in the middle of the termNumbers array
    //       and thus dividing the terms array maybe in half with each found index.
    int res[] = new int[len];

    for (int i=0; i < len; i++) {//应该从start开始,但是她从0开始，说明start没有意义
      res[i] = indexOf(termNumbers[i]);//找到每一个term在索引中的term序号
    }
    return res;
  }
}
