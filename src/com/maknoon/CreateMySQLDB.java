package com.maknoon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public final class CreateMySQLDB
{
	public static void main(String[] args)
	{
		new CreateMySQLDB();
	}

	private CreateMySQLDB()
	{
		final ClassLoader cl = CreateMySQLDB.class.getClassLoader();
		try
		{
			Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
			final Connection con = DriverManager.getConnection("jdbc:derby:" + new File(cl.getResource("db").getFile()).getAbsolutePath());

			final FileWriter fw1 = new FileWriter("UrlShortener_chapter.sql", StandardCharsets.UTF_8);
			final BufferedWriter writer1 = new BufferedWriter(fw1);

			writer1.append("CREATE TABLE Chapters(id INT UNSIGNED NOT NULL AUTO_INCREMENT, code INT UNSIGNED NOT NULL, url VARCHAR(1000) NOT NULL, PRIMARY KEY (id), UNIQUE (url)) CHARSET=utf8;");
			writer1.newLine();

			final Statement stmt = con.createStatement();

			ResultSet rs = stmt.executeQuery("SELECT * FROM Chapters");
			while (rs.next())
			{
				writer1.append("INSERT INTO Chapters(Code,url) VALUES(" + rs.getInt("Code") + ",'" + rs.getString("Path").replaceAll("\\\\", "/") + "/" + rs.getString("FileName") + ".m4a" + "');");
				writer1.newLine();
			}

			writer1.append("CREATE INDEX Chapters_Code ON Chapters(Code);");
			writer1.newLine();

			writer1.close();
			fw1.close();

			final FileWriter fw2 = new FileWriter("UrlShortener_chapter_part.sql", StandardCharsets.UTF_8);
			final BufferedWriter writer2 = new BufferedWriter(fw2);

			writer2.append("CREATE TABLE Parts(id INT UNSIGNED NOT NULL AUTO_INCREMENT, code INT UNSIGNED NOT NULL, seq INT UNSIGNED NOT NULL, url VARCHAR(1000) NOT NULL, PRIMARY KEY (id), UNIQUE (url)) CHARSET=utf8;");
			writer2.newLine();

			rs = stmt.executeQuery("SELECT * FROM Contents, Chapters WHERE Contents.Code=Chapters.Code ORDER BY Contents.Code, Contents.Seq");
			while (rs.next())
			{
				writer2.append("INSERT INTO Parts(Code,Seq,url) VALUES(" + rs.getInt("Code") + "," + rs.getInt("Seq") + ",'" + rs.getString("Path").replaceAll("\\\\", "/") + "/" + rs.getString("FileName") + "/" + rs.getInt("Seq") + ".m4a" + "');");
				writer2.newLine();
			}

			writer2.append("CREATE INDEX Parts_Code ON Parts(Code);");
			writer2.newLine();
			writer2.append("CREATE INDEX Parts_Seq ON Parts(Seq);");
			writer2.newLine();

			writer2.close();
			fw2.close();

			con.close();

			DriverManager.getConnection("jdbc:derby:;shutdown=true");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}