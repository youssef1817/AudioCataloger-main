package com.maknoon;

import org.sqlite.SQLiteConfig;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public final class CreateSQLiteDB
{
	public static void main(String[] args)
	{
		new CreateSQLiteDB();
	}

	private CreateSQLiteDB()
	{
		final ClassLoader cl = CreateSQLiteDB.class.getClassLoader();
		try
		{
			Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
			Class.forName("org.h2.Driver");
			Class.forName("org.sqlite.JDBC");

			final SQLiteConfig config = new SQLiteConfig();
			config.setEncoding(SQLiteConfig.Encoding.UTF8);

			final Connection memoryCon = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
			final Connection sqliteCon = DriverManager.getConnection("jdbc:sqlite:database.db", config.toProperties());
			final Connection sqliteCon2 = DriverManager.getConnection("jdbc:sqlite:contents.db", config.toProperties());
			final Connection con = DriverManager.getConnection("jdbc:derby:" + new File(cl.getResource("db").getFile()).getAbsolutePath());

			final FileWriter fw = new FileWriter("fiqh_web.sql", StandardCharsets.UTF_8);
			final PrintWriter bw = new PrintWriter(fw);

			bw.println("CREATE TABLE Sheekh(Sheekh_id TINYINT, Sheekh_name VARCHAR(100), PRIMARY KEY (Sheekh_id)) CHARSET=utf8;");
			bw.println("CREATE TABLE Book(Book_id SMALLINT, Book_name VARCHAR(100), Sheekh_id TINYINT, Multi_volume BOOLEAN, PRIMARY KEY(Book_id)) CHARSET=utf8;");
			bw.println("CREATE TABLE Chapters(Code SMALLINT UNSIGNED, Sheekh_id TINYINT, Book_id SMALLINT, Book_name VARCHAR(100), Sheekh_name VARCHAR(100), Title VARCHAR(100), FileName VARCHAR(500), Duration INTEGER UNSIGNED, Path VARCHAR(150), Multi_volume BOOLEAN, PRIMARY KEY(Code)) CHARSET=utf8;");
			bw.println("CREATE TABLE Contents(Code SMALLINT, Seq TINYINT UNSIGNED, Sheekh_id TINYINT, Book_id SMALLINT, `Offset` INTEGER UNSIGNED, Duration INTEGER UNSIGNED, Category_id TEXT, Line VARCHAR(20000), Tafreeg TEXT(65535), FileName VARCHAR(500), Path VARCHAR(150), Book_name VARCHAR(100), Sheekh_name VARCHAR(100), Title VARCHAR(100)) CHARSET=utf8;");
			bw.println("CREATE TABLE Category(Category_id SMALLINT, Category_name VARCHAR(100), Category_parent SMALLINT UNSIGNED, PRIMARY KEY(Category_id)) CHARSET=utf8;");

			// Using setAutoCommit(false) then INSERT then setAutoCommit(true) will boost the speed.
			sqliteCon.setAutoCommit(false);
			sqliteCon2.setAutoCommit(false);

			final Statement memoryStmt = memoryCon.createStatement();
			final Statement sqliteStmt = sqliteCon.createStatement();
			final Statement sqliteStmt2 = sqliteCon2.createStatement();
			final Statement stmt = con.createStatement();

			// Performance
			//sqliteStmt.execute("PRAGMA page_size = 2048"); // Didn't do anything, just increase the size

			// rowid INTEGER PRIMARY KEY AUTOINCREMENT is a requirement for android database
			sqliteStmt.execute("CREATE TABLE Sheekh(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Sheekh_id INTEGER, Sheekh_name TEXT)");
			sqliteStmt.execute("CREATE TABLE Book(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Book_id INTEGER, Book_name TEXT, Sheekh_id INTEGER, Multi_volume INTEGER)");
			sqliteStmt.execute("CREATE TABLE Chapters(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Code INTEGER, Sheekh_id INTEGER, Book_id INTEGER, Book_name TEXT, Sheekh_name TEXT, Title TEXT, FileName TEXT, Duration INTEGER, Path TEXT)");
			sqliteStmt2.execute("CREATE TABLE Contents(Code INTEGER, Seq INTEGER, Sheekh_id INTEGER, Book_id INTEGER, \"Offset\" INTEGER, Duration INTEGER, Category_id TEXT, Line TEXT, Tafreeg TEXT)"); // CLOB, CHAR, VARCHAR, TEXT are all TEXT in sqlite3. rowid is removed since this table will be converted to fts3,
			memoryStmt.execute("CREATE TABLE Contents(Code INTEGER, Seq INTEGER, Sheekh_id INTEGER, Book_id INTEGER, \"Offset\" INTEGER, Duration INTEGER, Category_id TEXT, Line TEXT, Tafreeg TEXT, FileName TEXT, Path TEXT, Book_name TEXT, Sheekh_name TEXT, Title TEXT)");
			sqliteStmt.execute("CREATE TABLE Category(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Category_id INTEGER, Category_name TEXT, Category_parent INTEGER)");
			//sqliteStmt.execute("CREATE TABLE ContentCat(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Code INTEGER, Seq INTEGER, Category_id INTEGER, Sheekh_id INTEGER, Book_id INTEGER)");
			sqliteStmt.execute("CREATE TABLE Favorite(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Reference TEXT, path TEXT, FileName TEXT, \"Offset\" INTEGER, Code INTEGER)");
			//sqliteStmt.execute("CREATE TABLE All_FTS(Code INTEGER, Seq INTEGER, Sheekh_id INTEGER, Sheekh_name TEXT, Book_name TEXT, Offset INTEGER, Duration INTEGER, Title TEXT, FileName TEXT, Path TEXT, Category_id TEXT, Line TEXT, Tafreeg TEXT)");

			// Android requirement
			sqliteStmt.execute("CREATE TABLE android_metadata(locale TEXT)");
			sqliteStmt.execute("INSERT INTO android_metadata VALUES('ar')");

			ResultSet rs = stmt.executeQuery("SELECT * FROM Sheekh");
			while (rs.next())
			{
				final String sql = "INSERT INTO Sheekh(Sheekh_id,Sheekh_name) VALUES(" + rs.getInt("Sheekh_id") + ",'" + rs.getString("Sheekh_name") + "')";
				sqliteStmt.execute(sql);
				bw.println(sql + ";");
			}

			rs = stmt.executeQuery("SELECT * FROM Book");
			while (rs.next())
			{
				final String sql = "INSERT INTO Book(Book_id,Book_name,Sheekh_id,Multi_volume) VALUES(" + rs.getInt("Book_id") + ",'" + rs.getString("Book_name") + "'," + rs.getInt("Sheekh_id") + ',' + (rs.getBoolean("Multi_volume") ? 1 : 0) + ")";
				sqliteStmt.execute(sql);
				bw.println(sql + ";");
			}

			rs = stmt.executeQuery("SELECT * FROM Chapters");
			while (rs.next())
			{
				final String sql = "INSERT INTO Chapters(Code,Sheekh_id,Book_id,Book_name,Sheekh_name,Title,FileName,Duration,Path) VALUES(" + rs.getInt("Code") + ',' + rs.getInt("Sheekh_id") + ',' + rs.getInt("Book_id") + ",'" + rs.getString("Book_name") + "','" + rs.getString("Sheekh_name") +
						"','" + rs.getString("Title") + "','" + rs.getString("FileName") + "', " + rs.getInt("Duration") + ", '" + rs.getString("Path").replaceAll("\\\\", "/") + "')";
				sqliteStmt.execute(sql);
			}

			rs = stmt.executeQuery("SELECT Code,Chapters.Sheekh_id,Chapters.Book_id,Chapters.Book_name,Sheekh_name,Title,FileName,Duration,Path,Multi_volume FROM Chapters,Book WHERE Chapters.Book_id = Book.Book_id");
			while (rs.next())
			{
				final String sql = "INSERT INTO Chapters(Code,Sheekh_id,Book_id,Book_name,Sheekh_name,Title,FileName,Duration,Path,Multi_volume) VALUES(" + rs.getInt("Code") + ',' + rs.getInt("Sheekh_id") + ',' + rs.getInt("Book_id") + ",'" + rs.getString("Book_name") + "','" + rs.getString("Sheekh_name") +
						"','" + rs.getString("Title") + "','" + rs.getString("FileName") + "', " + rs.getInt("Duration") + ", '" + rs.getString("Path").replaceAll("\\\\", "/") + "'," + (rs.getBoolean("Multi_volume") ? 1 : 0) + ")";
				bw.println(sql + ";");
			}

			rs = stmt.executeQuery("SELECT Contents.Code,Seq,Contents.Sheekh_id,Contents.Book_id,Offset,Contents.Duration,',' AS Category_id,Line,Tafreeg,FileName,Path,Book_name,Sheekh_name,Title FROM Contents,Chapters WHERE Contents.Code = Chapters.Code");
			while (rs.next())
			{
				final PreparedStatement ps1 = memoryCon.prepareStatement("INSERT INTO Contents VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)");
				ps1.setInt(1, rs.getInt("Code"));
				ps1.setInt(2, rs.getInt("Seq"));
				ps1.setInt(3, rs.getInt("Sheekh_id"));
				ps1.setInt(4, rs.getInt("Book_id"));
				ps1.setInt(5, rs.getInt("Offset"));
				ps1.setInt(6, rs.getInt("Duration"));
				ps1.setString(7, rs.getString("Category_id"));
				ps1.setString(8, rs.getString("Line"));

				// Only fill one chapter with tafreeq for testing, others should be empty to reduce the size of the DB for android.
				//if(rs.getInt("Code")==1)
				ps1.setString(9, rs.getString("Tafreeg"));
				//else
				//    ps1.setString(9, "");

				// For web DB only
				ps1.setString(10, rs.getString("FileName"));
				ps1.setString(11, rs.getString("Path").replaceAll("\\\\", "/"));
				ps1.setString(12, rs.getString("Book_name"));
				ps1.setString(13, rs.getString("Sheekh_name"));
				ps1.setString(14, rs.getString("Title"));

				ps1.execute();
				ps1.close();
			}

            /*
            rs = stmt.executeQuery("SELECT Contents.Code AS Code,Seq,Contents.Sheekh_id AS Sheekh_id,Sheekh_name,Book_name,Offset,Contents.Duration AS Duration,Title,FileName,Path,',' AS Category_id,Line,Tafreeg FROM Contents JOIN Chapters ON Contents.Code = Chapters.Code");
            while(rs.next())
            {
                final PreparedStatement ps1 = sqliteCon.prepareStatement("INSERT INTO All_FTS VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)");
                ps1.setInt(1, rs.getInt("Code"));
                ps1.setInt(2, rs.getInt("Seq"));
                ps1.setInt(3, rs.getInt("Sheekh_id"));
                ps1.setString(4, rs.getString("Sheekh_name"));
                ps1.setString(5, rs.getString("Book_name"));
                ps1.setInt(6, rs.getInt("Offset"));
                ps1.setInt(7, rs.getInt("Duration"));
                ps1.setString(8, rs.getString("Title"));
                ps1.setString(9, rs.getString("FileName"));
                ps1.setString(10, rs.getString("Path"));
                ps1.setString(11, rs.getString("Category_id"));
                ps1.setString(12, rs.getString("Line"));

                // Only fill one chapter with tafreeq for testing, others should be empty to reduce the size of the DB for android.
                if(rs.getInt("Code")==1)
                    ps1.setString(13, rs.getString("Tafreeg"));
                else
                    ps1.setString(13, "");
                ps1.execute();
                ps1.close();
            }
            */

			// Temporary for the next operation
			memoryStmt.execute("CREATE INDEX Contents_Code ON Contents(Code)");
			memoryStmt.execute("CREATE INDEX Contents_Seq ON Contents(Seq)");

			rs = stmt.executeQuery("SELECT * FROM Category");
			while (rs.next())
			{
				final String sql = "INSERT INTO Category(Category_id,Category_name,Category_parent) VALUES(" + rs.getInt("Category_id") + ",'" + rs.getString("Category_name") + "', " + rs.getInt("Category_parent") + ")";
				sqliteStmt.execute(sql);
				bw.println(sql + ";");
			}

			rs = stmt.executeQuery("SELECT * FROM ContentCat");
			while (rs.next())
			{
				//sqliteStmt.execute("INSERT INTO ContentCat(Code,Seq,Category_id,Sheekh_id,Book_id) VALUES("+rs.getInt("Code")+", "+rs.getInt("Seq")+", "+rs.getInt("Category_id")+", "+rs.getInt("Sheekh_id")+", "+rs.getInt("Book_id")+ ')');
				//sqliteStmt.execute("UPDATE All_FTS SET Category_id = Category_id || '" + rs.getInt("Category_id") + ",' WHERE All_FTS MATCH 'Code:" + rs.getInt("Code") + " AND Seq:" + rs.getInt("Seq")+"'"); // On FTS tables, normal indexes are not working. The values in the int column are indexed as words
				memoryStmt.execute("UPDATE Contents SET Category_id = Category_id || '" + rs.getInt("Category_id") + ",' WHERE Code = " + rs.getInt("Code") + " AND Seq = " + rs.getInt("Seq")); // sqliteStmt takes very long time. replaced with in memory h2
			}

			rs = memoryStmt.executeQuery("SELECT * FROM Contents");
			while (rs.next())
			{
				final PreparedStatement ps1 = sqliteCon2.prepareStatement("INSERT INTO Contents VALUES(?,?,?,?,?,?,?,?,?)");
				ps1.setInt(1, rs.getInt("Code"));
				ps1.setInt(2, rs.getInt("Seq"));
				ps1.setInt(3, rs.getInt("Sheekh_id"));
				ps1.setInt(4, rs.getInt("Book_id"));
				ps1.setInt(5, rs.getInt("Offset"));
				ps1.setInt(6, rs.getInt("Duration"));
				ps1.setString(7, rs.getString("Category_id"));
				ps1.setString(8, rs.getString("Line"));
				ps1.setString(9, rs.getString("Tafreeg"));
				ps1.execute();
				ps1.close();

				final String tafreeg = rs.getString("Tafreeg");
				bw.println("INSERT INTO Contents(Code,Seq,Sheekh_id,Book_id,Offset,Duration,Category_id,Line,Tafreeg,FileName,Path,Book_name,Sheekh_name,Title) VALUES(" + rs.getInt("Code") + ',' + rs.getInt("Seq") + ',' + rs.getInt("Sheekh_id") + "," + rs.getInt("Book_id") + "," + rs.getInt("Offset") +
						"," + rs.getInt("Duration") + ",'" + rs.getString("Category_id") + "', '" + rs.getString("Line") + "','" +
						tafreeg
								.replace("\\", "\\\\")
								.replace("\"", "\\\"")
								.replace("'", "\\'")
								.replace("\r\n", "\\r\\n")
								//.replaceAll("[\n\r]+$","\\\\r\\\\n")
								.replace("\r", "\\r")
								.replace("\n", "\\n")
						+ "','" + rs.getString("FileName") + "','" + rs.getString("Path") + "','" + rs.getString("Book_name") + "','" + rs.getString("Sheekh_name") + "','" + rs.getString("Title") + "');");
			}

			bw.println("CREATE INDEX Book_Sheekh_id ON Book(Sheekh_id);");
			bw.println("CREATE INDEX Chapters_Book_id ON Chapters(Book_id);");
			bw.println("CREATE INDEX Chapters_Code ON Chapters(Code);");
			bw.println("CREATE INDEX Category_Category_parent ON Category(Category_parent);");
			bw.println("CREATE INDEX Category_Category_id ON Category(Category_id);");

			//sqliteStmt.execute("DROP TABLE Contents");
			//sqliteStmt.execute("DROP INDEX All_FTS_Code");
			//sqliteStmt.execute("DROP INDEX All_FTS_Seq");

			//sqliteStmt.execute("CREATE INDEX Sheekh_Sheekh_id ON Sheekh(Sheekh_id)");
			//sqliteStmt.execute("CREATE INDEX Book_Book_id ON Book(Book_id)");
			//sqliteStmt.execute("CREATE INDEX Book_Sheekh_id ON Book(Sheekh_id)");
			//sqliteStmt.execute("CREATE INDEX Chapters_Code ON Chapters(Code)");//CREATE UNIQUE INDEX ChaptersCodeIndex ON Chapters(Code), try it and check the DB size
			//sqliteStmt.execute("CREATE INDEX Chapters_Book_id ON Chapters(Book_id)");
			//sqliteStmt.execute("CREATE INDEX Chapters_Sheekh_id ON Chapters(Sheekh_id)");
			//sqliteStmt.execute("CREATE INDEX Contents_Code ON Contents(Code)");
			//sqliteStmt.execute("CREATE INDEX Contents_Book_id ON Contents(Book_id)");
			//sqliteStmt.execute("CREATE INDEX Contents_Seq ON Contents(Seq)");
			//sqliteStmt.execute("CREATE INDEX ContentCat_Category_id ON ContentCat(Category_id)");
			//sqliteStmt.execute("CREATE INDEX ContentCat_Book_id ON ContentCat(Book_id)");
			//sqliteStmt.execute("CREATE INDEX ContentCat_Sheekh_id ON ContentCat(Sheekh_id)");
			//sqliteStmt.execute("CREATE INDEX ContentCat_Seq ON ContentCat(Seq)");
			//sqliteStmt.execute("CREATE INDEX ContentCat_Code ON ContentCat(Code)");
			//sqliteStmt.execute("CREATE INDEX Category_Category_parent ON Category(Category_parent)");
			//sqliteStmt.execute("CREATE INDEX Category_Category_id ON Category(Category_id)");
			// Use Covering Indices [http://www.sqlite.org/queryplanner.html#covidx]. The problem not from indices but from the mCursor.getCount()/mCursor.moveToFirst() i.e. fetching the results.

			sqliteCon.setAutoCommit(true); // 'VACUUM FULL' will not work without this
			sqliteCon2.setAutoCommit(true);
			//sqliteStmt.execute("VACUUM FULL"); // not useful since we use setAutoCommit(false)

			sqliteCon.close();
			sqliteCon2.close();
			memoryCon.close();
			con.close();
			bw.close();
			fw.close();

			DriverManager.getConnection("jdbc:derby:;shutdown=true");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	// Including FTS4 so that no need to recreate the FTS4 in android saving a lot of time for first time initiation
	// TODO: file size is big more than 100MB compressed (188MB), hence google play will not accept it
	private void CreateSQLiteDB_with_fts4()
	{
		final ClassLoader cl = CreateSQLiteDB.class.getClassLoader();
		try
		{
			Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
			Class.forName("org.h2.Driver");
			Class.forName("org.sqlite.JDBC");

			final SQLiteConfig config = new SQLiteConfig();
			config.setEncoding(SQLiteConfig.Encoding.UTF8);

			final Connection memoryCon = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
			final Connection sqliteCon = DriverManager.getConnection("jdbc:sqlite:database.db", config.toProperties());
			final Connection con = DriverManager.getConnection("jdbc:derby:" + new File(cl.getResource("db").getFile()).getAbsolutePath());

			// Using setAutoCommit(false) then INSERT then setAutoCommit(true) will boost the speed.
			sqliteCon.setAutoCommit(false);

			final Statement memoryStmt = memoryCon.createStatement();
			final Statement sqliteStmt = sqliteCon.createStatement();
			final Statement stmt = con.createStatement();

			// Performance
			//sqliteStmt.execute("PRAGMA page_size = 2048"); // Didn't do anything, just increase the size

			// rowid INTEGER PRIMARY KEY AUTOINCREMENT is a requirement for android database
			sqliteStmt.execute("CREATE TABLE Sheekh(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Sheekh_id INTEGER, Sheekh_name TEXT)");
			sqliteStmt.execute("CREATE TABLE Book(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Book_id INTEGER, Book_name TEXT, Sheekh_id INTEGER, Multi_volume INTEGER)");
			sqliteStmt.execute("CREATE TABLE Chapters(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Code INTEGER, Sheekh_id INTEGER, Book_id INTEGER, Book_name TEXT, Sheekh_name TEXT, Title TEXT, FileName TEXT, Duration INTEGER, Path TEXT)");
			memoryStmt.execute("CREATE TABLE Contents(Code INTEGER, Seq INTEGER, Sheekh_id INTEGER, Book_id INTEGER, \"Offset\" INTEGER, Duration INTEGER, Category_id TEXT, Line TEXT, Tafreeg TEXT)");
			sqliteStmt.execute("CREATE TABLE Category(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Category_id INTEGER, Category_name TEXT, Category_parent INTEGER)");
			//sqliteStmt.execute("CREATE TABLE ContentCat(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Code INTEGER, Seq INTEGER, Category_id INTEGER, Sheekh_id INTEGER, Book_id INTEGER)");
			sqliteStmt.execute("CREATE TABLE Favorite(rowid INTEGER PRIMARY KEY AUTOINCREMENT, Reference TEXT, path TEXT, FileName TEXT, \"Offset\" INTEGER, Code INTEGER)");
			//sqliteStmt.execute("CREATE TABLE All_FTS(Code INTEGER, Seq INTEGER, Sheekh_id INTEGER, Sheekh_name TEXT, Book_name TEXT, Offset INTEGER, Duration INTEGER, Title TEXT, FileName TEXT, Path TEXT, Category_id TEXT, Line TEXT, Tafreeg TEXT)");

			//sqliteStmt2.execute("CREATE TABLE Contents(Code INTEGER, Seq INTEGER, Sheekh_id INTEGER, Book_id INTEGER, \"Offset\" INTEGER, Duration INTEGER, Category_id TEXT, Line TEXT, Tafreeg TEXT)"); // CLOB, CHAR, VARCHAR, TEXT are all TEXT in sqlite3. rowid is removed since this table will be converted to fts3
			sqliteStmt.execute("CREATE VIRTUAL TABLE Contents_FTS USING fts4 (Code INTEGER, Seq INTEGER, Sheekh_id INTEGER, Book_id INTEGER, \"Offset\" INTEGER, Duration INTEGER, Category_id TEXT, Line TEXT, Tafreeg TEXT)"); // Android 4.2 is not supporting FTS4 notindexed only 5.0 is supporting this. TODO: Please add once we start using 5.0 ..... notindexed=Seq, notindexed=Book_id, notindexed=Offset, notindexed=Duration, notindexed=Tafreeg

			// Android requirement
			sqliteStmt.execute("CREATE TABLE android_metadata(locale TEXT)");
			sqliteStmt.execute("INSERT INTO android_metadata VALUES('ar')");

			ResultSet rs = stmt.executeQuery("SELECT Sheekh_id, Sheekh_name FROM Sheekh");
			while (rs.next())
				sqliteStmt.execute("INSERT INTO Sheekh(Sheekh_id,Sheekh_name) VALUES(" + rs.getInt("Sheekh_id") + ",'" + rs.getString("Sheekh_name") + "')");

			rs = stmt.executeQuery("SELECT * FROM Book");
			while (rs.next())
				sqliteStmt.execute("INSERT INTO Book(Book_id,Book_name,Sheekh_id,Multi_volume) VALUES(" + rs.getInt("Book_id") + ",'" + rs.getString("Book_name") + "'," + rs.getInt("Sheekh_id") + ',' + (rs.getBoolean("Multi_volume") ? 1 : 0) + ")");

			rs = stmt.executeQuery("SELECT * FROM Chapters");
			while (rs.next())
				sqliteStmt.execute("INSERT INTO Chapters(Code,Sheekh_id,Book_id,Book_name,Sheekh_name,Title,FileName,Duration,Path) VALUES(" + rs.getInt("Code") + ',' + rs.getInt("Sheekh_id") + ',' + rs.getInt("Book_id") + ",'" + rs.getString("Book_name") + "','" + rs.getString("Sheekh_name") +
						"','" + rs.getString("Title") + "','" + rs.getString("FileName") + "', " + rs.getInt("Duration") + ", '" + rs.getString("Path").replaceAll("\\\\", "/") + "')");

			rs = stmt.executeQuery("SELECT Code,Seq,Sheekh_id,Book_id,Offset,Duration,',' AS Category_id,Line,Tafreeg FROM Contents");
			while (rs.next())
			{
				final PreparedStatement ps1 = memoryCon.prepareStatement("INSERT INTO Contents VALUES(?,?,?,?,?,?,?,?,?)");
				ps1.setInt(1, rs.getInt("Code"));
				ps1.setInt(2, rs.getInt("Seq"));
				ps1.setInt(3, rs.getInt("Sheekh_id"));
				ps1.setInt(4, rs.getInt("Book_id"));
				ps1.setInt(5, rs.getInt("Offset"));
				ps1.setInt(6, rs.getInt("Duration"));
				ps1.setString(7, rs.getString("Category_id"));
				ps1.setString(8, rs.getString("Line"));

				// Only fill one chapter with tafreeq for testing, others should be empty to reduce the size of the DB for android.
				//if(rs.getInt("Code")==1)
				ps1.setString(9, rs.getString("Tafreeg"));
				//else
				//    ps1.setString(9, "");
				ps1.execute();
				ps1.close();
			}

            /*
            rs = stmt.executeQuery("SELECT Contents.Code AS Code,Seq,Contents.Sheekh_id AS Sheekh_id,Sheekh_name,Book_name,Offset,Contents.Duration AS Duration,Title,FileName,Path,',' AS Category_id,Line,Tafreeg FROM Contents JOIN Chapters ON Contents.Code = Chapters.Code");
            while(rs.next())
            {
                final PreparedStatement ps1 = sqliteCon.prepareStatement("INSERT INTO All_FTS VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)");
                ps1.setInt(1, rs.getInt("Code"));
                ps1.setInt(2, rs.getInt("Seq"));
                ps1.setInt(3, rs.getInt("Sheekh_id"));
                ps1.setString(4, (rs.getString("Sheekh_name")));
                ps1.setString(5, (rs.getString("Book_name")));
                ps1.setInt(6, (rs.getInt("Offset")));
                ps1.setInt(7, (rs.getInt("Duration")));
                ps1.setString(8, (rs.getString("Title")));
                ps1.setString(9, rs.getString("FileName"));
                ps1.setString(10, (rs.getString("Path")));
                ps1.setString(11, rs.getString("Category_id"));
                ps1.setString(12, rs.getString("Line"));

                // Only fill one chapter with tafreeq for testing, others should be empty to reduce the size of the DB for android.
                if(rs.getInt("Code")==1)
                    ps1.setString(13, rs.getString("Tafreeg"));
                else
                    ps1.setString(13, "");
                ps1.execute();
                ps1.close();
            }
            */

			// Temporary for the next operation
			memoryStmt.execute("CREATE INDEX Contents_Code ON Contents(Code)");
			memoryStmt.execute("CREATE INDEX Contents_Seq ON Contents(Seq)");

			rs = stmt.executeQuery("SELECT * FROM Category");
			while (rs.next())
				sqliteStmt.execute("INSERT INTO Category(Category_id,Category_name,Category_parent) VALUES(" + rs.getInt("Category_id") + ",'" + rs.getString("Category_name") + "', " + rs.getInt("Category_parent") + ')');

			rs = stmt.executeQuery("SELECT * FROM ContentCat");
			while (rs.next())
			{
				//sqliteStmt.execute("INSERT INTO ContentCat(Code,Seq,Category_id,Sheekh_id,Book_id) VALUES("+rs.getInt("Code")+", "+rs.getInt("Seq")+", "+rs.getInt("Category_id")+", "+rs.getInt("Sheekh_id")+", "+rs.getInt("Book_id")+ ')');
				//sqliteStmt.execute("UPDATE All_FTS SET Category_id = Category_id || '" + rs.getInt("Category_id") + ",' WHERE All_FTS MATCH 'Code:" + rs.getInt("Code") + " AND Seq:" + rs.getInt("Seq")+"'"); // On FTS tables, normal indexes are not working. The values in the int column are indexed as words
				memoryStmt.execute("UPDATE Contents SET Category_id = Category_id || '" + rs.getInt("Category_id") + ",' WHERE Code = " + rs.getInt("Code") + " AND Seq = " + rs.getInt("Seq")); // sqliteStmt takes very long time. replaced with in memory h2
			}

			rs = memoryStmt.executeQuery("SELECT * FROM Contents");
			while (rs.next())
			{
				final PreparedStatement ps1 = sqliteCon.prepareStatement("INSERT INTO Contents_FTS VALUES(?,?,?,?,?,?,?,?,?)");
				ps1.setInt(1, rs.getInt("Code"));
				ps1.setInt(2, rs.getInt("Seq"));
				ps1.setInt(3, rs.getInt("Sheekh_id"));
				ps1.setInt(4, rs.getInt("Book_id"));
				ps1.setInt(5, rs.getInt("Offset"));
				ps1.setInt(6, rs.getInt("Duration"));
				ps1.setString(7, rs.getString("Category_id"));
				ps1.setString(8, rs.getString("Line"));
				ps1.setString(9, rs.getString("Tafreeg"));
				ps1.execute();
				ps1.close();
			}

			//sqliteStmt.execute("DROP TABLE Contents");
			//sqliteStmt.execute("DROP INDEX All_FTS_Code");
			//sqliteStmt.execute("DROP INDEX All_FTS_Seq");

			sqliteStmt.execute("CREATE INDEX Book_Sheekh_id ON Book(Sheekh_id)");
			sqliteStmt.execute("CREATE INDEX Chapters_Book_id ON Chapters(Book_id)");
			sqliteStmt.execute("CREATE INDEX Chapters_Code ON Chapters(Code)");
			sqliteStmt.execute("CREATE INDEX Category_Category_parent ON Category(Category_parent)");
			sqliteStmt.execute("CREATE INDEX Category_Category_id ON Category(Category_id)");

			//sqliteStmt.execute("CREATE INDEX Sheekh_Sheekh_id ON Sheekh(Sheekh_id)");
			//sqliteStmt.execute("CREATE INDEX Book_Book_id ON Book(Book_id)");
			//sqliteStmt.execute("CREATE INDEX Book_Sheekh_id ON Book(Sheekh_id)");
			//sqliteStmt.execute("CREATE INDEX Chapters_Code ON Chapters(Code)");//CREATE UNIQUE INDEX ChaptersCodeIndex ON Chapters(Code), try it and check the DB size
			//sqliteStmt.execute("CREATE INDEX Chapters_Book_id ON Chapters(Book_id)");
			//sqliteStmt.execute("CREATE INDEX Chapters_Sheekh_id ON Chapters(Sheekh_id)");
			//sqliteStmt.execute("CREATE INDEX Contents_Code ON Contents(Code)");
			//sqliteStmt.execute("CREATE INDEX Contents_Book_id ON Contents(Book_id)");
			//sqliteStmt.execute("CREATE INDEX Contents_Seq ON Contents(Seq)");
			//sqliteStmt.execute("CREATE INDEX ContentCat_Category_id ON ContentCat(Category_id)");
			//sqliteStmt.execute("CREATE INDEX ContentCat_Book_id ON ContentCat(Book_id)");
			//sqliteStmt.execute("CREATE INDEX ContentCat_Sheekh_id ON ContentCat(Sheekh_id)");
			//sqliteStmt.execute("CREATE INDEX ContentCat_Seq ON ContentCat(Seq)");
			//sqliteStmt.execute("CREATE INDEX ContentCat_Code ON ContentCat(Code)");
			//sqliteStmt.execute("CREATE INDEX Category_Category_parent ON Category(Category_parent)");
			//sqliteStmt.execute("CREATE INDEX Category_Category_id ON Category(Category_id)");
			// Use Covering Indices [http://www.sqlite.org/queryplanner.html#covidx]. The problem not from indices but from the mCursor.getCount()/mCursor.moveToFirst() i.e. fetching the results.

			sqliteCon.setAutoCommit(true); // 'VACUUM FULL' will not work without this
			//sqliteStmt.execute("VACUUM FULL"); // not useful since we use setAutoCommit(false)

			sqliteCon.close();
			memoryCon.close();
			con.close();

			DriverManager.getConnection("jdbc:derby:;shutdown=true");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}