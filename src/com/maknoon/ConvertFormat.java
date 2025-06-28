package com.maknoon;

import java.io.*;
import java.sql.*;
import java.util.*;

public class ConvertFormat
{
	public static void main(String[] args)
	{
		try
		{
			Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").getDeclaredConstructor().newInstance();
			//Class.forName("com.mysql.jdbc.Driver");
			//Connection sql = DriverManager.getConnection("jdbc:mysql:///test"+"?socketFactory=com.mysql.jdbc.NamedPipeSocketFactory", "root", "secret");
			//Connection access = DriverManager.getConnection("jdbc:odbc:;DRIVER=Microsoft Access Driver (*.mdb);DBQ=E:\\AudioCataloger\\Access DB Versions\\Words.mdb;PWD=mypass","","");

			Properties prop = new Properties();
			prop.put("charSet", "UTF-8");
			Connection access = DriverManager.getConnection("jdbc:odbc:salam",prop);
			//Statement stmt = access .createStatement();
			//ResultSet rs = stmt.executeQuery("SELECT * FROM Words");

			//final Statement s = access.createStatement();
			//s.execute("CREATE TABLE New_Words(word VARCHAR(50), wordno INTEGER, root VARCHAR(50), occ INTEGER)");

			StringTokenizer tokens;
			final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("Access DB Versions/Words.txt"), "utf-8"));
			in.skip(1);

			final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream("Access DB Versions/test.txt"), "UTF-8");
			while(in.ready())
			{
				/*
				tokens = new StringTokenizer(in.readLine(), ",");
				String st1 = tokens.nextToken();
				int st2;
				if(st1.indexOf("\"")>-1)
				{
					st2 = Integer.valueOf(tokens.nextToken());
					st1 = st1.replaceAll("\"", "");
				}
				else
				{
					st2 = Integer.valueOf(st1);
					st1="";
				}

				String st3 = tokens.nextToken();
				int st4;
				if(st3.indexOf("\"")>-1)
				{
					st4 = Integer.valueOf(tokens.nextToken());
					st3 = st3.replaceAll("\"", "");
				}
				else
				{
					st4 = Integer.valueOf(st3);
					st3="";
				}

				//s.execute("INSERT INTO New_Words VALUES('"+st1+"', "+st2+", '"+st3+"', "+st4+")");
				//s.execute("INSERT INTO New_Words VALUES('"+new String(st1.getBytes("utf-8"), "utf-16")+"', "+st2+", '"+new String(st3.getBytes("utf-8"), "utf-16")+"', "+st4+")");
				out.write("\""+new String(st1.getBytes("Cp1252"), "Cp1256")+"\","+st2+",\""+new String(st3.getBytes("Cp1252"), "Cp1256")+"\","+st4+System.getProperty("line.separator"));
				*/

				tokens = new StringTokenizer(in.readLine(), ",");
				String st1 = tokens.nextToken();
				tokens.nextToken();
				String st2 = tokens.nextToken();

				if(!st1.equals("\"\"") && !st2.equals("\"\""))
				out.write(st1.replaceAll("\"", "")+","+st2.replaceAll("\"", "")+System.getProperty("line.separator"));
			}
			in.close();
			out.close();

			//while(rs.next())
			//	s.execute("INSERT INTO New_Words VALUES('"+((rs.getString("word")==null)?"":new String(rs.getString("word").getBytes("Cp1252"), "Cp1256"))+"', "+rs.getInt("wordno")+", '"+((rs.getString("root")==null)?"":new String(rs.getString("root").getBytes("Cp1252"), "Cp1256"))+"', "+rs.getInt("occ")+")");

			//sql.close();
			access.close();
		}
		catch(Exception e){e.printStackTrace();}
	}
}