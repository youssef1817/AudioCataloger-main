package com.maknoon;

import java.io.*;
import java.util.*;
import java.sql.*;
import java.util.concurrent.*;

import com.sun.jna.NativeLibrary;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import uk.co.caprica.vlcj.binding.support.runtime.RuntimeUtil;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.media.MediaRef;
import uk.co.caprica.vlcj.media.TrackType;
import uk.co.caprica.vlcj.player.base.LibVlcConst;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventListener;

import static com.maknoon.AudioCataloger.cl;
import static com.maknoon.AudioCataloger.derbyInUse;

// TODO: MPV with java using JSON-based IPC protocol or
// https://github.com/MacFJA/MpvService
class JVLC extends Stage implements MediaPlayerEventListener
{
	private MediaPlayer player = null;
	private MediaPlayerFactory mediaPlayerFactory = null;
	//private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
	private final Slider soundSilder, volumeSilder;
	private long currentTime; // In millisecond
	private long endTime; // In millisecond
	private boolean silderIsPressed;
	private long lastSeekValue = 0; // In millisecond
	private final Label fileLengthLabel, fileLengthSeekLabel, timeLabel, seekLabel;
	final Button play_pauseButton, stopButton, deletePlayListButton;
	private final Vector<Integer> audioCode = new Vector<>(10);
	private final Vector<Integer> audioOffset = new Vector<>(10);
	private final Vector<Integer> audioSeq = new Vector<>(10);
	private final ListView<String> playList;
	private final AudioCataloger AC;

	private final Line endPoint = new Line();
	private final Line progress = new Line();

	private boolean nativeVlcReady = true;

	JVLC(final Stage primaryStage, final AudioCataloger ac)
	{
		super();
		initOwner(primaryStage);
		setResizable(false);
		getIcons().add(new javafx.scene.image.Image(cl.getResource("images/icon.png").toString()));

		AC = ac;

		seekLabel = new Label();
		fileLengthSeekLabel = new Label();

		final BorderPane seekPanel = new BorderPane();
		seekPanel.setLeft(fileLengthSeekLabel);
		seekPanel.setCenter(new Label(" / "));
		seekPanel.setRight(seekLabel);
		seekPanel.setDisable(true);

		soundSilder = new Slider();
		soundSilder.setDisable(true);

		endPoint.setStroke(Color.rgb(200, 200, 200));
		progress.setStroke(Color.rgb(200, 200, 200));

		/*
		soundSilder.boundsInParentProperty().addListener((observable, oldValue, newValue) -> {
			drawEndPoint();
		});
		*/

		soundSilder.valueProperty().addListener(new ChangeListener<Number>()
		{
			public void changed(ObservableValue<? extends Number> ov, Number old_val, Number new_val)
			{
				lastSeekValue = new_val.intValue();
				Platform.runLater(() ->
				{
					seekLabel.setText(String.format("%d:%02d:%04.1f ", TimeUnit.MILLISECONDS.toHours(lastSeekValue), TimeUnit.MILLISECONDS.toMinutes(lastSeekValue) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(lastSeekValue)), (((float) lastSeekValue / 1000) % 60)));
					//soundSilder.setValue(lastSeekValue); // Version 2.7, New Mouse Implementation
				});

				drawEndPoint();
			}
		});

		soundSilder.setOnMouseReleased(new EventHandler<MouseEvent>()
		{
			@Override
			public void handle(MouseEvent m)
			{
				silderIsPressed = false; // Version 3.0

				System.out.println("silder is released");

				// Important, these two lines should be executed as atomic operation. If currentTime is changed between them it will return to its previous state.
				currentTime = lastSeekValue;
				player.controls().setTime(currentTime);

				setEndTime(-1L);
			}
		});

		soundSilder.setOnMousePressed(new EventHandler<MouseEvent>()
		{
			@Override
			public void handle(MouseEvent m)
			{
				silderIsPressed = true; // Version 3.0

				/*
				if(!soundSilder.isDisabled())
				{
					System.out.println("uuuuuuuuuuuuuuuuuuuuuu");

					// Important, those lines should be executed as atomic operation. If currentTime is changed between them due to LineRedirecter it will return to its previous state.
					lastSeekValue = basicSliderUI.valueForXPosition(e.getX());
					currentTime = lastSeekValue; // Version 3.0

					// Version 2.5, when pause, you cannot seek by clicking on the track. This is to solve it.
					//if(!playingMode) // Version 3.0
					soundSilder.setValue(lastSeekValue);
					seekLabel.setText(String.format("%d:%02d:%04.1f ", TimeUnit.MILLISECONDS.toHours(lastSeekValue), TimeUnit.MILLISECONDS.toMinutes(lastSeekValue) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(lastSeekValue)), (((float) lastSeekValue / 1000) % 60)));
				}
				*/
			}
		});

		// selected -> play mode , default not selected -> pause
		play_pauseButton = new Button(null, new ImageView(new Image(getClass().getResourceAsStream("/images/pause.png"))));
		play_pauseButton.setDisable(true); // Initially
		play_pauseButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				if (!player.status().isPlaying())
				{
					player.controls().play();
					setEndTime(-1L);
				}
				else
				{
					player.controls().pause();
				}
			}
		});

		stopButton = new Button(null, new ImageView(new Image(getClass().getResourceAsStream("/images/stop.png"))));
		stopButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				player.controls().stop();
			}
		});

		final Button forwardButton = new Button(null, new ImageView(new Image(getClass().getResourceAsStream("/images/skip_back.png"))));
		final Button backwardButton = new Button(null, new ImageView(new Image(getClass().getResourceAsStream("/images/skip_forward.png"))));
		final ToggleButton muteButton = new ToggleButton(null, new ImageView(new Image(getClass().getResourceAsStream("/images/volume.png"))));

		forwardButton.setPadding(new Insets(10, 12, 10, 12));
		backwardButton.setPadding(new Insets(10, 12, 10, 12));
		stopButton.setPadding(new Insets(10, 12, 10, 12));
		play_pauseButton.setPadding(new Insets(10, 12, 10, 12));

		timeLabel = new Label();
		fileLengthLabel = new Label();
		final BorderPane timePanel = new BorderPane();
		timePanel.setLeft(fileLengthLabel);
		timePanel.setCenter(new Label(" / "));
		timePanel.setRight(timeLabel);

		timeLabel.setText("0:00:00.0 ");
		seekLabel.setText("0:00:00.0 ");
		fileLengthLabel.setText(" 0:00:00.0");
		fileLengthSeekLabel.setText(" 0:00:00.0");

		final HBox audioControlPanel = new HBox();
		audioControlPanel.setSpacing(3);
		audioControlPanel.setPadding(new Insets(0, 20, 0, 20));
		audioControlPanel.getChildren().addAll(
				backwardButton,
				play_pauseButton,
				forwardButton,
				stopButton
		);

		volumeSilder = new Slider(LibVlcConst.MIN_VOLUME, LibVlcConst.MAX_VOLUME, 0.5);
		volumeSilder.valueProperty().addListener(new ChangeListener<Number>()
		{
			public void changed(ObservableValue<? extends Number> ov, Number old_val, Number new_val)
			{
				if (muteButton.isSelected())
					muteButton.setSelected(false);

				player.audio().setVolume(new_val.intValue());
			}
		});

		muteButton.selectedProperty().addListener((obs, wasSelected, isNowSelected) ->
		{
			if (isNowSelected)
			{
				player.audio().setMute(true);
				muteButton.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/images/mute.png"))));
			}
			else
			{
				player.audio().setMute(false);
				muteButton.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/images/volume.png"))));
			}
		});

		final BorderPane volumePanel = new BorderPane();
		volumePanel.setRight(muteButton);
		volumePanel.setCenter(volumeSilder);

		final Button addPlayListButton = new Button(null, new ImageView(new Image(getClass().getResourceAsStream("/images/add_playlist.png"))));
		addPlayListButton.setTooltip(new Tooltip("أضف إلى المفضلة"));
		addPlayListButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				if (running_Code != 0) // There is a running clip
				{
					final Thread thread = new Thread()
					{
						public void run()
						{
							try
							{
								if (player.status().isPlaying())
								{
									Platform.runLater(() -> {
										play_pauseButton.fire();
									});
								}

								long offset = player.status().time();
								final Statement stmt = AudioCataloger.sharedDBConnection.createStatement();
								stmt.execute("INSERT INTO PlayList VALUES (" + running_Code + ", -1, " + offset + ", '" + running_Line + "')");
								stmt.close();
							}
							catch (Exception e)
							{
								e.printStackTrace();
							}

							Platform.runLater(() -> {
								refreshPlayList();
							});
						}
					};
					thread.start();
				}
				else
					AudioCataloger.alertError("لا يمكن الإضافة إلى المفضلة إلا أثناء الاستماع إلى شريط معين", "تنبيه", "متابعة", null, Alert.AlertType.ERROR);
			}
		});

		deletePlayListButton = new Button(null, new ImageView(new Image(getClass().getResourceAsStream("/images/delete_playlist.png"))));
		deletePlayListButton.setTooltip(new Tooltip("حذف من المفضلة"));
		deletePlayListButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				int index = playList.getSelectionModel().getSelectedIndex();
				if (index != -1)
				{
					try
					{
						final Statement stmt = AudioCataloger.sharedDBConnection.createStatement();
						stmt.executeUpdate(derbyInUse?
								"DELETE FROM PlayList WHERE Code=" + audioCode.elementAt(index) + " AND Seq=" + audioSeq.elementAt(index) + " AND Offset=" + audioOffset.elementAt(index)
								:"DELETE FROM PlayList WHERE Code=" + audioCode.elementAt(index) + " AND Seq=" + audioSeq.elementAt(index) + " AND \"Offset\"=" + audioOffset.elementAt(index));
						stmt.close();
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
					}

					Platform.runLater(() -> {
						refreshPlayList();
					});
				}
			}
		});

		playList = new ListView<>();
		playList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>()
		{
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
			{
				int index = playList.getSelectionModel().getSelectedIndex();
				if (index != -1)
				{
					AC.selectTreeNode(audioCode.elementAt(index));
					if (audioSeq.elementAt(index) != -1)
					{
						//AC.audioDetailsList.getSelectionModel().clearSelection();
						AC.audioDetailsList.getSelectionModel().select(AC.audioDetailsSeq.indexOf(audioSeq.elementAt(index)));
						AC.audioDetailsList.scrollTo(AC.audioDetailsSeq.indexOf(audioSeq.elementAt(index)));
					}
					AC.firePlayListSelectionListener = true;
				}
			}
		});

		playList.setOnMouseClicked(new EventHandler<MouseEvent>()
		{
			@Override
			public void handle(MouseEvent m)
			{
				int playListIndex = playList.getSelectionModel().getSelectedIndex();
				if (!AC.firePlayListSelectionListener)
				{
					playList.getSelectionModel().clearSelection();
					playList.getSelectionModel().select(playListIndex);
				}

				if (m.getClickCount() == 2 && playListIndex != -1)
					player(playListIndex);
			}
		});

		forwardButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				int playListIndex = playList.getSelectionModel().getSelectedIndex();
				if (playListIndex == -1)
				{
					if (!audioOffset.isEmpty())
					{
						playList.getSelectionModel().select(0);
						player(0);
					}
				}
				else
				{
					if (playListIndex < (audioOffset.size() - 1))
					{
						playList.getSelectionModel().select(++playListIndex);
						player(playListIndex);
					}
				}
			}
		});

		backwardButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				int playListIndex = playList.getSelectionModel().getSelectedIndex();
				if (playListIndex > 0)
				{
					playList.getSelectionModel().select(--playListIndex);
					player(playListIndex);
				}
			}
		});

		final Button addIndex = new Button(null, new ImageView(new Image(getClass().getResourceAsStream("/images/add_index.png"))));
		addIndex.setTooltip(new Tooltip("أضف فهرسة جديدة إلى الشريط الحالي"));
		addIndex.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				if (running_Code != 0) // There is a running clip
				{
					final Thread thread = new Thread()
					{
						public void run()
						{
							try
							{
								if (player.status().isPlaying())
									play_pauseButton.fire();

								final int offset = (int) player.status().time();
								AC.selectTreeNode(running_Code);

								Platform.runLater(() -> {
									AC.new AddIndex(offset, true);
								});
							}
							catch (Exception ie)
							{
								ie.printStackTrace();
							}
						}
					};
					thread.start();
				}
				else
					AudioCataloger.alertError("لا يمكن إضافة فهرسة جديدة إلا أثناء الاستماع إلى شريط معين", "تنبيه", "متابعة", null, Alert.AlertType.ERROR);
			}
		});

		final GridPane decorate_west = new GridPane();
		decorate_west.add(timePanel, 0, 0);
		decorate_west.add(seekPanel, 0, 1);
		decorate_west.setPadding(new Insets(5.0, 5.0, 5.0, 5.0));

		final HBox saveHbox = new HBox();
		saveHbox.setSpacing(3);
		saveHbox.setAlignment(Pos.CENTER);
		saveHbox.getChildren().addAll(addPlayListButton, deletePlayListButton, addIndex);

		// Button size is changed on mouse over. solved by fixing the size
		addPlayListButton.setPadding(new Insets(5, 9, 5, 9));
		deletePlayListButton.setPadding(new Insets(5, 9, 5, 9));
		addIndex.setPadding(new Insets(5, 9, 5, 9));

		final VBox decorate_east = new VBox();
		decorate_east.setPadding(new Insets(0.0, 2.0, 0.0, 2.0));
		decorate_east.getChildren().addAll(volumePanel, saveHbox);

		final Pane pane = new Pane(endPoint, soundSilder, progress);
		pane.setPadding(new Insets(2.0, 2.0, 2.0, 2.0));
		soundSilder.prefWidthProperty().bind(pane.widthProperty());

		final BorderPane playerPanel = new BorderPane();
		playerPanel.setPadding(new Insets(7.0, 7.0, 7.0, 7.0));
		playerPanel.setRight(decorate_west);
		playerPanel.setTop(pane);
		playerPanel.setCenter(audioControlPanel);
		playerPanel.setLeft(decorate_east);

		final BorderPane mainPanel = new BorderPane();
		mainPanel.setCenter(playList);
		mainPanel.setTop(playerPanel);

		final Scene scene = new Scene(mainPanel, 600.0, 350.0);
		scene.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

		setScene(scene);
		setResizable(false);
		setTitle("مشغل الصوتيات");
		setOnCloseRequest(new EventHandler<WindowEvent>()
		{
			@Override
			public void handle(WindowEvent we)
			{
				stopButton.fire();
			}
		});

		// Load the play list
		refreshPlayList();

		try
		{
			// This solved by jna 4.2.0 and above only
			//System.setProperty("VLC_PLUGIN_PATH", new File(AudioCataloger.cl.getResource("bin/vlc/plugins").getFile()).getAbsolutePath());
			if (com.sun.jna.Platform.isWindows())
			{
				// https://github.com/caprica/vlcj/issues/901
				NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), new File(cl.getResource("bin/vlc").toURI()).getAbsolutePath());
				//LibC.INSTANCE._putenv("VLC_PLUGIN_PATH=" + new File(AudioCataloger.cl.getResource("bin/vlc/plugins").toURI()).getAbsolutePath());
				//libvlc_instance_t instance = libvlc_new(0, new StringArray(new String[0]));
				//if (instance != null) libvlc_release(instance);
			}
			else
			{
				if (com.sun.jna.Platform.isMac())
				{
					//System.setProperty("VLC_PLUGIN_PATH", new File(cl.getResource("bin/vlc/Contents/MacOS/plugins").getFile()).getAbsolutePath());

					// classpath is not working by setting it here. it is done in the classpath while creating the jar file
					//NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), new File(cl.getResource("bin/vlc/Contents/MacOS/lib").toURI()).getAbsolutePath()); // VLC.app is renamed to vlc to prevent making Mac OSX consider it as an application
					//NativeLibrary.addSearchPath(RuntimeUtil.getLibVlcLibraryName(), "/Applications/VLC.app/Contents/Frameworks"); // VLC.app is renamed to vlc to prevent making Mac OSX consider it as an application
				}
				// No need anymore
				//else
				//new NativeDiscovery().discover();
			}
			//Native.loadLibrary(RuntimeUtil.getLibVlcLibraryName(), LibVlc.class);

			String[] VLC_ARGS = {
					"--intf=dummy",			// no interface
					"--no-video",			// disables video output
					"--no-stats",			// no stats
					"--no-plugins-cache",	// for accurate seek via internet
					"--quiet",				// Slow start playback !
					"--file-caching=0",		// No effect
					"--disc-caching=0",		// No effect
					//"--network-caching=0",
					//"--codec=ffmpeg",
			};

			mediaPlayerFactory = new MediaPlayerFactory(VLC_ARGS);
			player = mediaPlayerFactory.mediaPlayers().newMediaPlayer();
			player.events().addMediaPlayerEventListener(this);
		}
		catch (Throwable e)
		{
			e.printStackTrace();
			getScene().getRoot().setDisable(true);
			nativeVlcReady = false;
		}

		// Another risky option
		//executorService.scheduleAtFixedRate(new UpdateRunnable(player), 0L, 200L, TimeUnit.MILLISECONDS); // VLC resolution is around 200/300 ms [https://github.com/caprica/vlcj/issues/74]
	}

	boolean nativeVlcReady()
	{
		return nativeVlcReady;
	}

	@Override
	public void timeChanged(MediaPlayer mediaPlayer, long newTime)
	{
		currentTime = newTime;

		try
		{
			// Updates to user interface components must be executed on the Event Dispatch Thread
			Platform.runLater(() -> {
				timeLabel.setText(String.format("%d:%02d:%04.1f ", TimeUnit.MILLISECONDS.toHours(currentTime), TimeUnit.MILLISECONDS.toMinutes(currentTime) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(currentTime)), (((float) currentTime / 1000) % 60))); // currentTime for seconds since we are displaying it in fractions.
				if (!silderIsPressed) // To make sure that it will not trigger ChangeListener while the user is pressing *sliding* the slider
					soundSilder.setValue((double) currentTime); // This triggers soundSlider.addChangeListener. Version 3.0
			});

			if (endTime <= currentTime && endTime != -1L) // If -1, no time limit. It should be until the end of the file.
			{
				mediaPlayer.controls().pause();
				setEndTime(-1L);
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void mediaChanged(MediaPlayer mediaPlayer, MediaRef media)
	{
	}

	@Override
	public void opening(MediaPlayer mediaPlayer)
	{
	}

	@Override
	public void buffering(MediaPlayer mediaPlayer, float newCache)
	{
		//System.out.println(newCache);

		Platform.runLater(() ->
		{
			setTitle("جاري التحميل " + newCache);

			if (newCache == 100)
			{
				fileLengthLabel.setText(String.format(" %d:%02d:%04.1f", TimeUnit.MILLISECONDS.toHours(running_Length), TimeUnit.MILLISECONDS.toMinutes(running_Length) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(running_Length)), (((float) running_Length / 1000) % 60)));
				fileLengthSeekLabel.setText(fileLengthLabel.getText());

				// To solve the issue of not displaying the endtime arrow (for the first time) because of the time different between jmplayer.open() and Starting playback in case of internet connection.
				setEndTime(endTime);

				running_Code = AC.selected_Code;

				// try-catch for both conditions.
				// This is to avoid hanging when the user click on one clip to play and immediately orderButton.doClick(), tree.getSelectionPath() will through exception and hang the JMplayer and shutdown.
				try
				{
					if (AC.feqhTreeSelected)
					{
						final Statement stmt = AudioCataloger.sharedDBConnection.createStatement();
						final ResultSet rs = stmt.executeQuery("SELECT Book_name, Sheekh_name, Title, FileName FROM Chapters WHERE Code=" + running_Code);
						rs.next();
						running_Line = rs.getString("Sheekh_name") + " ← " + rs.getString("Book_name") + " ← " + rs.getString("Title") + " ← " + rs.getString("FileName");
						stmt.close();
					}
					else
					{
						// TODO: if you click orderbutton, it will throw java.lang.NullPointerException
						TreeItem<AudioInfo> item = AC.tree.getSelectionModel().getSelectedItem();
						final StringBuilder pathBuilder = new StringBuilder(10);
						pathBuilder.insert(0, item.getValue().toString().replace("(", " ← ").replace(")", ""));
						pathBuilder.insert(0, " ← ");
						for (item = item.getParent(); item != null && item.getParent() != null; )
						{
							pathBuilder.insert(0, item.getValue());

							item = item.getParent();
							if (item != null && item.getParent() != null)
								pathBuilder.insert(0, " ← ");
						}
						running_Line = pathBuilder.toString();
					}
					setTitle(running_Line);
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
					setTitle("استماع");
				}
			}
		});
	}

	@Override
	public void playing(MediaPlayer mediaPlayer)
	{
		System.out.println("playing");
		Platform.runLater(() -> {
			play_pauseButton.setDisable(false);
			play_pauseButton.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/images/pause.png"))));
			soundSilder.setDisable(false);
		});
	}

	@Override
	public void paused(MediaPlayer mediaPlayer)
	{
		System.out.println("paused");
		Platform.runLater(() -> {
			play_pauseButton.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/images/play.png"))));
		});
	}

	@Override
	public void stopped(MediaPlayer mediaPlayer)
	{
		System.out.println("stopped");
		Platform.runLater(() ->
		{
			soundSilder.setDisable(true);
			soundSilder.setValue(0);
			timeLabel.setText("0:00:00.0 ");
			fileLengthLabel.setText(" 0:00:00.0");
			fileLengthSeekLabel.setText(" 0:00:00.0");
			seekLabel.setText("0:00:00.0 ");
			play_pauseButton.setDisable(true);
			setTitle("مشغل الصوتيات");
			running_Code = 0;
			endTime = -1L;
			running_Line = "";
			play_pauseButton.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/images/play.png"))));
			drawEndPoint();
		});
	}

	@Override
	public void forward(MediaPlayer mediaPlayer)
	{
		System.out.println("forward");
	}

	@Override
	public void backward(MediaPlayer mediaPlayer)
	{
		System.out.println("backward");
	}

	@Override
	public void finished(MediaPlayer mediaPlayer)
	{
		System.out.println("finished");
		Platform.runLater(() ->
		{
			soundSilder.setDisable(true);
			soundSilder.setValue(0);
			timeLabel.setText("0:00:00.0 ");
			fileLengthLabel.setText(" 0:00:00.0");
			fileLengthSeekLabel.setText(" 0:00:00.0");
			seekLabel.setText("0:00:00.0 ");
			play_pauseButton.setDisable(true);
			setTitle("مشغل الصوتيات");
			running_Code = 0;
			endTime = -1L;
			running_Line = "";
			play_pauseButton.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/images/play.png"))));
			drawEndPoint();
		});
	}

	@Override
	public void positionChanged(MediaPlayer mediaPlayer, float newPosition)
	{
		//System.out.println("positionChanged");
	}

	@Override
	public void seekableChanged(MediaPlayer mediaPlayer, int newSeekable)
	{
		Platform.runLater(() -> {
			play_pauseButton.setGraphic(new ImageView(new Image(getClass().getResourceAsStream("/images/play.png"))));
			play_pauseButton.setDisable(true);
			soundSilder.setDisable(true);
		});

		System.out.println("seekableChanged");
	}

	@Override
	public void pausableChanged(MediaPlayer mediaPlayer, int newSeekable)
	{
		System.out.println("pausableChanged");
	}

	@Override
	public void titleChanged(MediaPlayer mediaPlayer, int i)
	{
	}

	@Override
	public void snapshotTaken(MediaPlayer mediaPlayer, String filename)
	{
	}

	@Override
	public void lengthChanged(MediaPlayer mediaPlayer, long newLength)
	{
		System.out.println("lengthChanged");
	}

	@Override
	public void videoOutput(MediaPlayer mediaPlayer, int newCount)
	{
	}

	@Override
	public void scrambledChanged(MediaPlayer mediaPlayer, int i)
	{
	}

	@Override
	public void elementaryStreamAdded(MediaPlayer mediaPlayer, TrackType trackType, int i)
	{
	}

	@Override
	public void elementaryStreamDeleted(MediaPlayer mediaPlayer, TrackType trackType, int i)
	{
	}

	@Override
	public void elementaryStreamSelected(MediaPlayer mediaPlayer, TrackType trackType, int i)
	{
	}

	@Override
	public void error(MediaPlayer mediaPlayer)
	{
		Platform.runLater(() ->
		{
			setTitle("حدث خطأ أثناء تحميل الملف");
		});
	}

	@Override
	public void mediaPlayerReady(MediaPlayer mediaPlayer)
	{
	}

	@Override
	public void volumeChanged(MediaPlayer mediaPlayer, float volume)
	{
	}

	@Override
	public void muted(MediaPlayer mediaPlayer, boolean muted)
	{
	}

	@Override
	public void audioDeviceChanged(MediaPlayer mediaPlayer, String audioDevice)
	{
	}

	@Override
	public void corked(MediaPlayer mediaPlayer, boolean corked)
	{
	}

	@Override
	public void chapterChanged(MediaPlayer mediaPlayer, int newChapter)
	{
	}

	private void player(int playListIndex)
	{
		int nextOffset;
		if (audioSeq.elementAt(playListIndex) != -1)
		{
			if ((AC.detailsSelectedIndex + 1) < AC.audioDetailsOffset.size())
				nextOffset = AC.audioDetailsOffset.elementAt(AC.detailsSelectedIndex + 1);
			else
				nextOffset = -1;

			if (AC.detailsSelectedIndex != 0)
				if (AC.audioDetailsOffset.elementAt(AC.detailsSelectedIndex) < AC.audioDetailsOffset.elementAt(AC.detailsSelectedIndex - 1))
					nextOffset = -1;
		}
		else
			nextOffset = -1;

		// Offset should come from PlayList table since it can be a different offset than in Contents (customizable).
		AC.player(AudioCataloger.choosedAudioPath + AC.selected_FileName, audioOffset.elementAt(playListIndex), nextOffset);
	}

	/*
    private final class UpdateRunnable implements Runnable
    {
        private final MediaPlayer mediaPlayer;
        private UpdateRunnable(MediaPlayer mediaPlayer) {this.mediaPlayer = mediaPlayer;}

        public void run()
        {
            try
            {
                currentTime = mediaPlayer.getTime();

                // Updates to user interface components must be executed on the Event Dispatch Thread
                SwingUtilities.invokeAndWait(new Runnable()
                {
                    public void run()
                    {
                        if(mediaPlayer.isPlaying())
                        {
                            timeLabel.setText(String.format("%d:%02d:%04.1f ", TimeUnit.MILLISECONDS.toHours(currentTime), TimeUnit.MILLISECONDS.toMinutes(currentTime) - TimeUnit.HOURS.toMinutes(TimeUnit.MILLISECONDS.toHours(currentTime)), (((float)currentTime/1000)%60))); // currentTime for seconds since we are displaying it in fractions.
                            //timeLabel.setText(String.format("%d:%02d:%04.1f ", currentTime/3600000, ((currentTime/1000)%3600)/60, (((float)currentTime/1000)%60))); // currentTime for seconds since we are displaying it in fractions.
                            //if(!sliderMousePressed) // To make sure that it will not trigger ChangeListener while the user is pressing *sliding* the slider
                            if(!soundSilder.getValueIsAdjusting() && !silderIsPressed)
                                soundSilder.setValue((int)currentTime/100); // This triggers soundSlider.addChangeListener. Version 3.0
                        }
                    }
                });

                if(endTime <= currentTime && endTime!=-1) // If -1, no time limit. It should be until the end of the file.
                {
                    if(mediaPlayer.isPlaying())
                        mediaPlayer.pause();

                    SwingUtilities.invokeAndWait(new Runnable(){public void run()
                    {
                        play_pauseButton.setIcon(new ImageIcon(AudioCataloger.programFolder+"images/play.png"));
                        play_pauseButton.setRolloverIcon(new ImageIcon(AudioCataloger.programFolder+"images/play_rollover.png"));
                        play_pauseButton.setDisabledIcon(new ImageIcon(AudioCataloger.programFolder+"images/play_disable.png"));
                        play_pauseButton.setPressedIcon(new ImageIcon(AudioCataloger.programFolder+"images/play_pressed.png"));
                    }});

                    setEndTime(-1);
                }
            }
            catch(Exception e){e.printStackTrace();}
        }
    }
    */

	void refreshPlayList()
	{
		audioCode.removeAllElements();
		audioOffset.removeAllElements();
		audioSeq.removeAllElements();
		final ObservableList<String> audioLine = FXCollections.observableArrayList();

		try
		{
			final Statement stmt = AudioCataloger.sharedDBConnection.createStatement();
			final ResultSet rs = stmt.executeQuery("SELECT * FROM PlayList");
			while (rs.next())
			{
				audioCode.addElement(rs.getInt("Code"));
				audioOffset.addElement(rs.getInt("Offset"));
				audioSeq.addElement(rs.getInt("Seq"));
				audioLine.add(rs.getString("Line"));
			}
			stmt.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
		playList.setItems(audioLine);
	}

	private int running_Code = 0;
	private int running_Length = 0;
	private String running_Line = "";

	void open(String file, int timePos, int endTimePos, int fileLength)
	{
		System.out.println("open");
		if (player == null) // vlc is not in the shell path (not installed in Linux/Unix)
			AudioCataloger.alertError("لا يمكنك الاستماع لأن برنامج VLC غير منصب على جهازك، قم بتنصيبه أولا", "تنبيه", "متابعة", null, Alert.AlertType.ERROR);
		else
		{
			running_Length = fileLength;

			if (file.startsWith("http"))
			{
				//URL url = new URL(file.replace('\\', '/'));
				//String encodedurl = java.net.URLEncoder.encode(url.toString(), "UTF-8");
				// Arabic Files should be uploaded with FTP UTF8 client. Filezilla is not working. FireFTP is
				player.media().play(file.replace('\\', '/').replaceAll(" ", "%20"), "start-time=" + ((float) timePos / 1000)); // , "stop-time="+((float)endTimePos/1000)   this will stop the stream and NOT pause it
			}
			else
				player.media().play(new File(file).getAbsolutePath(), "start-time=" + ((float) timePos / 1000)); // , "stop-time="+((float)endTimePos/1000)   this will stop the stream and NOT pause it
			//player.setTime(timePos);

			currentTime = timePos;
			soundSilder.setMax(running_Length);
			volumeSilder.setValue(LibVlcConst.MAX_VOLUME);
			show();
			setEndTime(endTimePos); // Should be after setVisible
		}
	}

	public void exist()
	{
		//executorService.shutdown(); // This causes some crash, timechanged Event is used based on https://github.com/caprica/vlcj/issues/334
		//executorService.shutdownNow();

		if (player != null)
		{
			player.release();
			mediaPlayerFactory.release();
		}

        /* No need check: https://github.com/caprica/vlcj/issues/334
        if(media != null)
        {
            media.release();
            media = null;
        }
        */
	}

	private void setEndTime(long e)
	{
		endTime = e;
	}

	/*
	public long getLength(final File f)
	{
		player.controls().stop();
		//player.media().prepare(f.getAbsolutePath());
		final ParseApi a = player.media().parsing();
		a.parse();
		final MediaParsedStatus s = a.status();
		return player.status().length();
	}
	*/

	private void drawEndPoint()
	{
		final Bounds bn = soundSilder.lookup(".thumb").getBoundsInParent();
		final Bounds b = soundSilder.getLayoutBounds();
		final Bounds bounds = soundSilder.lookup(".thumb").getLayoutBounds();

		final double thumbWidth = (bounds.getMaxX() - bounds.getMinX()) / 2.0;
		final double thumbHight = (bounds.getMaxY() - bounds.getMinY()) / 2.0;

		if (endTime == -1L)
		{
			endPoint.setVisible(false);
			/*
			double x = b.getMaxX() - 2d * thumbWidth;

			endPoint.setStartX(x + thumbWidth);
			endPoint.setStartY(0);
			endPoint.setEndX(x + thumbWidth);
			endPoint.setEndY(b.getMaxY());
			*/
		}
		else
		{
			endPoint.setVisible(true);
			final double x = ((double) endTime / (double) running_Length) * (b.getMaxX() - 2.0 * thumbWidth);

			endPoint.setStartX(x + thumbWidth);
			endPoint.setStartY(0);
			endPoint.setEndX(x + thumbWidth);
			endPoint.setEndY(b.getMaxY());
		}

		final double w = bn.getMaxX() - thumbWidth - 2.0 * thumbWidth;
		if (w < thumbHight)
		{
			progress.setVisible(false);
			/*
			progress.setStartX(thumbWidth);
			progress.setStartY(thumbHight);
			progress.setEndX(thumbHight);
			progress.setEndY(thumbHight);
			*/
		}
		else
		{
			progress.setVisible(true);
			progress.setStartX(thumbWidth);
			progress.setStartY(thumbHight);
			progress.setEndX(w);
			progress.setEndY(thumbHight);
		}
	}
}