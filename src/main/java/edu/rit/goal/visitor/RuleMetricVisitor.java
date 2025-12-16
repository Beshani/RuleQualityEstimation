package edu.rit.goal.visitor;

import java.lang.management.ManagementFactory;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public abstract class RuleMetricVisitor {
	public GraphVisitor visitor;

	protected final int x, y;

	public RuleMetricVisitor(GraphVisitor visitor, int x, int y) {
		super();

		this.visitor = visitor;

		this.x = x;
		this.y = y;
	}

	protected final AtomicLong startingTime = new AtomicLong(), endingTime = new AtomicLong();

	protected final void getTime(AtomicLong t) {
		t.set(ManagementFactory.getThreadMXBean().getCurrentThreadCpuTime());
	}

	protected final BigDecimal getProbability(Map<Integer, Integer> matching, Integer selected, Integer notSelected) {
		// Correct probability. Original from the WSDM paper.
		// TODO Remove if this works better.
//		BigDecimal probabilityXAndY = visitor.getProbability(matching, Map.of(x, s, y, o));
//		return probability.divide(probabilityOfRest, MathContext.DECIMAL128);

		Integer selectedSize = null;

		// Selected is the variable we started the search from, if any.
		if (selected != null)
			// Let's find out the candidates of selected.
			selectedSize = visitor.getVariableCandidates(selected, matching).size();

		BigDecimal probability = visitor.getProbability(matching, Set.of());

		if (selectedSize != null)
			probability = probability.divide(new BigDecimal(selectedSize), MathContext.DECIMAL128);

		return probability;
	}
}
