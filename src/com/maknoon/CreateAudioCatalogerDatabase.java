package com.maknoon;

/*
 * Version 1.6
 * Class used to add one column to Contents table for the period
 * of each index instead of calculating it each time. This will save a lot of time.
 *
 * Version 2.7
 * Change MySQL to In-Memory H2 for performance and independence
 * Remove Quran indexes
 */
import java.sql.*;
import java.io.*;
import java.util.*;

class CreateAudioCatalogerDatabase
{
	public static void main(String[] args) throws Exception
	{
		//System.setOut(new PrintStream("output.txt", "UTF8"));
		new CreateAudioCatalogerDatabase();
	}

	static final boolean generate_H2 = false; // It will generate a working empty h2 db (with catalog table) in case of false
	static final boolean generate_derby = true; // It will generate a working empty derby db (with catalog table) in case of false

	CreateAudioCatalogerDatabase()
	{
		long startTime = System.nanoTime();

		try
		{
			Class.forName("org.h2.Driver");
			Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
			//Class.forName("sun.jdbc.odbc.JdbcOdbcDriver"); // Version 3.2, Not working in Java 8, ucanaccess is a replacement

			final Connection memoryCon = DriverManager.getConnection("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1");
			final Connection h2Con = DriverManager.getConnection("jdbc:h2:./db.h2/audioCatalogerDatabase");
			final Connection derbyCon = DriverManager.getConnection("jdbc:derby:db.derby;create=true");
			final Connection AccessCon = DriverManager.getConnection("jdbc:ucanaccess://E:/AudioCataloger Media/Archive/4.6/db-a.mdb");

			// Version 2.7, Copy the DB from MS ACCESS to H2 In-Memory DB
			Statement memoryStmt = memoryCon.createStatement();
			Statement AccessStmt = AccessCon.createStatement();

			memoryStmt.execute("CREATE TABLE Sheekh(Sheekh_id SMALLINT, Sheekh_name VARCHAR(100), PRIMARY KEY(Sheekh_id))");
			ResultSet AccessRs = AccessStmt.executeQuery("SELECT Sheekh_id, Sheekh_name FROM Sheekh");
			while (AccessRs.next())
				// Version 3.0, getBytes("...") instead of getString("...").getBytes() to run it from IDEA with "-Dfile.encoding=UTF-8"
				// Version 3.2, getString("...") instead of getBytes("...") since we are using now ucanaccess
				memoryStmt.execute("INSERT INTO Sheekh VALUES(" + AccessRs.getInt("Sheekh_id") + ",'" + AccessRs.getString("Sheekh_name") + "')");

			memoryStmt.execute("CREATE TABLE Book(Book_id SMALLINT, Book_name VARCHAR(100))");
			AccessRs = AccessStmt.executeQuery("SELECT * FROM Book");
			while (AccessRs.next())
				memoryStmt.execute("INSERT INTO Book VALUES(" + AccessRs.getInt("Book_id") + ",'" + AccessRs.getString("Book_name") + "')");

			memoryStmt.execute("CREATE TABLE Chapters(Code SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT, Title VARCHAR(100), FileName VARCHAR(500), Path VARCHAR(150), CDNumber VARCHAR(50), Chapters_order INTEGER, Short_sheekh_name VARCHAR(50))"); // FileName VARCHAR(500) instead of (20) just for the new indexes from المغربي
			AccessRs = AccessStmt.executeQuery("SELECT * FROM Chapters");
			while (AccessRs.next())
			{
				final PreparedStatement memoryPS = memoryCon.prepareStatement("INSERT INTO Chapters VALUES(" + AccessRs.getInt("Code") + ',' + AccessRs.getInt("Sheekh_id") + ',' + AccessRs.getInt("Book_id") + ",?,?,?,?," + AccessRs.getInt("Chapters_order") + ",?)");
				memoryPS.setString(1, AccessRs.getString("Title"));
				memoryPS.setString(2, AccessRs.getString("FileName"));
				memoryPS.setString(3, AccessRs.getString("Path"));
				memoryPS.setString(4, AccessRs.getString("CDNumber"));
				memoryPS.setString(5, AccessRs.getString("Short_sheekh_name"));
				memoryPS.execute();

				//memoryStmt.execute("INSERT INTO Chapters VALUES("+AccessRs.getInt("Code")+','+AccessRs.getInt("Sheekh_id")+','+AccessRs.getInt("Book_id")+",'"+new String(AccessRs.getBytes("Title"), "cp1256")+"','"+new String(AccessRs.getBytes("FileName"), "cp1256")+"','"+new String(AccessRs.getBytes("Path"), "cp1256")+"','"+new String(AccessRs.getBytes("CDNumber"), "cp1256")+"',"+new String(AccessRs.getBytes("Chapters_order"), "cp1256")+",'"+new String(AccessRs.getBytes("Short_sheekh_name"), "cp1256")+"')"); // Version 2.8
			}

			// H2 Offset is Reserved Keyword. It can't be used as identifiers for table names & column names unless they are quoted. old version h2-1.3.176 does not support OFFSET so it can be used as well.
			memoryStmt.execute("CREATE TABLE Contents(Code SMALLINT, Seq SMALLINT, \"Offset\" INTEGER, Line VARCHAR(20000), Tafreeg LONGVARCHAR)");
			AccessRs = AccessStmt.executeQuery("SELECT * FROM Contents");
			while (AccessRs.next())
			{
				final PreparedStatement memoryPS = memoryCon.prepareStatement("INSERT INTO Contents VALUES(" + AccessRs.getInt("Code") + "," + AccessRs.getInt("Seq") + "," + AccessRs.getInt("Offset") + ",?,?)");
				memoryPS.setString(1, AccessRs.getString("Line").strip()); // Version 4.5, strip() to remove newline/space from the end of string
				memoryPS.setString(2, AccessRs.getString("Tafreeg").strip());
				memoryPS.execute();
			}

			memoryStmt.execute("CREATE TABLE ContentCat(Code SMALLINT, Seq SMALLINT, Category_id SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT)");
			AccessRs = AccessStmt.executeQuery("SELECT * FROM ContentCat");
			while (AccessRs.next())
				memoryStmt.execute("INSERT INTO ContentCat VALUES(" + AccessRs.getInt("Code") + ',' + AccessRs.getInt("Seq") + ',' + AccessRs.getInt("Category_id") + ',' + AccessRs.getInt("Sheekh_id") + ',' + AccessRs.getInt("Book_id") + ')');

			memoryStmt.execute("CREATE TABLE Category(Category_id SMALLINT, Category_name VARCHAR(100), Category_parent SMALLINT)");
			AccessRs = AccessStmt.executeQuery("SELECT * FROM Category");
			while (AccessRs.next())
				memoryStmt.execute("INSERT INTO Category VALUES(" + AccessRs.getInt("Category_id") + ",'" + AccessRs.getString("Category_name") + "'," + AccessRs.getInt("Category_parent") + ')');

			memoryStmt.close();
			AccessCon.close();

			System.out.println("Time to mem db: " + ((double) (System.nanoTime() - startTime) / 1000000000.0));
			startTime = System.nanoTime();

			// Version 2.0, Update Book Table so that each book_id is related to only one Sheekh_id i.e. duplicate the shared Book with different Book_id and update Chapters/Contents.
			Statement memoryStmt1 = memoryCon.createStatement();
			Statement memoryStmt2 = memoryCon.createStatement();
			Statement memoryStmt3 = memoryCon.createStatement(); // Version 2.5, removed (ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE)

			ResultSet memoryRs = memoryStmt1.executeQuery("SELECT MAX(Book_id) AS max_Book_id FROM Book");
			memoryRs.next();
			int max_Book_id = memoryRs.getInt("max_Book_id") + 1;

			memoryRs = memoryStmt1.executeQuery("SELECT * FROM Book");
			while (memoryRs.next())
			{
				ResultSet rs1 = memoryStmt2.executeQuery("SELECT Sheekh_id FROM Chapters WHERE Book_id = " + memoryRs.getString("Book_id") + " GROUP BY Sheekh_id");
				if (rs1.next())
				{
					// We ignore the first row (it will be the original)
					while (rs1.next())
					{
						memoryStmt3.executeUpdate("INSERT INTO Book VALUES(" + max_Book_id + ", '" + memoryRs.getString("Book_name") + "')");
						memoryStmt3.executeUpdate("UPDATE Chapters SET Book_id = " + max_Book_id + " WHERE Book_id = " + memoryRs.getString("Book_id") + " AND Sheekh_id = " + rs1.getString("Sheekh_id"));

						// Version 2.7, DB was not correct before this version because we didn't update ContentCat !
						memoryStmt3.executeUpdate("UPDATE ContentCat SET Book_id = " + max_Book_id + " WHERE Book_id = " + memoryRs.getString("Book_id") + " AND Sheekh_id = " + rs1.getString("Sheekh_id"));

						max_Book_id++;
					}
				}
				rs1.close();
			}

			// Version 1.5, Using setAutoCommit(false) then INSERT then setAutoCommit(true) will speed the process.
			h2Con.setAutoCommit(false);
			derbyCon.setAutoCommit(false);

			final Statement h2Stmt = h2Con.createStatement();
			final Statement derbyStmt = derbyCon.createStatement();

			/*
			 * Version 1.5
			 *
			 * We create a schema in the database to have the ability to release some storage to the OS when deleting
			 * some records using SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE.
			 */
			//s.execute("CREATE SCHEMA ROOT");

			/*
			 * Version 1.5
			 *
			 * This is only to make the pageSize=32768 to increase the speed but it seems nothing to be obtained.
			 * On the other hand the DB Heap increased from 50 MB to 80 MB !!
			 *
			 * PreparedStatement ps = conn.prepareStatement("CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY('derby.storage.pageSize', '32768')");
			 * ps.execute();
			 * conn.commit();
			 */
			derbyStmt.execute("CREATE TABLE Sheekh(Sheekh_id SMALLINT, Sheekh_name VARCHAR(100), PRIMARY KEY(Sheekh_id))");
			h2Stmt.execute("CREATE TABLE Sheekh(Sheekh_id SMALLINT, Sheekh_name VARCHAR(100), PRIMARY KEY(Sheekh_id))");

			// Version 2.0, New Table
			// Version 2.7, Multi_volume is BOOLEAN instead of SMALLINT. Previously it is named Book_level
			derbyStmt.execute("CREATE TABLE Book(Book_id SMALLINT, Book_name VARCHAR(100), Sheekh_id SMALLINT, Multi_volume BOOLEAN, Short_sheekh_name VARCHAR(50), PRIMARY KEY(Book_id), FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE)"); //Sub_books VARCHAR(1000),  'ON UPDATE CASCADE' is not supported in Derby
			h2Stmt.execute("CREATE TABLE Book(Book_id SMALLINT, Book_name VARCHAR(100), Sheekh_id SMALLINT, Multi_volume BOOLEAN, Short_sheekh_name VARCHAR(50), PRIMARY KEY(Book_id), FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE ON UPDATE CASCADE)");

			// Version 1.7, add Duration column
			// Version 2.3, Duration to SMALLINT instead of VARCHAR
			// Version 2.4, Duration to INTEGER instead of SMALLINT
			derbyStmt.execute("CREATE TABLE Chapters(Code SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT, Book_name VARCHAR(100), Sheekh_name VARCHAR(100), Title VARCHAR(100), FileName VARCHAR(500), FileType VARCHAR(10), Duration INTEGER, Path VARCHAR(150), CDNumber VARCHAR(50), " +
					"PRIMARY KEY(Code), FOREIGN KEY(Book_id) REFERENCES Book(Book_id) ON DELETE CASCADE, FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE)");
			h2Stmt.execute("CREATE TABLE Chapters(Code SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT, Book_name VARCHAR(100), Sheekh_name VARCHAR(100), Title VARCHAR(100), FileName VARCHAR(500), FileType VARCHAR(10), Duration INTEGER, Path VARCHAR(150), CDNumber VARCHAR(50), " +
					"PRIMARY KEY(Code), FOREIGN KEY(Book_id) REFERENCES Book(Book_id) ON DELETE CASCADE ON UPDATE CASCADE, FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE ON UPDATE CASCADE)");

			/*
			 * Version 1.5
			 *
			 * Important Note:
			 * If a table contains one large column (line column) along with several small columns, put the large
			 * column at the end of the row, so that commonly used columns will not be moved to
			 * overflow pages. Do not index large columns.
			 */
			// Version 1.6, Note that Duration is added to this table.
			// Version 2.3, Duration, Offset to SMALLINT instead of VARCHAR
			// Version 2.4, Duration, Offset to INTEGER instead of SMALLINT
			// Version 3.0, Add Tafreeg
			derbyStmt.execute("CREATE TABLE Contents(Code SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT, Seq SMALLINT, Offset INTEGER, Duration INTEGER, Line VARCHAR(20000), Tafreeg CLOB(65 K), " +
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE)"); // Line LONG VARCHAR, for performance, SELECT MAX(LENGTH(Line)) FROM Contents
			h2Stmt.execute("CREATE TABLE Contents(Code SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT, Seq SMALLINT, \"Offset\" INTEGER, Duration INTEGER, Line VARCHAR(20000), Tafreeg CLOB(65536), " +
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE ON UPDATE CASCADE)"); // Line LONGVARCHAR, for performance

			// Version 2.0, New Table
			// Version 2.5, Removing Category_order, Category_level
			derbyStmt.execute("CREATE TABLE Category(Category_id SMALLINT, Category_name VARCHAR(100), Category_parent SMALLINT, PRIMARY KEY(Category_id))");
			h2Stmt.execute("CREATE TABLE Category(Category_id SMALLINT, Category_name VARCHAR(100), Category_parent SMALLINT, PRIMARY KEY(Category_id))");

			// Version 2.0, New Table
			// TODO: ' ON UPDATE CASCADE' is not supported in Derby. check if we need to update manually in case of derby.
			derbyStmt.execute("CREATE TABLE ContentCat(Code SMALLINT, Seq SMALLINT, Category_id SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT, " +
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE, FOREIGN KEY(Category_id) REFERENCES Category(Category_id), " +
					"FOREIGN KEY(Book_id) REFERENCES Book(Book_id) ON DELETE CASCADE, FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE)");
			h2Stmt.execute("CREATE TABLE ContentCat(Code SMALLINT, Seq SMALLINT, Category_id SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT, " +
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE ON UPDATE CASCADE, FOREIGN KEY(Category_id) REFERENCES Category(Category_id), " +
					"FOREIGN KEY(Book_id) REFERENCES Book(Book_id) ON DELETE CASCADE ON UPDATE CASCADE, FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE ON UPDATE CASCADE)");

			// Version 2.4
			derbyStmt.execute("CREATE TABLE PlayList (Code SMALLINT, Seq SMALLINT, Offset INTEGER, Line VARCHAR(20000), " +
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE)");
			h2Stmt.execute("CREATE TABLE PlayList (Code SMALLINT, Seq SMALLINT, \"Offset\" INTEGER, Line VARCHAR(20000), " +
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE ON UPDATE CASCADE)");

			memoryStmt = memoryCon.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
			memoryRs = memoryStmt.executeQuery("SELECT Sheekh_id, Sheekh_name FROM Sheekh");

			while (memoryRs.next())
			{
				if (generate_derby)
					derbyStmt.execute("INSERT INTO Sheekh VALUES(" + memoryRs.getInt("Sheekh_id") + ",'" + memoryRs.getString("Sheekh_name") + "')");
				if (generate_H2)
					h2Stmt.execute("INSERT INTO Sheekh VALUES(" + memoryRs.getInt("Sheekh_id") + ",'" + memoryRs.getString("Sheekh_name") + "')");
			}

			memoryRs = memoryStmt.executeQuery("SELECT * FROM Book");
			memoryStmt1 = memoryCon.createStatement();
			while (memoryRs.next())
			{
				// This will not work correctly in some cases since there are some shared book_id with different levels e.g. mashhor\moslem for alothaymeen\moslem\?
				// But it works as we want in this case( but might not in the future). In addition there is a special case that is not working at all e.g. abdulmohsen\sonanaltermethi
				// Solved by: checking Chapters_order if it is !=0 so multi-layer book. Also re-build 'Book' Table so that there is no shared Book_id
				final ResultSet rs_book = memoryStmt1.executeQuery("SELECT * FROM Chapters WHERE Book_id=" + memoryRs.getInt("Book_id"));
				if (rs_book.next()) // The first row is enough
				{
					// To get the number of '\' in Path (Book Level)
					boolean Multi_volume = rs_book.getString("Path").replaceAll("[^" + java.util.regex.Matcher.quoteReplacement("\\") + ']', "").length() != 1;
					if (rs_book.getInt("Chapters_order") != 0)
						Multi_volume = true;

					if (generate_derby)
						derbyStmt.execute("INSERT INTO Book VALUES(" + memoryRs.getInt("Book_id") + ",'" + memoryRs.getString("Book_name") + "'," + rs_book.getInt("Sheekh_id") + ',' + Multi_volume + ",'" + rs_book.getString("Short_sheekh_name") + "')"); // Version 2.8
					if (generate_H2)
						h2Stmt.execute("INSERT INTO Book VALUES(" + memoryRs.getInt("Book_id") + ",'" + memoryRs.getString("Book_name") + "'," + rs_book.getInt("Sheekh_id") + ',' + Multi_volume + ",'" + rs_book.getString("Short_sheekh_name") + "')"); // Version 2.8

                    /* Can be used also
                    final PreparedStatement h2Ps = h2Con.prepareStatement("INSERT INTO Book VALUES("+memoryRs.getInt("Book_id")+",'"+memoryRs.getString("Book_name")+"',"+rs_book.getInt("Sheekh_id")+",?,'"+rs_book.getString("CDNumber").split("-")[0]+"')");
					h2Ps.setBoolean(1, Multi_volume);
                    h2Ps.executeUpdate();
                    h2Ps.close();
                    */
				}
			}

			memoryRs = memoryStmt.executeQuery("SELECT * FROM Chapters, Sheekh, Book WHERE Book.Book_id=Chapters.Book_id AND Sheekh.Sheekh_id=Chapters.Sheekh_id");

			// Update version 1.6, These two vector will store the duration for each audio file to speed up the process in creating Contents table.
			Vector<Integer> codeVector = new Vector<>();
			Vector<Integer> durationVector = new Vector<>();

			//final PrintWriter out = new PrintWriter(new FileWriter("DerbyTestEnhanced_DB_Creation.txt", false));
			while (memoryRs.next())
			{
				// Update version 1.7
				int duration = getAudioDuration("E:/AudioCataloger Media/Audios/" + memoryRs.getString("Path") + '/' + memoryRs.getString("FileName") + ".rm");

				if (generate_derby)
					derbyStmt.execute("INSERT INTO Chapters VALUES(" + memoryRs.getInt("Code") + ',' + memoryRs.getInt("Sheekh_id") + ',' + memoryRs.getInt("Book_id") + ",'" + memoryRs.getString("Book_name") + "', '" + memoryRs.getString("Sheekh_name") +
							"','" + memoryRs.getString("Title") + "', '" + memoryRs.getString("FileName") + "', 'rm', " + duration + ", '" + memoryRs.getString("Path") + "', '" + memoryRs.getString("CDNumber") + "')");
				if (generate_H2)
					h2Stmt.execute("INSERT INTO Chapters VALUES(" + memoryRs.getInt("Code") + ',' + memoryRs.getInt("Sheekh_id") + ',' + memoryRs.getInt("Book_id") + ",'" + memoryRs.getString("Book_name") + "', '" + memoryRs.getString("Sheekh_name") +
							"','" + memoryRs.getString("Title") + "', '" + memoryRs.getString("FileName") + "', 'rm', " + duration + ", '" + memoryRs.getString("Path") + "', '" + memoryRs.getString("CDNumber") + "')");
				//out.println(rs.getInt("Code")+" - "+duration);
				codeVector.add(memoryRs.getInt("Code"));
				durationVector.add(duration);
			}
			//out.close();

			System.out.println("Time to here: " + ((double) (System.nanoTime() - startTime) / 1000000000.0));

			memoryRs = memoryStmt.executeQuery("SELECT * FROM Contents, Chapters, Book WHERE Contents.Code=Chapters.Code AND Chapters.Book_id=Book.Book_id ORDER BY Contents.Code, Contents.Seq"); // Version 3.0, ORDER BY .... otherwise it will not work

			// Update version 1.6
			int code, Offset, nextOffset, period;

			while (memoryRs.next())
			{
				code = memoryRs.getInt("Code");
				Offset = memoryRs.getInt("Offset");

				if (!memoryRs.isLast())
				{
					memoryRs.next();
					int nextCode = memoryRs.getInt("Code");
					if (code == nextCode)
						nextOffset = memoryRs.getInt("Offset");
					else
						// nextOffset is the audio duration
						nextOffset = durationVector.elementAt(codeVector.indexOf(code));

					if (nextOffset < Offset /* Update version 2.4, no offset with -1 value || nextOffset==-1*/)
						period = -1;
					else
						period = nextOffset - Offset;

					memoryRs.previous();

					// Version 3.0, Changed
					//if(generate_derby)derbyStmt.execute("INSERT INTO Contents VALUES("+code+", "+memoryRs.getInt("Sheekh_id")+", "+memoryRs.getInt("Book.Book_id")+", "+memoryRs.getInt("Seq")+", "+Offset+", "+period+", '"+/* Update version 2.5, Removed quoteReplace(..) */ memoryRs.getString("Line")+"')");
					//if(generate_H2)h2Stmt.execute("INSERT INTO Contents VALUES("+code+", "+memoryRs.getInt("Sheekh_id")+", "+memoryRs.getInt("Book.Book_id")+", "+memoryRs.getInt("Seq")+", "+Offset+", "+period+", '"+/* Update version 2.5, Removed quoteReplace(..) */ memoryRs.getString("Line")+"')");

					if (generate_derby)
					{
						final PreparedStatement ps1 = derbyCon.prepareStatement("INSERT INTO Contents VALUES(" + code + "," + memoryRs.getInt("Sheekh_id") + "," + memoryRs.getInt("Book.Book_id") + "," + memoryRs.getInt("Seq") + "," + Offset + "," + period + ",?,?)");
						ps1.setString(1, memoryRs.getString("Line"));
						ps1.setCharacterStream(2, new StringReader(memoryRs.getString("Tafreeg")));
						ps1.execute();
						ps1.close();
					}

					if (generate_H2)
					{
						final PreparedStatement ps2 = h2Con.prepareStatement("INSERT INTO Contents VALUES(" + code + "," + memoryRs.getInt("Sheekh_id") + "," + memoryRs.getInt("Book.Book_id") + "," + memoryRs.getInt("Seq") + "," + Offset + "," + period + ",?,?)");
						ps2.setString(1, memoryRs.getString("Line"));
						ps2.setCharacterStream(2, new StringReader(memoryRs.getString("Tafreeg")));
						ps2.execute();
						ps2.close();
					}
				}
				else
				{
					// nextOffset is the audio duration.
					nextOffset = durationVector.elementAt(codeVector.indexOf(code));

					if (nextOffset < Offset || nextOffset == -1)
						period = -1;
					else
						period = nextOffset - Offset;

					// Version 3.0, Changed
					//if(generate_derby)derbyStmt.execute("INSERT INTO Contents VALUES("+code+", "+memoryRs.getInt("Sheekh_id")+", "+memoryRs.getInt("Book.Book_id")+", "+memoryRs.getInt("Seq")+", "+Offset+", "+period+", '"+memoryRs.getString("Line")+"')");
					//if(generate_H2)h2Stmt.execute("INSERT INTO Contents VALUES("+code+", "+memoryRs.getInt("Sheekh_id")+", "+memoryRs.getInt("Book.Book_id")+", "+memoryRs.getInt("Seq")+", "+Offset+", "+period+", '"+memoryRs.getString("Line")+"')");

					if (generate_derby)
					{
						final PreparedStatement ps1 = derbyCon.prepareStatement("INSERT INTO Contents VALUES(" + code + "," + memoryRs.getInt("Sheekh_id") + "," + memoryRs.getInt("Book.Book_id") + "," + memoryRs.getInt("Seq") + "," + Offset + "," + period + ",?,?)");
						ps1.setString(1, memoryRs.getString("Line"));
						ps1.setCharacterStream(2, new StringReader(memoryRs.getString("Tafreeg")));
						ps1.execute();
						ps1.close();
					}

					if (generate_H2)
					{
						final PreparedStatement ps2 = h2Con.prepareStatement("INSERT INTO Contents VALUES(" + code + "," + memoryRs.getInt("Sheekh_id") + "," + memoryRs.getInt("Book.Book_id") + "," + memoryRs.getInt("Seq") + "," + Offset + "," + period + ",?,?)");
						ps2.setString(1, memoryRs.getString("Line"));
						ps2.setCharacterStream(2, new StringReader(memoryRs.getString("Tafreeg")));
						ps2.execute();
						ps2.close();
					}
				}
			}

			memoryRs = memoryStmt.executeQuery("SELECT * FROM Category");
			while (memoryRs.next())
			{
				// In all cases, create Category table, to have a working empty h2 and/or derby db
				derbyStmt.execute("INSERT INTO Category VALUES(" + memoryRs.getInt("Category_id") + ",'" + memoryRs.getString("Category_name") + "', " + memoryRs.getInt("Category_parent") + ')');
				h2Stmt.execute("INSERT INTO Category VALUES(" + memoryRs.getInt("Category_id") + ",'" + memoryRs.getString("Category_name") + "', " + memoryRs.getInt("Category_parent") + ')');
			}

			memoryRs = memoryStmt.executeQuery("SELECT * FROM ContentCat");
			while (memoryRs.next())
			{
				//System.out.println(memoryRs.getInt("Code")+ "       "+ memoryRs.getInt("Seq")+"     "+memoryRs.getInt("Category_id"));
				//if(memoryRs.getInt("Code")!=1104&&memoryRs.getInt("Code")!=1119) // المغربي Code 1104,1119 does not have a chapter !!
				{
					if (generate_derby)
						derbyStmt.execute("INSERT INTO ContentCat VALUES(" + memoryRs.getInt("Code") + ", " + memoryRs.getInt("Seq") + ", " + memoryRs.getInt("Category_id") + ", " + memoryRs.getInt("Sheekh_id") + ", " + memoryRs.getInt("Book_id") + ')');
					if (generate_H2)
						h2Stmt.execute("INSERT INTO ContentCat VALUES(" + memoryRs.getInt("Code") + ", " + memoryRs.getInt("Seq") + ", " + memoryRs.getInt("Category_id") + ", " + memoryRs.getInt("Sheekh_id") + ", " + memoryRs.getInt("Book_id") + ')');
				}
			}

			// In all cases, create INDEX's, to have a working empty h2 and/or derby db
			//if (generate_derby)
			{
				derbyStmt.execute("CREATE INDEX Sheekh_Sheekh_id ON Sheekh(Sheekh_id)");
				derbyStmt.execute("CREATE INDEX Book_Book_id ON Book(Book_id)");
				derbyStmt.execute("CREATE INDEX Chapters_Code ON Chapters(Code)");//CREATE UNIQUE INDEX ChaptersCodeIndex ON Chapters(Code), try it and check the DB size
				derbyStmt.execute("CREATE INDEX Chapters_Book_id ON Chapters(Book_id)");
				derbyStmt.execute("CREATE INDEX Chapters_Sheekh_id ON Chapters(Sheekh_id)");
				derbyStmt.execute("CREATE INDEX Contents_Code ON Contents(Code)");
				derbyStmt.execute("CREATE INDEX Contents_Book_id ON Contents(Book_id)");
				derbyStmt.execute("CREATE INDEX Contents_Seq ON Contents(Seq)");
				derbyStmt.execute("CREATE INDEX ContentCat_Category_id ON ContentCat(Category_id)");
				derbyStmt.execute("CREATE INDEX ContentCat_Book_id ON ContentCat(Book_id)");
				derbyStmt.execute("CREATE INDEX ContentCat_Sheekh_id ON ContentCat(Sheekh_id)");

				// Version 3.3, it will be used in JOIN stmt between ContentCat / Contents
				derbyStmt.execute("CREATE INDEX ContentCat_Seq ON ContentCat(Seq)");
				derbyStmt.execute("CREATE INDEX ContentCat_Code ON ContentCat(Code)");
				derbyStmt.execute("CREATE INDEX Category_Category_parent ON Category(Category_parent)");
				derbyStmt.execute("CREATE INDEX Book_Sheekh_id ON Book(Sheekh_id)");
			}

			//if (generate_H2)
			{
				h2Stmt.execute("CREATE INDEX Sheekh_Sheekh_id ON Sheekh(Sheekh_id)");
				h2Stmt.execute("CREATE INDEX Book_Book_id ON Book(Book_id)");
				h2Stmt.execute("CREATE INDEX Chapters_Code ON Chapters(Code)");//CREATE UNIQUE INDEX ChaptersCodeIndex ON Chapters(Code), try it and check the DB size
				h2Stmt.execute("CREATE INDEX Chapters_Book_id ON Chapters(Book_id)");
				h2Stmt.execute("CREATE INDEX Chapters_Sheekh_id ON Chapters(Sheekh_id)");
				h2Stmt.execute("CREATE INDEX Contents_Code ON Contents(Code)");
				h2Stmt.execute("CREATE INDEX Contents_Book_id ON Contents(Book_id)");
				h2Stmt.execute("CREATE INDEX Contents_Seq ON Contents(Seq)");
				h2Stmt.execute("CREATE INDEX ContentCat_Category_id ON ContentCat(Category_id)");
				h2Stmt.execute("CREATE INDEX ContentCat_Book_id ON ContentCat(Book_id)");
				h2Stmt.execute("CREATE INDEX ContentCat_Sheekh_id ON ContentCat(Sheekh_id)");

				h2Stmt.execute("CREATE INDEX ContentCat_Seq ON ContentCat(Seq)");
				h2Stmt.execute("CREATE INDEX ContentCat_Code ON ContentCat(Code)");
				h2Stmt.execute("CREATE INDEX Category_Category_parent ON Category(Category_parent)");
				h2Stmt.execute("CREATE INDEX Book_Sheekh_id ON Book(Sheekh_id)");
			}

			h2Con.setAutoCommit(true);
			derbyCon.setAutoCommit(true);
			h2Con.createStatement().execute("SHUTDOWN COMPACT"); // TODO: try this the next time: SHUTDOWN DEFRAG. it improves the speed. better than COMPACT

			// Version 2.9
			final Statement stmt = derbyCon.createStatement();
			final ResultSet rs = stmt.executeQuery("SELECT schemaname, tablename FROM sys.sysschemas s, sys.systables t WHERE s.schemaid = t.schemaid AND t.tabletype = 'T'");
			final CallableStatement cs = derbyCon.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(?, ?, 1)");
			while (rs.next())
			{
				cs.setString(1, rs.getString("schemaname"));
				cs.setString(2, rs.getString("tablename"));
				cs.execute();
			}

			/* Version 2.9, Replaced by the above
            final CallableStatement cs1 = derbyCon.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'CONTENTS', 1)");
            final CallableStatement cs2 = derbyCon.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'BOOK', 1)");
            final CallableStatement cs3 = derbyCon.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'CHAPTERS', 1)");
            final CallableStatement cs4 = derbyCon.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'CONTENTCAT', 1)");
            final CallableStatement cs5 = derbyCon.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'SHEEKH', 1)");
            final CallableStatement cs6 = derbyCon.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'CONTENTCAT', 1)");
			cs1.execute();
            cs2.execute();
            cs3.execute();
            cs4.execute();
            cs5.execute();
            cs6.execute();
            */

			h2Con.close();
			derbyCon.close();
			memoryCon.close();

			// Derby throw exception in normal shutdown so it should be the last statement
			DriverManager.getConnection("jdbc:derby:;shutdown=true");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	// Version 1.7, This function is used to get the duration of the RM file.
	private static int getAudioDuration(final String filePath)
	{
		int duration = 0;

		try
		{
			final InputStream is = new FileInputStream(filePath);

			// Create the byte array to hold the data
			final byte[] bytes = new byte[4];

			// The offset to reach the duration 4 bytes.
			// Check this URL for the format -> https://common.helixcommunity.org/nonav/2003/HCS_SDK_r5/htmfiles/rmff.htm
			is.skip(48L);

			// Read in the bytes
			is.read(bytes);

			// Get the duration by converting the bytes array into int.
			for (int i = 0; i < 4; i++)
			{
				int shift = (4 - 1 - i) * 8;
				duration += (bytes[i] & 0x000000FF) << shift;
			}

			// Close the input stream and return bytes
			is.close();
		}
		catch (IOException ioe)
		{
			ioe.printStackTrace();
			duration = -1;
		}

		// This is just to check if the file is really RM file format. Check if the duration is more than 10 hours or 0
		if (duration > 36000000 || duration == 0)
			duration = -1;

		return duration;
	}

	// Version 2.3, Remove the space after the first (odd) quote " and the space before the second (even) quote "
	// Check if there is a space before the first quote or a space after the second quote and add them if not available.
    /*
	static String quoteReplace(String line)
	{
		int count=0;
		int start = line.indexOf('"');
		while(start != -1)
		{
			count++;
			if(count%2 == 0) // Even
			{
				if(line.charAt(start-1)==' ')
				{
					line = removeCharAt(line, start-1);
					start--;
				}
			}
			else // Odd
			{
				if((start+1) != line.length()) // Not the last charactor
					if(line.charAt(start+1)==' ')
						line = removeCharAt(line, start+1);
			}

			start = line.indexOf('"', start+1);
		}
		return line;
	}

	public static String replaceCharAt(String s, int pos, char c){return s.substring(0, pos) + c + s.substring(pos+1);}
	public static String removeCharAt(String s, int pos){return s.substring(0, pos)+s.substring(pos+1);}
	*/
}