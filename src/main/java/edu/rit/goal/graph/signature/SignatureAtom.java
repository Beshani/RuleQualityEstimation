package edu.rit.goal.graph.signature;

public class SignatureAtom implements Comparable<SignatureAtom> {
	public int s, o;
	public String p;

	@Override
	public int compareTo(SignatureAtom o) {
		int ret = Integer.compare(this.s, o.s);

		if (ret == 0)
			ret = Integer.compare(this.o, o.o);

		if (ret == 0)
			ret = this.p.compareTo(o.p);

		return ret;
	}

	@Override
	public String toString() {
		return "[" + s + "," + o + "," + p + "]";
	}
}
