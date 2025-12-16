package edu.rit.goal.estimator;

import java.math.BigDecimal;

public class ConfidenceIntervalBigDecimal {
	private BigDecimal lowerBound, upperBound, confidence;

	public ConfidenceIntervalBigDecimal(BigDecimal lowerBound, BigDecimal upperBound, BigDecimal confidence) {
		super();
		this.lowerBound = lowerBound;
		this.upperBound = upperBound;
		this.confidence = confidence;
	}

	public BigDecimal getLowerBound() {
		return lowerBound;
	}

	public BigDecimal getUpperBound() {
		return upperBound;
	}

	public BigDecimal getConfidence() {
		return confidence;
	}
	
}
