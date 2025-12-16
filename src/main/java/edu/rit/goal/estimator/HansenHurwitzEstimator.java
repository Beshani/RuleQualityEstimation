package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.MathContext;

import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

// https://online.stat.psu.edu/stat506/Lesson03
public class HansenHurwitzEstimator extends ProbabilityEstimator {
	public HansenHurwitzEstimator(EstimatorMemento memento, double accuracy, double confidence, EstimatorLimit limit,
			int minSamples) {
		super(memento, accuracy, confidence, limit, minSamples);
	}

	@Override
	public boolean withReplacement() {
		return true;
	}

	@Override
	public BigDecimal getEstimation() {
		return super.getEstimation().divide(new BigDecimal(n), MathContext.DECIMAL128);
	}

}
