package edu.rit.goal.visitor.confidence;

import java.math.BigInteger;
import java.util.Collection;

import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.primitive.MutableIntList;

import edu.rit.goal.visitor.GraphVisitor;
import edu.rit.goal.visitor.RuleMetricListener;
import edu.rit.goal.visitor.RuleMetricVisitor;

public abstract class PCAConfidenceVisitor extends RuleMetricVisitor {
	int corrupt;

	public PCAConfidenceVisitor(GraphVisitor visitor, int x, int y, int corrupt) {
		super(visitor, x, y);
		this.corrupt = corrupt;
	}

	MutableIntList xCandidates, yCandidates;
	Collection<RuleMetricListener> listeners;
	BigInteger total;

	public void compute(Collection<RuleMetricListener> listeners) {
		this.listeners = listeners;

		// We are starting!
		this.listeners.forEach(l -> l.reset());

		{
			// Pre-visit.
			getTime(startingTime);

			xCandidates = IntLists.mutable.ofAll(visitor.getVariableCandidates(x));
			yCandidates = IntLists.mutable.ofAll(visitor.getVariableCandidates(y));

			// Let's shuffle them!
			xCandidates.shuffleThis();
			yCandidates.shuffleThis();

			total = BigInteger.valueOf(xCandidates.size()).multiply(BigInteger.valueOf(yCandidates.size()));

			getTime(endingTime);

			this.listeners.forEach(l -> l.addToTimeElapsed(startingTime, endingTime));
		}

		initVisit();

		visit();

		endVisit();
	}

	public abstract void initVisit();

	public abstract void endVisit();

	public abstract void visit();

	public void updateCalls() {
		this.listeners.forEach(l -> l.updateCalls(visitor.matchingCalls.get()));
	}

}
