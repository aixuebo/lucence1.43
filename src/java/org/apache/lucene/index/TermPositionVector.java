package org.apache.lucene.index;

/** Extends <code>TermFreqVector</code> to provide additional information about
 *  positions in which each of the terms is found.
 */
public interface TermPositionVector extends TermFreqVector {

    /** Returns an array of positions in which the term is found.
     *  Terms are identified by the index at which its number appears in the
     *  term number array obtained from <code>getTermNumbers</code> method.
     *  如果一个词term存在索引中,也可以找到该term对应的序号,则返回该term的词频对应的所有词的位置
     */
    public int[] getTermPositions(int index);
}