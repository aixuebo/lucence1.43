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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Token;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.index.TermFreqVector;

import java.io.IOException;
import java.io.StringReader;
import java.util.*;

/**
 *
 *
 **/
public class QueryTermVector implements TermFreqVector {
  private String [] terms = new String[0];//每一个term组成的集合
  private int [] termFreqs = new int[0];//每一个term出现的词频

  public String getField() { return null;  }

  /**
   * 参数是一组term,该term是可以重复出现的,主要计算这些term中有哪些不同的元素,以及每一个元素所占的词频
   * @param queryTerms The original list of terms from the query, can contain duplicates
   */ 
  public QueryTermVector(String [] queryTerms) {

    processTerms(queryTerms);
  }

  public QueryTermVector(String queryString, Analyzer analyzer) {
    if (analyzer != null)
    {
      TokenStream stream = analyzer.tokenStream("", new StringReader(queryString));//对查询关键字分词
      if (stream != null)
      {
        Token next = null;
        List terms = new ArrayList();
        try {
          while ((next = stream.next()) != null) //不断的获取分词结果
          {
            terms.add(next.termText());//不断的将分词结果加入到集合中--该集合可以允许重复
          }
          processTerms((String[])terms.toArray(new String[terms.size()]));//将获取的结果进行term过滤重复 以及计算出现词频处理
        } catch (IOException e) {
        }
      }
    }                                                              
  }
  
  //处理所有的term分词
  private void processTerms(String[] queryTerms) {
    if (queryTerms != null) {
      Arrays.sort(queryTerms);//对term分词进行先排序处理
      Map tmpSet = new HashMap(queryTerms.length);//<term,该term在集合的第几个位置存放>组成的元祖
      //filter out duplicates
      List tmpList = new ArrayList(queryTerms.length);//每一个term组成的集合
      List tmpFreqs = new ArrayList(queryTerms.length);//每一个term对应的词频
      int j = 0;
      for (int i = 0; i < queryTerms.length; i++) {
        String term = queryTerms[i];
        Integer position = (Integer)tmpSet.get(term);
        if (position == null) {//说明该term第一次出现
          tmpSet.put(term, new Integer(j++));
          tmpList.add(term);
          tmpFreqs.add(new Integer(1));//该term出现的词频
        }       
        else {//说明该term以前添加过
          Integer integer = (Integer)tmpFreqs.get(position.intValue());//获取该term在集合的下标
          tmpFreqs.set(position.intValue(), new Integer(integer.intValue() + 1));  //设置该term对应出现的词频        
        }
      }
      terms = (String[])tmpList.toArray(terms);
      //termFreqs = (int[])tmpFreqs.toArray(termFreqs);
      termFreqs = new int[tmpFreqs.size()];
      int i = 0;
      for (Iterator iter = tmpFreqs.iterator(); iter.hasNext();) {
        Integer integer = (Integer) iter.next();
        termFreqs[i++] = integer.intValue();//记录每一个term出现的词频
      }
    }
  }
  
  //输出每一个term对应的词频
  public final String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append('{');
        for (int i=0; i<terms.length; i++) {
            if (i>0) sb.append(", ");
            sb.append(terms[i]).append('/').append(termFreqs[i]);
        }
        sb.append('}');
        return sb.toString();
    }
  

  //多少个term
  public int size() {
    return terms.length;
  }

  //所有term
  public String[] getTerms() {
    return terms;
  }

  //所有term对应的词频
  public int[] getTermFrequencies() {
    return termFreqs;
  }

  //查找term对应的下标
  public int indexOf(String term) {
    int res = Arrays.binarySearch(terms, term);
        return res >= 0 ? res : -1;
  }

  public int[] indexesOf(String[] terms, int start, int len) {
    int res[] = new int[len];

    for (int i=0; i < len; i++) {
        res[i] = indexOf(terms[i]);
    }
    return res;                  
  }

}
