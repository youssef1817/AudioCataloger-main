package com.maknoon;

import java.io.File;
import java.sql.*;

// Version 3.0, This class cannot create empty DB because of 'Category' values
public class EmptyDatabase
{
	public static void main(String[] args){new EmptyDatabase();}

	public EmptyDatabase()
	{
		final ClassLoader cl = EmptyDatabase.class.getClassLoader();

		try
		{
            final String dbURL;
			if(AudioCataloger.derbyInUse)
			{
				dbURL="jdbc:derby:"+new File(cl.getResource("db").toURI()).getAbsolutePath(); // To make it work in Mac where the call is from the jar but the folders are outside in the *.app
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
			}
			else
			{
				// ;CACHE_SIZE=65536;CACHE_TYPE=SOFT_LRU
				dbURL = "jdbc:h2:" + new File(cl.getResource("db").toURI()).getAbsolutePath() + "/audioCatalogerDatabase"; // ;CACHE_SIZE=32768;LOG=0 (logging is disabled, faster). it is only speed import.
				Class.forName("org.h2.Driver");
			}

			final Connection con = DriverManager.getConnection(dbURL);
			final Statement s = con.createStatement();

			/* Very slow and consume all the memory
			s.execute("DELETE FROM Sheekh");
			s.execute("DELETE FROM Book");
			s.execute("DELETE FROM Chapters");
			s.execute("DELETE FROM Contents");
			s.execute("DELETE FROM ContentCat");
			*/

            // Version 3.0, Rearrange the order of dropping tables since derby through exceptions because of removing tables with dependency first.
            s.execute("DROP TABLE PlayList");
            s.execute("DROP TABLE ContentCat");
            s.execute("DROP TABLE Contents");
            s.execute("DROP TABLE Chapters");
			s.execute("DROP TABLE Book");
            s.execute("DROP TABLE Sheekh");
			//s.execute("DROP TABLE Category"); // Version 2.7

            /* Version 2.9, Deprecated as of Lucene 3.5.0
			// Version 2.7/2.8/2.9, Clear the index
            IndexWriter.unlock(FSDirectory.open(new File("index")));
            IndexWriter.unlock(FSDirectory.open(new File("rootsIndex")));
            IndexWriter.unlock(FSDirectory.open(new File("luceneIndex")));

            final IndexWriter indexWriter = new IndexWriter(FSDirectory.open(new File("index")), new IndexWriterConfig(Version.LUCENE_36, new StandardAnalyzer(Version.LUCENE_36)).setOpenMode(IndexWriterConfig.OpenMode.APPEND));
            final IndexWriter rootsWriter = new IndexWriter(FSDirectory.open(new File("rootsIndex")), new IndexWriterConfig(Version.LUCENE_36, new StandardAnalyzer(Version.LUCENE_36)).setOpenMode(IndexWriterConfig.OpenMode.APPEND));
            final IndexWriter luceneWriter = new IndexWriter(FSDirectory.open(new File("luceneIndex")), new IndexWriterConfig(Version.LUCENE_36, new StandardAnalyzer(Version.LUCENE_36)).setOpenMode(IndexWriterConfig.OpenMode.APPEND));

            indexWriter.optimize();
            rootsWriter.optimize();
            luceneWriter.optimize();

            indexWriter.close();
            rootsWriter.close();
            luceneWriter.close();
            */

			if(AudioCataloger.derbyInUse)
				s.execute("CREATE TABLE Sheekh(Sheekh_id SMALLINT, Sheekh_name VARCHAR(100), PRIMARY KEY(Sheekh_id))");
			else
				s.execute("CREATE TABLE Sheekh(Sheekh_id TINYINT, Sheekh_name VARCHAR(100), PRIMARY KEY(Sheekh_id))");
			s.execute("CREATE INDEX Sheekh_Sheekh_id ON Sheekh(Sheekh_id)");

			if(AudioCataloger.derbyInUse)
				s.execute("CREATE TABLE Book(Book_id SMALLINT, Book_name VARCHAR(100), Sheekh_id SMALLINT, Multi_volume BOOLEAN, Short_sheekh_name VARCHAR(50), PRIMARY KEY(Book_id), FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE)"); //Sub_books VARCHAR(1000),  'ON UPDATE CASCADE' is not supported in Derby
			else
				s.execute("CREATE TABLE Book(Book_id TINYINT, Book_name VARCHAR(100), Sheekh_id TINYINT, Multi_volume BOOLEAN, Short_sheekh_name VARCHAR(50), PRIMARY KEY(Book_id), FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE ON UPDATE CASCADE)");
			s.execute("CREATE INDEX Book_Book_id ON Book(Book_id)");
			s.execute("CREATE INDEX Book_Sheekh_id ON Book(Sheekh_id)");

			if(AudioCataloger.derbyInUse)
				s.execute("CREATE TABLE Chapters(Code SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT, Book_name VARCHAR(100), Sheekh_name VARCHAR(100), Title VARCHAR(100), FileName VARCHAR(20), FileType VARCHAR(10), Duration INTEGER, Path VARCHAR(150), CDNumber VARCHAR(50), "+
					"PRIMARY KEY(Code), FOREIGN KEY(Book_id) REFERENCES Book(Book_id) ON DELETE CASCADE, FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE)");
			else
				s.execute("CREATE TABLE Chapters(Code SMALLINT, Sheekh_id TINYINT, Book_id TINYINT, Book_name VARCHAR(100), Sheekh_name VARCHAR(100), Title VARCHAR(100), FileName VARCHAR(20), FileType VARCHAR(10), Duration INTEGER, Path VARCHAR(150), CDNumber VARCHAR(50), "+
					"PRIMARY KEY(Code), FOREIGN KEY(Book_id) REFERENCES Book(Book_id) ON DELETE CASCADE ON UPDATE CASCADE, FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE ON UPDATE CASCADE)");
			s.execute("CREATE INDEX Chapters_Code ON Chapters(Code)");
			s.execute("CREATE INDEX Chapters_Book_id ON Chapters(Book_id)");
			s.execute("CREATE INDEX Chapters_Sheekh_id ON Chapters(Sheekh_id)");

			// Version 3.0, Add Tafreeg
			if(AudioCataloger.derbyInUse)
				s.execute("CREATE TABLE Contents(Code SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT, Seq SMALLINT, Offset INTEGER, Duration INTEGER, Line VARCHAR(8000), Tafreeg CLOB(65 K), "+
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE)");
			else
				s.execute("CREATE TABLE Contents(Code SMALLINT, Sheekh_id TINYINT, Book_id TINYINT, Seq SMALLINT, \"Offset\" INTEGER, Duration INTEGER, Line VARCHAR(8000), Tafreeg CLOB(65536), "+
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE ON UPDATE CASCADE)");
			s.execute("CREATE INDEX Contents_Code ON Contents(Code)");
			s.execute("CREATE INDEX Contents_Book_id ON Contents(Book_id)");
			s.execute("CREATE INDEX Contents_Seq ON Contents(Seq)");

			if(AudioCataloger.derbyInUse)
				s.execute("CREATE TABLE ContentCat(Code SMALLINT, Seq SMALLINT, Category_id SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT, "+
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE, FOREIGN KEY(Category_id) REFERENCES Category(Category_id), "+
					"FOREIGN KEY(Book_id) REFERENCES Book(Book_id) ON DELETE CASCADE, FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE)");
			else
				s.execute("CREATE TABLE ContentCat(Code SMALLINT, Seq SMALLINT, Category_id SMALLINT, Sheekh_id SMALLINT, Book_id SMALLINT, "+
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE ON UPDATE CASCADE, FOREIGN KEY(Category_id) REFERENCES Category(Category_id), "+
					"FOREIGN KEY(Book_id) REFERENCES Book(Book_id) ON DELETE CASCADE ON UPDATE CASCADE, FOREIGN KEY(Sheekh_id) REFERENCES Sheekh(Sheekh_id) ON DELETE CASCADE ON UPDATE CASCADE)");
			s.execute("CREATE INDEX ContentCat_Category_id ON ContentCat(Category_id)");
			s.execute("CREATE INDEX ContentCat_Book_id ON ContentCat(Book_id)");
			s.execute("CREATE INDEX ContentCat_Sheekh_id ON ContentCat(Sheekh_id)");
			s.execute("CREATE INDEX ContentCat_Seq ON ContentCat(Seq)");
			s.execute("CREATE INDEX ContentCat_Code ON ContentCat(Code)");

			if(AudioCataloger.derbyInUse)
				s.execute("CREATE TABLE PlayList (Code SMALLINT, Seq SMALLINT, Offset INTEGER, Line VARCHAR(8000), "+
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE)");
			else
				s.execute("CREATE TABLE PlayList (Code SMALLINT, Seq SMALLINT, \"Offset\" INTEGER, Line VARCHAR(8000), "+
					"FOREIGN KEY(Code) REFERENCES Chapters(Code) ON DELETE CASCADE ON UPDATE CASCADE)");

			s.close();
            con.close();

			if(AudioCataloger.derbyInUse)
                DriverManager.getConnection("jdbc:derby:;shutdown=true");
		}
		catch(Exception e){e.printStackTrace();}
	}
}