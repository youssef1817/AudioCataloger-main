package com.maknoon;

import java.io.*;
import java.sql.*;
import java.net.*;

public class MissingURLs
{
	public static void main(String[] args)
	{
		try
		{
			Connection con;
			if(AudioCataloger.derbyInUse)
			{
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
				con = DriverManager.getConnection("jdbc:derby:db");
			}
			else
			{
				Class.forName("org.h2.Driver");
				con = DriverManager.getConnection("jdbc:h2:db"+System.getProperty("file.separator")+"audioCatalogerDatabase");
			}

			final Statement stmt = con.createStatement();
			final ResultSet rs = stmt.executeQuery("SELECT Path, FileName FROM Chapters");
			final String internetPrefix = "http://www.maknoon.com/download/audios/";
			String internetPath;

			final PrintWriter out = new PrintWriter(new FileWriter("MissingURLs.txt", false));
			HttpURLConnection.setFollowRedirects(false);
			while(rs.next())
			{
				internetPath = internetPrefix + rs.getString("Path") + "/" + rs.getString("FileName");
				internetPath = internetPath.replace('\\', '/');

				final HttpURLConnection con1 = (HttpURLConnection) new URL(internetPath).openConnection();
			    con1.setRequestMethod("HEAD");

				if(con1.getResponseCode() != HttpURLConnection.HTTP_OK)
				{
					out.println(internetPath);
					System.out.println("Not available: "+internetPath);
				}
				else
					System.out.println("Available: "+internetPath);
			}
			out.close();
			con.close();
		}
		catch(Exception e){e.printStackTrace();}
	}
}