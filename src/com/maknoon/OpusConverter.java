package com.maknoon;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.commons.net.ftp.*;

import static java.lang.System.lineSeparator;

// OpusConverter <source folder> <dest folder> <FTP: true/false> <per Index: true/false>
// Disable anti-virus while converting since it is consuming the CPU. or label the folder as trusted
// ffmpeg needs libfdk-aac [http://oss.netfarm.it/mplayer/]
public class OpusConverter
{
	private static final boolean opus = false; // m4a/opus
	private static final boolean ftpEnabled = false;
	private static final boolean perIndex = true;
	private static final String src = "E:/AudioCataloger Media/Audios1";
	private static final String dst = "C:/Users/ias12/Desktop/Audios";
	private static final String ffmpegPath = "E:/Support/ffmpeg-gst/ffmpeg.exe";
	private static final String ffprobePath = "E:/Support/ffmpeg-gst/ffprobe.exe";

	static ClassLoader cl;

	public static void main(String[] arg)
	{
		cl = OpusConverter.class.getClassLoader();
		new OpusConverter();
	}

	Connection conn;
	public OpusConverter()
	{
		try
		{
			if (AudioCataloger.derbyInUse)
			{
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
				//conn = DriverManager.getConnection("jdbc:derby:E:/AudioCataloger/resources/db");
				conn = DriverManager.getConnection("jdbc:derby:" + new File(cl.getResource("db").toURI()).getAbsolutePath());
			}
			else
			{
				Class.forName("org.h2.Driver");
				conn = DriverManager.getConnection("jdbc:h2:E:/AudioCataloger/resources/db/audioCatalogerDatabase");
			}

			final Thread[] threads = new Thread[10];
			//15065 - 15077
			final int startCode = 13555; // (chapters.code-1) TODO: must to do based on every update. otherwise you will end up using only one thread
			final int endCode = 13718; // (chapters.code+1)
			final int maxCode = endCode - startCode;
			for (int i = 0; i < threads.length; i++)
			{
				final int end = startCode + ((i + 1) * maxCode / threads.length);
				final int start = startCode + (i * maxCode / threads.length + 1);
				threads[i] = new Thread()
				{
					public void run()
					{
						if (ftpEnabled)
							ftp(start, end);
						else
						{
							if (perIndex)
								local_perIndex(start, end);
							else
								local(start, end);
						}
					}
				};
				threads[i].start();
			}

			for (Thread thread : threads) thread.join();

			conn.close();

			if (AudioCataloger.derbyInUse)
				DriverManager.getConnection("jdbc:derby:;shutdown=true");
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	void ftp(final int from, final int to)
	{
		final FTPClient ftp = new FTPClient();
		final FTPClientConfig config = new FTPClientConfig(FTPClientConfig.SYST_UNIX);
		ftp.configure(config);
		ftp.setBufferSize(0);
		ftp.setAutodetectUTF8(true);
		ftp.setControlEncoding("UTF-8");
		ftp.setDefaultTimeout(30000);
		ftp.setControlKeepAliveTimeout(Duration.ofSeconds(10));
		boolean error = false;
		try
		{
			ftp.connect("ftp.maknoon.com");
			System.out.println("Connected to ftp.maknoon.com");
			//System.out.print(ftp.getReplyString());

			// After connection attempt, you should check the reply code to verify success.
			final int reply = ftp.getReplyCode();

			if (!FTPReply.isPositiveCompletion(reply))
			{
				ftp.disconnect();
				System.err.println("FTP server refused connection.");
			}

			if (!ftp.login("user", "password"))
			{
				ftp.logout();
				error = true;
			}

			System.out.println("Remote system is " + ftp.getSystemType());

			ftp.setFileType(FTP.BINARY_FILE_TYPE);
			ftp.enterLocalPassiveMode();

			final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(from + "-" + to + ".txt"), StandardCharsets.UTF_8);

			final Statement s = conn.createStatement();
			final ResultSet rs = s.executeQuery("SELECT * FROM Chapters WHERE Code>=" + from + " AND Code<=" + to + " ORDER BY Code");
			while (rs.next())
			{
				final String code = rs.getString("Code");
				final String path = rs.getString("Path").replaceAll("\\\\", "/");
				final String file = rs.getString("FileName");
				final String type = rs.getString("FileType");
				final File srcFile = new File(src + '/' + path + '/' + file + "." + type);
				final String source = srcFile.getAbsolutePath();

				if (srcFile.exists()) // Version 3.3
				{
					final File destFile = new File(dst + '/' + path + '/' + file + (opus ? ".opus" : ".m4a"));
					final String dest = destFile.getAbsolutePath();
					new File(dst + '/' + path).mkdirs();

					out.write(code + ":	" + dest + lineSeparator());
					out.flush(); // To avoid power-off

					if (!destFile.exists())
					{
						final Process proc;

						if (opus)
							proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-map_metadata", "-1", "-metadata", "copyright=©maknoon.com", "-metadata", "Artist=" + rs.getString("Sheekh_name"), "-metadata", "album=" + rs.getString("Book_name"), "-metadata", "title=" + rs.getString("Title"), "-c:a", "libopus", "-b:a", "12k", dest});
						else
						{
							/*
							FFmpeg's m4a muxer honors the following metadata keys:
							“title”
							“author”
							“album”
							“year”
							“comment”
							 */
							final long br = org.jaudiotagger.audio.AudioFileIO.read(srcFile).getAudioHeader().getBitRateAsNumber();
							final int sr = getAudioSampleRate(source);
							if((br < 16L || sr < 16000) && type.equals("rm")) // profile aac_he is not working with bit rates below 16k for input files
								proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-map_metadata", "-1", "-metadata", "Artist=" + rs.getString("Sheekh_name"), "-metadata", "comment=©maknoon.com", "-metadata", "author=" + rs.getString("Sheekh_name"), "-metadata", "album=" + rs.getString("Book_name"), "-metadata", "title=" + rs.getString("Title"), "-c:a", "libfdk_aac", "-b:a", "16k", "-movflags", "+faststart", "-af", "aresample=async=1000", dest});
							else
								proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-map_metadata", "-1", "-metadata", "Artist=" + rs.getString("Sheekh_name"), "-metadata", "comment=©maknoon.com", "-metadata", "author=" + rs.getString("Sheekh_name"), "-metadata", "album=" + rs.getString("Book_name"), "-metadata", "title=" + rs.getString("Title"), "-c:a", "libfdk_aac", "-profile:a", "aac_he", "-b:a", "16k", "-movflags", "+faststart", "-af", "aresample=async=1000", dest});
						}

						final StreamGrabber errorGrabber = new StreamGrabber(proc.getErrorStream(), "OUTPUT"); // ffmpeg sends all diagnostic messages (the "console output") to stderr
						final StreamGrabber outputGrabber = new StreamGrabber(proc.getInputStream(), "Media-OUTPUT"); // actual output (the media stream) can goto stdout

						errorGrabber.start();
						outputGrabber.start();

						int exitVal = proc.waitFor();
						if (exitVal != 0)
						{
							out.write("Finished code: " + code + " ExitValue: " + exitVal + lineSeparator());
							out.flush(); // To avoid power-off
						}
					}

					// Creates a directory
					String dirToCreate = "public_html/audio/" + path;
					//boolean success = ftp.makeDirectory(dirToCreate);

					final String[] pathElements = dirToCreate.split("/");
					for (String singleDir : pathElements)
					{
						boolean existed = ftp.changeWorkingDirectory(singleDir);
						if (!existed)
						{
							boolean created = ftp.makeDirectory(singleDir);
							if (created)
							{
								//System.out.println("CREATED directory: " + singleDir);
								ftp.changeWorkingDirectory(singleDir);
							}
							else
							{
								//System.out.println("COULD NOT create directory: " + singleDir);
							}
						}
					}

					ftp.changeWorkingDirectory("/");

                    /*
                    if (success)
                        System.out.println("Successfully created directory: " + dirToCreate);
                    else
                        System.out.println("Failed to create directory: " + dirToCreate);
                    */

					final InputStream input = new FileInputStream(destFile);
					//System.out.print(ftp.getReplyString());
					boolean done = ftp.storeFile(dirToCreate + '/' + file + (opus ? ".opus" : ".m4a"), input);
					//System.out.print(ftp.getReplyString());
					//showServerReply(ftp);
					input.close();

					if (done)
						destFile.delete();
					else
					{
						out.write("FTP not done" + lineSeparator());
						out.flush();
					}
				}
			}

			s.close();
			out.close();
			ftp.logout();
		}
		catch (IOException e)
		{
			error = true;
			e.printStackTrace();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		finally
		{
			if (ftp.isConnected())
			{
				try
				{
					ftp.disconnect();
				}
				catch (IOException ioe)
				{
					// do nothing
				}
			}
			System.out.println("Error in thread");
		}
	}

	private static void showServerReply(final FTPClient ftpClient)
	{
		final String[] replies = ftpClient.getReplyStrings();
		if (replies != null && replies.length > 0)
		{
			for (String aReply : replies)
				System.out.println("SERVER: " + aReply);
		}
	}

	void local(final int from, final int to)
	{
		try
		{
			final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(from + "-" + to + ".txt"), StandardCharsets.UTF_8);

			final Statement s = conn.createStatement();
			final ResultSet rs = s.executeQuery("SELECT * FROM Chapters WHERE Code>=" + from + " AND Code<=" + to + " ORDER BY Code");
			while (rs.next())
			{
				final String code = rs.getString("Code");
				final String path = rs.getString("Path").replaceAll("\\\\", "/");
				final String file = rs.getString("FileName");
				final String type = rs.getString("FileType");
				final File srcFile = new File(src + '/' + path + '/' + file + "." + type);
				final String source = srcFile.getAbsolutePath();

				if (srcFile.exists()) // Version 3.3
				{
					final File destFile = new File(dst + '/' + path + '/' + file + (opus ? ".opus" : ".m4a"));
					final String dest = destFile.getAbsolutePath();
					new File(dst + '/' + path).mkdirs();

					out.write(code + ":	" + dest + lineSeparator());
					out.flush(); // To avoid power-off

					if (!destFile.exists())
					{
						final Process proc;

						if (opus)
							proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-map_metadata", "-1", "-metadata", "copyright=©maknoon.com", "-metadata", "Artist=" + rs.getString("Sheekh_name"), "-metadata", "album=" + rs.getString("Book_name"), "-metadata", "title=" + rs.getString("Title"), "-c:a", "libopus", "-b:a", "12k", dest});
						else
						{
							/*
							FFmpeg's m4a muxer honors the following metadata keys:
							“title”
							“author”
							“album”
							“year”
							“comment”
							 */
							final long br = org.jaudiotagger.audio.AudioFileIO.read(srcFile).getAudioHeader().getBitRateAsNumber();
							final int sr = getAudioSampleRate(source);
							//final int sr = org.jaudiotagger.audio.AudioFileIO.read(srcFile).getAudioHeader().getSampleRateAsNumber(); // NULL all the time

							if((br < 16L || sr < 16000) && type.equals("rm"))
								proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-map_metadata", "-1", "-metadata", "Artist=" + rs.getString("Sheekh_name"), "-metadata", "comment=©maknoon.com", "-metadata", "author=" + rs.getString("Sheekh_name"), "-metadata", "album=" + rs.getString("Book_name"), "-metadata", "title=" + rs.getString("Title"), "-c:a", "libfdk_aac", "-b:a", "16k", "-movflags", "+faststart", "-af", "aresample=async=1000", dest});
							else
								//proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-map_metadata", "-1", "-metadata", "Artist=" + rs.getString("Sheekh_name"), "-metadata", "comment=©maknoon.com", "-metadata", "author=" + rs.getString("Sheekh_name"), "-metadata", "album=" + rs.getString("Book_name"), "-metadata", "title=" + rs.getString("Title"), "-c:a", "libfdk_aac", "-vbr", "1", "-movflags", "frag_keyframe", "-af", "aresample=async=1000", "-f", "mp4", dest});
								proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-map_metadata", "-1", "-metadata", "Artist=" + rs.getString("Sheekh_name"), "-metadata", "comment=©maknoon.com", "-metadata", "author=" + rs.getString("Sheekh_name"), "-metadata", "album=" + rs.getString("Book_name"), "-metadata", "title=" + rs.getString("Title"), "-c:a", "libfdk_aac", "-profile:a", "aac_he", "-b:a", "16k", "-movflags", "+faststart", "-af", "aresample=async=1000", dest});
						}

						final StreamGrabber errorGrabber = new StreamGrabber(proc.getErrorStream(), "OUTPUT"); // ffmpeg sends all diagnostic messages (the "console output") to stderr
						final StreamGrabber outputGrabber = new StreamGrabber(proc.getInputStream(), "Media-OUTPUT"); // actual output (the media stream) can goto stdout

						errorGrabber.start();
						outputGrabber.start();

						int exitVal = proc.waitFor();
						if (exitVal != 0)
						{
							out.write("Finished code: " + code + " ExitValue: " + exitVal + lineSeparator());
							out.flush(); // To avoid power-off
						}
					}
				}
			}

			s.close();
			out.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	void local_perIndex(final int from, final int to)
	{
		try
		{
			final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(from + "-" + to + ".txt"), StandardCharsets.UTF_8);

			final Statement s = conn.createStatement();
			final Statement s1 = conn.createStatement();
			final ResultSet rs = s.executeQuery("SELECT * FROM Chapters WHERE Code >= " + from + " AND Code <= " + to + " ORDER BY Code");
			while (rs.next())
			{
				final String code = rs.getString("Code");
				final String path = rs.getString("Path").replaceAll("\\\\", "/");
				final String file = rs.getString("FileName");
				final String sheekh_name = rs.getString("Sheekh_name");
				final String book_name = rs.getString("Book_name");
				final String title = rs.getString("Title");
				final String type = rs.getString("FileType");
				final File srcFile = new File(src + '/' + path + '/' + file + "." + type);
				final String source = srcFile.getAbsolutePath();

				if (srcFile.exists()) // Version 3.3
				{
					final ResultSet rs1 = s1.executeQuery("SELECT * FROM Contents WHERE Code = " + code + " ORDER BY Seq");
					new File(dst + '/' + path + '/' + file).mkdirs();
					while (rs1.next())
					{
						final String seq = rs1.getString("Seq");
						final int offset = rs1.getInt("Offset");
						final int duration = rs1.getInt("Duration");
						final File destFile = new File(dst + '/' + path + '/' + file + '/' + seq + (opus ? ".opus" : ".m4a"));
						final String dest = destFile.getAbsolutePath();

						out.write(code + ":	" + dest + lineSeparator());
						out.flush(); // To avoid power-off

						if (!destFile.exists())
						{
							Process proc = null;
							if (opus)
							{
								if (duration == -1)
									proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-ss", String.valueOf(offset / 1000), "-map_metadata", "-1", "-metadata", "copyright=©maknoon.com", "-metadata", "Artist=" + sheekh_name, "-metadata", "album=" + book_name, "-metadata", "title=" + title, "-c:a", "libopus", "-b:a", "12k", dest});
								else
								{
									if (duration != 0)
										proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-ss", String.valueOf(offset / 1000), "-t", String.valueOf((duration / 1000) + 1), "-map_metadata", "-1", "-metadata", "copyright=©maknoon.com", "-metadata", "Artist=" + sheekh_name, "-metadata", "album=" + book_name, "-metadata", "title=" + title, "-c:a", "libopus", "-b:a", "12k", dest});
								}
							}
							else
							{
								final long br = org.jaudiotagger.audio.AudioFileIO.read(srcFile).getAudioHeader().getBitRateAsNumber();
								final int sr = getAudioSampleRate(source);
								if((br < 16L || sr < 16000) && type.equals("rm"))
								{
									if (duration == -1)
										proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-ss", String.valueOf(offset / 1000), "-map_metadata", "-1", "-metadata", "Artist=" + sheekh_name, "-metadata", "comment=©maknoon.com", "-metadata", "author=" + sheekh_name, "-metadata", "album=" + book_name, "-metadata", "title=" + title, "-c:a", "libfdk_aac", "-b:a", "16k", "-movflags", "+faststart", "-af", "aresample=async=1000", dest});
									else
									{
										if (duration != 0)
											proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-ss", String.valueOf(offset / 1000), "-t", String.valueOf((duration / 1000) + 1), "-map_metadata", "-1", "-metadata", "Artist=" + sheekh_name, "-metadata", "comment=©maknoon.com", "-metadata", "author=" + sheekh_name, "-metadata", "album=" + book_name, "-metadata", "title=" + title, "-c:a", "libfdk_aac", "-b:a", "16k", "-movflags", "+faststart", "-af", "aresample=async=1000", dest});
									}
								}
								else
								{
									if (duration == -1)
										//proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-ss", String.valueOf(offset / 1000), "-map_metadata", "-1", "-metadata", "Artist=" + sheekh_name, "-metadata", "comment=©maknoon.com", "-metadata", "author=" + sheekh_name, "-metadata", "album=" + book_name, "-metadata", "title=" + title, "-c:a", "libfdk_aac", "-vbr", "1", "-movflags", "frag_keyframe", "-af", "aresample=async=1000", "-f", "mp4", dest});
										proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-ss", String.valueOf(offset / 1000), "-map_metadata", "-1", "-metadata", "Artist=" + sheekh_name, "-metadata", "comment=©maknoon.com", "-metadata", "author=" + sheekh_name, "-metadata", "album=" + book_name, "-metadata", "title=" + title, "-c:a", "libfdk_aac", "-profile:a", "aac_he", "-b:a", "16k", "-movflags", "+faststart", "-af", "aresample=async=1000", dest});
									else
									{
										if (duration != 0)
											proc = Runtime.getRuntime().exec(new String[]{ffmpegPath, "-i", source, "-ss", String.valueOf(offset / 1000), "-t", String.valueOf((duration / 1000) + 1), "-map_metadata", "-1", "-metadata", "Artist=" + sheekh_name, "-metadata", "comment=©maknoon.com", "-metadata", "author=" + sheekh_name, "-metadata", "album=" + book_name, "-metadata", "title=" + title, "-c:a", "libfdk_aac", "-profile:a", "aac_he", "-b:a", "16k", "-movflags", "+faststart", "-af", "aresample=async=1000", dest});
									}
								}
							}

							if(proc != null)
							{
								final StreamGrabber errorGrabber = new StreamGrabber(proc.getErrorStream(), "OUTPUT"); // ffmpeg sends all diagnostic messages (the "console output") to stderr
								final StreamGrabber outputGrabber = new StreamGrabber(proc.getInputStream(), "Media-OUTPUT"); // actual output (the media stream) can goto stdout

								errorGrabber.start();
								outputGrabber.start();

								int exitVal = proc.waitFor();
								if (exitVal != 0)
								{
									out.write("Finished code: " + code + " Seq " + seq + " ExitValue: " + exitVal + lineSeparator());
									out.flush(); // To avoid power-off
								}
							}
							else
							{
								out.write("Finished code: " + code + " Seq " + seq + " -> empty index (0 duration)" + lineSeparator());
								out.flush(); // To avoid power-off
							}
						}
						else
						{
							if (destFile.length() == 0L)
							{
								out.write("File is empty: " + destFile + lineSeparator());
								out.flush();
							}
						}
					}
					rs1.close();
				}
			}

			s.close();
			s1.close();
			out.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	int getAudioSampleRate(String source)
	{
		int sr = 9999999; // just a bigger number than any sample rate

		try
		{
			final Process sr_p = Runtime.getRuntime().exec(new String[]{ffprobePath, "-show_streams", "-show_entries", "stream=sample_rate", source});

			new StreamGrabber(sr_p.getErrorStream(), "sr_p_error").start(); // any error message?
			final BufferedReader in = new BufferedReader(new InputStreamReader(sr_p.getInputStream()));
			String inputLine;
			while ((inputLine = in.readLine()) != null)
			{
				if (inputLine.contains("sample_rate"))
					sr = Integer.parseInt(inputLine.split("=")[1]);
			}
			in.close();
			final boolean exitVal2 = sr_p.waitFor(2, TimeUnit.SECONDS);
			//System.out.println("sr_p process exit value (true -> OK): " + exitVal2);
			if (!exitVal2)
				sr_p.destroyForcibly();
		}
		catch (IOException | InterruptedException e)
		{
			e.printStackTrace();
		}

		return sr;
	}

	static class StreamGrabber extends Thread
	{
		InputStream is;
		String type;

		StreamGrabber(InputStream is, String type)
		{
			this.is = is;
			this.type = type;
		}

		public void run()
		{
			try
			{
				final BufferedReader br = new BufferedReader(new InputStreamReader(is));
				String line;
				while ((line = br.readLine()) != null)
					;//System.out.println(type + '>' + line);
				br.close();
				is.close();
			}
			catch (IOException ioe)
			{
				ioe.printStackTrace();
			}
		}
	}
}