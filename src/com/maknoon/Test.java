package com.maknoon;

import java.sql.*;
import java.util.*;

class Test
{
	public static void main(String[] args)
	{
		try
		{
			Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
			//String database = "jdbc:odbc:Driver={Microsoft Access Driver (*.mdb)};DBQ=E:/AudioCataloger/Access_DB_Versions/User_1.mdb;DriverID=22;READONLY=true}";
			String database = "jdbc:odbc:mdbTEST"; // Through DSN, you need to create it
			Connection con = DriverManager.getConnection( database ,"" ,"");
			Statement stmt1 = con.createStatement();
			Statement stmt2 = con.createStatement();
			Statement stmt3 = con.createStatement();
			ResultSet rs1 = stmt1.executeQuery("SELECT * FROM Contents");
			while(rs1.next())
			{
				String line = rs1.getString("Line");
				int code = rs1.getInt("Code"); // You cannot get the same column twice using ODBC !!! That why we store the values if used more than once.
				int offset = rs1.getInt("Offset");
				if(line.indexOf('-')>-1 && line.indexOf('-')<5)
				{
					StringTokenizer tokens = new StringTokenizer(line, "-");
					int seq = Integer.parseInt(tokens.nextToken().trim());
					stmt2.executeUpdate("UPDATE Contents SET Seq = "+seq+" WHERE Code = "+code+" AND Line = '"+line+"'"); // once you update stmt1 in odbc, you cannot use rs1 since it is closed
					stmt2.executeUpdate("UPDATE Contents SET Line = '"+line.substring(line.indexOf('-')+1).trim()+"' WHERE Code = "+code+" AND Line = '"+line+"'");
					line = line.substring(line.indexOf('-')+1).trim(); // Used in the condition in other update down
				}
				else
					System.out.println("Error in 'Line' Code: "+code+" Offset: "+offset);

				int category = 0;
				String Complete_category_name = rs1.getString("Complete_category_name");
				if(Complete_category_name==null)
				{
					System.out.println("Empty 'Complete_category_name' Code: "+code+" Offset: "+offset);
					continue;
				}

				StringTokenizer tokens = new StringTokenizer(Complete_category_name, "\\");
				boolean cont = false;
				while(tokens.hasMoreTokens())
				{
					cont = true;
					ResultSet rs3 = stmt3.executeQuery("SELECT * FROM Category WHERE Category_name = '"+tokens.nextToken()+"' AND Category_parent = "+category);
					if(rs3.next())
					{
						int tmp = rs3.getInt("Category_parent");
						if(category==0 && tmp!=0)
						{
							cont = false;
							break;
						}
						category = rs3.getInt("Category_id");
					}
					else
					{
						System.out.print("In Category ");
						cont = false;
						break;
					}
				}

				if(cont)
					stmt2.executeUpdate("UPDATE Contents SET Category_id = "+category+" WHERE Code = "+code+" AND Line = '"+line+"'");
				else
					System.out.println("Error 'Complete_category_name' Code: "+code+" Offset: "+offset);
			}
			stmt1.close();
			stmt2.close();
			stmt3.close();
		}
		catch(Exception e){e.printStackTrace();}
	}
}