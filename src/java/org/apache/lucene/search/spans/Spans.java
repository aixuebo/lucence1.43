package org.apache.lucene.search.spans;

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

/** Expert: an enumeration of span matches.  Used to implement span searching.
 * Each span represents a range of term positions within a document.  Matches
 * are enumerated in order, by increasing document number, within that by
 * increasing start position and finally by increasing end position. 
 * 一个枚举span匹配的对象,被适用于进行span搜索。 
 * 每一个span待办一个term的位置范围
 * 匹配是按照顺序进行枚举的,通过以此增加docid、同一个docid内需要增加开始位置和结束位置来获取一定的span范围
 * 
 * span表示跨度
 **/
public interface Spans {
  /** Move to the next match, returning true iff any such exists.移动到下一个匹配的位置 */
  boolean next() throws IOException;

  /** Skips to the first match beyond the current, whose document number is
   * greater than or equal to <i>target</i>. <p>Returns true iff there is such
   * a match.  <p>Behaves as if written: <pre>
   *   boolean skipTo(int target) {
   *     do {
   *       if (!next())
   * 	     return false;
   *     } while (target > doc());
   *     return true;
   *   }
   * </pre>
   * Most implementations are considerably more efficient than that.
   * 跳跃到某一个doc下--效率非常高
   */
  boolean skipTo(int target) throws IOException;

  /** Returns the document number of the current match.  Initially invalid. 返回当前匹配的doc*/
  int doc();

  /** Returns the start position of the current match.  Initially invalid. 返回当前匹配的doc下的开始位置*/
  int start();

  /** Returns the end position of the current match.  Initially invalid. 返回当前匹配的doc下的结束位置*/
  int end();

}
