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
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermDocs;
import org.apache.lucene.index.TermEnum;

import java.io.IOException;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.HashMap;

/**
 * Expert: The default cache implementation, storing all values in memory.
 * A WeakHashMap is used for storage.
 *
 * <p>Created: May 19, 2004 4:40:36 PM
 *
 * @author  Tim Jones (Nacimiento Software)
 * @since   lucene 1.4
 * @version $Id: FieldCacheImpl.java,v 1.3.2.1 2004/09/30 19:10:26 dnaber Exp $
 */
class FieldCacheImpl
implements FieldCache {

  /** Expert: Every key in the internal cache is of this type. */
  static class Entry {
    final String field;        // which Field 对某个field进行缓存
    final int type;            // which SortField type
    final Object custom;       // which custom comparator  可能是自定义的field内容比较器

    /** Creates one of these objects. */
    Entry (String field, int type) {
      this.field = field.intern();
      this.type = type;
      this.custom = null;
    }

    /** Creates one of these objects for a custom comparator. */
    Entry (String field, Object custom) {
      this.field = field.intern();
      this.type = SortField.CUSTOM;
      this.custom = custom;
    }

    /** Two of these are equal iff they reference the same field and type. */
    public boolean equals (Object o) {
      if (o instanceof Entry) {
        Entry other = (Entry) o;
        if (other.field == field && other.type == type) {
          if (other.custom == null) {
            if (custom == null) return true;
          } else if (other.custom.equals (custom)) {
            return true;
          }
        }
      }
      return false;
    }

    /** Composes a hashcode based on the field and type. */
    public int hashCode() {
      return field.hashCode() ^ type ^ (custom==null ? 0 : custom.hashCode());
    }
  }


  /** The internal cache. Maps Entry to array of interpreted term values. 
   * key是Reader索引,value是map,map的key是Entry,value是具体的值 
   ***/
  final Map cache = new WeakHashMap();

  /** See if an object is in the cache. */
  Object lookup (IndexReader reader, String field, int type) {
    Entry entry = new Entry (field, type);
    synchronized (this) {
      HashMap readerCache = (HashMap)cache.get(reader);//先找到reader对应的map
      if (readerCache == null) return null;
      return readerCache.get (entry);//在map中查找对应的缓存值
    }
  }

  /** See if a custom object is in the cache. 定义该field需要一个自定义比较器*/
  Object lookup (IndexReader reader, String field, Object comparer) {
    Entry entry = new Entry (field, comparer);
    synchronized (this) {
      HashMap readerCache = (HashMap)cache.get(reader);
      if (readerCache == null) return null;
      return readerCache.get (entry);
    }
  }

  /** Put an object into the cache. 存储数据*/
  Object store (IndexReader reader, String field, int type, Object value) {
    Entry entry = new Entry (field, type);
    synchronized (this) {
      HashMap readerCache = (HashMap)cache.get(reader);
      if (readerCache == null) {
        readerCache = new HashMap();
        cache.put(reader,readerCache);
      }
      return readerCache.put (entry, value);
    }
  }

  /** Put a custom object into the cache. */
  Object store (IndexReader reader, String field, Object comparer, Object value) {
    Entry entry = new Entry (field, comparer);
    synchronized (this) {
      HashMap readerCache = (HashMap)cache.get(reader);
      if (readerCache == null) {
        readerCache = new HashMap();
        cache.put(reader, readerCache);
      }
      return readerCache.put (entry, value);
    }
  }

  // inherit javadocs
  public int[] getInts (IndexReader reader, String field)
  throws IOException {
    field = field.intern();
    Object ret = lookup (reader, field, SortField.INT);
    if (ret == null) {
      final int[] retArray = new int[reader.maxDoc()];
      if (retArray.length > 0) {
        TermDocs termDocs = reader.termDocs();
        TermEnum termEnum = reader.terms (new Term (field, ""));
        try {
          if (termEnum.term() == null) {
            throw new RuntimeException ("no terms in field " + field);
          }
          do {
            Term term = termEnum.term();
            if (term.field() != field) break;
            int termval = Integer.parseInt (term.text());
            termDocs.seek (termEnum);
            while (termDocs.next()) {
              retArray[termDocs.doc()] = termval;
            }
          } while (termEnum.next());
        } finally {
          termDocs.close();
          termEnum.close();
        }
      }
      store (reader, field, SortField.INT, retArray);
      return retArray;
    }
    return (int[]) ret;
  }

  // inherit javadocs
  public float[] getFloats (IndexReader reader, String field)
  throws IOException {
    field = field.intern();
    Object ret = lookup (reader, field, SortField.FLOAT);
    if (ret == null) {
      final float[] retArray = new float[reader.maxDoc()];
      if (retArray.length > 0) {
        TermDocs termDocs = reader.termDocs();
        TermEnum termEnum = reader.terms (new Term (field, ""));
        try {
          if (termEnum.term() == null) {
            throw new RuntimeException ("no terms in field " + field);
          }
          do {
            Term term = termEnum.term();
            if (term.field() != field) break;
            float termval = Float.parseFloat (term.text());
            termDocs.seek (termEnum);
            while (termDocs.next()) {
              retArray[termDocs.doc()] = termval;
            }
          } while (termEnum.next());
        } finally {
          termDocs.close();
          termEnum.close();
        }
      }
      store (reader, field, SortField.FLOAT, retArray);
      return retArray;
    }
    return (float[]) ret;
  }

  // inherit javadocs  因为每一个doc文档的field上是字符串,而相同字符串内容的又以doc集合形式连接在一起
  //因此先获取字符串内容---然后获取相同字符串的docid,将docid的位置在数组中添加内容，然后获取下一个字符串以此类推
  //数组返回值每一个元素表示doc的具体的值
  public String[] getStrings (IndexReader reader, String field)
  throws IOException {
    field = field.intern();
    Object ret = lookup (reader, field, SortField.STRING);
    if (ret == null) {
      final String[] retArray = new String[reader.maxDoc()];//每一个文档占用一个数组位置
      if (retArray.length > 0) {
        TermDocs termDocs = reader.termDocs();
        TermEnum termEnum = reader.terms (new Term (field, ""));//定位到该field第一个字符
        try {
          if (termEnum.term() == null) {
            throw new RuntimeException ("no terms in field " + field);
          }
          do {
            Term term = termEnum.term();//该field内的第一个term
            if (term.field() != field) break;
            String termval = term.text();//具体的term值
            termDocs.seek (termEnum);
            while (termDocs.next()) {//获取该term所在的下一个doc,即相同doc中内容相同的field value
              retArray[termDocs.doc()] = termval;//每一个doc的内容
            }
          } while (termEnum.next());
        } finally {
          termDocs.close();
          termEnum.close();
        }
      }
      store (reader, field, SortField.STRING, retArray);
      return retArray;
    }
    return (String[]) ret;
  }

  // inherit javadocs  比上面的方法省空间，因为相同的词出现n次,不应该被存储多次，而存储一次即可,两个数组互相作用即可
  public StringIndex getStringIndex (IndexReader reader, String field)
  throws IOException {
    field = field.intern();
    Object ret = lookup (reader, field, STRING_INDEX);
    if (ret == null) {
      final int[] retArray = new int[reader.maxDoc()];//存储每一个doc文档值所在term的序号
      String[] mterms = new String[reader.maxDoc()+1];//通过term是第几个词,可以在该数字内查找到具体的内容
      if (retArray.length > 0) {
        TermDocs termDocs = reader.termDocs();
        TermEnum termEnum = reader.terms (new Term (field, ""));
        int t = 0;  // current term number

        // an entry for documents that have no terms in this field
        // should a document with no terms be at top or bottom?
        // this puts them at the top - if it is changed, FieldDocSortedHitQueue
        // needs to change as well.
        mterms[t++] = null;

        try {
          if (termEnum.term() == null) {
            throw new RuntimeException ("no terms in field " + field);
          }
          do {
            Term term = termEnum.term();
            if (term.field() != field) break;

            // store term text
            // we expect that there is at most one term per document
            if (t >= mterms.length) throw new RuntimeException ("there are more terms than documents in field \"" + field + "\"");
            mterms[t] = term.text();//存储具体的内容

            termDocs.seek (termEnum);
            while (termDocs.next()) {//因为所有doc都持有相同的term值
              retArray[termDocs.doc()] = t;//相同的term都有相同的序号--即term的排序
            }

            t++;
          } while (termEnum.next());//获取下一个term
        } finally {
          termDocs.close();
          termEnum.close();
        }

        if (t == 0) {
          // if there are no terms, make the term array
          // have a single null entry
          mterms = new String[1];
        } else if (t < mterms.length) {
          // if there are less terms than documents,
          // trim off the dead array space
          String[] terms = new String[t];
          System.arraycopy (mterms, 0, terms, 0, t);
          mterms = terms;
        }
      }
      StringIndex value = new StringIndex (retArray, mterms);
      store (reader, field, STRING_INDEX, value);
      return value;
    }
    return (StringIndex) ret;
  }

  /** The pattern used to detect integer values in a field */
  /** removed for java 1.3 compatibility
   protected static final Pattern pIntegers = Pattern.compile ("[0-9\\-]+");
   **/

  /** The pattern used to detect float values in a field */
  /**
   * removed for java 1.3 compatibility
   * protected static final Object pFloats = Pattern.compile ("[0-9+\\-\\.eEfFdD]+");
   */

  // inherit javadocs
  public Object getAuto (IndexReader reader, String field)
  throws IOException {
    field = field.intern();
    Object ret = lookup (reader, field, SortField.AUTO);
    if (ret == null) {
      TermEnum enumerator = reader.terms (new Term (field, ""));
      try {
        Term term = enumerator.term();//获取该term的第一个词
        if (term == null) {
          throw new RuntimeException ("no terms in field " + field + " - cannot determine sort type");
        }
        if (term.field() == field) {
          String termtext = term.text().trim();//判断第一个词的类型是什么值

          /**
           * Java 1.4 level code:

           if (pIntegers.matcher(termtext).matches())
           return IntegerSortedHitQueue.comparator (reader, enumerator, field);

           else if (pFloats.matcher(termtext).matches())
           return FloatSortedHitQueue.comparator (reader, enumerator, field);
           */

          // Java 1.3 level code:
          try {
            Integer.parseInt (termtext);
            ret = getInts (reader, field);
          } catch (NumberFormatException nfe1) {
            try {
              Float.parseFloat (termtext);
              ret = getFloats (reader, field);
            } catch (NumberFormatException nfe2) {
              ret = getStringIndex (reader, field);
            }
          }
          if (ret != null) {
            store (reader, field, SortField.AUTO, ret);//存储自动的索引为具体的比较类型
          }
        } else {
          throw new RuntimeException ("field \"" + field + "\" does not appear to be indexed");
        }
      } finally {
        enumerator.close();
      }

    }
    return ret;
  }

  // inherit javadocs  自定义一个比较工厂
  public Comparable[] getCustom (IndexReader reader, String field, SortComparator comparator)
  throws IOException {
    field = field.intern();
    Object ret = lookup (reader, field, comparator);
    if (ret == null) {//创建一个比较工厂
      final Comparable[] retArray = new Comparable[reader.maxDoc()];//每一个doc的比较工厂是不相同的
      if (retArray.length > 0) {
        TermDocs termDocs = reader.termDocs();
        TermEnum termEnum = reader.terms (new Term (field, ""));//先定位到该field的第一个term词
        try {
          if (termEnum.term() == null) {
            throw new RuntimeException ("no terms in field " + field);
          }
          do {
            Term term = termEnum.term();
            if (term.field() != field) break;//找到该field最后一个term后退出
            Comparable termval = comparator.getComparable (term.text());//找到该值对应的比较器
            termDocs.seek (termEnum);
            while (termDocs.next()) {//找到该term的下一个文档的词频、docid等信息
              retArray[termDocs.doc()] = termval;//存储每一个值对应的比较器
            }
          } while (termEnum.next());//不断迭代该field中的term
        } finally {
          termDocs.close();
          termEnum.close();
        }
      }
      store (reader, field, SortField.CUSTOM, retArray);
      return retArray;
    }
    return (Comparable[]) ret;
  }

}

