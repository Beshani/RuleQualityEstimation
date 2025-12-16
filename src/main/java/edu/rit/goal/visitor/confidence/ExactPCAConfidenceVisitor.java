package edu.rit.goal.visitor.confidence;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import edu.rit.goal.visitor.GraphVisitor;

public class ExactPCAConfidenceVisitor extends PCAConfidenceVisitor {
	public ExactPCAConfidenceVisitor(GraphVisitor visitor, int x, int y, int corrupt) {
		super(visitor, x, y, corrupt);
	}

	@Override
	public void initVisit() {
		getTime(startingTime);
		visitor.getOrder(Set.of(x, y));
		getTime(endingTime);

		this.listeners.forEach(l -> l.addToTimeElapsed(startingTime, endingTime));
	}

	@Override
	public void visit() {
		xCandidates.forEach(s -> {
			yCandidates.forEach(o -> {
				getTime(startingTime);

				AtomicInteger oneFound = new AtomicInteger();

				// It cannot be in the search space (then, it is positive).
				if (!visitor.isInHeadCandidates(s, o)) {
					// Add s and o to the partial matching.
					Map<Integer, Integer> partialMatching = new HashMap<>();

					partialMatching.put(x, s);
					partialMatching.put(y, o);

					Consumer<Map<Integer, Integer>> matchingFound = matching -> {
						oneFound.incrementAndGet();
					};

					visitor.matching(0, partialMatching, matchingFound, visitor::toIterate, a -> {
						return oneFound.get() > 0;
					});
				}

				getTime(endingTime);

				this.listeners.forEach(l -> l.addToTimeElapsed(startingTime, endingTime));

				updateCalls();

				if (oneFound.get() > 0)
					listeners.stream().forEach(l -> {
						getTime(startingTime);
						l.incrementMetric(BigDecimal.ONE);
						getTime(endingTime);

						l.addToTimeElapsed(startingTime, endingTime);
					});
			});
		});
	}

	@Override
	public void endVisit() {

	}

}
