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
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.apache.lucene.util.PriorityQueue;


/**
 * Describe class <code>MultipleTermPositions</code> here.
 * 用于同义词处理
 * 传入的term都是属于同义词term,因此同一个doc下包含这些term的词，都应该看成是相同的,因此词频和位置就是所有同义词term组成的merge操作
 * @author Anders Nielsen
 * @version 1.0
 */
public class MultipleTermPositions implements TermPositions {
	
    private static final class TermPositionsQueue extends PriorityQueue {
    	//参数元素内容是TermPositions,表示每一个term对应的doc以及位置信息
		TermPositionsQueue(List termPositions)
		    throws IOException
		{
		    initialize(termPositions.size());
	
		    Iterator i = termPositions.iterator();
		    while (i.hasNext())
		    {
			TermPositions tp = (TermPositions)i.next();
			if (tp.next())
			    put(tp);
		    }
		}
	
		final TermPositions peek()
		{
		    return (TermPositions)top();
		}
	
		//按照doc排序
		public final boolean lessThan(Object a, Object b)
		{
		    return ((TermPositions)a).doc() < ((TermPositions)b).doc();
		}
    }

    //int的队列---存储同一个doc下的所有同义词的位置集合
    private static final class IntQueue {
    	
		private int _arraySize = 16;//数组大小
	
		private int _index = 0;//读的下标
		private int _lastIndex = 0;//使用的最后一个数组下标,即下一次插入数据向该位置下标写入数据
	
		private int[] _array = new int[_arraySize];
	
		//向队列插入int
		final void add(int i)
		{
		    if (_lastIndex == _arraySize)
			growArray();//扩容
	
		    _array[_lastIndex++] = i;
		}
	
		//以此读取数据
		final int next()
		{
		    return _array[_index++];
		}
	
		//对集合内的数据进行排序
		final void sort()
		{
		    Arrays.sort(_array, _index, _lastIndex);
		}
	
		final void clear()
		{
		    _index = 0;
		    _lastIndex = 0;
		}
	
		//队列剩余长度
		final int size()
		{
		    return (_lastIndex-_index);
		}
	
		//扩容2倍
		private void growArray()
		{
		    int[] newArray = new int[_arraySize*2];
		    System.arraycopy(_array, 0, newArray, 0, _arraySize);
		    _array = newArray;
		    _arraySize *= 2;
		}
    }

    private int _doc;//当前处理的doc是哪个
    private int _freq;//词频,即所有同义词term的出现的总次数

    private TermPositionsQueue _termPositionsQueue;//优先队列,按照term中最小的doc排序
    private IntQueue _posList;

    /**
     * Creates a new <code>MultipleTermPositions</code> instance.
     *
     * @param indexReader an <code>IndexReader</code> value
     * @param terms a <code>Term[]</code> value
     * @exception IOException if an error occurs
     */
    public MultipleTermPositions(IndexReader indexReader, Term[] terms)
	throws IOException
    {
	List termPositions = new LinkedList();

	for (int i=0; i<terms.length; i++)
	    termPositions.add(indexReader.termPositions(terms[i]));

	_termPositionsQueue = new TermPositionsQueue(termPositions);
	_posList = new IntQueue();
    }

    /**
     * Describe <code>next</code> method here.
     *
     * @return a <code>boolean</code> value
     * @exception IOException if an error occurs
     * @see TermDocs#next()
     */
    public final boolean next() throws IOException {
    	
		if (_termPositionsQueue.size() == 0) //说明没有doc文档了
		    return false;
	
		_posList.clear();
		_doc = _termPositionsQueue.peek().doc();//此时最小的doc
	
		TermPositions tp;
		do
		{
		    tp = _termPositionsQueue.peek();//循环每一个term
	
		    for (int i=0; i<tp.freq(); i++) //该doc内该term的词频
		    	_posList.add(tp.nextPosition());//加入该词频的位置
	
		    if (tp.next()) //获取下一个doc
		    	_termPositionsQueue.adjustTop();//调整优先级队列
		    else {//说明该term没有下一个doc了
		    	_termPositionsQueue.pop();//该term在队列中移除
		    	tp.close();//关闭该term
		    }
		}while (_termPositionsQueue.size() > 0 && _termPositionsQueue.peek().doc() == _doc);//找到相同doc的所有term的位置,所有的term是表示所有同义词的term
	
		_posList.sort();//按照位置排序
		_freq = _posList.size();//词频,即所有同义词term的出现的总次数
	
		return true;
    }

    /**
     * Describe <code>nextPosition</code> method here.
     *
     * @return an <code>int</code> value
     * @exception IOException if an error occurs
     * @see TermPositions#nextPosition()
     * 不断获取下一个term的位置
     */
    public final int nextPosition() throws IOException {
    	return _posList.next();
    }

    /**
     * Describe <code>skipTo</code> method here.
     *
     * @param target an <code>int</code> value
     * @return a <code>boolean</code> value
     * @exception IOException if an error occurs
     * @see TermDocs#skipTo(int)
     * 所有term都要跳跃到参数docid位置上
     */
    public final boolean skipTo(int target) throws IOException {
		while (target > _termPositionsQueue.peek().doc()) {
		    TermPositions tp = (TermPositions)_termPositionsQueue.pop();
	
		    if (tp.skipTo(target))
			_termPositionsQueue.put(tp);
		    else
			tp.close();
		}
	
		return next();
    }

    /**
     * Describe <code>doc</code> method here.
     *
     * @return an <code>int</code> value
     * @see TermDocs#doc()
     */
    public final int doc()
    {
    	return _doc;
    }

    /**
     * Describe <code>freq</code> method here.
     * 
     * @return an <code>int</code> value
     * @see TermDocs#freq()
     */
    public final int freq()
    {
    	return _freq;
    }

    /**
     * Describe <code>close</code> method here.
     *
     * @exception IOException if an error occurs
     * @see TermDocs#close()
     */
    public final void close()
	throws IOException
    {
		while (_termPositionsQueue.size() > 0)
		    ((TermPositions)_termPositionsQueue.pop()).close();
    }

    /**
     * Describe <code>seek</code> method here.
     *
     * @param arg0 a <code>Term</code> value
     * @exception IOException if an error occurs
     * @see TermDocs#seek(Term)
     */
    public void seek(Term arg0)
	throws IOException
    {
    	throw new UnsupportedOperationException();
    }

    public void seek(TermEnum termEnum) throws IOException {
    	throw new UnsupportedOperationException();
    }


    /**
     * Describe <code>read</code> method here.
     *
     * @param arg0 an <code>int[]</code> value
     * @param arg1 an <code>int[]</code> value
     * @return an <code>int</code> value
     * @exception IOException if an error occurs
     * @see TermDocs#read(int[], int[])
     */
    public int read(int[] arg0, int[] arg1)
	throws IOException
    {
    	throw new UnsupportedOperationException();
    }

}
