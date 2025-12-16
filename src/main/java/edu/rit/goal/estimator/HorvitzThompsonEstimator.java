package edu.rit.goal.estimator;

import java.math.BigDecimal;

import org.eclipse.collections.api.list.ImmutableList;

import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

// https://online.stat.psu.edu/stat506/Lesson03
public class HorvitzThompsonEstimator extends ProbabilityEstimator {
	boolean replacement;

	public HorvitzThompsonEstimator(EstimatorMemento memento, double accuracy, double confidence, EstimatorLimit limit,
			int minSamples, boolean replacement) {
		super(memento, accuracy, confidence, limit, minSamples);
		this.replacement = replacement;
	}

	@Override
	public boolean withReplacement() {
		return replacement;
	}

	@Override
	public ImmutableList<BigDecimal> processProbabilities(ImmutableList<BigDecimal> probabilities) {
		return getStats(super.processProbabilities(probabilities));
	}

}
