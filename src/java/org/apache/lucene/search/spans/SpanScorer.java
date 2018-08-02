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

import org.apache.lucene.search.Weight;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Similarity;


class SpanScorer extends Scorer {
  private Spans spans;
  private Weight weight;
  private byte[] norms;
  private float value;

  private boolean firstTime = true;
  private boolean more = true;

  private int doc;
  private float freq;

  SpanScorer(Spans spans, Weight weight, Similarity similarity, byte[] norms)
    throws IOException {
    super(similarity);
    this.spans = spans;
    this.norms = norms;
    this.weight = weight;
    this.value = weight.getValue();
  }

  //判断是否有下一个span跨度
  public boolean next() throws IOException {
    if (firstTime) {
      more = spans.next();//是否有下一个匹配的span跨度
      firstTime = false;
    }

    if (!more) return false;

    freq = 0.0f;//词频
    doc = spans.doc();//此时所处的docid

    while (more && doc == spans.doc()) {//说明在同一个doc下
      int matchLength = spans.end() - spans.start();
      freq += getSimilarity().sloppyFreq(matchLength);
      more = spans.next();
    }

    return more || freq != 0.0f;
  }

  public int doc() { return doc; }

  public float score() throws IOException {
    float raw = getSimilarity().tf(freq) * value; // raw score
    return raw * Similarity.decodeNorm(norms[doc]); // normalize
  }

  //跳跃到参数的doc下继续计算
  public boolean skipTo(int target) throws IOException {
    more = spans.skipTo(target);

    if (!more) return false;

    freq = 0.0f;
    doc = spans.doc();

    while (more && spans.doc() == target) {
      freq += getSimilarity().sloppyFreq(spans.end() - spans.start());
      more = spans.next();
    }

    return more || freq != 0.0f;
  }

  public Explanation explain(final int doc) throws IOException {
    Explanation tfExplanation = new Explanation();

    skipTo(doc);

    float phraseFreq = (doc() == doc) ? freq : 0.0f;
    tfExplanation.setValue(getSimilarity().tf(phraseFreq));
    tfExplanation.setDescription("tf(phraseFreq=" + phraseFreq + ")");

    return tfExplanation;
  }

}
