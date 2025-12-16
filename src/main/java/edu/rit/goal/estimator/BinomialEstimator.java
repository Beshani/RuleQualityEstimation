package edu.rit.goal.estimator;

import java.math.BigDecimal;

import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

public class BinomialEstimator extends StatisticalEstimator {
	public BinomialEstimator(EstimatorMemento memento, double accuracy, double confidence, EstimatorLimit limit,
			int minSamples) {
		super(memento, accuracy, confidence, limit, minSamples);
	}

	@Override
	public BigDecimal getEstimation() {
		return getMean().multiply(new BigDecimal(total));
	}

	@Override
	public boolean requiresProbability() {
		return false;
	}

	@Override
	public boolean withReplacement() {
		return true;
	}

}
