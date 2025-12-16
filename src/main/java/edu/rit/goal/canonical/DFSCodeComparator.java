package edu.rit.goal.canonical;

import java.util.Comparator;
import java.util.List;

public class DFSCodeComparator implements Comparator<DFSCodeItem> {
	boolean allowEqual;
	
	public DFSCodeComparator(boolean allowEqual) {
		super();
		this.allowEqual = allowEqual;
	}
	
	public static int compareItems(DFSCodeItem e1, DFSCodeItem e2, boolean allowEqual) {
		int ret = 0;
		int i1 = e1.i, j1 = e1.j, i2 = e2.i, j2 = e2.j;
		
		if (case1(i1, j1, i2, j2) || case2(i1, j1, i2, j2) || case3(i1, j1, i2, j2) || case4(i1, j1, i2, j2))
			ret = -1;
		
		if (ret == 0)
			ret = e1.e.getPredicate().compareTo(e2.e.getPredicate());
		
		// TODO Is this working?
		if (ret == 0)
			ret = Integer.compare(e1.e.hashCode(), e2.e.hashCode());
		
		// These can never be equal.
		if (!allowEqual && ret == 0) {
			// Sanity check.
			if (i1 == i2 && j1 == j2)
				throw new Error("Equal!: " + e1 + "--" + e2 + ".");
			ret = 1;
		}
		
		return ret;
	}
	
	private static boolean case1(int i1, int j1, int i2, int j2) {
		return i1 < j1 && i2 < j2 && (j1 < j2 || (i1 > i2 && j1 == j2));
	}
	
	private static boolean case2(int i1, int j1, int i2, int j2) {
		return i1 > j1 && i2 > j2 && (i1 < i2 || (i1 == i2 && j1 < j2));
	}
	
	private static boolean case3(int i1, int j1, int i2, int j2) {
		return i1 > j1 && i2 < j2 && i1 < j2;
	}
	
	private static boolean case4(int i1, int j1, int i2, int j2) {
		return i1 < j1 && i2 > j2 && j1 <= i2;
	}

	@Override
	public int compare(DFSCodeItem e1, DFSCodeItem e2) {
		return DFSCodeComparator.compareItems(e1, e2, allowEqual);
	}
	
	public static boolean codeEqOtherCode(List<DFSCodeItem> alpha, List<DFSCodeItem> beta) {
		// The first one needs to be equal.
		boolean isEq = true;
		for (int i = 0; isEq && i < Math.min(alpha.size(), beta.size()); i++) {
			DFSCodeItem a = alpha.get(i), b = beta.get(i);
			isEq = a.toString().equals(b.toString());
		}
		return isEq;
	}
	
	public static boolean codeLessOtherCode(List<DFSCodeItem> alpha, List<DFSCodeItem> beta) {
		// The first one needs to be equal.
		boolean isLessOrEq = alpha.get(0).toString().equals(beta.get(0).toString()), lessFound = false;
		for (int i = 1; isLessOrEq && i < Math.min(alpha.size(), beta.size()); i++) {
			DFSCodeItem a = alpha.get(i), b = beta.get(i);
			
			// We keep going until finding one such that a != b.
			if (!a.toString().equals(b.toString())) {
				// Two options: If a < b, then alpha is less or equal than beta.
				if (DFSCodeComparator.compareItems(a, b, false) < 0) {
					lessFound = true;
					break;
				} else
					isLessOrEq = false;
			}
		}
		
		// It is strictly less if we found the last one to be less.
		return isLessOrEq && lessFound;
	}

}
