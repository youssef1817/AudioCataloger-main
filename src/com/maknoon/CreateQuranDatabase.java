package com.maknoon;

// Version 3.0
import java.sql.*;
import java.io.*;
import java.util.Vector;

public class CreateQuranDatabase
{
	public static void main(String[] args)
	{
		new CreateQuranDatabase();
	}

	String programFolder;
	public CreateQuranDatabase()
	{
		try
		{
			programFolder = new File(CreateQuranDatabase.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath()+"/";

			final Connection con;
			if (AudioCataloger.derbyInUse)
			{
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
				con = DriverManager.getConnection("jdbc:derby:db");
			}
			else
			{
				Class.forName("org.h2.Driver");
				con = DriverManager.getConnection("jdbc:h2:db/audioCatalogerDatabase");
			}

			con.setAutoCommit(false);

			Class.forName("sun.jdbc.odbc.JdbcOdbcDriver");
			final Connection QuranAccessCon = DriverManager.getConnection("jdbc:odbc:Driver={Microsoft Access Driver (*.mdb)};DBQ=C:\\Users\\Ibrahim\\Documents\\MIE Media\\qurandb-2.mdb;DriverID=22;READONLY=true}");

			final Statement QuranAccessStmt = QuranAccessCon.createStatement();
			final Statement s = con.createStatement();

			s.execute("INSERT INTO Sheekh VALUES(0, 'القرآن العظيم')");

			final String[] folders = StreamConverter("../../quranIndexing/folders.txt");
			final String[] reciters = StreamConverter("../../quranIndexing/reciters.txt");
			final String[] recitersCD = StreamConverter("../../quranIndexing/recitersCD.txt");
			final String[] suraNames = StreamConverter("../../quranIndexing/sura.txt");
			String bookName = "تلاوة ";
			String padString = "000", seqName;

			ResultSet rs = s.executeQuery("SELECT MAX(Code) as max_Code FROM Chapters");
			rs.next();
			int new_Code = rs.getInt("max_Code") + 1;

			rs = s.executeQuery("SELECT MAX(Book_id) as max_Book_id FROM Book");
			rs.next();
			int new_Book_id = rs.getInt("max_Book_id") + 1;

			for (int w = 0; w < reciters.length; w++)
			{
				s.execute("INSERT INTO Book VALUES (" + new_Book_id + ", '" + bookName + reciters[w] + "', 0, false, 'القرآن العظيم')");

				for (int i = 1; i <= 114; i++)
				{
					seqName = Integer.valueOf(i).toString();
					seqName = padString.substring(seqName.length()) + seqName;

					final File f = new File("C:\\Users\\Ibrahim\\Documents\\MIE Media\\" + folders[w] + "\\" + seqName + ".mp3");
					org.jaudiotagger.audio.mp3.MP3File h = new org.jaudiotagger.audio.mp3.MP3File(f);
					int fileDuration = (int) (f.length() * 8 / h.getMP3AudioHeader().getBitRateAsNumber());

					s.execute("INSERT INTO Chapters VALUES (" + new_Code + ", 0, " + new_Book_id + ", '" + bookName + reciters[w] + "', 'القرآن العظيم', '" + suraNames[i - 1] + "', '" + seqName + "', 'mp3', " + fileDuration + ", 'quran\\" + folders[w] + "', '" + recitersCD[w] + "')");

					int aya = 0;
					final String[] ayaOffsets = StreamConverter("../../quranIndexing/" + folders[w] + "/" + seqName + ".txt");
					final ResultSet rsQuran = QuranAccessStmt.executeQuery("SELECT Quran FROM Quran WHERE Sura=" + i + " ORDER BY Aya");
					while (rsQuran.next())
					{
						int offset = Integer.parseInt(ayaOffsets[aya]);
						int duration;
						if (aya == (ayaOffsets.length - 2))
						{
							if (fileDuration < Integer.parseInt(ayaOffsets[aya]))
								duration = -1;
							else
								duration = fileDuration - Integer.parseInt(ayaOffsets[aya]);
						}
						else
						{
							if (Integer.parseInt(ayaOffsets[aya + 1]) < Integer.parseInt(ayaOffsets[aya]))
								duration = -1;
							else
								duration = Integer.parseInt(ayaOffsets[aya + 1]) - Integer.parseInt(ayaOffsets[aya]);
						}

						s.execute("INSERT INTO Contents VALUES (" + new_Code + ", 0, " + new_Book_id + ", " + (aya + 1) + ", " + offset + ", " + duration + ", '" + new String(rsQuran.getString("Quran").getBytes(), "cp1256") + "','')");
						aya++;
					}
					new_Code++;
				}
				new_Book_id++;
			}

			con.setAutoCommit(true);
			QuranAccessCon.close();

			if (AudioCataloger.derbyInUse)
			{
				final Statement stmt = con.createStatement();
				final ResultSet rs1 = stmt.executeQuery("SELECT schemaname, tablename FROM sys.sysschemas s, sys.systables t WHERE s.schemaid = t.schemaid AND t.tabletype = 'T'");
				final CallableStatement cs = con.prepareCall("CALL SYSCS_UTIL.SYSCS_COMPRESS_TABLE(?, ?, 1)");
				while (rs1.next())
				{
					cs.setString(1, rs1.getString("schemaname"));
					cs.setString(2, rs1.getString("tablename"));
					cs.execute();
				}

				con.close();
				DriverManager.getConnection("jdbc:derby:;shutdown=true");
			}
			else
				con.createStatement().execute("SHUTDOWN COMPACT");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	// Function to read arabic translation files.
	public String[] StreamConverter(final String filePath)
	{
		try
		{
			//Version 1.4
			final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(programFolder + filePath), "UTF-8"));
			final Vector<String> lines = new Vector<>();

			while (in.ready()) lines.addElement(in.readLine());
			in.close();

			//return lines.toArray(new String[0]);
			return lines.toArray(new String[lines.size()]); // For performance as indicated by IDEA InspectionGadgets
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		System.exit(0);
		return null;
	}
}