package edu.rit.goal.graph;

import org.jgrapht.graph.DefaultEdge;

public class LabeledEdge extends DefaultEdge {
	private static final long serialVersionUID = -8237534662863081340L;

	public String predicate;
	public int pid;
	
	public LabeledEdge(String predicate, int pid) {
		super();
		this.predicate = predicate;
		this.pid = pid;
	}

	@Override
	public String toString() {
		return predicate + super.toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + pid;
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		LabeledEdge other = (LabeledEdge) obj;
		if (pid != other.pid)
			return false;
		return true;
	}
}
