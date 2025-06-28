package com.maknoon;

import java.io.*;
import java.sql.*;
import java.util.*;

// Testing the audio files.
public class TestFiles
{
	public static void main(String[] args)
	{
		//TestDBFiles();
		TestEmptyFiles();
	}

	static void TestEmptyFiles()
	{
		final Vector<File> files = new Vector<>();
		getFilesList(new File("E:/AudioCataloger Media/Audios.m4a"), files);
		System.out.println(files.size());

		for (int i = 0; i < files.size(); i++)
		{
			if (files.elementAt(i).length() == 0)
				System.out.println(files.elementAt(i));
		}
	}

	static void TestDBFiles()
	{
		try
		{
			Class.forName("sun.jdbc.odbc.JdbcOdbcDriver").newInstance();
			final Connection con = DriverManager.getConnection("jdbc:odbc:;DRIVER=Microsoft Access Driver (*.mdb);DBQ=E:\\AudioCataloger\\Access_DB_Versions\\index-14.mdb;PWD=secret","","");
			final Statement stmt = con.createStatement();
			final ResultSet rs = stmt.executeQuery("SELECT Path, FileName FROM Chapters"); //  WHERE sheekhid <> 2                   '!='  the same as  '<>'

			final Vector<String> dbFiles = new Vector<String>();
			while(rs.next())
				dbFiles.add("E:\\Maknoon Audio Cataloger Audios\\"+rs.getString("Path")+"\\"+rs.getString("FileName")+".rm");

			con.close();

			final Vector<String> files = new Vector<String>();
			getFileList(new File("E:/Maknoon Audio Cataloger Audios"), files);

			System.out.println(dbFiles.size());
			System.out.println(files.size());

			for(int i=0; i<dbFiles.size(); i++)
			{
				if(files.indexOf(dbFiles.elementAt(i)) == -1)
					System.out.println(dbFiles.elementAt(i));
			}
		}
		catch(Exception e){e.printStackTrace();}
	}

	public static void getFileList(File file, Vector<String> result)
	{
		if (file.isDirectory())
		{
			final File[] child = file.listFiles();
			if (child != null && child.length > 0)
			{
				for (int i = 0; i < child.length; i++)
					getFileList(child[i], result);
			}
		}
		else
			result.add(file.toString());
	}

	public static void getFilesList(File file, Vector<File> result)
	{
		if (file.isDirectory())
		{
			final File[] child = file.listFiles();
			if (child != null && child.length > 0)
			{
				for (int i = 0; i < child.length; i++)
					getFilesList(child[i], result);
			}
		}
		else
			result.add(file);
	}
}