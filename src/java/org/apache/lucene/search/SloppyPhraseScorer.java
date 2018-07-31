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

import org.apache.lucene.index.TermPositions;

import java.io.IOException;

/**
 *     //如果doc中真的包含顺序相同的短语，那么短语顺序相同的前提下,如果相邻两个term之间的词数量<slop,那么就应该会被参与计算
    //如果doc中包含的顺序与短语不同,那么应该没有他才对,但是考虑到可能是写错了，因此也会冗余这种可能，比如先将相邻两个单词掉顺序,属于2步,然后再看中间相差多少term数量,颠倒顺序后依然<slop，我们也要,只是权重分数会低。
    //如果doc中包含的顺序与短语严重不同,而排序拿出来的词是和doc的term有关系的,因此权重分数自然会低很多,因为匹配不上的数据太多了
    //基于以上结论,说明该算法还是可用的,而slop暂且就当他是每个相邻term之间最大term间距即可。如果稍微冗余相邻term可以颠倒的情况，则slop+2也可以，不过这种也会对别的有影响。
     * 
     * 即其实真没必要考虑他短语是否翻过来,就直接考虑短语之间最大差距多少,还依然算匹配上就可以了,考虑多了更容易出问题
 *
 */
final class SloppyPhraseScorer extends PhraseScorer {
    private int slop;

    SloppyPhraseScorer(Weight weight, TermPositions[] tps, int[] positions, Similarity similarity,
                       int slop, byte[] norms) {
        super(weight, tps, positions, similarity, norms);
        this.slop = slop;
    }

    //查找该doc中包含短语的次数,即短语词频
    //进入该方法的前提是所有的doc都相同,即所有的term都在改doc存在,因此只需要计算term组成短语的词频即可
    protected final float phraseFreq() throws IOException {
        pq.clear();
        int end = 0;//短语中term第一次出现的位置的最大值
        for (PhrasePositions pp = first; pp != null; pp = pp.next) {//每一个PhrasePositions表示短语中的一个term在该doc中的每一个出现的位置,每一个PhrasePositions分别表示每一个term
            pp.firstPosition();//切换到计算该doc中该term的词频
            if (pp.position > end)
                end = pp.position;
            pq.put(pp);				  // build pq from list  重新排序
        }

        float freq = 0.0f;
        boolean done = false;
        do {
        	//每次拿着doc中存在在短语中的term,每每相邻的term就比较一次,只要都在slop范围内,就计算词频,计算为1次
            PhrasePositions pp = (PhrasePositions) pq.pop();//弹出位置最靠前的 term----该term是出现在doc中的第一个词,而不是短语中的第一个词
            int start = pp.position;//记录该term的位置
            int next = ((PhrasePositions) pq.top()).position;//下一个term的位置,但是不会被弹出来
            for (int pos = start; pos <= next; pos = pp.position) {//循环第一个位置最靠前的term
                start = pos;				  // advance pp to min window
                if (!pp.nextPosition()) {//表示该文档中没有改term了
                    done = true;				  // ran out of a term -- done
                    break;
                }
            }

            System.out.println("=="+end+"==="+start);
            int matchLength = end - start;
            if (matchLength <= slop) {
            	freq += getSimilarity().sloppyFreq(matchLength); // score match  只要有匹配，就有一定的分数,只是离得越近,分数越高而已
            	System.out.println("matchLength==="+matchLength+"=="+end+"==="+start);
            }
                

            if (pp.position > end)
                end = pp.position;
            pq.put(pp);				  // restore pq
        } while (!done);

        return freq;
    }
}
