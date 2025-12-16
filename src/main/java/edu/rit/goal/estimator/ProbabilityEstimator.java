package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;

import ch.obermuhlner.math.big.BigDecimalMath;
import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

public abstract class ProbabilityEstimator extends StatisticalEstimator {
	public ProbabilityEstimator(EstimatorMemento memento, double accuracy, double confidence, EstimatorLimit limit,
			int minSamples) {
		super(memento, accuracy, confidence, limit, minSamples);
	}

	@Override
	public final boolean requiresProbability() {
		return true;
	}

	// Implementing the sampling scheme with replacement as in the example here:
	// https://online.stat.psu.edu/stat506/Lesson03
	protected BigDecimal getPi(BigDecimal p) {
		BigDecimal ret = null;

		try {
			// 1.0 / (1.0 - Math.pow(1.0 - 1/p, n))
			ret = BigDecimal.ONE.divide(BigDecimal.ONE.subtract(
					BigDecimalMath.pow(BigDecimal.ONE.subtract(BigDecimal.ONE.divide(p, MathContext.DECIMAL128)),
							new BigDecimal(n), MathContext.DECIMAL128)),
					MathContext.DECIMAL128);
		} catch (Exception oops) {
			// There is some weird division by zero error from time to time.
			ret = BigDecimal.ZERO;
		}

		return ret;
	}

	protected ImmutableList<BigDecimal> getStats(ImmutableList<BigDecimal> probabilities) {
		// Let's put together the same probabilities.
		Map<BigDecimal, AtomicInteger> probHistogram = new HashMap<>();
		for (BigDecimal p : probabilities)
			if (!probHistogram.containsKey(p))
				probHistogram.put(p, new AtomicInteger(1));
			else
				probHistogram.get(p).incrementAndGet();

		// Probability values are typically repeated. This should speed up quite a bit.
		MutableList<BigDecimal> ret = Lists.mutable.empty();

		for (BigDecimal p : probHistogram.keySet()) {
			BigDecimal probabilityOfInclusion = getPi(p);

			for (int i = 0; i < probHistogram.get(p).intValue(); i++)
				ret.add(probabilityOfInclusion);
		}

		return Lists.immutable.ofAll(ret);
	}

	@Override
	public Map<String, Object> getExtraInfo() {
		Map<String, Object> extra = super.getExtraInfo();

		ImmutableList<BigDecimal> processed = processProbabilities(memento.getProbabilities(this.successes));

		extra.put("probSum", EstimationUtils.getSum(processed));
		extra.put("probMean", EstimationUtils.getMean(processed));
		extra.put("probVar", EstimationUtils.getVariance(processed));

		return extra;
	}

	public ImmutableList<BigDecimal> processProbabilities(ImmutableList<BigDecimal> probabilities) {
		return probabilities;
	}

	@Override
	public BigDecimal getEstimation() {
		ImmutableList<BigDecimal> probabilities = memento.getProbabilities(this.successes);
		return probabilities.isEmpty() ? BigDecimal.ZERO : EstimationUtils.getSum(processProbabilities(probabilities));
	}

}
