package edu.rit.goal.estimator;

import java.math.BigDecimal;
import java.math.MathContext;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;

import edu.rit.goal.estimator.EstimatorFactory.EstimatorLimit;

// http://www.asasrms.org/Proceedings/papers/1997_130.pdf
public class HajekEstimator extends ProbabilityEstimator {
	boolean replacement;

	public HajekEstimator(EstimatorMemento memento, double accuracy, double confidence, EstimatorLimit limit,
			int minSamples, boolean replacement) {
		super(memento, accuracy, confidence, limit, minSamples);
		this.replacement = replacement;
	}

	// TODO I think this is not correct!
	@Override
	public ImmutableList<BigDecimal> processProbabilities(ImmutableList<BigDecimal> probabilities) {
		ImmutableList<BigDecimal> newProbabilities = getStats(probabilities);
		
		BigDecimal probSummation = newProbabilities.stream().reduce(BigDecimal.ZERO, (x, y) -> x.add(y));
		
		MutableList<BigDecimal> toRet = Lists.mutable.empty();
		for (int i = 0; i < newProbabilities.size(); i++)
			toRet.add(probSummation.divide(newProbabilities.get(i), MathContext.DECIMAL128));
		
		return getStats(probabilities);
	}
	
	@Override
	public BigDecimal getEstimation() {
		return super.getEstimation();
	}

	@Override
	public boolean withReplacement() {
		return replacement;
	}

}
