package com.maknoon;

/*
	setting.txt format:
	Line 1: Directory path (default is internal -> '<APP ROOT PATH>/audios/')
	Line 2: Internet path ('https://www.maknoon.com/download/audios/')
	Line 3: Default media choice i.e. DIRECTORY, INTERNET
 */
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.sql.*;

class Settings extends Dialog
{
	Settings(final Stage primaryStage)
	{
		super();
		initOwner(primaryStage);

		setTitle("إعدادات");
		setHeaderText("تحديد موضع الملفات الصوتية");
		setResizable(false);

		final BorderPane directoryPathPanel = new BorderPane();
		directoryPathPanel.setPadding(new Insets(10.0));

		final TextField directoryPathTextField = new TextField(AudioCataloger.directoryPath);
		directoryPathTextField.setPrefColumnCount(30);
		directoryPathTextField.setEditable(false);
		directoryPathPanel.setCenter(directoryPathTextField);
		BorderPane.setMargin(directoryPathTextField, new Insets(2.0));

		final Button directoryPathButton = new Button("تعيين");
		directoryPathButton.setTooltip(new Tooltip("استعراض لموضع الملفات الصوتية على القرص الصلب (HD)، سيقوم البرنامج بإنشاء المجلدات في الموضع المحدد"));
		directoryPathPanel.setLeft(directoryPathButton);
		BorderPane.setMargin(directoryPathButton, new Insets(2.0));
		directoryPathButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				final DirectoryChooser dc = new DirectoryChooser();
				dc.setTitle("فتح");

				try
				{
					final File defaultFolder = new File(AudioCataloger.cl.getResource("audios").toURI());
					if (directoryPathTextField.getText().equals("<APP ROOT PATH>/audios/"))
						dc.setInitialDirectory(defaultFolder);
					else
						dc.setInitialDirectory(new File(directoryPathTextField.getText()));

					final File f = dc.showDialog(primaryStage);
					if (f != null)
					{
						if (f.compareTo(defaultFolder) == 0)
							directoryPathTextField.setText("<APP ROOT PATH>/audios/");
						else
						{
							String p = f.getAbsolutePath();
							if (!p.endsWith(File.separator)) // i.e. Not driver C:\ OR D:\ OR ...
								p = p + File.separator;
							directoryPathTextField.setText(p);
						}
					}
					else
						System.out.println("Attachment cancelled by user.");
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}
			}
		});

		final RadioButton DirectoryPathRadioButton = new RadioButton("القرص الصلب (HD)");
		final RadioButton internetPathRadioButton = new RadioButton("الاتصال المباشر بالإنترنت");

		final ToggleGroup PathGroup = new ToggleGroup();
		DirectoryPathRadioButton.setToggleGroup(PathGroup);
		internetPathRadioButton.setToggleGroup(PathGroup);

		final EventHandler<ActionEvent> CARODirectoryListener = new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent ae)
			{
				if (ae.getSource() == DirectoryPathRadioButton)
				{
					directoryPathButton.setDisable(false);
					directoryPathTextField.setDisable(false);
				}

				if (ae.getSource() == internetPathRadioButton)
				{
					directoryPathButton.setDisable(true);
					directoryPathTextField.setDisable(true);
				}
			}
		};
		DirectoryPathRadioButton.setOnAction(CARODirectoryListener);
		internetPathRadioButton.setOnAction(CARODirectoryListener);

		if (AudioCataloger.defaultMediaChoice == AudioCataloger.pathMedia.DIRECTRY)
			DirectoryPathRadioButton.setSelected(true);
		else
		{
			// i.e. defaultChoice = AudioCataloger.pathMedia.INTERNET
			directoryPathButton.setDisable(true);
			directoryPathTextField.setDisable(true);
			internetPathRadioButton.setSelected(true);
		}

		final HBox PathPanel = new HBox(DirectoryPathRadioButton, internetPathRadioButton);
		PathPanel.setAlignment(Pos.CENTER);
		PathPanel.setSpacing(30.0);

		final VBox mainPanel = new VBox(PathPanel, directoryPathPanel);
		getDialogPane().setContent(mainPanel);

		// Put at the last to have LEFT TO RIGHT Orientation before Container Orientation since it will override all Orientations previously
		directoryPathTextField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

		final ButtonType okButton = new ButtonType("إدخال", ButtonBar.ButtonData.OK_DONE);
		final ButtonType cancelButton = new ButtonType("إلغاء", ButtonBar.ButtonData.CANCEL_CLOSE);
		getDialogPane().getButtonTypes().addAll(okButton, cancelButton);

		final Optional<ButtonType> result = showAndWait();
		if (result.get() == okButton)
		{
			if (DirectoryPathRadioButton.isSelected())
				AudioCataloger.directoryPath = directoryPathTextField.getText();

			try
			{
				final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(new File(AudioCataloger.cl.getResource("setting/setting.txt").toURI())), StandardCharsets.UTF_8);
				out.write(AudioCataloger.directoryPath + System.lineSeparator());
				out.write(AudioCataloger.internetPath + System.lineSeparator());

				if (DirectoryPathRadioButton.isSelected())
				{
					out.write("DIRECTRY" + System.lineSeparator());
					AudioCataloger.defaultMediaChoice = AudioCataloger.pathMedia.DIRECTRY;
				}
				else
				{
					out.write("INTERNET" + System.lineSeparator());
					AudioCataloger.defaultMediaChoice = AudioCataloger.pathMedia.INTERNET;
				}

				out.close();
			}
			catch (Exception ex)
			{
				ex.printStackTrace();
			}

			if (DirectoryPathRadioButton.isSelected())
			{
				try
				{
					if (AudioCataloger.directoryPath.equals("<APP ROOT PATH>/audios/"))
						AudioCataloger.choosedAudioPath = new File(AudioCataloger.cl.getResource("audios").toURI()).getAbsolutePath() + File.separator;
					else
						AudioCataloger.choosedAudioPath = AudioCataloger.directoryPath;
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}

				// Version 2.6, Thread instead of SwingWorker
				final Thread thread = new Thread()
				{
					public void run()
					{
						try
						{
							/*
							 * Update version 1.6
							 * Re-opening the DB will make it work in READ mode and AUTO modes.
							 *
							 * This will NOT prevent breaking the DB connection when you shutdown the program directly
							 * after this leading to not creating the folders. The DB should  work in multi-user database access mode
							 * in which is not possible with derby unless running inside a server framework.
							 * Then we use Thread manager to wait till the creation is finished.
							 * N.B. The following code is tried to do that with CreateFolders class.

							try
							{
								// Version 1.6, System.getProperty("java.home") to call the class using the correct java version (the one that is used by the parent class) and not necessary the one in the path.
								if(AC.windows)
								{
									Runtime.getRuntime().exec("cmd.exe /c java -classpath .;lib/derby.jar CreateFolders \""+AC.directryPath+"\"");
									System.out.println("cmd.exe /c java -classpath .;lib/derby.jar CreateFolders \""+AC.directryPath+"\"");
								}
								else
								{
									String [] com ={"/bin/bash", "-c", System.getProperty("java.home")+fileSeparator+"bin"+fileSeparator+"java -classpath .;lib/derby.jar CreateFolders \""+AC.directryPath+"\""};
									Runtime.getRuntime().exec(com);
								}
							}
							catch(IOException ae){ae.printStackTrace();}
							*/

							final Statement stmt = AudioCataloger.sharedDBConnection.createStatement();
							final ResultSet rs = stmt.executeQuery("SELECT Path FROM Chapters GROUP BY Path");
							//final Vector<String> paths = new Vector<String>();

							while (rs.next())
							{
								/*
								 * Update version 1.6
								 * Using File.mkdirs() instead of the previous method:

								if(AudioCataloger.windows)
									Runtime.getRuntime().exec("cmd.exe /c mkdir \""+AC.directryPath+fileSeparator+rs.getString("Path")+"\"");
								else
								{
									// You have to edit this to create the folders in order
									final StringTokenizer tokens = new StringTokenizer(AC.directryPath+fileSeparator+rs.getString("Path"), "\\/");
									String path = "";
									while(tokens.hasMoreTokens())
									{
										path = path + fileSeparator + tokens.nextToken();

										// To speed up the process.
										// It may take 45 second to do the job so we remove the repition
										if(paths.indexOf(path)>-1);
										else
										{
											paths.addElement(path);

											/*
											 * Update version 1.6
											 * We remove Thread.sleep(60); (To catch the system delay). Instead
											 * waitFor() causes the current thread to wait, until the process represented
											 * has terminated. This to make sure that computers with low speeds can create
											 * these folders.

												final String [] com ={"/bin/bash", "-c", "mkdir \""+path+"\""};
												final Process wait = Runtime.getRuntime().exec(com);
												wait.waitFor();

											 * Then RuntimeExec is used since the previous method generate dead locks.
											 *

											new RuntimeExec("mkdir \""+path+"\"");
										}
									}
								}
								*/

								final File folder = new File(AudioCataloger.choosedAudioPath + (com.sun.jna.Platform.isWindows() ? rs.getString("Path") : rs.getString("Path").replace('\\', '/')));
								final boolean success = folder.mkdirs();
								if (!success) System.out.println("Directory creation failed: " + folder);
							}
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}
					}
				};
				thread.start();
			}
			else
				// i.e. pathMedia.INTERNET
				AudioCataloger.choosedAudioPath = AudioCataloger.internetPath;
		}
	}
}