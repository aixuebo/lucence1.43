package org.apache.lucene.testmm;

import org.apache.lucene.document.DateField;
import org.apache.lucene.search.DateFilter;

public class FilterTest {

	public void test1() {
		DateFilter filter = DateFilter.Before("aaa", System.currentTimeMillis());
		  String start = DateField.MIN_DATE_STRING();
		  String end = DateField.MAX_DATE_STRING();
		  System.out.println(start+"=="+end);
	}
	
	public static void main(String[] args) {
		FilterTest test = new FilterTest();
		test.test1();
	}
	
}
