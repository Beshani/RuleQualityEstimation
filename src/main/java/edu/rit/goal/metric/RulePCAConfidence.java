package edu.rit.goal.metric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.MutableSet;

public class RulePCAConfidence extends RuleMetric {
	public BigDecimal pcaPrime;
	public BigInteger pcaPrimeMatchingCalls;
	public int corrupt;
	
	private MutableSet<Entry<Integer, Integer>> positivePairs = Sets.mutable.empty();

	public RulePCAConfidence(int corrupt) {
		super();
		this.pcaPrime = BigDecimal.ZERO;
		this.pcaPrimeMatchingCalls = BigInteger.ZERO;
		this.corrupt = corrupt;
	}
	
	public void gatherPositive(int s, int o) {
		positivePairs.add(Map.entry(s, o));
	}
	
	public MutableSet<Entry<Integer, Integer>> getPositivePairs() {
		return positivePairs;
	}

	public BigDecimal getPCAConfidence(BigDecimal support) {
		if (support.add(pcaPrime).compareTo(BigDecimal.ZERO) == 0)
			return BigDecimal.ZERO;
		else

			return support.divide(support.add(pcaPrime), MathContext.DECIMAL128);
	}

	@Override
	public String toString() {
		return "PCA " + corrupt + ": " + pcaPrime + "; calls: " + pcaPrimeMatchingCalls;
	}

	@Override
	public void reset() {
		super.reset();

		this.pcaPrime = BigDecimal.ZERO;
		this.pcaPrimeMatchingCalls = BigInteger.ZERO;
	}

}
