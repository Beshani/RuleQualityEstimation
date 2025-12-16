package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

public class AnyBURLEstimator extends NonStatisticalEstimator {
	// These are the default values proposed by AnyBURL.
	int maxBeta = 5, maxPhi = 1000, maxAlpha = 10000;

	int repeated, attempts;

	public AnyBURLEstimator(EstimatorMemento memento) {
		super(memento);
	}

	@Override
	public boolean withinLimit() {
		return repeated >= maxBeta || (this.successes.intValue() - repeated) >= maxPhi
				|| n.compareTo(BigInteger.valueOf(maxAlpha)) >= 0;
	}
	
	@Override
	public void newSuccess() {
		super.newSuccess();

		Map<Entry<Integer, Integer>, AtomicInteger> pairs = getPairs();

		Entry<Integer, Integer> pair = memento.getLastPair();

		if (pairs.get(pair).get() == 1)
			repeated = 0;
		else
			repeated++;
	}

	@Override
	public Map<String, Object> getExtraInfo() {
		Map<String, Object> extra = super.getExtraInfo();

		extra.put("repeated", repeated);
		extra.put("attempts", attempts);
		extra.put("maxBeta", maxBeta);
		extra.put("maxPhi", maxPhi);
		extra.put("maxAlpha", maxAlpha);

		return extra;
	}

	@Override
	public BigDecimal getEstimation() {
		return new BigDecimal(this.successes.intValue() - repeated);
	}

	@Override
	public boolean requiresProbability() {
		return false;
	}

	@Override
	public boolean withReplacement() {
		return true;
	}

	@Override
	public void reset(BigInteger total) {
		super.reset(total);

		repeated = 0;
		attempts = 0;
	}
}
