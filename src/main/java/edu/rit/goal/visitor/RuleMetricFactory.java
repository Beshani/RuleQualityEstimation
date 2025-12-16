package edu.rit.goal.visitor;

import java.util.Map.Entry;

import edu.rit.goal.estimator.EstimatorMemento;
import edu.rit.goal.estimator.Sampling;
import edu.rit.goal.visitor.confidence.EstimatedPCAConfidenceVisitor;
import edu.rit.goal.visitor.confidence.EstimatedPCAConfidenceVisitor.SampleSelection;
import edu.rit.goal.visitor.confidence.ExactPCAConfidenceVisitor;
import edu.rit.goal.visitor.support.EstimatedSupportVisitor;
import edu.rit.goal.visitor.support.ExactSupportVisitor;

public class RuleMetricFactory {
	public static ExactSupportVisitor getExactSupport(GraphVisitor visitor) {
		int x = visitor.query.getEdgeSource(visitor.head), y = visitor.query.getEdgeTarget(visitor.head);

		return new ExactSupportVisitor(visitor, x, y);
	}

	public static EstimatedSupportVisitor getEstimatedSupport(GraphVisitor visitor, EstimatorMemento memento,
			Sampling<Entry<Integer, Integer>> sampling) {
		int x = visitor.query.getEdgeSource(visitor.head), y = visitor.query.getEdgeTarget(visitor.head);

		return new EstimatedSupportVisitor(visitor, memento, x, y, sampling);
	}

	public static ExactPCAConfidenceVisitor getExactPCAConfidence(GraphVisitor visitor, int x, int y, int corrupt) {
		return new ExactPCAConfidenceVisitor(visitor, x, y, corrupt);
	}

	public static EstimatedPCAConfidenceVisitor getEstimatedPCAConfidence(GraphVisitor visitor,
			EstimatorMemento memento, int x, int y, int corrupt, SampleSelection choice, Sampling<Integer> samplingX,
			Sampling<Integer> samplingY, boolean beamSearch) {
		return new EstimatedPCAConfidenceVisitor(visitor, memento, x, y, corrupt, choice, samplingX, samplingY,
				beamSearch);
	}

}
