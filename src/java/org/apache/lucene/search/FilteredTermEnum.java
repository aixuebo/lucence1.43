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
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermEnum;

/** Abstract class for enumerating a subset of all terms. 

  <p>Term enumerations are always ordered by Term.compareTo().  Each term in
  the enumeration is greater than all that precede it.  
   枚举term集合的一个子集合,该子集是排序后的term结果
  */
public abstract class FilteredTermEnum extends TermEnum {
    private Term currentTerm = null;
    private TermEnum actualEnum = null;//真实的term文件迭代器
    
    public FilteredTermEnum() throws IOException {}

    /** Equality compare on the term 
     * 参数是term原始内容,判断该term是否匹配规则 
     **/
    protected abstract boolean termCompare(Term term);
    
    /** Equality measure on the term */
    protected abstract float difference();

    /** Indiciates the end of the enumeration has been reached */
    protected abstract boolean endEnum();
    
    protected void setEnum(TermEnum actualEnum) throws IOException {
        this.actualEnum = actualEnum;
        // Find the first term that matches
        Term term = actualEnum.term();//第一个匹配的term原始内容
        if (term != null && termCompare(term)) //说明term匹配该规则
            currentTerm = term;//选择该term作为query匹配的term
        else next();
    }
    
    /** 
     * Returns the docFreq of the current Term in the enumeration.
     * Initially invalid, valid after next() called for the first time. 
     * 获取该term出现在多少个doc中
     */
    public int docFreq() {
        if (actualEnum == null) return -1;
        return actualEnum.docFreq();
    }
    
    /** Increments the enumeration to the next element.  True if one exists. */
    public boolean next() throws IOException {
        if (actualEnum == null) return false; // the actual enumerator is not initialized! 说明term文档遍历结束了
        currentTerm = null;
        while (currentTerm == null) {
            if (endEnum()) return false;
            if (actualEnum.next()) {//循环每一个具体的term
                Term term = actualEnum.term();
                if (termCompare(term)) {//找到匹配的term
                    currentTerm = term;
                    return true;
                }
            }
            else return false;
        }
        currentTerm = null;
        return false;
    }
    
    /** Returns the current Term in the enumeration.
     * Initially invalid, valid after next() called for the first time.
     * 真正匹配的term 
     **/
    public Term term() {
        return currentTerm;
    }
    
    /** Closes the enumeration to further activity, freeing resources.  */
    public void close() throws IOException {
        actualEnum.close();
        currentTerm = null;
        actualEnum = null;
    }
}
