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

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;

/**
 * Subclass of FilteredTermEnum for enumerating all terms that match the
 * specified wildcard filter term.
 * 如何筛选满足通配符的字符
 * <p>
 * Term enumerations are always ordered by Term.compareTo().  Each term in
 * the enumeration is greater than all that precede it.
 *
 * @version $Id: WildcardTermEnum.java,v 1.8 2004/05/11 17:23:21 otis Exp $
 */
public class WildcardTermEnum extends FilteredTermEnum {
  Term searchTerm;//搜索的关于通配符的term
  String field = "";//搜索的关于通配符的term所在的field
  String text = "";//搜索的关于通配符的term所在的具体的text内容
  
  String pre = "";//截取没有通配符*或者?的前缀
  int preLen = 0;//前缀长度
  boolean fieldMatch = false;
  boolean endEnum = false;

  /**
   * Creates a new <code>WildcardTermEnum</code>.  Passing in a
   * {@link org.apache.lucene.index.Term Term} that does not contain a
   * <code>WILDCARD_CHAR</code> will cause an exception to be thrown.
   */
  public WildcardTermEnum(IndexReader reader, Term term) throws IOException {
    super();
    searchTerm = term;
    field = searchTerm.field();
    text = searchTerm.text();

    int sidx = text.indexOf(WILDCARD_STRING);//*的位置
    int cidx = text.indexOf(WILDCARD_CHAR);//?的位置
    int idx = sidx;//获取第一个*或者?出现的位置
    if (idx == -1) {
      idx = cidx;
    }
    else if (cidx >= 0) {
      idx = Math.min(idx, cidx);
    }

    pre = searchTerm.text().substring(0,idx);//截取没有通配符*或者?的前缀
    preLen = pre.length();
    text = text.substring(preLen);
    setEnum(reader.terms(new Term(searchTerm.field(), pre)));//先定位到前缀term的位置
  }

  //计算参数term是否匹配规则--true表示匹配
  protected final boolean termCompare(Term term) {
    if (field == term.field()) {//field必须相同
      String searchText = term.text();//拿到term的具体指
      if (searchText.startsWith(pre)) {//前缀必须相同
        return wildcardEquals(text, 0, searchText, preLen);//真正去匹配
      }
    }
    endEnum = true;//前缀不同,因此说明term已经遍历结束了
    return false;
  }

  //这种模式匹配的权重都是相同的
  public final float difference() {
    return 1.0f;
  }

  public final boolean endEnum() {
    return endEnum;
  }

  /********************************************
   * String equality with support for wildcards
   ********************************************/

  public static final char WILDCARD_STRING = '*';
  public static final char WILDCARD_CHAR = '?';

  /**
   * Determines if a word mat ches a wildcard pattern.
   * <small>Work released by Granta Design Ltd after originally being done on
   * company time.</small>
   * @param pattern剩余匹配的通配符
   * @param patternIdx 从pattern通配符的第几个位置开始匹配
   * @param string 具体的term
   * @param stringIdx 从string的第几个位置开始匹配
   */
  public static final boolean wildcardEquals(String pattern, int patternIdx,
    String string, int stringIdx)
  {
    for (int p = patternIdx; ; ++p)
    {
      for (int s = stringIdx; ; ++p, ++s)
      {
        // End of string yet?
        boolean sEnd = (s >= string.length());
        // End of pattern yet?
        boolean pEnd = (p >= pattern.length());

        // If we're looking at the end of the string...
        if (sEnd)
        {
          // Assume the only thing left on the pattern is/are wildcards
          boolean justWildcardsLeft = true;

          // Current wildcard position
          int wildcardSearchPos = p;
          // While we haven't found the end of the pattern,
          // and haven't encountered any non-wildcard characters
          while (wildcardSearchPos < pattern.length() && justWildcardsLeft)
          {
            // Check the character at the current position
            char wildchar = pattern.charAt(wildcardSearchPos);
            // If it's not a wildcard character, then there is more
            // pattern information after this/these wildcards.

            if (wildchar != WILDCARD_CHAR && wildchar != WILDCARD_STRING)
            {
              justWildcardsLeft = false;
            }
            else
            {
              // Look at the next character
              wildcardSearchPos++;
            }
          }

          // This was a prefix wildcard search, and we've matched, so
          // return true.
          if (justWildcardsLeft)
          {
            return true;
          }
        }

        // If we've gone past the end of the string, or the pattern,
        // return false.
        if (sEnd || pEnd)
        {
          break;
        }

        // Match a single character, so continue.
        if (pattern.charAt(p) == WILDCARD_CHAR)
        {
          continue;
        }

        //
        if (pattern.charAt(p) == WILDCARD_STRING)
        {
          // Look at the character beyond the '*'.
          ++p;
          // Examine the string, starting at the last character.
          for (int i = string.length(); i >= s; --i)
          {
            if (wildcardEquals(pattern, p, string, i))
            {
              return true;
            }
          }
          break;
        }
        if (pattern.charAt(p) != string.charAt(s))
        {
          break;
        }
      }
      return false;
    }
  }

  public void close() throws IOException
  {
    super.close();
    searchTerm = null;
    field = null;
    text = null;
  }
}
