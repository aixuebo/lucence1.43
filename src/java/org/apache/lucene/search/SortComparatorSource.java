package org.apache.lucene.search;

import org.apache.lucene.index.IndexReader;
import java.io.IOException;
import java.io.Serializable;

/**
 * Expert: returns a comparator for sorting ScoreDocs.
 *
 * <p>Created: Apr 21, 2004 3:49:28 PM
 * 
 * @author  Tim Jones
 * @version $Id: SortComparatorSource.java,v 1.2 2004/05/19 23:05:27 tjones Exp $
 * @since   1.4
 * 自定义排序工厂,如何读一个field进行排序
 */
public interface SortComparatorSource
extends Serializable {

  /**
   * Creates a comparator for the field in the given index.
   * @param reader Index to create comparator for.
   * @param fieldname  Field to create comparator for.
   * @return Comparator of ScoreDoc objects.
   * @throws IOException If an error occurs reading the index.
   * 如果给field创建一个排序对象,该对象可以让field的内容进行排序
   */
  ScoreDocComparator newComparator (IndexReader reader, String fieldname)
  throws IOException;
}