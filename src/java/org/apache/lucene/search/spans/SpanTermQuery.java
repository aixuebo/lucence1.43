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

import java.util.Collection;
import java.util.ArrayList;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermPositions;

/** Matches spans containing a term. 
 * 基于词的一个跨度匹配 
 * 
 * 这个查询如果单独使用，效果跟term查询差不多
 * 区别在于
 * 1.termQuery的词频是真实的词出现多少次，而SpanTermQuery计算的词频是sloppy方式计算出来的,比termQuery真实的词频要小一些
 * 2.增加了查询结果中单词的距离信息,可以通过词的位置,进行高亮展示,其实记录词的位置也是可以高亮的,这个不是最主要的功能(我个人觉得)
 * 
 * 因为lucene匹配的是以term为基准的,而我不想以term为基准,而是想扩大范围,多个term为一组,类似于短语,但是还可以不代表短语，
 * 那么就定义一个名词为span,Spans spans = query.getSpans(reader);   可以通过spans的next方法依次找到span单位代替term
 **/
public class SpanTermQuery extends SpanQuery {
  private Term term;

  /** Construct a SpanTermQuery matching the named term's spans. */
  public SpanTermQuery(Term term) { this.term = term; }

  /** Return the term whose spans are matched. */
  public Term getTerm() { return term; }

  public String getField() { return term.field(); }

  public Collection getTerms() {
    Collection terms = new ArrayList();
    terms.add(term);
    return terms;
  }

  //如果field相同,则直接输出text内容即可,否则输出field+text
  public String toString(String field) {
    if (term.field().equals(field))
      return term.text();
    else
      return term.toString();
  }

  //如何每次读取到一个span---即span不再是一个term了,而是一个范围
  public Spans getSpans(final IndexReader reader) throws IOException {
    return new Spans() {
        private TermPositions positions = reader.termPositions(term);//nextPosition返回在该doc中每一个term出现的位置,next返回下一个doc以及词频

        private int doc = -1;//当前docid
        private int freq;//当前doc内该term的词频
        private int count;//当前doc内已经处理了多少个term了
        private int position;//当前文档下一个词的位置

        public boolean next() throws IOException {
          if (count == freq) {
            if (!positions.next()) {//切换下一个doc的id以及词频
              doc = Integer.MAX_VALUE;
              return false;
            }
            //获取下一个doc的词频以及id
            doc = positions.doc();
            freq = positions.freq();
            count = 0;
          }
          position = positions.nextPosition();//下一个term的位置
          count++;//记录已经处理了一个term,最多不超过doc中term的词频
          return true;
        }

        public boolean skipTo(int target) throws IOException {
          if (!positions.skipTo(target)) {//跳跃到某一个doc下
            doc = Integer.MAX_VALUE;
            return false;
          }

          doc = positions.doc();
          freq = positions.freq();
          count = 0;

          position = positions.nextPosition();
          count++;

          return true;
        }

        public int doc() { return doc; }
        public int start() { return position; }
        public int end() { return position + 1; }

        //正常输出spans(SpanTermQuery)@doc-position,即表示当前是处理哪个doc的哪个位置
        public String toString() {
          return "spans(" + SpanTermQuery.this.toString() + ")@"+
            (doc==-1?"START":(doc==Integer.MAX_VALUE)?"END":doc+"-"+position);
        }

      };
  }

}
