package com.maknoon;

/*
 * DB update files formatted like:
 * a) 'ö' is used as a StringTokenizer delimiter,
 * b) 'Ö' is used as a string delimiter
 *
 * 'info' file in the tar file ACDB (Database) package:
 *
 * Line 1: Short description
 * Line 2: Exported DB version // Version 2.7, 'DB' instead of 'system'
 * Next Lines are details of the package
 */
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.*;
import java.sql.*;
import java.nio.channels.*;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.apache.tools.tar.*;

class Update extends Dialog
{
	private Thread updateThread;

	private static Button updateButton;
	private static CheckBox importWithAudiosCheckBox;

	Update(final Stage primaryStage, final File file, final AudioCataloger AC)
	{
		super();

		initOwner(primaryStage);

		setTitle("ترقية قاعدة البيانات");
		setResizable(false);
		setOnCloseRequest(new EventHandler<DialogEvent>()
		{
			@Override
			public void handle(DialogEvent event)
			{
				AC.updateMenuItem.setDisable(false);
			}
		});

		final TextArea descriptionTextArea = new TextArea();
		descriptionTextArea.setEditable(false);

		final TextField titleTextField = new TextField();
		titleTextField.setEditable(false);

		final StringBuilder descriptionTxt = new StringBuilder(100);

		try
		{
			final TarInputStream tarFile = new TarInputStream(new FileInputStream(file), "UTF-8"); // UTF-8 to avoid issues with Arabic folders/filenames
			for (TarEntry tarEntry = tarFile.getNextEntry(); tarEntry != null; )
			{
				if (tarEntry.getName().equals("info"))
				{
					final Path tempDir = Files.createTempDirectory("audiocataloger");

					tarFile.copyEntryContents(new FileOutputStream(tempDir + "/info"));
					final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(tempDir + "/info"), StandardCharsets.UTF_8));
					final String shortDescription = in.readLine();
					final float importedDBVersion = Float.parseFloat(in.readLine());

					// Check if db is compatible with this version
					if (importedDBVersion != AudioCataloger.db_version)
					{
						setHeaderText("لا يمكن استيراد قاعدة البيانات هذه لعدم توافقها مع البرنامج إما لكونها أحدث أو أقدم منه: " + importedDBVersion);
						setContentText(shortDescription);
						getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL);
						show();
						AC.updateMenuItem.setDisable(false);
						return;
					}

					titleTextField.setText(shortDescription);

					while (in.ready())
						descriptionTxt.append(in.readLine()).append(System.lineSeparator());
						//descriptionTextArea.appendText(in.readLine() + System.lineSeparator());

					break;
				}
			}
			tarFile.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		final BorderPane mainPanel = new BorderPane();
		mainPanel.setTop(titleTextField);
		mainPanel.setCenter(descriptionTextArea);
		getDialogPane().setContent(mainPanel);

		importWithAudiosCheckBox = new CheckBox("استيراد الملفات الصوتية مع قاعدة البيانات إن وجدت");
		importWithAudiosCheckBox.setTooltip(new Tooltip("لا يمكن الاستيراد إلا إذا قمت بتحديد موضع الملفات الصوتية من قائمة \"إعدادات\" ليكون على القرص الصلب (HD). كما لا يمكن الاستيراد إذا كان التحديث عن طريق الانترنت."));
		importWithAudiosCheckBox.setSelected(true);
		mainPanel.setBottom(importWithAudiosCheckBox);

		if(AudioCataloger.defaultMediaChoice == AudioCataloger.pathMedia.INTERNET)
			importWithAudiosCheckBox.setDisable(true);

		final ButtonType updateButtonType = new ButtonType("تحديث", ButtonBar.ButtonData.OK_DONE);
		getDialogPane().getButtonTypes().add(updateButtonType);
		updateButton = (Button) getDialogPane().lookupButton(updateButtonType);
		updateButton.addEventFilter(ActionEvent.ACTION, event ->
		{
			event.consume(); // prevent closing the dialog unless manually done

			updateButton.setDisable(true);
			importWithAudiosCheckBox.setDisable(true);
			setHeaderText("يتم حاليا تحديث البرنامج، عليك الانتظار إلى حين الانتهاء ...");

			updateThread = new Thread()
			{
				int duplicateRecords = 0;
				boolean restartRequired = false;

				public void run()
				{
					restartRequired = false;
					try
					{
						// Version 1.5, convert all the code to be bigger than what it is in the database.
						final Vector<String> Chapters_Code = new Vector<>(200, 200);
						final Vector<String> Contents_Code = new Vector<>(1000, 1000);
						final Vector<String> Chapters_Sheekh_id = new Vector<>(200, 200);
						final Vector<String> Contents_Sheekh_id = new Vector<>(1000, 1000);
						final Vector<String> Sheekh_id = new Vector<>();// Contains the previous sheekh id + "a"
						final Vector<Integer> new_Sheekh_id = new Vector<>();// It will hold the new ID for sheekhs
						final Vector<String> Chapters_Sheekh_name = new Vector<>();// Temp variable to examine that if the Sheekh_name is exist in the DB

						// Version 2.0
						final Vector<String> Chapters_Book_id = new Vector<>(200, 200);
						final Vector<String> Contents_Book_id = new Vector<>(1000, 1000);
						final Vector<String> Book_Book_id = new Vector<>();
						final Vector<String> Book_Book_name = new Vector<>();
						final Vector<String> Book_Sheekh_id = new Vector<>();
						final Vector<String> Book_Multi_volume = new Vector<>(); // Version 2.7
						final Vector<String> Book_Short_sheekh_name = new Vector<>();
						final Vector<Integer> new_Book_id = new Vector<>();
						final Vector<String> ContentCat_Code = new Vector<>(1000, 1000);
						final Vector<String> ContentCat_Sheekh_id = new Vector<>(1000, 1000);
						final Vector<String> ContentCat_Book_id = new Vector<>(1000, 1000);

						final Path tempDir = Files.createTempDirectory("audiocataloger");

						// Un-tar the exported file
						//final TarInputStream tarFile = new TarInputStream(new FileInputStream(updatingFiles.elementAt(i).startsWith("http")?("temp/downloadedFile"):updatingFiles.elementAt(i)));
						final TarInputStream tarFile = new TarInputStream(Channels.newInputStream(new FileInputStream(file).getChannel()), "UTF-8"); // Version 2.6, NIO for thread interrupt. Version 3.1, UTF-8 to avoid issues with Arabic folders/filenames
						for (TarEntry tarEntry = tarFile.getNextEntry(); tarEntry != null; tarEntry = tarFile.getNextEntry())
						{
							if (tarEntry.getName().equals("info") || tarEntry.getName().equals("exported_db"))
								tarFile.copyEntryContents(Channels.newOutputStream(new FileOutputStream(tempDir + "/" + tarEntry.getName()).getChannel())); // Version 2.6, NIO for thread interrupt
								/*
								{
									final FileOutputStream out = new FileOutputStream("temp/"+tarEntry.getName());
									tarFile.copyEntryContents(out);
									out.close();
								}
								*/
							else
							{
								if (importWithAudiosCheckBox.isSelected() && AudioCataloger.defaultMediaChoice == AudioCataloger.pathMedia.DIRECTRY)
								{
									// This to avoid problems by importing files created in another OS
									final String tarFolderName = com.sun.jna.Platform.isWindows() ? tarEntry.getName().replace('/', '\\') : tarEntry.getName().replace('\\', '/');

									// To extract a folder we must create it first.
									final File f = new File(AudioCataloger.choosedAudioPath + tarFolderName.substring(0, tarFolderName.lastIndexOf(File.separator)));
									f.mkdirs();

									// Write the files to the disk
									//tarFile.copyEntryContents(new FileOutputStream(AC.choosedAudioPath+tarFolderName));
									tarFile.copyEntryContents(Channels.newOutputStream(new FileOutputStream(AudioCataloger.choosedAudioPath + tarFolderName).getChannel())); // Version 2.6, NIO for thread interrupt
								}
							}
						}
						tarFile.close();

						// Extract the Zipped file
						final byte[] buffer = new byte[2048];
						int nrBytesRead;
						//final ZipInputStream inStream = new ZipInputStream(new FileInputStream("temp/exported_db"));
						final ZipInputStream inStream = new ZipInputStream(Channels.newInputStream(new FileInputStream(tempDir + "/exported_db").getChannel())); // Version 2.6, NIO for thread interrupt
						for (ZipEntry zipEntry = inStream.getNextEntry(); zipEntry != null; zipEntry = inStream.getNextEntry())
						{
							//final OutputStream outStream = new FileOutputStream("temp/"+zipEntry.getName());
							final OutputStream outStream = Channels.newOutputStream(new FileOutputStream(tempDir + "/" + zipEntry.getName()).getChannel()); // Version 2.6, NIO for thread interrupt
							while ((nrBytesRead = inStream.read(buffer, 0, 2048)) > 0)
								outStream.write(buffer, 0, nrBytesRead);
							outStream.close();
						}
						inStream.close();

						// Use BufferedReader to get one line at a time
						//BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("temp/Chapters"), "UTF-8"));
						BufferedReader in = new BufferedReader(Channels.newReader(new FileInputStream(tempDir + "/Chapters").getChannel(), StandardCharsets.UTF_8)); // Version 2.6, NIO for thread interrupt

						// Version 1.9, Skip the header line when using H2
						if (!AudioCataloger.derbyInUse)
							in.readLine();
						String line;

						//while(in.ready()) // Version 2.6, TODO: Not working with NIO FileChannel !!!!
						while ((line = in.readLine()) != null)
						{
							final StringTokenizer tokens = new StringTokenizer(line, "öÖ"); // These to get the Sheekh_name also to not replicate in the DB
							Chapters_Code.addElement(tokens.nextToken() + 'a'); // Code
							Chapters_Sheekh_id.addElement(tokens.nextToken() + 'a'); // Sheekh_id
							Chapters_Book_id.addElement(tokens.nextToken() + 'a'); // Book_id
							tokens.nextToken(); // Book_name
							Chapters_Sheekh_name.addElement(tokens.nextToken()); // Sheekh_name
						}
						in.close();

						//in = new BufferedReader(new InputStreamReader(new FileInputStream("temp/Contents"), "UTF-8"));
						in = new BufferedReader(Channels.newReader(new FileInputStream(tempDir + "/Contents").getChannel(), StandardCharsets.UTF_8)); // Version 2.6, NIO for thread interrupt
						if (!AudioCataloger.derbyInUse)
							in.readLine();
						//while(in.ready()) // Version 2.6, Not working with NIO FileChannel !!!!
						while ((line = in.readLine()) != null)
						{
							final StringTokenizer tokens = new StringTokenizer(line, "öÖ");// Version 1.9, Add Ö

							// This is done to avoid parsing strings when the description contains many lines. In that case
							// we have to avoid these lines.
							// Version 1.9, Re-Enhance to handle the H2 database file structure
							if (tokens.countTokens() >= 5)
							{
								Contents_Code.addElement(tokens.nextToken() + 'a');
								Contents_Sheekh_id.addElement(tokens.nextToken() + 'a');
								Contents_Book_id.addElement(tokens.nextToken() + 'a');
							}
						}
						in.close();

						//in = new BufferedReader(new InputStreamReader(new FileInputStream("temp/ContentCat"), "UTF-8"));
						in = new BufferedReader(Channels.newReader(new FileInputStream(tempDir + "/ContentCat").getChannel(), StandardCharsets.UTF_8)); // Version 2.6, NIO for thread interrupt
						if (!AudioCataloger.derbyInUse)
							in.readLine();
						//while(in.ready()) // Version 2.6, Not working with NIO FileChannel !!!!
						while ((line = in.readLine()) != null)
						{
							final StringTokenizer tokens = new StringTokenizer(line, "öÖ");
							ContentCat_Code.addElement(tokens.nextToken() + 'a');
							tokens.nextToken();
							tokens.nextToken();
							ContentCat_Sheekh_id.addElement(tokens.nextToken() + 'a');
							ContentCat_Book_id.addElement(tokens.nextToken() + 'a');
						}
						in.close();

						//in = new BufferedReader(new InputStreamReader(new FileInputStream("temp/Book"), "UTF-8"));
						in = new BufferedReader(Channels.newReader(new FileInputStream(tempDir + "/Book").getChannel(), StandardCharsets.UTF_8)); // Version 2.6, NIO for thread interrupt
						if (!AudioCataloger.derbyInUse)
							in.readLine();
						//while(in.ready()) // Version 2.6, Not working with NIO FileChannel !!!!
						while ((line = in.readLine()) != null)
						{
							final StringTokenizer tokens = new StringTokenizer(line, "öÖ");
							Book_Book_id.addElement(tokens.nextToken() + 'a');
							Book_Book_name.addElement(tokens.nextToken());
							Book_Sheekh_id.addElement(tokens.nextToken() + 'a');
							Book_Multi_volume.addElement(tokens.nextToken());
							Book_Short_sheekh_name.addElement(tokens.nextToken());
						}
						in.close();

						/////////////////////////
						// Inserting into Sheekh
						/////////////////////////

						// Version 2.6, To allow rollback in case the user cancel the operation.
						AudioCataloger.sharedDBConnection.setAutoCommit(false);

						final Statement stmt = AudioCataloger.sharedDBConnection.createStatement();
						ResultSet rs = stmt.executeQuery("SELECT MAX(Code) as max_Code FROM Chapters");

						rs.next();
						int new_Code = rs.getInt("max_Code") + 1;

						rs = stmt.executeQuery("SELECT MAX(Sheekh_id) as max_Sheekh_id FROM Sheekh");
						int maxSheekhIDTemp;
						rs.next();
						maxSheekhIDTemp = rs.getInt("max_Sheekh_id") + 1;

						for (int q = 0; q < Chapters_Sheekh_id.size(); q++)
						{
							if (!Sheekh_id.contains(Chapters_Sheekh_id.elementAt(q)))
							{
								Sheekh_id.addElement(Chapters_Sheekh_id.elementAt(q));

								rs = stmt.executeQuery("SELECT Sheekh_id FROM Sheekh WHERE Sheekh_name = '" + Chapters_Sheekh_name.elementAt(q) + "'");
								if (rs.next()) // i.e. the same sheekh exists.
									new_Sheekh_id.addElement(rs.getInt("Sheekh_id"));
								else
								{
									new_Sheekh_id.addElement(maxSheekhIDTemp);

									// Version 1.6, just the new sheekhs are added to the 'sheekh' table.
									stmt.execute("INSERT INTO Sheekh VALUES(" + (maxSheekhIDTemp++) + ",'" + Chapters_Sheekh_name.elementAt(q) + "')"); // Version 2.0
								}
							}
						}

						///////////////////////
						// Inserting into Book
						///////////////////////

						// Version 2.6
						if (isInterrupted()) // Version 2.8
						{
							// To enable auto commit in case the user cancel the update after disabling it.
							AudioCataloger.sharedDBConnection.rollback();
							AudioCataloger.sharedDBConnection.setAutoCommit(true);
						}
						else
						{
							rs = stmt.executeQuery("SELECT MAX(Book_id) as max_Book_id FROM Book");
							rs.next();
							int new_book_id = rs.getInt("max_Book_id") + 1;

							for (int q = 0; q < Book_Book_name.size(); q++)
							{
								rs = stmt.executeQuery("SELECT Book_id FROM Chapters WHERE Book_name='" + Book_Book_name.elementAt(q) + "' AND Sheekh_name='" + Chapters_Sheekh_name.elementAt(Chapters_Sheekh_id.indexOf(Book_Sheekh_id.elementAt(q))) + "'");
								if (rs.next()) // i.e. the same book for the same sheekh exists.
									new_Book_id.addElement(rs.getInt("Book_id"));
								else
								{
									new_Book_id.addElement(new_book_id);
									stmt.execute("INSERT INTO Book VALUES(" + (new_book_id++) + ", '" + Book_Book_name.elementAt(q) + "', " + new_Sheekh_id.elementAt(Sheekh_id.indexOf(Book_Sheekh_id.elementAt(q))) + ", " + Book_Multi_volume.elementAt(q) + ", '" + Book_Short_sheekh_name.elementAt(q) + "')");
								}
							}

							// Freeing the memory
							Chapters_Sheekh_name.removeAllElements();
							Book_Book_name.removeAllElements();
							Book_Sheekh_id.removeAllElements();
							Book_Multi_volume.removeAllElements();
							Book_Short_sheekh_name.removeAllElements();

							//////////////////////////////////////////////////////
							// For Contents, Chapters, ContentCat code replacement
							//////////////////////////////////////////////////////

							// Version 2.6
							if (isInterrupted())
							{
								// To enable auto commit in case the user cancel the update after disabling it.
								AudioCataloger.sharedDBConnection.rollback();
								AudioCataloger.sharedDBConnection.setAutoCommit(true);
							}
							else
							{
								long startTime = System.nanoTime();

								//int ContentsLastRowIndex = 0, ContentCatLastRowIndex = 0;
								for (int q = 0; q < Chapters_Code.size(); q++)
								{
									/* Version 2.2, Removed for replaced by Collections.replaceAll for performance.
									for(int j=ContentsLastRowIndex; j<Contents_Code.size(); j++)
									{
										if(Chapters_Code.elementAt(q).equals(Contents_Code.elementAt(j)))
										{
											while(j!=Contents_Code.size() && Chapters_Code.elementAt(q).equals(Contents_Code.elementAt(j)))
												Contents_Code.setElementAt(String.valueOf(new_Code), j++);

											ContentsLastRowIndex = j;
											break;
										}
									}

									for(int j=ContentCatLastRowIndex; j<ContentCat_Code.size(); j++)
									{
										if(Chapters_Code.elementAt(q).equals(ContentCat_Code.elementAt(j)))
										{
											while(j!=ContentCat_Code.size() && Chapters_Code.elementAt(q).equals(ContentCat_Code.elementAt(j)))
												ContentCat_Code.setElementAt(String.valueOf(new_Code), j++);

											ContentCatLastRowIndex = j;
											break;
										}
									}
									*/
									Collections.replaceAll(Contents_Code, Chapters_Code.elementAt(q), String.valueOf(new_Code));
									Collections.replaceAll(ContentCat_Code, Chapters_Code.elementAt(q), String.valueOf(new_Code));
									Chapters_Code.setElementAt(String.valueOf(new_Code), q); // Version 2.0
									new_Code++;
								}

								long estimatedTime = System.nanoTime() - startTime;
								System.out.println("Time: " + ((double) estimatedTime / 1000000000.0));

								///////////////////////////////////////////////////////////
								// For Contents, Chapters, ContentCat Sheekh_id replacement
								///////////////////////////////////////////////////////////

								// Version 2.6
								if (isInterrupted())
								{
									// To enable auto commit in case the user cancel the update after disabling it.
									AudioCataloger.sharedDBConnection.rollback();
									AudioCataloger.sharedDBConnection.setAutoCommit(true);
								}
								else
								{
									for (int q = 0; q < Sheekh_id.size(); q++)
									{
										/* Version 2.2, Removed for replaced by Collections.replaceAll for performance.
										for(int j=0; j<Contents_Sheekh_id.size(); j++)
										{
											if(Sheekh_id.elementAt(q).equals(Contents_Sheekh_id.elementAt(j)))
												Contents_Sheekh_id.setElementAt(String.valueOf(new_Sheekh_id.elementAt(q)), j);
										}

										for(int j=0; j<Chapters_Sheekh_id.size(); j++)
										{
											if(Sheekh_id.elementAt(q).equals(Chapters_Sheekh_id.elementAt(j)))
												Chapters_Sheekh_id.setElementAt(String.valueOf(new_Sheekh_id.elementAt(q)), j);
										}

										for(int j=0; j<ContentCat_Sheekh_id.size(); j++)
										{
											if(Sheekh_id.elementAt(q).equals(ContentCat_Sheekh_id.elementAt(j)))
												ContentCat_Sheekh_id.setElementAt(String.valueOf(new_Sheekh_id.elementAt(q)), j);
										}
										*/
										Collections.replaceAll(Contents_Sheekh_id, Sheekh_id.elementAt(q), String.valueOf(new_Sheekh_id.elementAt(q)));
										Collections.replaceAll(Chapters_Sheekh_id, Sheekh_id.elementAt(q), String.valueOf(new_Sheekh_id.elementAt(q)));
										Collections.replaceAll(ContentCat_Sheekh_id, Sheekh_id.elementAt(q), String.valueOf(new_Sheekh_id.elementAt(q)));
									}

									// Freeing the memory
									Sheekh_id.removeAllElements();
									new_Sheekh_id.removeAllElements();

									/////////////////////////////////////////////////////////
									// For Contents, Chapters, ContentCat Book_id replacement
									/////////////////////////////////////////////////////////

									// Version 2.6
									if (isInterrupted())
									{
										// To enable auto commit in case the user cancel the update after disabling it.
										AudioCataloger.sharedDBConnection.rollback();
										AudioCataloger.sharedDBConnection.setAutoCommit(true);
									}
									else
									{
										startTime = System.nanoTime();

										for (int q = 0; q < Book_Book_id.size(); q++)
										{
											/* Version 2.2, Removed for replaced by Collections.replaceAll for performance.
											for(int j=0; j<Contents_Book_id.size(); j++)
											{
												if(Book_Book_id.elementAt(q).equals(Contents_Book_id.elementAt(j)))
													Contents_Book_id.setElementAt(String.valueOf(new_Book_id.elementAt(q)), j);
											}

											for(int j=0; j<Chapters_Book_id.size(); j++)
											{
												if(Book_Book_id.elementAt(q).equals(Chapters_Book_id.elementAt(j)))
													Chapters_Book_id.setElementAt(String.valueOf(new_Book_id.elementAt(q)), j);
											}

											for(int j=0; j<ContentCat_Book_id.size(); j++)
											{
												if(Book_Book_id.elementAt(q).equals(ContentCat_Book_id.elementAt(j)))
													ContentCat_Book_id.setElementAt(String.valueOf(new_Book_id.elementAt(q)), j);
											}
											*/
											Collections.replaceAll(Contents_Book_id, Book_Book_id.elementAt(q), String.valueOf(new_Book_id.elementAt(q)));
											Collections.replaceAll(Chapters_Book_id, Book_Book_id.elementAt(q), String.valueOf(new_Book_id.elementAt(q)));
											Collections.replaceAll(ContentCat_Book_id, Book_Book_id.elementAt(q), String.valueOf(new_Book_id.elementAt(q)));
										}

										estimatedTime = System.nanoTime() - startTime;
										System.out.println("Time: " + ((double) estimatedTime / 1000000000.0));

										// Version 2.6
										if (isInterrupted())
										{
											// To enable auto commit in case the user cancel the update after disabling it.
											AudioCataloger.sharedDBConnection.rollback();
											AudioCataloger.sharedDBConnection.setAutoCommit(true);
										}
										else
										{
											//in = new BufferedReader(new InputStreamReader(new FileInputStream("temp/Contents"), "UTF-8"));
											//Writer out = new OutputStreamWriter(new FileOutputStream("temp/Update_Contents"), "UTF-8");
											in = new BufferedReader(Channels.newReader(new FileInputStream(tempDir + "/Contents").getChannel(), StandardCharsets.UTF_8)); // Version 2.6, NIO for thread interrupt
											Writer out = Channels.newWriter(new FileOutputStream(tempDir + "/Update_Contents").getChannel(), StandardCharsets.UTF_8); // Version 2.6, NIO for thread interrupt

											// Version 1.9, Skip the header line when using H2 and write it to the output file
											if (!AudioCataloger.derbyInUse)
												out.write(in.readLine() + System.lineSeparator());

											int contextIndex = 0;
											//while(in.ready()) // Version 2.6, Not working with NIO FileChannel !!!!
											while ((line = in.readLine()) != null)
											{
												final StringTokenizer lineTokens = new StringTokenizer(line, "öÖ"); // Version 1.9, Add Ö

												// Update version 1.9, Re-enhance to handle the H2 database file structure
												if (lineTokens.countTokens() >= 5)
												{
													//line.substring(line.indexOf('ö', line.indexOf('ö', line.indexOf('ö') + 1) + 1)) + lineSeparator, to get the third index of ö
													if (AudioCataloger.derbyInUse)
														out.write(Contents_Code.elementAt(contextIndex) + 'ö' + Contents_Sheekh_id.elementAt(contextIndex) + 'ö' + Contents_Book_id.elementAt(contextIndex++) + line.substring(line.indexOf('ö', line.indexOf('ö', line.indexOf('ö') + 1) + 1)) + System.lineSeparator());
													else
														out.write('Ö' + Contents_Code.elementAt(contextIndex) + "ÖöÖ" + Contents_Sheekh_id.elementAt(contextIndex) + "ÖöÖ" + Contents_Book_id.elementAt(contextIndex++) + 'Ö' + line.substring(line.indexOf('ö', line.indexOf('ö', line.indexOf('ö') + 1) + 1)) + System.lineSeparator());
												}
												else
													out.write(line + System.lineSeparator());
											}
											out.close();
											in.close();

											// Version 2.6
											if (isInterrupted())
											{
												// To enable auto commit in case the user cancel the update after disabling it.
												AudioCataloger.sharedDBConnection.rollback();
												AudioCataloger.sharedDBConnection.setAutoCommit(true);
											}
											else
											{
												//in = new BufferedReader(new InputStreamReader(new FileInputStream("temp/Chapters"), "UTF-8"));
												//out = new OutputStreamWriter(new FileOutputStream("temp/Update_Chapters"), "UTF-8");
												in = new BufferedReader(Channels.newReader(new FileInputStream(tempDir + "/Chapters").getChannel(), "UTF-8")); // Version 2.6, NIO for thread interrupt
												out = Channels.newWriter(new FileOutputStream(tempDir + "/Update_Chapters").getChannel(), "UTF-8"); // Version 2.6, NIO for thread interrupt
												if (!AudioCataloger.derbyInUse)
													out.write(in.readLine() + System.lineSeparator());
												contextIndex = 0;
												//while(in.ready()) // Version 2.6, Not working with NIO FileChannel !!!!
												while ((line = in.readLine()) != null)
												{
													//lineTokens = new StringTokenizer(line, "ö"); // Version 1.9, No Need for it.
													if (AudioCataloger.derbyInUse)
														out.write(/*(new_Code++)*/Chapters_Code.elementAt(contextIndex) + 'ö' + Chapters_Sheekh_id.elementAt(contextIndex) + 'ö' + Chapters_Book_id.elementAt(contextIndex++) + line.substring(line.indexOf('ö', line.indexOf('ö', line.indexOf('ö') + 1) + 1)) + System.lineSeparator());
													else
														out.write('Ö' + Chapters_Code.elementAt(contextIndex) + "ÖöÖ" + Chapters_Sheekh_id.elementAt(contextIndex) + "ÖöÖ" + Chapters_Book_id.elementAt(contextIndex++) + 'Ö' + line.substring(line.indexOf('ö', line.indexOf('ö', line.indexOf('ö') + 1) + 1)) + System.lineSeparator());
												}
												out.close();
												in.close();

												// Version 2.6
												if (isInterrupted())
												{
													// To enable auto commit in case the user cancel the update after disabling it.
													AudioCataloger.sharedDBConnection.rollback();
													AudioCataloger.sharedDBConnection.setAutoCommit(true);
												}
												else
												{
													//in = new BufferedReader(new InputStreamReader(new FileInputStream("temp/ContentCat"), "UTF-8"));
													//out = new OutputStreamWriter(new FileOutputStream("temp/Update_ContentCat"), "UTF-8");
													in = new BufferedReader(Channels.newReader(new FileInputStream(tempDir + "/ContentCat").getChannel(), StandardCharsets.UTF_8)); // Version 2.6, NIO for thread interrupt
													out = Channels.newWriter(new FileOutputStream(tempDir + "/Update_ContentCat").getChannel(), StandardCharsets.UTF_8); // Version 2.6, NIO for thread interrupt
													if (!AudioCataloger.derbyInUse)
														out.write(in.readLine() + System.lineSeparator());
													contextIndex = 0;
													//while(in.ready()) // Version 2.6, Not working with NIO FileChannel !!!!
													while ((line = in.readLine()) != null)
													{
														if (AudioCataloger.derbyInUse)
															out.write(ContentCat_Code.elementAt(contextIndex) + line.substring(line.indexOf('ö'), line.indexOf('ö', line.indexOf('ö', line.indexOf('ö') + 1) + 1) + 1) + ContentCat_Sheekh_id.elementAt(contextIndex) + 'ö' + ContentCat_Book_id.elementAt(contextIndex++) + System.lineSeparator());
														else
															out.write('Ö' + ContentCat_Code.elementAt(contextIndex) + 'Ö' + line.substring(line.indexOf('ö'), line.indexOf('ö', line.indexOf('ö', line.indexOf('ö') + 1) + 1) + 1) + 'Ö' + ContentCat_Sheekh_id.elementAt(contextIndex) + "ÖöÖ" + ContentCat_Book_id.elementAt(contextIndex++) + 'Ö' + System.lineSeparator());
													}
													out.close();
													in.close();

													// Freeing the memory
													Chapters_Code.removeAllElements();
													Contents_Code.removeAllElements();
													Chapters_Sheekh_id.removeAllElements();
													Contents_Sheekh_id.removeAllElements();
													Chapters_Book_id.removeAllElements();
													Contents_Book_id.removeAllElements();
													Book_Book_id.removeAllElements();
													new_Book_id.removeAllElements();
													ContentCat_Code.removeAllElements();
													ContentCat_Sheekh_id.removeAllElements();
													ContentCat_Book_id.removeAllElements();

													// Version 2.6
													if (isInterrupted())
													{
														// To enable auto commit in case the user cancel the update after disabling it.
														AudioCataloger.sharedDBConnection.rollback();
														AudioCataloger.sharedDBConnection.setAutoCommit(true);
													}
													else
													{
														AudioCataloger.sharedDBConnection.commit(); // Version 2.6

														// To reduce the memory if all the db is imported [http://groups.google.com/group/h2-database/browse_thread/thread/71b901f8521fd78f]
														if (!AudioCataloger.derbyInUse)
															stmt.execute("SET UNDO_LOG 0"); // Version 2.5

														//final String pathToUnzippedFilesFolder = DBArchivesPathNames[i].substring(0, DBArchivesPathNames[i].lastIndexOf(fileSeparator))+fileSeparator;
														//System.out.println(pathToUnzippedFilesFolder);
														File file = new File(tempDir + "/Update_Chapters");
														if (file.exists())
														{
															if (AudioCataloger.derbyInUse)
															{
																final PreparedStatement ps = AudioCataloger.sharedDBConnection.prepareStatement("CALL SYSCS_UTIL.SYSCS_IMPORT_TABLE (NULL, 'CHAPTERS', '" + file + "', 'ö', 'Ö', 'UTF-8', 0)");
																ps.execute();
																//conn.commit();
																ps.close();
															}
															else
																stmt.execute("INSERT INTO Chapters SELECT * FROM CSVREAD('" + file + "',NULL,'UTF-8','ö','Ö')");

															//conn.commit(); // To free the memory
															System.out.println("Chapters table is updated");
														}

														file = new File(tempDir + "/Update_Contents");
														if (file.exists())
														{
															if (AudioCataloger.derbyInUse)
															{
																final PreparedStatement ps = AudioCataloger.sharedDBConnection.prepareStatement("CALL SYSCS_UTIL.SYSCS_IMPORT_TABLE (NULL, 'CONTENTS', '" + file + "', 'ö', 'Ö', 'UTF-8', 0)");
																ps.execute();
																ps.close();
															}
															else
																stmt.execute("INSERT INTO Contents SELECT * FROM CSVREAD('" + file + "',NULL,'UTF-8','ö','Ö')");
															System.out.println("Contents table is updated");
														}

														file = new File(tempDir + "/Update_ContentCat");
														if (file.exists())
														{
															if (AudioCataloger.derbyInUse)
															{
																final PreparedStatement ps = AudioCataloger.sharedDBConnection.prepareStatement("CALL SYSCS_UTIL.SYSCS_IMPORT_TABLE (NULL, 'CONTENTCAT', '" + file + "', 'ö', 'Ö', 'UTF-8', 0)");
																ps.execute();
																ps.close();
															}
															else
																stmt.execute("INSERT INTO ContentCat SELECT * FROM CSVREAD('" + file + "',NULL,'UTF-8','ö','Ö')");
															System.out.println("ContentCat table is updated");
														}

														AudioCataloger.sharedDBConnection.setAutoCommit(true);
														if (!AudioCataloger.derbyInUse)
															stmt.execute("SET UNDO_LOG 1");

														rs = stmt.executeQuery("SELECT COUNT(*) AS DUPLICATE FROM (SELECT FileName, FileType, Path FROM Chapters GROUP BY FileName, FileType, Path HAVING COUNT(*) > 1) AS T"); // Version 2.7, Add 'AS T' to solve derby issue as in [http://old.nabble.com/Issue-with-COUNT-function-td30731070.html]
														rs.next();

														// This will be executed many times depending on the number of ACDB files. It should be only for the last one (for speed).
														// If there are the same files repeated many times, it will only count as 1. To find the count for each repeated index -> SELECT FileName, FileType, Path, COUNT(*) ...
														duplicateRecords = rs.getInt("DUPLICATE");
														stmt.close();
													}
												}
											}
										}
									}
								}
							}
						}
					}
					//catch(Exception e){e.printStackTrace();}
					catch (Throwable t) // Version 2.6, To catch OutOfMemoryError
					{
						t.printStackTrace();
						if (t.toString().contains("java.lang.OutOfMemoryError"))
							AudioCataloger.alertError("لقد تم نفاد الذاكرة اللازمة لإتمام عملية البحث لكثرة النتائج، لإنجاح العملية قم بتحرير الملف startup.bat" + System.lineSeparator() +
									"والموضوع تحت مجلد البرنامج كما يلي:" + System.lineSeparator() +
									"- قم بتغيير المقطع من Xmx1024m إلى Xmx2048m أو أكثر إن احتيج إلى ذلك. إبدأ البرنامج بالضغط على startup.bat" + System.lineSeparator() +
									"[قم بالتغيير في startup.sh لأنظمة Linux/MacOS]", "تنبيه", "متابعة", t.toString(), Alert.AlertType.ERROR);
					}

					/* Version 4.0, removed since it is system temp now
					try
					{
						// Clear the temp folder
						//final File deletedFiles [] = FileSystemView.getFileSystemView().getFiles(new File("temp/"), true); // Version 2.6, Removed since it is not working with NIO FileChannel. It is meant only to work with JFileChooser
						final File deletedFiles[] = new File(AudioCataloger.cl.getResource("temp/").getFile()).listFiles();
						for (File element : deletedFiles)
							element.delete();
					}
					catch (Exception e)
					{
						e.printStackTrace();
					}
					*/

					Platform.runLater(() -> {
						setTitle("تم الانتهاء من تحديث قاعدة البيانات.");

						// To refresh detailList
						AC.createNodes();
						AC.orderButton.fire();
						AC.updateMenuItem.setDisable(false);
						close();
						if (duplicateRecords != 0)
							AudioCataloger.alertError("تم الانتهاء من تحديث قاعدة البيانات." + System.lineSeparator() +
									"عليك التنبه إلى أن قاعدة البيانات تتضمن " + duplicateRecords +
									" شريطا مكررا، لمعرفة الأشرطة المكررة عليك:" + System.lineSeparator() +
									"بإغلاق البرنامج والذهاب إلى مجلد البرنامج والضغط على DBCheck.bat لأنظمة Windows أو DBCheck.sh لبقية الأنظمة", "تنبيه", "متابعة", null, Alert.AlertType.WARNING);
					});
				}
			};
			updateThread.start();
		});

		final ButtonType cancelButtonType = new ButtonType("خروج", ButtonBar.ButtonData.CANCEL_CLOSE);
		getDialogPane().getButtonTypes().add(cancelButtonType);
		final Button cancelButton = (Button) getDialogPane().lookupButton(cancelButtonType);
		cancelButton.addEventFilter(ActionEvent.ACTION, event ->
		{
			event.consume(); // prevent closing the dialog

			// Version 1.9
			if (updateThread != null)
			{
				if (updateThread.isAlive())
				{
					final ButtonType ok = new ButtonType("نعم", ButtonBar.ButtonData.OK_DONE);
					final Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "عملية التحديث جارية الآن. هل أنت متأكد من أنك تريد إنهاء التحديث (قد يحدث هذا عطلا في البرنامج أو قاعدة البيانات)؟", ok, new ButtonType("لا", ButtonBar.ButtonData.CANCEL_CLOSE));
					alert.setTitle("تحذير");
					alert.setHeaderText(null);
					alert.initOwner(primaryStage);
					if (alert.showAndWait().get() == ok)
					{
						// Thread.interrupt() will close the DB connection internally, we cannot control it by isInterrupted().
						updateThread.interrupt(); // Version 2.6, Remember that it will affect only the current blocking IO, the next IO in the thread will not be blocked, we will use 'if(isInterrupted) break' for that
						close();
					}
				}
				else
					close();
			}
			else
				close();

			AC.updateMenuItem.setDisable(false);
		});
		show();
		descriptionTextArea.setText(descriptionTxt.toString()); // workaround because of a bug having a lead before the text. solution setText after show()
	}
}