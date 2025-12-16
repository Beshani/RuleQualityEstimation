package edu.rit.goal.estimator;

import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

import org.apache.commons.math3.distribution.BinomialDistribution;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;

public class Sampling<T> {
	// This indicates whether to shuffle at the beginning.
	boolean shuffle;

	// This indicates whether it is with replacement or not.
	boolean replacement;

	// This indicates whether n (the sample size) is known in advanced.
	Integer n;

	public Sampling(boolean shuffle, boolean replacement, Integer n) {
		super();
		this.shuffle = shuffle;
		this.replacement = replacement;
		this.n = n;
	}

	MutableList<T> list, sample;
	// These are the indexes of the list.
	MutableIntList listIdx;
	
	// This initializes our sampling for the selected parameters.
	public void init(MutableList<T> toSample) {
		// Let's copy to avoid side effects.
		list = toSample;
		
		listIdx = IntLists.mutable.ofAll(IntStream.range(0, list.size()));
		
		if (shuffle)
			listIdx.shuffleThis(ThreadLocalRandom.current());

		// Using this reference: https://link.springer.com/book/10.1007/0-387-34240-0
		if (n != null) {
			sample = Lists.mutable.empty();
			int N = listIdx.size();

			// With replacement, Algorithm 4.8.
			if (replacement) {
				int s = 0;

				for (int k = 1; k <= listIdx.size(); k++) {
					T elemK = list.get(listIdx.get(k - 1));

					// Get sk using binomial distribution.
					int sk = new BinomialDistribution(n - s, 1.0 / (N - k + 1)).sample();

					// Add k-th element sk times to the sample.
					for (int i = 0; i < sk; i++)
						sample.add(elemK);

					// Accumulate s.
					s += sk;
					
					// We are done!
					if (sample.size() == n)
						break;
				}
			}
			// Without replacement, Algorithm 4.3.
			else {
				int j = 0;

				for (int k = 1; k <= listIdx.size(); k++) {
					T elemK = list.get(listIdx.get(k - 1));

					// Add k-th element to the sample with probability n − j/N − (k − 1)
					if (ThreadLocalRandom.current().nextDouble() <= (1.0 * n - j) / (N - (k - 1))) {
						sample.add(elemK);
						j++;
					}
					
					// We are done!
					if (sample.size() == n)
						break;
				}
			}
		}
	}

	// This is the index that we are currently iterating.
	int index = 0;

	public T getNext() {
		T toRet = null;

		// We don't know the sample size.
		if (n == null) {
			if (!replacement) {
				if (index >= list.size())
					index = 0;

				toRet = list.get(index++);
			} else
				toRet = list.get(ThreadLocalRandom.current().nextInt(list.size()));
		}
		// We do know the sample size.
		else {
			// This should not happen, but I've seen things you people wouldn't believe.
			if (index >= sample.size())
				index = 0;

			toRet = sample.get(index++);

		}

		return toRet;
	}
}
