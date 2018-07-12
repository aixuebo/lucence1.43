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
 * 解析tis或者tii文件---解析每一个term内容
 */

import java.io.IOException;
import org.apache.lucene.store.InputStream;

final class SegmentTermEnum extends TermEnum implements Cloneable {
  private InputStream input;
  FieldInfos fieldInfos;
  long size;//总term数量
  long position = -1;//当前迭代到哪个term上了

  private Term term = new Term("", "");//该词的内容
  private TermInfo termInfo = new TermInfo();//该词的统计维度信息

  private int format;
  private boolean isIndex = false;//是否是索引文件
  long indexPointer = 0;//索引tii文件要获取tis的文件位置
  int indexInterval;
  int skipInterval;
  
  private int formatM1SkipInterval;
  
  Term prev;//前一个term词

  private char[] buffer = {};

  //初始化
  SegmentTermEnum(InputStream i, FieldInfos fis, boolean isi)
          throws IOException {
    input = i;
    fieldInfos = fis;
    isIndex = isi;//是否是tii索引文件

    int firstInt = input.readInt();//版本号version
    if (firstInt >= 0) {
      // original-format file, without explicit format version number
      format = 0;
      size = firstInt;

      // back-compatible settings
      indexInterval = 128;
      skipInterval = Integer.MAX_VALUE; // switch off skipTo optimization

    } else {
      // we have a format version number
      format = firstInt;

      // check that it is a format we can understand
      if (format < TermInfosWriter.FORMAT)
        throw new IOException("Unknown format version:" + format);

      size = input.readLong();                    // read the size
      
      if(format == -1){
        if (!isIndex) {
          indexInterval = input.readInt();
          formatM1SkipInterval = input.readInt();
        }
        // switch off skipTo optimization for file format prior to 1.4rc2 in order to avoid a bug in 
        // skipTo implementation of these versions
        skipInterval = Integer.MAX_VALUE;
      }
      else{
        indexInterval = input.readInt();
        skipInterval = input.readInt();
      }
    }

  }

  protected Object clone() {
    SegmentTermEnum clone = null;
    try {
      clone = (SegmentTermEnum) super.clone();
    } catch (CloneNotSupportedException e) {}

    clone.input = (InputStream) input.clone();
    clone.termInfo = new TermInfo(termInfo);
    if (term != null) clone.growBuffer(term.text.length());

    return clone;
  }

  final void seek(long pointer, int p, Term t, TermInfo ti)
          throws IOException {
    input.seek(pointer);
    position = p;
    term = t;
    prev = null;
    termInfo.set(ti);
    growBuffer(term.text.length());		  // copy term text into buffer
  }

  /** Increments the enumeration to the next element.  True if one exists
   * 迭代每一个term词
  */
  public final boolean next() throws IOException {
    if (position++ >= size - 1) {//说明已经没有term可以被迭代了
      term = null;
      return false;
    }

    prev = term;//获取前一个term
    term = readTerm();//获取当前term

    termInfo.docFreq = input.readVInt();	  // read doc freq 该term出现在多少个document中
    termInfo.freqPointer += input.readVLong();	  // read freq pointer 该term词频文件的位置
    termInfo.proxPointer += input.readVLong();	  // read prox pointer  该tem词位置文件的位置
    
    if(format == -1){
    //  just read skipOffset in order to increment  file pointer; 
    // value is never used since skipTo is switched off
      if (!isIndex) {
        if (termInfo.docFreq > formatM1SkipInterval) {
          termInfo.skipOffset = input.readVInt(); 
        }
      }
    }
    else{
      if (termInfo.docFreq >= skipInterval) 
        termInfo.skipOffset = input.readVInt();//该值暂时没看到实际用的地方,因此暂时不了解
    }
    
    if (isIndex)
      indexPointer += input.readVLong();	  // read index pointer  索引tii文件要获取tis的文件位置

    return true;
  }

  //读取下一个term的具体内容
  private final Term readTerm() throws IOException {
    int start = input.readVInt();
    int length = input.readVInt();
    int totalLength = start + length;//该term总字节
    if (buffer.length < totalLength)
      growBuffer(totalLength);

    input.readChars(buffer, start, length);//读取term剩余字节到buffer中
    return new Term(fieldInfos.fieldName(input.readVInt()),
            new String(buffer, 0, totalLength), false);//获取该term的field以及词内容
  }

  private final void growBuffer(int length) {
    buffer = new char[length];
    for (int i = 0; i < term.text.length(); i++)  // copy contents
      buffer[i] = term.text.charAt(i);
  }

  /** Returns the current Term in the enumeration.
   Initially invalid, valid after next() called for the first time.*/
  public final Term term() {
    return term;
  }

  /** Returns the current TermInfo in the enumeration.
   Initially invalid, valid after next() called for the first time.*/
  final TermInfo termInfo() {
    return new TermInfo(termInfo);
  }

  /** Sets the argument to the current TermInfo in the enumeration.
   Initially invalid, valid after next() called for the first time.*/
  final void termInfo(TermInfo ti) {
    ti.set(termInfo);
  }

  /** Returns the docFreq from the current TermInfo in the enumeration.
   Initially invalid, valid after next() called for the first time.*/
  public final int docFreq() {
    return termInfo.docFreq;
  }

  /* Returns the freqPointer from the current TermInfo in the enumeration.
    Initially invalid, valid after next() called for the first time.*/
  final long freqPointer() {
    return termInfo.freqPointer;
  }

  /* Returns the proxPointer from the current TermInfo in the enumeration.
    Initially invalid, valid after next() called for the first time.*/
  final long proxPointer() {
    return termInfo.proxPointer;
  }

  /** Closes the enumeration to further activity, freeing resources. */
  public final void close() throws IOException {
    input.close();
  }
}
