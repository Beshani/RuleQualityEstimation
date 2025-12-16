package edu.rit.goal.canonical;

import java.util.Map;

public class DFSCodeItem {
	int i, j;
	Integer src, tgt;
	EdgeWithPredicate e;
	
	public DFSCodeItem(Integer src, Integer tgt, EdgeWithPredicate e, Map<Integer, Integer> dfsSubscript) {
		super();
		this.i = dfsSubscript.get(src);
		this.j = dfsSubscript.get(tgt);
		this.src = src;
		this.tgt = tgt;
		this.e = e;
	}
	
	public DFSCodeItem(Integer src, Integer tgt, EdgeWithPredicate e, int i, int j) {
		super();
		this.i = i;
		this.j = j;
		this.src = src;
		this.tgt = tgt;
		this.e = e;
	}

	@Override
	public String toString() {
		return "(" + i + ", " + j + ", id_u=" + src + "," + e.getPredicate() + ",  id_v=" + tgt + ")";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((e == null) ? 0 : e.hashCode());
		result = prime * result + i;
		result = prime * result + j;
		result = prime * result + ((src == null) ? 0 : src.hashCode());
		result = prime * result + ((tgt == null) ? 0 : tgt.hashCode());
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
		DFSCodeItem other = (DFSCodeItem) obj;
		if (e == null) {
			if (other.e != null)
				return false;
		} else if (!e.equals(other.e))
			return false;
		if (i != other.i)
			return false;
		if (j != other.j)
			return false;
		if (src == null) {
			if (other.src != null)
				return false;
		} else if (!src.equals(other.src))
			return false;
		if (tgt == null) {
			if (other.tgt != null)
				return false;
		} else if (!tgt.equals(other.tgt))
			return false;
		return true;
	}

	public int getI() {
		return i;
	}

	public int getJ() {
		return j;
	}

	public Integer getSrc() {
		return src;
	}

	public Integer getTgt() {
		return tgt;
	}

	public EdgeWithPredicate getE() {
		return e;
	}
}
