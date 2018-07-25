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
        int end = 0;//最后的一个位置
        for (PhrasePositions pp = first; pp != null; pp = pp.next) {
            pp.firstPosition();//切换到计算该doc中该term的词频
            if (pp.position > end)
                end = pp.position;
            pq.put(pp);				  // build pq from list  重新排序
        }

        float freq = 0.0f;
        boolean done = false;
        do {
            PhrasePositions pp = (PhrasePositions) pq.pop();
            int start = pp.position;
            int next = ((PhrasePositions) pq.top()).position;
            for (int pos = start; pos <= next; pos = pp.position) {
                start = pos;				  // advance pp to min window
                if (!pp.nextPosition()) {
                    done = true;				  // ran out of a term -- done
                    break;
                }
            }

            int matchLength = end - start;
            if (matchLength <= slop)
                freq += getSimilarity().sloppyFreq(matchLength); // score match

            if (pp.position > end)
                end = pp.position;
            pq.put(pp);				  // restore pq
        } while (!done);

        return freq;
    }
}
