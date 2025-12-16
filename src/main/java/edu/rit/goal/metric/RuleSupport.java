package edu.rit.goal.metric;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;

public class RuleSupport extends RuleMetric {
	public BigDecimal support;
	public BigInteger supportMatchingCalls, predicateSize;
	
	public RuleSupport(int predicateSize) {
		this.support = BigDecimal.ZERO;
		this.supportMatchingCalls = BigInteger.ZERO;
		this.predicateSize = BigInteger.valueOf(predicateSize);
	}
	
	public BigDecimal getHeadCoverage() {
		return support.divide(new BigDecimal(predicateSize), MathContext.DECIMAL128);
	}

	@Override
	public String toString() {
		return "Support: " + support + "; calls: " + supportMatchingCalls;
	}

	@Override
	public void reset() {
		super.reset();
		
		this.support = BigDecimal.ZERO;
		this.supportMatchingCalls = BigInteger.ZERO;
	}
}
