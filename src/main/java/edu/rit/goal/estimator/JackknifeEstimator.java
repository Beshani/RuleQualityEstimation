package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.util.Map;

import org.apache.commons.math3.util.CombinatoricsUtils;

import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

// https://www.jstor.org/stable/1936861 (order k)
// TODO Jackknife is based on capture events t. The implementation using binomial coefficient is weird. Let's revisit at some point.
public class JackknifeEstimator extends FrequencyEstimator {

	public JackknifeEstimator(EstimatorMemento memento, double accuracy, double confidence, EstimatorLimit limit,
			int minSamples) {
		super(memento, accuracy, confidence, limit, minSamples);
	}

	@Override
	public BigDecimal getEstimation() {
		if (this.successes.intValue() == 0)
			return BigDecimal.ZERO;
		else {
			Map<Integer, Long> histogram = getHistogram();

			BigDecimal ret = getTotal(histogram);

			int order = 5;
			for (int i = 1; i <= order; i++) {
				int sign = 1;

				if (i % 2 == 0)
					sign = -1;

				long comb = sign * CombinatoricsUtils.binomialCoefficient(order, i);

				ret = ret.add(new BigDecimal(comb).multiply(getFi(histogram, i)));
			}

			return ret;
		}
	}

}
