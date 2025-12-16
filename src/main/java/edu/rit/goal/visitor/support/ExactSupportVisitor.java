package edu.rit.goal.visitor.support;

import java.math.BigDecimal;
import java.util.Map;

import edu.rit.goal.estimator.Sampling;
import edu.rit.goal.visitor.GraphVisitor;

public class ExactSupportVisitor extends SupportVisitor {
	public ExactSupportVisitor(GraphVisitor visitor, int x, int y) {
		super(visitor, x, y, new Sampling<>(false, false, null));
	}

	@Override
	public void newSuccess(Map<Integer, Integer> matching) {
		listeners.forEach(l -> {
			getTime(startingTime);
			l.incrementMetric(BigDecimal.ONE);
			getTime(endingTime);

			l.addToTimeElapsed(startingTime, endingTime);
		});
	}

	@Override
	public boolean stop() {
		return totalVisited == candidatePairs.size();
	}

	@Override
	public void initVisit() {

	}

	@Override
	public void endVisit() {

	}

	@Override
	public void newFailure() {

	}
}
