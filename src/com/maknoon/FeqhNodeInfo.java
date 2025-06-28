package com.maknoon;

class FeqhNodeInfo
{
	final int Category_id;
	private final String Category_name;
	
	FeqhNodeInfo(final String category_name, final int category_id)
	{
		Category_id = category_id;
		Category_name = category_name;
	}
	
	public String toString() {return Category_name;}
}