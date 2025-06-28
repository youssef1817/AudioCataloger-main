package com.maknoon;

import java.io.*;
import java.sql.*;

/*
 * Obtained results:
 * The Seq table is ordered and no jumps between each two sequential Seq.
 */
public class TestDB
{
	public static void main(String[] args)
	{
		try
		{
			Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
			Connection con = DriverManager.getConnection("jdbc:derby:db");
			Connection con1 = DriverManager.getConnection("jdbc:derby:db");
			
			//Class.forName("com.mysql.jdbc.Driver");
			//Connection con = DriverManager.getConnection("jdbc:mysql:///test"+"?socketFactory=com.mysql.jdbc.NamedPipeSocketFctory", "root", "secret");
			Statement stmt = con.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
			Statement stmt1 = con1.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
			con1.setAutoCommit(false);
			ResultSet rs = stmt.executeQuery("SELECT * FROM Contents ORDER BY Code, Seq");
			
			int count = 1;
			int code = 1;
			int Offset = 0;
			int nextOffset = 0;
			int Seq = 0;
			int nextSeq = 0;
			
			final PrintWriter out = new PrintWriter(new FileWriter("DerbyTestDBResults.txt", false));
			
			while(rs.next())
			{
				if(rs.getInt("Code") == code)
				{
					nextOffset = rs.getInt("Offset");
					nextSeq = rs.getInt("Seq");
					
					if(nextOffset < Offset)
					{
						out.println(count+"	Code: "+code+"	Seq:"+nextSeq);
						
						stmt1.executeUpdate("UPDATE Contents SET Seq = 1000 WHERE Seq = "+nextSeq+" AND Code = "+code);
						stmt1.executeUpdate("UPDATE Contents SET Seq = "+nextSeq+" WHERE Seq = "+Seq+" AND Code = "+code);
						stmt1.executeUpdate("UPDATE Contents SET Seq = "+Seq+" WHERE Seq = 1000 AND Code = "+code);
						
						//Offset = nextOffset; is not include by default
						
						Seq = nextSeq;
					}
					else
					{
						Offset = nextOffset;
						Seq = nextSeq;
					}
				}
				else
				{
					code = rs.getInt("Code");
					Offset = rs.getInt("Offset");
					Seq = rs.getInt("Seq");
				}
				count++;
			}
			con1.setAutoCommit(true);
			out.close();
		}
		catch(Exception e){e.printStackTrace();}
	}
}