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

import junit.framework.TestCase;
import org.apache.lucene.analysis.WhitespaceAnalyzer;
import org.apache.lucene.analysis.StopAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.RAMDirectory;

/**
 * Tests {@link PhraseQuery}.
 *
 * @see TestPositionIncrement
 * @author Erik Hatcher
 */
public class TestPhraseQueryMM extends TestCase {
  private IndexSearcher searcher;
  private PhraseQuery query;
  private RAMDirectory directory;

  public void setUp() throws Exception {
    directory = new RAMDirectory();
    IndexWriter writer = new IndexWriter(directory, new WhitespaceAnalyzer(), true);
    
    Document doc = new Document();
    doc.add(Field.Text("field", "the quick brown fox jumped over the lazy dog"));
    writer.addDocument(doc);
    
    writer.optimize();
    writer.close();

    searcher = new IndexSearcher(directory);
    query = new PhraseQuery();
  }

  public void tearDown() throws Exception {
    searcher.close();
    directory.close();
  }

  /**
   * 测试the quick brown fox jumped  over the lazy dog
   * 结果是出现了1次,分数0.13561106    matchLength===1==2===1  即quick为1,fox为2,因此差距是1
   */
  public void testBarelyCloseEnough() throws Exception {
    query.setSlop(3);
    query.add(new Term("field", "quick"));
    query.add(new Term("field", "fox"));
    Hits hits = searcher.search(query);
    int size = hits.length();
    System.out.println("size==>"+size);
    for(int i=0 ;i<size ;i++) {
    	System.out.println(i+"===hits.score(i)==>"+hits.score(i));
    }
  }

  /**
   * 测试the quick brown fox jumped  quick over the lazy dog
   * 
   * 结果是分数为0.16608895
matchLength===1==2===1
matchLength===3==5===2

   */
  public void testBarelyCloseEnough2() throws Exception {
	    query.setSlop(3);
	    query.add(new Term("field", "quick"));
	    query.add(new Term("field", "fox"));
	    Hits hits = searcher.search(query);
	    int size = hits.length();
	    System.out.println("size==>"+size);
	    for(int i=0 ;i<size ;i++) {
	    	System.out.println(i+"===hits.score(i)==>"+hits.score(i));
	    }
	  }
  
  /**
   */
  public void testBarelyCloseEnough3() throws Exception {
	    query.setSlop(2);
	    query.add(new Term("field", "fox"));
	    query.add(new Term("field", "quick"));
	    Hits hits = searcher.search(query);
	    int size = hits.length();
	    System.out.println("size==>"+size);
	    for(int i=0 ;i<size ;i++) {
	    	System.out.println(i+"===hits.score(i)==>"+hits.score(i));
	    }
	  }
}
