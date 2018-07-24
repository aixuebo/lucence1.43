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

/** Subclass of FilteredTermEnum for enumerating all terms that are similiar to the specified filter term.

  <p>Term enumerations are always ordered by Term.compareTo().  Each term in
  the enumeration is greater than all that precede it.  
  通过编辑距离的相似度进行匹配,匹配最相似的term
  */
public final class FuzzyTermEnum extends FilteredTermEnum {
    double distance;
    boolean endEnum = false;

    Term searchTerm = null;//模糊匹配的term
    String field = "";//模糊匹配的term所在field
    
    String text = "";//剩余模糊匹配的字符串
    int textlen;//剩余模糊匹配字符串的长度
    String prefix = "";//前缀精准匹配的字符串
    int prefixLength = 0;//前缀精准匹配的字符串长度
    
    float minimumSimilarity;//最小模糊相似度阈值
    double scale_factor;//minimumSimilarity越小,scale_factor计算的结果越小
    
    
    /**
     * Empty prefix and minSimilarity of 0.5f are used.
     * 
     * @param reader
     * @param term
     * @throws IOException
     * @see #FuzzyTermEnum(IndexReader, Term, float, int)
     */
    public FuzzyTermEnum(IndexReader reader, Term term) throws IOException {
      this(reader, term, FuzzyQuery.defaultMinSimilarity, 0);
    }
    
    /**
     * This is the standard FuzzyTermEnum with an empty prefix.
     * 
     * @param reader
     * @param term
     * @param minSimilarity
     * @throws IOException
     * @see #FuzzyTermEnum(IndexReader, Term, float, int)
     */
    public FuzzyTermEnum(IndexReader reader, Term term, float minSimilarity) throws IOException {
      this(reader, term, minSimilarity, 0);
    }
    
    /**
     * Constructor for enumeration of all terms from specified <code>reader</code> which share a prefix of
     * length <code>prefixLength</code> with <code>term</code> and which have a fuzzy similarity &gt;
     * <code>minSimilarity</code>. 
     * 
     * @param reader Delivers terms.
     * @param term Pattern term.
     * @param minSimilarity Minimum required similarity for terms from the reader. Default value is 0.5f.
     * @param prefixLength Length of required common prefix. Default value is 0.
     * @throws IOException
     */
    public FuzzyTermEnum(IndexReader reader, Term term, float minSimilarity, int prefixLength) throws IOException {
        super();
        minimumSimilarity = minSimilarity;
        scale_factor = 1.0f / (1.0f - minimumSimilarity);//minimumSimilarity越小,scale_factor计算的结果越小
        searchTerm = term;
        field = searchTerm.field();
        text = searchTerm.text();
        textlen = text.length();
        if(prefixLength > 0 && prefixLength < textlen){//截取查询前缀
            this.prefixLength = prefixLength;
            prefix = text.substring(0, prefixLength);//查询前缀
            text = text.substring(prefixLength);
            textlen = text.length();
        }
        setEnum(reader.terms(new Term(searchTerm.field(), prefix)));
    }
    
    /**
     The termCompare method in FuzzyTermEnum uses Levenshtein distance to 
     calculate the distance between the given term and the comparing term. 
     */
    protected final boolean termCompare(Term term) {
        String termText = term.text();
        if (field == term.field() && termText.startsWith(prefix)) {//field和前缀必须相同
            String target = termText.substring(prefixLength);//term剩余内容
            int targetlen = target.length();//剩余长度
            int dist = editDistance(text, target, textlen, targetlen);//计算相似度---操作次数越少，说明两个字符串距离Levenshtein Distance越小，表示两个字符串越想似
            distance = 1 - ((double)dist / (double)Math.min(textlen, targetlen));
            return (distance > minimumSimilarity);//比最小相似度大,就选择该term
        }
        endEnum = true;//前缀不同,因此说明term已经遍历结束了
        return false;
    }
    
    protected final float difference() {
        return (float)((distance - minimumSimilarity) * scale_factor);
    }
    
    public final boolean endEnum() {
        return endEnum;
    }
    
    /******************************
     * Compute Levenshtein distance
     ******************************/
    
    /**
     Finds and returns the smallest of three integers 
     找到三个int值中最小的
     */
    private static final int min(int a, int b, int c) {
        int t = (a < b) ? a : b;
        return (t < c) ? t : c;
    }
    
    /**
     * This static array saves us from the time required to create a new array
     * everytime editDistance is called.
     */
    private int e[][] = new int[1][1];
    
    /**
     Levenshtein distance also known as edit distance is a measure of similiarity
     between two strings where the distance is measured as the number of character 
     deletions, insertions or substitutions required to transform one string to 
     the other string. 
     <p>This method takes in four parameters; two strings and their respective 
     lengths to compute the Levenshtein distance between the two strings.
     The result is returned as an integer.
     操作次数越少，说明两个字符串距离Levenshtein Distance越小，表示两个字符串越想似
     @param s表示匹配格式  n是s的长度
     @param t表示target目标term的值 m是t的长度
     */ 
    private final int editDistance(String s, String t, int n, int m) {
        if (e.length <= n || e[0].length <= m) {//根据匹配的n和m的长度,创建数组
            e = new int[Math.max(e.length, n+1)][Math.max(e[0].length, m+1)];
        }
        int d[][] = e; // matrix
        int i; // iterates through s 循环每一个匹配的字符
        int j; // iterates through t 循环每一个目标的字符
        char s_i; // ith character of s 此时计算的是匹配字符
        
        if (n == 0) return m;
        if (m == 0) return n;
        
        // init matrix d
        for (i = 0; i <= n; i++) d[i][0] = i;//固定列,因此输入第0列---内容就是n个长度----匹配值为列
        for (j = 0; j <= m; j++) d[0][j] = j;//固定行,因此输入第0行---内容就是m个长度----target目标值为行
        
        // start computing edit distance 开始计算编辑距离
        for (i = 1; i <= n; i++) {//循环每一个匹配的字符
            s_i = s.charAt(i - 1);//获取每一个匹配的字符
            for (j = 1; j <= m; j++) {//循环每一个target目标
                if (s_i != t.charAt(j-1))//说明不等于
                    d[i][j] = min(d[i-1][j], d[i][j-1], d[i-1][j-1])+1;
                else //说明等于
                	d[i][j] = min(d[i-1][j]+1, d[i][j-1]+1, d[i-1][j-1]);
            }
        }
        
        // we got the result!
        return d[n][m];//获取最右下角的数字,就是编辑距离
    }
    
  public void close() throws IOException {
      super.close();
      searchTerm = null;
      field = null;
      text = null;
  }
}
