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

import java.io.Serializable;
import java.util.Locale;

/**
 * Stores information about how to sort documents by terms in an individual
 * field.  Fields must be indexed in order to sort by them.
 *
 * <p>Created: Feb 11, 2004 1:25:29 PM
 *
 * @author  Tim Jones (Nacimiento Software)
 * @since   lucene 1.4
 * @version $Id: SortField.java,v 1.9 2004/05/24 22:51:42 tjones Exp $
 * @see Sort
 * 表示在一个field属性中,如何排序
 */
public class SortField
implements Serializable {

  /** Sort by document score (relevancy).  Sort values are Float and higher
   * values are at the front.
   * 按照得分排序 
   **/
  public static final int SCORE = 0;

  /** Sort by document number (index order).  Sort values are Integer and lower
   * values are at the front. 
   * 按照docid排序 
   **/
  public static final int DOC = 1;

  /** Guess type of sort based on field contents.  A regular expression is used
   * to look at the first term indexed for the field and determine if it
   * represents an integer number, a floating point number, or just arbitrary
   * string characters. 
   * 基于field的内容来猜测排序方式
   * 通常看第一个term,如果他是数字类型的,或者是字符串类型的 
   **/
  public static final int AUTO = 2;

  /** Sort using term values as Strings.  Sort values are String and lower
   * values are at the front. */
  public static final int STRING = 3;

  /** Sort using term values as encoded Integers.  Sort values are Integer and
   * lower values are at the front. */
  public static final int INT = 4;

  /** Sort using term values as encoded Floats.  Sort values are Float and
   * lower values are at the front. */
  public static final int FLOAT = 5;

  /** Sort using a custom Comparator.  Sort values are any Comparable and
   * sorting is done according to natural order. 
   * 用户自定义排序规则 
   **/
  public static final int CUSTOM = 9;

  // IMPLEMENTATION NOTE: the FieldCache.STRING_INDEX is in the same "namespace"
  // as the above static int values.  Any new values must not have the same value
  // as FieldCache.STRING_INDEX.


  /** Represents sorting by document score (relevancy). */
  public static final SortField FIELD_SCORE = new SortField (null, SCORE);

  /** Represents sorting by document number (index order). */
  public static final SortField FIELD_DOC = new SortField (null, DOC);


  private String field;//属性
  private int type = AUTO;  // defaults to determining type dynamically  默认是自动根据term选择排序方式
  private Locale locale;    // defaults to "natural order" (no Locale)
  boolean reverse = false;  // defaults to natural order 倒排还是正序
  private SortComparatorSource factory;//如何排序---如果排序类型是自定义的时候,必须有该属性存在,去设计如何对field排序

  /** Creates a sort by terms in the given field where the type of term value
   * is determined dynamically ({@link #AUTO AUTO}).
   * @param field Name of field to sort by, cannot be <code>null</code>.
   */
  public SortField (String field) {
    this.field = field.intern();
  }

  /** Creates a sort, possibly in reverse, by terms in the given field where
   * the type of term value is determined dynamically ({@link #AUTO AUTO}).
   * @param field Name of field to sort by, cannot be <code>null</code>.
   * @param reverse True if natural order should be reversed.
   */
  public SortField (String field, boolean reverse) {
    this.field = field.intern();
    this.reverse = reverse;
  }

  /** Creates a sort by terms in the given field with the type of term
   * values explicitly given.
   * @param field  Name of field to sort by.  Can be <code>null</code> if
   *               <code>type</code> is SCORE or DOC.
   * @param type   Type of values in the terms.
   */
  public SortField (String field, int type) {
    this.field = (field != null) ? field.intern() : field;
    this.type = type;
  }

  /** Creates a sort, possibly in reverse, by terms in the given field with the
   * type of term values explicitly given.
   * @param field  Name of field to sort by.  Can be <code>null</code> if
   *               <code>type</code> is SCORE or DOC.
   * @param type   Type of values in the terms.
   * @param reverse True if natural order should be reversed.
   */
  public SortField (String field, int type, boolean reverse) {
    this.field = (field != null) ? field.intern() : field;
    this.type = type;
    this.reverse = reverse;
  }

  /** Creates a sort by terms in the given field sorted
   * according to the given locale.
   * @param field  Name of field to sort by, cannot be <code>null</code>.
   * @param locale Locale of values in the field.
   */
  public SortField (String field, Locale locale) {
    this.field = field.intern();
    this.type = STRING;
    this.locale = locale;
  }

  /** Creates a sort, possibly in reverse, by terms in the given field sorted
   * according to the given locale.
   * @param field  Name of field to sort by, cannot be <code>null</code>.
   * @param locale Locale of values in the field.
   */
  public SortField (String field, Locale locale, boolean reverse) {
    this.field = field.intern();
    this.type = STRING;
    this.locale = locale;
    this.reverse = reverse;
  }

  /** Creates a sort with a custom comparison function.
   * @param field Name of field to sort by; cannot be <code>null</code>.
   * @param comparator Returns a comparator for sorting hits.
   */
  public SortField (String field, SortComparatorSource comparator) {
    this.field = (field != null) ? field.intern() : field;
    this.type = CUSTOM;
    this.factory = comparator;
  }

  /** Creates a sort, possibly in reverse, with a custom comparison function.
   * @param field Name of field to sort by; cannot be <code>null</code>.
   * @param comparator Returns a comparator for sorting hits.
   * @param reverse True if natural order should be reversed.
   */
  public SortField (String field, SortComparatorSource comparator, boolean reverse) {
    this.field = (field != null) ? field.intern() : field;
    this.type = CUSTOM;
    this.reverse = reverse;
    this.factory = comparator;
  }

  /** Returns the name of the field.  Could return <code>null</code>
   * if the sort is by SCORE or DOC.
   * @return Name of field, possibly <code>null</code>.
   */
  public String getField() {
    return field;
  }

  /** Returns the type of contents in the field.
   * @return One of the constants SCORE, DOC, AUTO, STRING, INT or FLOAT.
   */
  public int getType() {
    return type;
  }

  /** Returns the Locale by which term values are interpreted.
   * May return <code>null</code> if no Locale was specified.
   * @return Locale, or <code>null</code>.
   */
  public Locale getLocale() {
    return locale;
  }

  /** Returns whether the sort should be reversed.
   * @return  True if natural order should be reversed.
   */
  public boolean getReverse() {
    return reverse;
  }

  public SortComparatorSource getFactory() {
    return factory;
  }

  public String toString() {
    StringBuffer buffer = new StringBuffer();
    switch (type) {
      case SCORE: buffer.append("<score>");
                  break;

      case DOC: buffer.append("<doc>");
                break;

      case CUSTOM: buffer.append ("<custom:\"" + field + "\": "
                                               + factory + ">"); //输出自定义排序的field,以及如何排序
                break;

      default: buffer.append("\"" + field + "\"");
               break;
    }

    if (locale != null) buffer.append ("("+locale+")");
    if (reverse) buffer.append('!');

    return buffer.toString();
  }
}
