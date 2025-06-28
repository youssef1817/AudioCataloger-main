package com.maknoon;

import java.sql.*;

/*
 * Obtained results:
 * The Seq table is ordered and no jumps between each two sequential Seq.
 */
public class TestSeq
{
	static String fileSeparator = System.getProperty("file.separator");
	public static void main(String[] args)
	{
		try
		{
			String dbURL;
			if(AudioCataloger.derbyInUse)
			{
				dbURL="jdbc:derby:db";
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
			}
			else
			{
				dbURL="jdbc:h2:db"+fileSeparator+"audioCatalogerDatabase";
				Class.forName("org.h2.Driver");
			}
			
			Connection con = DriverManager.getConnection(dbURL);
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
			ResultSet rs = stmt.executeQuery("SELECT * FROM Contents ORDER BY Code, Seq");
			
			int code = 1, preSeq = 0, currentSeq;
			
			while(rs.next())
			{
				if(rs.getInt("Code") == code)
				{
					currentSeq = rs.getInt("Seq");
					
					if((preSeq+1) != currentSeq)
					{
						System.out.println("Code: "+code+"	Seq:"+currentSeq);
						preSeq = currentSeq;
					}
					else
						preSeq = currentSeq;
				}
				else
				{
					code = rs.getInt("Code");
					preSeq = rs.getInt("Seq");
				}
			}
		}
		catch(Exception e){e.printStackTrace();}
	}
}