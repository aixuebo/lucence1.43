package org.apache.lucene.index;

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

import org.apache.lucene.store.Directory;

//记录一个段落,里面有若干个doc文档集合的汇总
final class SegmentInfo {
  public String name;				  // unique name in dir
  public int docCount;				  // number of docs in seg  有多少个文档在这个段里面
  public Directory dir;				  // where segment resides

  public SegmentInfo(String name, int docCount, Directory dir) {
    this.name = name;
    this.docCount = docCount;
    this.dir = dir;
  }
}
