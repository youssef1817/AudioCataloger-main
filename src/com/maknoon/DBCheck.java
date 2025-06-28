package com.maknoon;

import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.io.*;
import java.util.*;

public class DBCheck
{
	public static void main(String[] args){new DBCheck();}
	public DBCheck()
	{
		final ClassLoader cl = DBCheck.class.getClassLoader();

		try
		{
            //final String programFolder = new File(DBCheck.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath()+"/";

			Connection con;
			if(AudioCataloger.derbyInUse)
			{
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
				con = DriverManager.getConnection("jdbc:derby:" + new File(cl.getResource("db").toURI()).getAbsolutePath());
			}
			else
			{
				Class.forName("org.h2.Driver");
				con = DriverManager.getConnection("jdbc:h2:" + new File(cl.getResource("db").toURI()).getAbsolutePath() + "/audioCatalogerDatabase");
			}

			final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream("duplicate_records.txt"), StandardCharsets.UTF_8);
			final Statement stmt1 = con.createStatement();
			final Statement stmt2 = con.createStatement();

			// Update version 2.0, Notify the user that there is a duplicate in the file path which should not be allowed.
			// Update version 2.2, Only display the ones that are related to this update.
			final PreparedStatement ps1 = con.prepareStatement("SELECT Code FROM Chapters WHERE FileName=? AND FileType=? AND Path=?");
			final Vector<String> Code = new Vector<>();
			final ResultSet rs1 = stmt1.executeQuery("SELECT FileName, FileType, Path FROM Chapters GROUP BY FileName, FileType, Path HAVING COUNT(*) > 1");
			while(rs1.next())
			{
				ps1.setString(1, rs1.getString("FileName"));
				ps1.setString(2, rs1.getString("FileType"));
				ps1.setString(3, rs1.getString("Path"));
				final ResultSet rs2 = ps1.executeQuery();
				Code.removeAllElements();
				while(rs2.next())
					Code.addElement(rs2.getString("Code"));

				//if((new Vector<String>(Code)).removeAll(Chapters_Code)) // i.e. one element from Code is contained in Chapters_Code
				//{
					String line = "الشريط ("+rs1.getString("Path")+'\\'+rs1.getString("FileName")+'.'+rs1.getString("FileType")+") مكرر في ";
					final ResultSet rs3 = stmt2.executeQuery("SELECT Book_name, Sheekh_name, Title FROM Chapters WHERE Code IN ("+Code.toString().substring(1, Code.toString().length()-1)+')');
					while(rs3.next())
						line = line +'['+rs3.getString("Sheekh_name")+"←"+rs3.getString("Book_name")+"←"+rs3.getString("Title")+'('+rs1.getString("FileName")+")]";
				//}
				out.write(line+System.getProperty("line.separator"));
			}
			out.close();

			if(AudioCataloger.derbyInUse)
			{
                try{DriverManager.getConnection("jdbc:derby:;shutdown=true");} // This will throw exception in normal case.
                catch(Exception e){e.printStackTrace();}
			}
            else
            {
                final Statement s = con.createStatement();
                s.execute("SHUTDOWN COMPACT");
            }

			System.out.println("Finished. Please check 'duplicate_records.txt' file.");
		}
		catch(Exception e){e.printStackTrace();}
	}
}