package edu.rit.goal.canonical;

import org.jgrapht.graph.DefaultEdge;

public class EdgeWithPredicate extends DefaultEdge {
	private static final long serialVersionUID = 4271205700089458215L;
	
	String predicate;

	public EdgeWithPredicate(String predicate) {
		super();
		this.predicate = predicate;
	}

	public String getPredicate() {
		return predicate;
	}

	@Override
	public String toString() {
		return predicate;
	}
	
}
