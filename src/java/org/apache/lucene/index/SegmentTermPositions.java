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

import java.io.IOException;

import org.apache.lucene.store.InputStream;

final class SegmentTermPositions
extends SegmentTermDocs implements TermPositions {
  private InputStream proxStream;//位置文件对象
  private int proxCount;//为一个document中的词频
  private int position;//记录每一个term在该document中的词位置
  
  SegmentTermPositions(SegmentReader p) throws IOException {
    super(p);
    this.proxStream = (InputStream)parent.proxStream.clone();
  }

  final void seek(TermInfo ti) throws IOException {
    super.seek(ti);
    if (ti != null)
      proxStream.seek(ti.proxPointer);//定位到该term对应的位置
    proxCount = 0;
  }

  public final void close() throws IOException {
    super.close();
    proxStream.close();
  }

  public final int nextPosition() throws IOException {
    proxCount--;//减少一个词
    return position += proxStream.readVInt();//获取该term出现的位置
  }

  //跳过该document的所有词频
  protected final void skippingDoc() throws IOException {
    for (int f = freq; f > 0; f--)		  // skip all positions
      proxStream.readVInt();
  }

  //还下一个词
  public final boolean next() throws IOException {
    for (int f = proxCount; f > 0; f--)		  // skip unread positions
      proxStream.readVInt();//如果还有词频，则都让他的位置过滤掉

    if (super.next()) {				  // run super 换到下一个document
      proxCount = freq;				  // note frequency 获取词频
      position = 0;				  // reset position 记录出现的位置
      return true;
    }
    return false;
  }

  public final int read(final int[] docs, final int[] freqs)
      throws IOException {
    throw new UnsupportedOperationException("TermPositions does not support processing multiple documents in one call. Use TermDocs instead.");
  }


  /** Called by super.skipTo(). 跳跃到某一个新的term上*/
  protected void skipProx(long proxPointer) throws IOException {
    proxStream.seek(proxPointer);
    proxCount = 0;
  }

}
