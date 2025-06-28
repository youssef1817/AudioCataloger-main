package com.maknoon;

import java.io.File;
import java.sql.*;

public class DBCompact
{
	public static void main(String[] args)
	{
		new DBCompact();
	}

	public DBCompact()
	{
		System.out.println("Make sure that the program is not running while compacting the database");
		final ClassLoader cl = DBCompact.class.getClassLoader();

		try
		{
			final String dbURL;
			if (AudioCataloger.derbyInUse)
			{
				dbURL = "jdbc:derby:" + new File(cl.getResource("db").toURI()).getAbsolutePath();
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
			}
			else
			{
				dbURL = "jdbc:h2:" + new File(cl.getResource("db").toURI()).getAbsolutePath() + "/audioCatalogerDatabase";
				Class.forName("org.h2.Driver");
			}

			final Connection con = DriverManager.getConnection(dbURL);
			if (AudioCataloger.derbyInUse)
			{
				// This CALL will retain storage of the unused deleted rows to OS which will reduce the size of the DB.
				// Other option was: CALL SYSCS_UTIL.SYSCS_INPLACE_COMPRESS_TABLE(NULL, 'CONTENTS', 1, 1, 1) but it didn't save anything [http://download.oracle.com/javadb/10.6.1.0/adminguide/cadminspace21579.html]
                /* Version 2.9, Replaced by the below
				final CallableStatement cs1 = con.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'CONTENTS', 1)");
                final CallableStatement cs2 = con.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'BOOK', 1)");
                final CallableStatement cs3 = con.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'CHAPTERS', 1)");
                final CallableStatement cs4 = con.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'CONTENTCAT', 1)");
                final CallableStatement cs5 = con.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'SHEEKH', 1)");
                final CallableStatement cs6 = con.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(NULL, 'CONTENTCAT', 1)");
                cs1.execute();
                cs2.execute();
                cs3.execute();
                cs4.execute();
                cs5.execute();
                cs6.execute();
                */

				// Version 2.9
				final Statement stmt = con.createStatement();
				final ResultSet rs = stmt.executeQuery("SELECT schemaname, tablename FROM sys.sysschemas s, sys.systables t WHERE s.schemaid = t.schemaid AND t.tabletype = 'T'");
				final CallableStatement cs = con.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(?, ?, 1)");
				while (rs.next())
				{
					cs.setString(1, rs.getString("schemaname"));
					cs.setString(2, rs.getString("tablename"));
					cs.execute();
				}

				try
				{
					DriverManager.getConnection("jdbc:derby:;shutdown=true");
				} // This will throw exception in normal case.
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
			else
			{
				final Statement s = con.createStatement();
				s.execute("SHUTDOWN COMPACT");
			}

            /* Version 2.9, Deprecated as of Lucene 3.5.0
            // Version 2.7, Optimise Index as well
            IndexWriter.unlock(FSDirectory.open(new File("index")));
			IndexWriter.unlock(FSDirectory.open(new File("rootsIndex")));
			IndexWriter.unlock(FSDirectory.open(new File("luceneIndex")));

            // Version 2.8, Not working [http://lucene.472066.n3.nabble.com/Using-IndexWriterConfig-repeatedly-in-3-1-tt2764754.html]
            //final IndexWriterConfig conf = new IndexWriterConfig(Version.LUCENE_36, new StandardAnalyzer(Version.LUCENE_36));
            //conf.setOpenMode(IndexWriterConfig.OpenMode.APPEND);

			final IndexWriter indexWriter = new IndexWriter(FSDirectory.open(new File("index")), new IndexWriterConfig(Version.LUCENE_36 new StandardAnalyzer(Version.LUCENE_36)).setOpenMode(IndexWriterConfig.OpenMode.APPEND));
			final IndexWriter rootsWriter = new IndexWriter(FSDirectory.open(new File("rootsIndex")), new IndexWriterConfig(Version.LUCENE_36, new StandardAnalyzer(Version.LUCENE_36)).setOpenMode(IndexWriterConfig.OpenMode.APPEND));
			final IndexWriter luceneWriter = new IndexWriter(FSDirectory.open(new File("luceneIndex")), new IndexWriterConfig(Version.LUCENE_36, new StandardAnalyzer(Version.LUCENE_36)).setOpenMode(IndexWriterConfig.OpenMode.APPEND));

            indexWriter.optimize();
			rootsWriter.optimize();
			luceneWriter.optimize();

			indexWriter.close();
			rootsWriter.close();
			luceneWriter.close();
			*/
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}
}