package com.maknoon;

class AudioInfo
{
	final String info1, info2, info6; //, info3
	final int info4, info5;

	AudioInfo(final String var1, final String var2/*, final String var3*/, final int var4, final int var5, final String var6)
	{
		info1 = var1;
		info2 = var2;
		//info3 = var3; // CDNumber removed
		info4 = var4;
		info5 = var5;
		info6 = var6;
	}

	public String toString() {return info1;}
}