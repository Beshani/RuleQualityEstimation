package edu.rit.goal.mining.reward;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.rit.goal.metric.Rule;

// Original AnyBURL considers only new rules (w.r.t. last timestamp) but it also mixes threads.
public class RuleMetricsReward extends Reward {
	private enum Feature {
		Length, Support, Confidence, HeadCoverage, PredicateSize
	};

	// This stores the different parameters for a window size.
	private List<Map<Feature, BigDecimal>> history = Collections.synchronizedList(new ArrayList<>());

	// These are the features we will use.
	private Set<Feature> features = new HashSet<>();

	private int windowSize;

	public void addLength() {
		features.add(Feature.Length);
	}

	// Support and head coverage are exclusive (one or the other, but not both).
	public void addSupport() {
		features.add(Feature.Support);
	}

	public void addHeadCoverage() {
		features.add(Feature.HeadCoverage);
	}

	public void addConfidence() {
		features.add(Feature.Confidence);
	}

	Map<String, Integer> predicateSizes;

	public void addPredicateSize(Map<String, Integer> predicateSizes) {
		features.add(Feature.PredicateSize);
		this.predicateSizes = predicateSizes;
	}

	public void setWindowSize(int s) {
		windowSize = s;
	}

	@Override
	public synchronized BigDecimal getReward() {
		BigDecimal totalReward = BigDecimal.ZERO;

		// Visit the history.
		for (Map<Feature, BigDecimal> prev : history) {
			BigDecimal reward = BigDecimal.ONE;

			if (features.contains(Feature.Support)) {
				reward = reward.multiply(prev.get(Feature.Support));

				if (features.contains(Feature.Confidence))
					reward = reward.multiply(prev.get(Feature.Confidence));
			}

			if (features.contains(Feature.HeadCoverage)) {
				BigDecimal hc = prev.get(Feature.HeadCoverage).add(BigDecimal.ONE), cf = BigDecimal.ONE;

				if (features.contains(Feature.Confidence))
					cf = prev.get(Feature.Confidence).add(BigDecimal.ONE);

				// Let's make the reward being in %.
				reward = new BigDecimal(2).multiply(hc).multiply(cf).divide(hc.add(cf), MathContext.DECIMAL128)
						.multiply(new BigDecimal(100));
			}

			if (features.contains(Feature.Length))
				// Size two is best.
				reward = reward.divide(
						BigDecimal.valueOf(2).pow(prev.get(Feature.Length).intValue() - 2, MathContext.DECIMAL128),
						MathContext.DECIMAL128);

			if (features.contains(Feature.PredicateSize))
				reward = reward.multiply(prev.get(Feature.PredicateSize));

			totalReward = totalReward.add(reward);
		}

		// Let's normalize by the window size...
		if (!history.isEmpty())
			totalReward = totalReward.divide(BigDecimal.valueOf(history.size()), MathContext.DECIMAL128);

		return totalReward;
	}

	private synchronized void checkWindowSize() {
		// Check window size.
		if (!history.isEmpty() && history.size() >= windowSize)
			history.remove(0);
	}

	@Override
	public synchronized void reportFailure() {
		reportCommon(null);
	}

	@Override
	public synchronized void reportRepetetion(Rule r) {
		reportCommon(r);
	}

	private synchronized void reportCommon(Rule r) {
		checkWindowSize();

		Map<Feature, BigDecimal> current = new HashMap<>();

		if (features.contains(Feature.Support))
			current.put(Feature.Support, BigDecimal.ZERO);

		if (features.contains(Feature.HeadCoverage))
			current.put(Feature.HeadCoverage, BigDecimal.ZERO);

		if (features.contains(Feature.Confidence))
			current.put(Feature.Confidence, BigDecimal.ZERO);

		if (features.contains(Feature.Length)) {
			if (r != null)
				current.put(Feature.Length, BigDecimal.valueOf(r.getRuleLength()));
			else
				current.put(Feature.Length, BigDecimal.ZERO);
		}

		if (features.contains(Feature.PredicateSize)) {
			if (r != null)
				current.put(Feature.PredicateSize, BigDecimal.valueOf(predicateSizes.get(r.getHead().predicate)));
			else
				current.put(Feature.PredicateSize, BigDecimal.ZERO);
		}

		history.add(current);
	}

	@Override
	public synchronized void reportRule(Rule r) {
		checkWindowSize();

		Map<Feature, BigDecimal> current = new HashMap<>();

		if (features.contains(Feature.Support))
			current.put(Feature.Support, r.getSupport().support);

		if (features.contains(Feature.HeadCoverage))
			current.put(Feature.HeadCoverage, r.getSupport().getHeadCoverage());

		if (features.contains(Feature.Confidence)) {
			BigDecimal confidence = null, support = r.getSupport().support;

			// We get the best (maximum) confidence found.
			if (r.getConfidenceX() != null && r.getConfidenceY() != null)
				confidence = r.getConfidenceX().getPCAConfidence(support)
						.max(r.getConfidenceY().getPCAConfidence(support));
			else
				confidence = BigDecimal.ZERO;

			current.put(Feature.Confidence, confidence);
		}

		if (features.contains(Feature.Length))
			current.put(Feature.Length, new BigDecimal(r.getRuleLength()));

		if (features.contains(Feature.PredicateSize))
			current.put(Feature.PredicateSize, new BigDecimal(predicateSizes.get(r.getHead().predicate)));

		history.add(current);
	}

}
