package com.maknoon;

/*
 * Maknoon Audio Cataloger
 * Version 4.6
 */
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.*;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.image.*;
import javafx.scene.image.Image;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.media.AudioClip;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.*;
import javafx.util.Callback;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.*;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.store.FSDirectory;
import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarOutputStream;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.text.DecimalFormat;
import java.text.ParsePosition;
import java.util.*;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class AudioCataloger extends Application
{
	private static final String ls = System.lineSeparator();
	private static final Rectangle2D screenSize = Screen.getPrimary().getVisualBounds();

	static final float version = 4.6f;
	static final float db_version = 4.6f;

	private TitledPane searchPanel;

	// This enum will be used to indicate the search type
	enum searchType
	{MATCH_CASE, WHOLE_ALL_WORDS, MATCH_CASE_ALL_WORDS}

	private searchType searchChoice = searchType.WHOLE_ALL_WORDS;

	ListView<String> audioDetailsList;
	private ListView<String> audioSearchList;
	private final ObservableList<String> audioDetailsListModel = FXCollections.observableArrayList();

	private TextArea indexTextArea, tafreegTextArea;
	private boolean searchThreadWork = false;

	// This enum will be used to indicate the files location
	enum pathMedia {DIRECTRY, INTERNET}

	static pathMedia defaultMediaChoice = pathMedia.INTERNET;

	static String directoryPath;
	static String internetPath;
	static String choosedAudioPath;

	// This is used to indicate the type of the search (with TASHKEEL or not)
	private CheckMenuItem withTashkeelButton;

	// This variable will be used to indicate the relation between the words in the search tokens i.e. AND, OR relations.
	private boolean AND_SearchRelation = true;

	// false -> line, true -> tafreeg
	private boolean tafreegIndexSearch = false;

	private final static boolean goldenVersion = false;

	// This panel made global to update the border for index time.
	private TitledPane indexPanel;

	// Global analyzer to speed the process time.
	private ArabicAnalyzer analyzer;

	// Global rootsAnalyzer to speed the process time.
	private ArabicRootAnalyzer rootsAnalyzer;

	private org.apache.lucene.analysis.ar.ArabicAnalyzer luceneAnalyzer;

	// Global Searcher boots the speed especially in root indexing since we use rootIndexSearcher
	private static IndexSearcher defaultSearcher, rootsSearcher, luceneSearcher;

	// Global IndexWriter to get rid of all lock exceptions.
	private static IndexWriter defaultWriter, rootsWriter, luceneWriter;

	// This variable stores the type of search i.e. normal or using the index.
	private RadioButton indexSearch;

	// list model for the displayed history search entries.
	private final ObservableList<String> displayedHistoryListModel = FXCollections.observableArrayList();

	// make it global to allow it in the shutdown()
	private final ObservableList<String> historyListModel = FXCollections.observableArrayList();

	// Variable to be used for to choose between Derby and H2.
	// 'public static final boolean' is used for Conditional Compilation
	static final boolean derbyInUse = true;
	static String dbURL;

	// Global variable to be accessible from Update.class
	private TreeItem<AudioInfo> treeRootNode;
	private CheckBoxTreeItem<AudioInfo> searchRootNode;

	Button orderButton;

	private TreeView<AudioInfo> searchTree;

	// Shared DB Connection for performance increase.
	static Connection sharedDBConnection;

	// Identifier to distinguish between the leaf node and the rest.
	private boolean leafNode = false;

	// Identifier to distinguish between the root node and the rest.
	private boolean rootNode = false;

	// To know which tree is being selected.
	boolean feqhTreeSelected = false;

	// This is used to avoid double-clicking on the search panel when nothing is displayed.
	private int searchSelectedIndex = -1;

	// Initialize detailsSelectedIndex to -1 to solve the problem of trying to delete at the start of the program when nothing is selected.
	int detailsSelectedIndex = -1;

	// To re-select the same search list index
	private boolean fireSearchListSelectionListener;

	// To re-select the same play list index
	boolean firePlayListSelectionListener;

	private JVLC jVLC;

	TreeView<AudioInfo> tree;

	private TabPane treeTabbedPane;

	MenuItem updateMenuItem;

	private static Stage primaryStage;

	private static AudioClip ALERT_AUDIOCLIP;

	// TODO: workaround for bug https://bugs.openjdk.java.net/browse/JDK-8246104
	// Remove in JavaFX 18
	static
	{
		Font.loadFont("file:/System/Library/Fonts/ArabicUIText.ttc", 20);
		Font.loadFont("file:/System/Library/Fonts/ArialHB.ttc", 20);
	}

	// Version 4.5
	private int listSearchPosition = -1;
	private TextField listSearchTextField;
	private Button searchDownButton;
	private Button searchUpButton;

	@Override
	public void start(Stage stage)
	{
		primaryStage = stage;

		final Menu menuFile = new Menu("ملف");

		final MenuItem player = new MenuItem("المفضلة");
		player.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				jVLC.show();
			}
		});

		final MenuItem settings = new MenuItem("إعدادات");
		settings.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				new Settings(primaryStage);
			}
		});

		updateMenuItem = new MenuItem("استيراد فهارس");
		updateMenuItem.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				updateMenuItem.setDisable(true);

				final FileChooser fc = new FileChooser();
				fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("ACDB", "*.acdb"));
				fc.setTitle("أختيار ملف تحديث قاعدة البيانات");
				final File file = fc.showOpenDialog(primaryStage);
				if (file != null)
					new Update(primaryStage, file, AudioCataloger.this);
				else
					updateMenuItem.setDisable(false);
			}
		});

		final MenuItem close = new MenuItem("خروج");
		close.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				shutdown();
			}
		});

		menuFile.getItems().addAll(player, new SeparatorMenuItem(), settings, updateMenuItem, new SeparatorMenuItem(), close);

		final Menu menuHelp = new Menu("مساعدة");
		final MenuItem help = new MenuItem("شرح البرنامج");
		help.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				getHostServices().showDocument("https://www.maknoon.com/community/threads/108/");
			}
		});

		final MenuItem about = new MenuItem("عن البرنامج");
		about.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				/*
				final Stage splash = new Stage(StageStyle.UNDECORATED);
				splash.initOwner(primaryStage.getOwner());
				final Scene scene = new Scene(new Pane(new ImageView(new Image(cl.getResourceAsStream("images/MaknoonAudioCataloger.png")))));
				scene.setOnMousePressed(new EventHandler<MouseEvent>()
				{
					public void handle(MouseEvent m)
					{
						splash.close();
					}
				});
				splash.setScene(scene);
				splash.show();
				*/

				// Hard Coded
				alertError("برنامج المفهرس لمحاضرات جمع من أهل العلم" + ls +
								"يمنع بيع البرنامج أو استخدامه فيما يخالف أهل السنة والجماعة" + ls +
								"البرنامج مصرح لنشره واستخدامه للجميع" + ls +
								"جميع الحقوق محفوظة © مكنون 2024" + ls +
								"الإصدار " + version + ls
						, "عن البرنامج", "متابعة", null, AlertType.INFORMATION);
			}
		});

		menuHelp.getItems().addAll(help, about);

		final MenuBar menuBar = new MenuBar();
		menuBar.getMenus().addAll(menuFile, menuHelp);

		// MacOS native menu. not working as expected as for swing -Dapple.laf.useScreenMenuBar. TODO: recheck this with future Javafx version, a bug
		Platform.runLater(() ->
		{
			menuBar.useSystemMenuBarProperty().set(true);
			menuBar.setUseSystemMenuBar(true);
		});

		try
		{
			if (derbyInUse)
			{
				dbURL = "jdbc:derby:" + new File(cl.getResource("db").toURI()).getAbsolutePath(); // To make it work in Mac where the call is from the jar but the folders are outside in the *.app
				Class.forName("org.apache.derby.jdbc.EmbeddedDriver").getDeclaredConstructor().newInstance();
			}
			else
			{
				// ;CACHE_SIZE=65536;CACHE_TYPE=SOFT_LRU
				dbURL = "jdbc:h2:" + new File(cl.getResource("db").toURI()).getAbsolutePath() + "/audioCatalogerDatabase"; // ;CACHE_SIZE=32768;LOG=0 (logging is disabled, faster). it is only speed import.
				Class.forName("org.h2.Driver");
			}

			sharedDBConnection = DriverManager.getConnection(dbURL);

			//rootsTableIndexSearcher = new IndexSearcher(new RAMDirectory(FSDirectory.open(new File("rootsTableIndex"))), true); // This is faster but consumes memory, no need since 16MB is expensive

			// Constructor is now using DirectoryReader/IndexReader in read only mode as of Lucene 4.0
			//rootsTableIndexSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File("rootsTableIndex")))); // Shifted to ArabicRootFilter
			defaultSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(cl.getResource("index").toURI()).toPath())));
			rootsSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(cl.getResource("rootsIndex").toURI()).toPath())));
			luceneSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(cl.getResource("luceneIndex").toURI()).toPath())));

			rootsAnalyzer = new ArabicRootAnalyzer();
			luceneAnalyzer = new org.apache.lucene.analysis.ar.ArabicAnalyzer();
			analyzer = new ArabicAnalyzer();

			// To unlock the index if something happens
			//FSDirectory.open(new File("index")).clearLock("write.lock"); // Different way

            /* Deprecated, no replacement
            IndexWriter.unlock(FSDirectory.open(new File(cl.getResource("index").toURI())));
            IndexWriter.unlock(FSDirectory.open(new File(cl.getResource("luceneIndex").toURI())));
            IndexWriter.unlock(FSDirectory.open(new File(cl.getResource("rootsIndex").toURI())));
            */

			final IndexWriterConfig conf = new IndexWriterConfig(analyzer); // ArabicGlossAnalyzer(), ArabicStemAnalyzer(), StandardAnalyzer
			final IndexWriterConfig rootsConf = new IndexWriterConfig(rootsAnalyzer);
			final IndexWriterConfig luceneConf = new IndexWriterConfig(luceneAnalyzer);
			conf.setOpenMode(OpenMode.APPEND);
			rootsConf.setOpenMode(OpenMode.APPEND);
			luceneConf.setOpenMode(OpenMode.APPEND);
			defaultWriter = new IndexWriter(FSDirectory.open(new File(cl.getResource("index").toURI()).toPath()), conf);
			rootsWriter = new IndexWriter(FSDirectory.open(new File(cl.getResource("rootsIndex").toURI()).toPath()), rootsConf);
			luceneWriter = new IndexWriter(FSDirectory.open(new File(cl.getResource("luceneIndex").toURI()).toPath()), luceneConf);
		}
		catch (Exception e)
		{
			e.printStackTrace();

			// Messages (derby -> 'Failed to start database') and (H2 -> 'Locked by another process')
			if (e.toString().contains("Locked by another process") || e.toString().contains("Failed to start database"))
			{
				alertError("هناك نسخة أخرى من برنامج المفهرس تعمل الآن. لا يمكن تشغيل نسختين معا من البرنامج لاشتراكهما في قاعدة البيانات.", "تنبيه", "خروج", e.getMessage(), AlertType.ERROR);
				System.exit(1);
			}
		}

		try
		{
			final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(cl.getResource("setting/setting.txt").toURI())), StandardCharsets.UTF_8));
			directoryPath = in.readLine();
			internetPath = in.readLine();
			final String defaultMediaChoiceString = in.readLine();
			switch (defaultMediaChoiceString)
			{
				case "DIRECTRY":
					defaultMediaChoice = pathMedia.DIRECTRY;
					break;
				default:
					defaultMediaChoice = pathMedia.INTERNET;
					break;
			}
			in.close();

			if (defaultMediaChoice == pathMedia.DIRECTRY)
			{
				if (directoryPath.equals("<APP ROOT PATH>/audios/"))
					choosedAudioPath = new File(cl.getResource("audios").toURI()).getAbsolutePath() + File.separator;
				else
					choosedAudioPath = directoryPath;
			}
			else
				// i.e. defaultMediaChoice = pathMedia.INTERNET
				choosedAudioPath = internetPath;
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		searchPanel = new TitledPane();
		searchPanel.setText("البحث");
		searchPanel.setCollapsible(false);

		treeRootNode = new TreeItem<>(new AudioInfo("قائمة المشايخ والكتب", "", 0, 0, "root"));
		searchRootNode = new CheckBoxTreeItem<>(new AudioInfo("قائمة المشايخ والكتب", "", 0, 0, "root"));
		treeRootNode.setExpanded(true);
		searchRootNode.setExpanded(true);

		createNodes();

		searchTree = new TreeView<>(searchRootNode);
		searchTree.setCellFactory(CheckBoxTreeCell.forTreeView());

		tree = new TreeView<>(treeRootNode);
		tree.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

		// Construct the new feqh tree.
		final TreeItem<FeqhNodeInfo> feqhRootNode = new TreeItem<>(new FeqhNodeInfo("التصانيف الفقهية", -1));

		try
		{
			final Statement stmt = sharedDBConnection.createStatement();
			final ResultSet rs = stmt.executeQuery("SELECT * FROM Category");
			while (rs.next())
			{
				final int Category_parent = rs.getInt("Category_parent");
				if (Category_parent == 0)
					feqhRootNode.getChildren().add(new TreeItem<>(new FeqhNodeInfo(rs.getString("Category_name"), rs.getInt("Category_id"))));
				else
				{
					final TreeIterator<FeqhNodeInfo> iterator = new TreeIterator<>(feqhRootNode);
					while (iterator.hasNext())
					{
						final TreeItem<FeqhNodeInfo> node = iterator.next();
						final FeqhNodeInfo nodeInfo = node.getValue();
						if (Category_parent == nodeInfo.Category_id)
						{
							node.getChildren().add(new TreeItem<>(new FeqhNodeInfo(rs.getString("Category_name"), rs.getInt("Category_id"))));
							break;
						}
					}
				}
			}
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		jVLC = new JVLC(primaryStage, this);

		final TreeView<FeqhNodeInfo> feqhTree = new TreeView<>(feqhRootNode);
		feqhTree.setShowRoot(false);
		//feqhTree.setToggleClickCount(1); TODO, no replacement

		// Enable tooltips
		tree.setCellFactory(tv ->
		{
			final Tooltip tooltip = new Tooltip();
			final TreeCell<AudioInfo> cell = new TreeCell<>()
			{
				@Override
				public void updateItem(AudioInfo item, boolean empty)
				{
					super.updateItem(item, empty);
					if (empty)
					{
						setText(null);
						setGraphic(null);
						setTooltip(null);
					}
					else
					{
						if (getTreeItem().isLeaf())
						{
							// Is Root Leaf ?
							if (getTreeItem() == treeRootNode)
							{
								//getTreeItem().setGraphic(new ImageView(new Image(cl.getResourceAsStream("images/rootLeaf.png"))));
								setGraphic(getTreeItem().getGraphic());
								setText(item.toString());
								setTooltip(null);
							}
							else
							{
								// Displaying the duration for each audio file.
								String length;

								if (item.info5 == -1)
									length = "?";
								else
								{
									final int duration = item.info5;
									final String hour = String.valueOf(duration / 3600 / 1000);
									final String minute = String.valueOf(duration / 60 / 1000 - (duration / 3600 / 1000) * 60);
									final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));

									//final String minute = String.valueOf(duration/60/1000);
									//final String second = String.valueOf((duration/1000) - ((int)((float)duration/60/1000)*60));

									length = hour + ':' + minute + ':' + second;
								}

								/*
								// Change icon can be in the construction of TreeItem(<T>, icon) in createNodes(). it seems more suitable and maybe performance is better considering very big tree.
								if (item.info2.endsWith(".rm"))
									getTreeItem().setGraphic(new ImageView(new Image(cl.getResourceAsStream("images/rm.png"))));
								else if (item.info2.endsWith(".mp3"))
									getTreeItem().setGraphic(new ImageView(new Image(cl.getResourceAsStream("images/mp3.png"))));
								else if (item.info2.endsWith(".wma"))
									getTreeItem().setGraphic(new ImageView(new Image(cl.getResourceAsStream("images/wma.png"))));
								else
									getTreeItem().setGraphic(new ImageView(new Image(cl.getResourceAsStream("images/m4a.png"))));
								*/

								tooltip.setText("المسار: " + choosedAudioPath + ((defaultMediaChoice == pathMedia.INTERNET) ? item.info2.replace('\\', '/') : item.info2) + ls + "المدة: " + length);
								setTooltip(tooltip);
								setText(item.toString());
								setGraphic(getTreeItem().getGraphic());
							}
						}
						else
						{
							setGraphic(getTreeItem().getGraphic());
							setText(item.toString());
							setTooltip(null);
						}
					}
				}
			};

			/*
			cell.setOnMouseClicked(e ->
			{
				if (e.getClickCount() == 2 && ! cell.isEmpty())
				{
					AudioInfo s = cell.getItem();
				}
			});
			*/
			return cell;
		});

		indexPanel = new TitledPane();
		indexPanel.setText("عرض الفهرسة");
		indexPanel.setCollapsible(false);

		// Listen for tree when the selection changes.
		tree.getSelectionModel().getSelectedItems().
				addListener(new ListChangeListener<TreeItem<AudioInfo>>()
				{
					@Override
					public void onChanged(Change<? extends TreeItem<AudioInfo>> change)
					{
						leafNode = false;
						rootNode = false;

						// re-initialise
						selected_Code = 0;
						selected_Sheekh_id = 0;
						selected_Book_duration = 0;
						selected_Sheekh_name = "";
						selected_Book_name = "";
						selected_TreeLevel = "";
						//selected_FileName = ""; // No need
						//selected_Book_id = 0;

						// since audioDetailsList Listener will not fire in case there are no selections. Playlist selection will not work in that case.
						firePlayListSelectionListener = false;
						//fireSearchListSelectionListener = false; // it is moved to audioDetailsList Listener, it is fired always because there is a selected item in audioDetailsList all the time.

						// Clean if there is nothing selected or multiple rows are selected
						final ObservableList<TreeItem<AudioInfo>> nodes = tree.getSelectionModel().getSelectedItems();
						if (nodes.size() != 1)
						{
							Platform.runLater(() ->
							{
								indexTextArea.clear();
								tafreegTextArea.clear();
								indexPanel.setText("عرض الفهرسة");
								audioDetailsListModel.clear();
							});
							return;
						}

						//feqhTreeSelected = false;
						//feqhTree.clearSelection();  causes consequence selection in both trees
						//final TreeItem<AudioInfo> node = tree.getSelectionModel().getSelectedItem(); // Not working properly in jfx 14 -> https://mail.openjdk.java.net/pipermail/openjfx-discuss/2020-May/000165.html    solution is to use ".getSelectedItems()" instead of ".getSelectedItem()"
						final TreeItem<AudioInfo> node = nodes.get(0);

						if (node == null)
							return;

						final AudioInfo audio = node.getValue();
						if (node.isLeaf())
						{
							// This declaration is made here instead of the 'else' statement.
							leafNode = true;

							// This condition is added to avoid trying to activate root URL since it has null URL
							if (node == treeRootNode)
							{
								rootNode = true;

								/*
								 * selected_TreeLevel is assign to 0 to give the user the ability to add to the root
								 * if it is the only item i.e. leaf node. This is exactly the same as we do at the beginning when
								 * initialising the 'rootNode' node of the tree.
								 */
								selected_TreeLevel = "0";
							}
							else
							{
								selected_TreeLevel = "4"; // "4" instead of "". Anything except 3, 2 or 1 to not enter the add section
								//selected_Book_id = audio.info5; // It is the real duration now
								selected_Book_duration = audio.info5;
								selected_Code = audio.info4;
								displayIndexes(audio.info2);
							}
						}
						else
						{
							if (node == treeRootNode)
								rootNode = true;

							/*
							 * info1 in this case will represent the BookName since it is not a leaf node it is a book
							 * title or a sheekh name.
							 */
							selected_Sheekh_id = audio.info4;
							//selected_TreeLevel = audio.info3;
							switch (audio.info6) // Version 4.4
							{
								case "sheekhLevel":
									selected_TreeLevel = "1";
									break;
								case "bookLevel":
									selected_TreeLevel = "2";
									break;
								case "subBookLevel":
									selected_TreeLevel = "3";
									break;
							}

							selected_Book_id = audio.info5;
							if (selected_TreeLevel.equals("1"))
							{
								selected_Sheekh_name = audio.info1;// In the case of selected_TreeLevel == 0, we will not use any variable here.
								selected_Book_name = "";
							}
							else
							{
								// i.e. selected_TreeLevel.equals("2") or ("3")
								selected_Sheekh_name = audio.info2;
								selected_Book_name = audio.info1;
							}

							Platform.runLater(() ->
							{
								indexTextArea.clear();
								tafreegTextArea.clear();
								indexPanel.setText("عرض الفهرسة");
								audioDetailsListModel.clear();
							});

							// Version 1.6, This assign helps to avoid adding an index to an audio file when a non leaf node is selected after selecting a leaf node.
							//selected_Code = 0; // Version 2.2, Removed
						}
					}
				});

		feqhTree.getSelectionModel().getSelectedItems().
				addListener(new ListChangeListener<TreeItem<FeqhNodeInfo>>()
				{
					@Override
					public void onChanged(Change<? extends TreeItem<FeqhNodeInfo>> change)
					{
						// Re-initialise.
						selected_Code = 0;
						//selected_Sheekh_id = 0; // No need
						//selected_Book_duration = 0;
						//selected_Sheekh_name = "";
						//selected_Book_name = "";
						//selected_TreeLevel = "";
						//selected_FileName = "";
						//selected_Book_id = 0;

						//tree.clearSelection(); // shifted to tabbedpane change listener. // This is needed when searchlist item is clicked again after changing or clearing the audioDetailsList.
						//feqhTreeSelected = true;

						// Clean if there is nothing selected
						final ObservableList<TreeItem<FeqhNodeInfo>> nodes = feqhTree.getSelectionModel().getSelectedItems();
						if (nodes.size() != 1)
						{
							indexTextArea.clear();
							tafreegTextArea.clear();
							indexPanel.setText("عرض الفهرسة");
							audioDetailsListModel.clear();
							return;
						}

						//final TreeItem<FeqhNodeInfo> node = feqhTree.getSelectionModel().getSelectedItem();
						// Not working properly in jfx 14 -> https://mail.openjdk.java.net/pipermail/openjfx-discuss/2020-May/000165.html    solution is to use ".getSelectedItems()" instead of ".getSelectedItem()"
						final TreeItem<FeqhNodeInfo> node = nodes.get(0);

						if (node != null) // removed: && node.isLeaf(), To allow even folders to have index as in the new ahl_alhdeeth.exe 4.3.2
						{
							String SQL_Combination = null;
							boolean wholeDB = true;
							final java.util.List<CheckBoxTreeItem<AudioInfo>> checkedNodes = getSelectedNodes(searchRootNode);
							if (!checkedNodes.isEmpty())
							{
								//for(int i=0, size=treePaths.length; i<size; i++)
								for (final CheckBoxTreeItem<AudioInfo> infoNode : checkedNodes)
								{
									final AudioInfo info = infoNode.getValue();
									//if(path.getPathCount()==2) // i.e. sheekhLevel, there are many ways to do this using node.isLeaf/Root or info.info3.equals("1") ...
									//if (info.info3.equals("1")) // Version 3.2, sheekhLevel. old: path.getPathCount()==2
									if (info.info6.equals("sheekhLevel")) // Version 4.4
									{
										wholeDB = false;
										if (SQL_Combination != null)
											SQL_Combination = SQL_Combination + " OR Sheekh_id = " + info.info4;
										else
											SQL_Combination = "Sheekh_id = " + info.info4;

										/*
										for(AudioCatalogerSearchData element : searchData)
										{
											if(element.Sheekh_name.equals(String.valueOf(treePaths[i].getPathComponent(1))))
											{
												// i.e. For the Second time
												if(SQL_Combination != null)
													SQL_Combination = SQL_Combination+" OR Sheekh_id = "+element.Sheekh_id;
												else
													SQL_Combination = "Sheekh_id = "+element.Sheekh_id;
												break;
											}
										}
										*/
									}
									else
									{
										//if(path.getPathCount()==3) // i.e. leaf/book
										if (infoNode.isLeaf() && infoNode != searchRootNode) // leaf/book level. old path.getPathCount()==3
										{
											wholeDB = false;
											if (SQL_Combination != null)
												SQL_Combination = SQL_Combination + " OR Book_id = " + info.info5; // No need for ' AND Sheekh_id ='
											else
												SQL_Combination = "Book_id = " + info.info5;

											/*
											for(AudioCatalogerSearchData element : searchData)
											{
												if(element.Sheekh_name.equals(String.valueOf(treePaths[i].getPathComponent(1))))
												{
													for(int j=0, length=element.Book_name.size(); j<length; j++)
													{
														if(element.Book_name.elementAt(j).equals(String.valueOf(treePaths[i].getPathComponent(2))))
														{
															if(SQL_Combination != null)
																SQL_Combination = SQL_Combination + " OR Book_id = "+element.Book_id.elementAt(j); // No need for ' AND Sheekh_id ='
															else
																SQL_Combination = "Book_id = "+element.Book_id.elementAt(j);
															break;
														}
													}
													break;
												}
											}
											*/
										}
									}
								}
							}

							if (SQL_Combination != null || (wholeDB && !checkedNodes.isEmpty()))
							{
								final FeqhNodeInfo nodeInfo = node.getValue();

								audioDetailsLine.removeAllElements();
								audioDetailsTafreeg.removeAllElements();
								audioDetailsOffset.removeAllElements();
								audioDetailsDuration.removeAllElements();
								audioDetailsCode.removeAllElements();
								audioDetailsSeq.removeAllElements();

								/* replaced with setAll(), since clear() is delayed after adding the list, causing it to be empty all the time
								Platform.runLater(() ->
								{
									audioDetailsListModel.clear();
								});
								*/

								try
								{
									final Statement stmt1 = sharedDBConnection.createStatement();
									final ResultSet rs1 = stmt1.executeQuery("SELECT Code, Seq FROM ContentCat WHERE Category_id = " + nodeInfo.Category_id + (wholeDB ? "" : " AND (" + SQL_Combination + ')')); // TODO: use Table JOIN for performance as in Android project
									final PreparedStatement ps = sharedDBConnection.prepareStatement(derbyInUse?
											"SELECT Offset, Line, Duration, Tafreeg FROM Contents WHERE Code = ? AND Seq = ?"
											:"SELECT \"Offset\", Line, Duration, Tafreeg FROM Contents WHERE Code = ? AND Seq = ?"); // Version 3.0
									ResultSet rs2;

									while (rs1.next())
									{
										ps.setInt(1, rs1.getInt("Code"));
										ps.setInt(2, rs1.getInt("Seq"));
										rs2 = ps.executeQuery();
										rs2.next();

										audioDetailsCode.addElement(rs1.getInt("Code"));
										audioDetailsOffset.addElement(rs2.getInt("Offset"));
										audioDetailsLine.addElement(rs2.getString("Line"));
										audioDetailsTafreeg.addElement(DBString(rs2.getCharacterStream("Tafreeg"))); // Version 3.0
										audioDetailsDuration.addElement(rs2.getInt("Duration"));
										audioDetailsSeq.addElement(rs1.getInt("Seq")); // Version 2.4, Required for bookmark
									}
									ps.close();
									stmt1.close();

									String LineDetailed;
									final Vector<String> audioList = new Vector<>();
									for (int i = 0, size = audioDetailsCode.size(); i < size; i++)
									{
										if (audioDetailsDuration.elementAt(i) == -1)
											LineDetailed = '(' + String.valueOf(i + 1) + ") [?] " + audioDetailsLine.elementAt(i);
										else
										{
											final int duration = audioDetailsDuration.elementAt(i);
											final String minute = String.valueOf(duration / 60 / 1000);
											final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
											LineDetailed = '(' + String.valueOf(i + 1) + ") [" + minute + ':' + second + "] " + audioDetailsLine.elementAt(i);
										}
										audioList.add(LineDetailed);
									}
									audioDetailsListModel.setAll(audioList);
								}
								catch (Exception ex)
								{
									ex.printStackTrace();
								}
							}
							else
							{
								Platform.runLater(() ->
								{
									audioDetailsListModel.clear();
								});
							}
						}
						else
						{
							Platform.runLater(() ->
							{
								audioDetailsListModel.clear();
							});
						}
					}
				});

		treeTabbedPane = new TabPane();

		final Tab treeTab = new Tab("المشايخ والكتب", tree);
		final Tab feqhTreeTab = new Tab("التصانيف الفقهية", feqhTree);
		treeTab.setClosable(false);
		feqhTreeTab.setClosable(false);

		treeTabbedPane.getTabs().addAll(treeTab, feqhTreeTab);
		treeTabbedPane.getSelectionModel().selectedIndexProperty().addListener(
				new ChangeListener<Number>()
				{
					@Override
					public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue)
					{
						if(newValue.intValue() == oldValue.intValue())
							return;

						feqhTree.getSelectionModel().clearSelection();
						tree.getSelectionModel().clearSelection();

						//audioDetailsListModel.clear(); // no need, it is done part of clearSelection() of any of feqhTree & tree
						indexTextArea.clear();
						tafreegTextArea.clear();
						indexPanel.setText("عرض الفهرسة");

						listSearchTextField.clear();
						searchDownButton.setDisable(true);
						searchUpButton.setDisable(true);

						final int selectedIndex = newValue.intValue();
						if (selectedIndex == 0)
						{
							feqhTreeSelected = false;
							audioDetailsCode.removeAllElements(); // To hide audioDetailsPopupMenu when there is nothing displayed. (This happens when you select a node in feqhtree then change to normal tree then come back)
							audioDetailsSeq.removeAllElements();
							audioDetailsLine.removeAllElements();
							audioDetailsTafreeg.removeAllElements();
							audioDetailsOffset.removeAllElements();
							audioDetailsDuration.removeAllElements();
						}
						else
						{
							feqhTreeSelected = true;
							audioSearchList.getSelectionModel().clearSelection();
						}
					}
				}
		);

		final TitledPane listPanel = new TitledPane();
		listPanel.setText("قوائم العرض");
		listPanel.setCollapsible(false);

		final BorderPane listPanelBox = new BorderPane();
		listPanelBox.setCenter(treeTabbedPane);
		listPanelBox.setPadding(Insets.EMPTY);

		listPanel.setContent(listPanelBox);
		listPanel.setPrefSize(screenSize.getWidth() / 5 + 50, screenSize.getHeight());

		orderButton = new Button(null, new ImageView(new Image(cl.getResourceAsStream("images/order.png"))));
		orderButton.setTooltip(new Tooltip("ترتيب"));
		orderButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				tree.getSelectionModel().clearSelection();
				for (TreeItem<?> child : treeRootNode.getChildren())
					child.setExpanded(false);

				feqhTree.getSelectionModel().clearSelection();
				for (TreeItem<?> child : feqhRootNode.getChildren())
					child.setExpanded(false);

				searchTree.getSelectionModel().clearSelection();
				searchRootNode.setSelected(true);
				for (TreeItem<?> child : searchRootNode.getChildren())
					child.setExpanded(false);

				// To make sure that the adding process will not happen.
				selected_Code = 0;
				selected_Sheekh_id = 0;
				selected_Book_duration = 0;
				selected_Sheekh_name = "";
				selected_Book_name = "";
				selected_TreeLevel = "";

				indexTextArea.clear();
				tafreegTextArea.clear();
				indexPanel.setText("عرض الفهرسة");

				// To remove the details list, updating area and search list to avoid selecting or updating deleted items
				audioDetailsListModel.clear();
				audioSearchList.getItems().clear();

				// Free The memory.
				searchResults.removeAllElements();
				searchPanel.setText("البحث");
			}
		});

		audioDetailsList = new ListView<>(audioDetailsListModel);

		// List Selection Listener for Details panel.
		audioDetailsList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>()
		{
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
			{
				detailsSelectedIndex = audioDetailsList.getSelectionModel().getSelectedIndex();
				fireSearchListSelectionListener = false;
				firePlayListSelectionListener = false;

				// This condition is added to avoid setting textfields when refreshing the detailslists.
				if (detailsSelectedIndex != -1)
				{
					if (feqhTreeSelected)
					{
						try
						{
							selected_Code = audioDetailsCode.elementAt(detailsSelectedIndex);

							final Statement stmt = sharedDBConnection.createStatement();
							final ResultSet rs = stmt.executeQuery("SELECT Book_name, Sheekh_name, Title, FileName, FileType, Duration, Path FROM Chapters WHERE Code=" + selected_Code);
							rs.next();

							selected_FileName = rs.getString("Path").replace("\\", File.separator) + File.separator + rs.getString("FileName") + '.' + rs.getString("FileType");
							selected_Book_duration = rs.getInt("Duration");

							final int offset = audioDetailsOffset.elementAt(detailsSelectedIndex);
							indexTextArea.setText(audioDetailsLine.elementAt(detailsSelectedIndex));
							tafreegTextArea.setText(audioDetailsTafreeg.elementAt(detailsSelectedIndex));
							final String hour = String.valueOf(offset / 3600 / 1000);
							final String minute = String.valueOf(offset / 60 / 1000 - (offset / 3600 / 1000) * 60);
							final String second = String.valueOf(offset / 1000 - ((int) ((float) offset / 60F / 1000F) * 60));
							indexPanel.setText("عرض الفهرسة" + " [" + hour + ':' + minute + ':' + second + "] [" + rs.getString("Sheekh_name") + " ← " + rs.getString("Book_name") + " ← " + rs.getString("Title") + '(' + rs.getString("FileName") + ")]");

							stmt.close();
						}
						catch (Exception ex)
						{
							ex.printStackTrace();
						}
					}
					else
					{
						final int offset = audioDetailsOffset.elementAt(detailsSelectedIndex);
						indexTextArea.setText(audioDetailsLine.elementAt(detailsSelectedIndex));
						tafreegTextArea.setText(audioDetailsTafreeg.elementAt(detailsSelectedIndex));
						final String hour = String.valueOf(offset / 3600 / 1000);
						final String minute = String.valueOf(offset / 60 / 1000 - (offset / 3600 / 1000) * 60);
						final String second = String.valueOf(offset / 1000 - ((int) ((float) offset / 60F / 1000F) * 60));
						indexPanel.setText("عرض الفهرسة" + " [" + hour + ':' + minute + ':' + second + ']');
					}
				}
			}
		});

		final BorderPane detailPanelPane = new BorderPane();
		detailPanelPane.setPadding(Insets.EMPTY);
		detailPanelPane.setCenter(audioDetailsList);

		final TitledPane detailPanel = new TitledPane();
		//detailPanel.setContent(audioDetailsList); // Not used since the background color is not white
		detailPanel.setContent(detailPanelPane);
		detailPanel.setText("الفهارس");
		detailPanel.setCollapsible(false);

		try
		{
			final BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(cl.getResource("setting/history.txt").toURI())), StandardCharsets.UTF_8));
			while (in.ready()) historyListModel.add(in.readLine());
			in.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		displayedHistoryListModel.addAll(historyListModel);

		final PopupControl historyPopup = new PopupControl();
		historyPopup.setAutoFix(true);
		historyPopup.setHideOnEscape(true);
		historyPopup.setAutoHide(true);

		// History of the search inputs
		final ListView<String> historyList = new ListView<>(displayedHistoryListModel);
		historyList.setStyle("-fx-background-color: -fx-outer-border, -fx-inner-border, -fx-body-color; -fx-background-insets: 0, 1;");
		historyList.setPrefHeight(200.0);
		historyList.setFocusTraversable(false);
		historyList.setCellFactory(new Callback<ListView<String>, ListCell<String>>()
		{
			@Override
			public ListCell<String> call(ListView<String> list)
			{
				return new HistoryListCell(historyPopup);

				/*
				final ListCell<String> cell = new ListCell<String>()
				{
					@Override
					public void updateItem(String item, boolean empty)
					{
						super.updateItem(item, empty);

						if (empty || item == null)
						{
							setText(null);
							setGraphic(null);
						}
						else
							//setGraphic(buildTextFlow(item)); // causes issues since arabic letters are not connected !
							setText(item);
					}
				};
				return cell;
				*/
			}
		});
		historyPopup.getScene().setRoot(historyList);

		final TextField searchTextField = new TextField();
		//final AutoCompleteTextField searchTextField = new AutoCompleteTextField();
		//searchTextField.getEntries().addAll(historyListModel);
		searchTextField.widthProperty().addListener((obs, oldVal, newVal) -> {
			historyList.setPrefWidth(newVal.doubleValue());
		});

		historyList.getSelectionModel().selectedItemProperty().addListener(new ChangeListener<String>()
		{
			@Override
			public void changed(ObservableValue<? extends String> observable, String oldValue, String newValue)
			{
				//int i = historyList.getSelectionModel().getSelectedIndex();
				//if (i != -1) searchTextField.setText(displayedHistoryListModel.get(i)); // newValue = displayedHistoryListModel.get(i)
				if (newValue != null)
				{
					Platform.runLater(() ->
					{
						searchTextField.setText(newValue);
						historyPopup.hide();
					});
				}
			}
		});

		searchTextField.textProperty().addListener(new ChangeListener<String>()
		{
			@Override
			public void changed(ObservableValue<? extends String> observableValue, String s, String s2)
			{
				if (searchTextField.getText().isEmpty())
					historyPopup.hide();
				else
				{
					displayedHistoryListModel.clear();
					for (final String item : historyListModel)
					{
						final String text = searchTextField.getText();

						//if (item.contains(text)) // To use it with buildTextFlow(item)
							//displayedHistoryListModel.add(item.replaceAll(text, "ö" + text + "ö"));

						if (item.startsWith(text))
							displayedHistoryListModel.add(item);
					}

					if (!displayedHistoryListModel.isEmpty())
					{
						final Point2D point = searchTextField.localToScreen(0.0, 0.0);
						historyPopup.show(primaryStage);
						historyPopup.setX(point.getX() - historyPopup.getWidth());
						historyPopup.setY(point.getY() + searchTextField.getHeight());
					}
					else
						historyPopup.hide();
				}
			}
		});

		/* Auto Complete, causes a lot of issues. it prevents continue entering letters in textfield
		historyList.setOnKeyPressed(new EventHandler<KeyEvent>()
		{
			public void handle(KeyEvent e)
			{
				System.out.println("historyList.setOnKeyPressed " + e.getCode());

				if (e.getCode() == KeyCode.ENTER || e.getCode() == KeyCode.ESCAPE)
				{
					historyPopup.hide();
					searchTextField.requestFocus();
				}

				if (e.getCode() == KeyCode.DELETE)
				{
					int index = historyList.getSelectionModel().getSelectedIndex();
					if (index != -1)
					{
						historyListModel.remove(displayedHistoryListModel.get(index)); // Test all of this
						displayedHistoryListModel.remove(index);
						if (!displayedHistoryListModel.isEmpty())
						{
							if (displayedHistoryListModel.size() != index)
								historyList.getSelectionModel().select(index);
							else
								historyList.getSelectionModel().select(index - 1);
						}
						else
							historyPopup.hide();
					}
				}
			}
		});

		// Auto Complete
		searchTextField.setOnKeyReleased(new EventHandler<KeyEvent>()
		{
			public void handle(KeyEvent e)
			{
				System.out.println("searchTextField.setOnKeyReleased " + e.getCode());

				// Causing a lot of issues by preventing adding letters to searchTextField
				if (!displayedHistoryListModel.isEmpty() && (e.getCode() == KeyCode.DOWN || e.getCode() == KeyCode.UP))
				{
					final Point2D point = searchTextField.localToScreen(0, 0);
					historyPopup.show(primaryStage);
					historyPopup.setX(point.getX() - historyPopup.getWidth());
					historyPopup.setY(point.getY() + searchTextField.getHeight());

					//historyList.requestFocus();
					//historyList.setPrefWidth(searchTextField.getPrefWidth());
					//historyList.getSelectionModel().select(0);
					//searchTextField.getParent().requestFocus();
				}

				if (!searchTextField.getText().isEmpty())
				{
					displayedHistoryListModel.clear();
					for (final String item : historyListModel)
					{
						if (item.contains(searchTextField.getText()))
							displayedHistoryListModel.add(item);
					}

					Platform.runLater(() ->
					{
						if (!displayedHistoryListModel.isEmpty() && e.getCode() != KeyCode.ENTER && e.getCode() != KeyCode.ESCAPE)
						{
							final Point2D point = searchTextField.localToScreen(0.0, 0.0);
							historyPopup.show(primaryStage);
							historyPopup.setX(point.getX() - historyPopup.getWidth());
							historyPopup.setY(point.getY() + searchTextField.getHeight());

							//historyList.requestFocus();
							//historyList.getSelectionModel().select(0);
							//searchTextField.getParent().requestFocus();
						}
						else
							historyPopup.hide();
					});
				}
			}
		});
		*/

		// Create the popup menu for indexing choice i.e. with roots or not
		final MenuItem treePopupRootsIndexingMenuItem = new MenuItem("فهرسة بجذور الكلمات");
		final MenuItem treePopupIndexingMenuItem = new MenuItem("فهرسة بدون تشكيل");
		final MenuItem treePopupLuceneIndexingMenuItem = new MenuItem("فهرسة بدون لواصق (light10)");

		final Menu indexingMenu = new Menu("فهرسة");
		indexingMenu.getItems().addAll(treePopupIndexingMenuItem, treePopupRootsIndexingMenuItem, treePopupLuceneIndexingMenuItem);

		// This is to indicate the type of the index search i.e. by roots or not.
		final RadioMenuItem rootsIndexSearchTypeButton = new RadioMenuItem("بحث بجذور الكلمات");
		final RadioMenuItem defaultIndexSearchTypeButton = new RadioMenuItem("بحث بدون تشكيل");
		final RadioMenuItem luceneIndexSearchTypeButton = new RadioMenuItem("بحث بدون لواصق (light10)");
		final CheckMenuItem withHighlighter = new CheckMenuItem("اختصار وتوضيح نتائج البحث");

		defaultIndexSearchTypeButton.setSelected(true);

		final Button searchButton = new Button(null, new ImageView(new Image(cl.getResourceAsStream("images/search.png"))));
		searchButton.setTooltip(new Tooltip("بحث"));

		final EventHandler<ActionEvent> searchListener = new EventHandler<ActionEvent>()
		{
			boolean stopIndexingSearch = false;

			@Override
			public void handle(ActionEvent e)
			{
				stopIndexingSearch = false;

				if (!historyListModel.contains(searchTextField.getText()) && !searchTextField.getText().isEmpty())
					historyListModel.add(searchTextField.getText());

				if (searchTextField.getText().trim().length() < 2)
					alertError("قم بإدخال كلمة صحيحة مكونة من حرفين أو أكثر.", "خطأ", "إلغاء", null, AlertType.ERROR);
				else
				{
					// This is to search in the index NOT the Database or the memory.
					if (indexSearch.isSelected())
					{
						if (rootsIndexSearchTypeButton.isSelected() && treePopupRootsIndexingMenuItem.isDisable())
						{
							alertError("لا يمكنك البحث في الفهارس بجذور الكلمات أثناء القيام بعملية الفهرسة من نفس النوع.", "خطأ", "إلغاء", null, AlertType.ERROR);
							return;
						}
						else
						{
							if (defaultIndexSearchTypeButton.isSelected() && treePopupIndexingMenuItem.isDisable())
							{
								alertError("لا يمكنك البحث في الفهارس بدون تشكيل أثناء القيام بعملية الفهرسة من نفس النوع.", "خطأ", "إلغاء", null, AlertType.ERROR);
								return;
							}
							else
							{
								if (luceneIndexSearchTypeButton.isSelected() && treePopupLuceneIndexingMenuItem.isDisable())
								{
									alertError("لا يمكنك البحث في الفهارس بدون لواصق أثناء القيام بعملية الفهرسة من نفس النوع.", "خطأ", "إلغاء", null, AlertType.ERROR);
									return;
								}
							}
						}

						if (!searchThreadWork)
						{
							final Thread thread = new Thread()
							{
								public void run()
								{
									// This variable is used to stop searching two items at the same time.
									searchThreadWork = true;

									Platform.runLater(() -> {
										searchTextField.setDisable(true);
										searchButton.setGraphic(new ImageView(new Image(cl.getResourceAsStream("images/stop_indexing_search.png"))));
										searchPanel.setText("البحث");
										historyPopup.hide();
										audioSearchList.getItems().clear();
									});

									// This is to ensure that '؟' (in arabic) is converted to '?' (in english)
									String searchText = searchTextField.getText().trim().replace('؟', '?');

									// Replace all english search keywords to arabic
									searchText = searchText.replaceAll(" و ", " AND ");
									searchText = searchText.replaceAll(" أو ", " OR ");

									try
									{
										searchResults.removeAllElements(); // Remove the search list

										final ObservableList<String> searchResultsListModel = FXCollections.observableArrayList();
										final QueryParser queryParser = new QueryParser(tafreegIndexSearch?"Tafreeg":"Line", defaultIndexSearchTypeButton.isSelected() ? analyzer : (rootsIndexSearchTypeButton.isSelected() ? rootsAnalyzer : luceneAnalyzer));

										queryParser.setAllowLeadingWildcard(true);
										queryParser.setDefaultOperator(QueryParser.Operator.AND);
										final Query query = queryParser.parse(searchText);

										// Limiting the search
										//String filterQuery = "";
										boolean fullSearch = true;
										final BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder(); // https://issues.apache.org/jira/browse/LUCENE-6583   & LUCENE-6301

										// Format the required range for the search.
										final java.util.List<CheckBoxTreeItem<AudioInfo>> checkedNodes = getSelectedNodes(searchRootNode);
										if (!checkedNodes.isEmpty())
										{
											//for(int i=0, size=treePaths.length; i<size; i++)
											for (final CheckBoxTreeItem<AudioInfo> node : checkedNodes)
											{
												final AudioInfo info = node.getValue();
												//if (info.info3.equals("1")) // sheekhLevel. old: path.getPathCount()==2
												if (info.info6.equals("sheekhLevel")) // sheekhLevel. old: path.getPathCount()==2
												{
													fullSearch = false;
                                                    /*if(filterQuery.length() != 0)
                                                        filterQuery = filterQuery + " OR Sheekh_id:\""+ info.info4 + '\"';
                                                    else
                                                        filterQuery = "Sheekh_id:\""+ info.info4 + '\"';*/
													booleanQueryBuilder.add(new BooleanClause(new TermQuery(new Term("Sheekh_id", String.valueOf(info.info4))), BooleanClause.Occur.SHOULD));
												}
												else
												{
													if (node.isLeaf() && node != searchRootNode) // leaf/book level. old path.getPathCount()==3
													{
														fullSearch = false;
                                                        /*if(filterQuery.length() != 0)
                                                            filterQuery = filterQuery + " OR (Book_id:\""+info.info5+"\" AND Sheekh_id:\""+ info.info4 +"\")";
                                                        else
                                                            filterQuery = "(Book_id:\""+info.info5+"\" AND Sheekh_id:\""+ info.info4 +"\")";*/
														final BooleanQuery.Builder innerBuilder = new BooleanQuery.Builder();
														innerBuilder.add(new TermQuery(new Term("Book_id", String.valueOf(info.info5))), BooleanClause.Occur.MUST);
														innerBuilder.add(new TermQuery(new Term("Sheekh_id", String.valueOf(info.info4))), BooleanClause.Occur.MUST);
														booleanQueryBuilder.add(innerBuilder.build(), BooleanClause.Occur.SHOULD);

                                                        /*
								    					for(AudioCatalogerSearchData element : searchData)
									    				{
									    					if(element.Sheekh_name.equals(String.valueOf(treePaths[i].getPathComponent(1))))
									    					{
									    						for(int j=0, length=element.Book_name.size(); j<length; j++)
									    						{
									    							if(element.Book_name.elementAt(j).equals(String.valueOf(treePaths[i].getPathComponent(2))))
									    							{
									    								if(!filterQuery.equals(""))
																			filterQuery = filterQuery + " OR (Book_id:\""+element.Book_id.elementAt(j)+"\" AND Sheekh_id:\""+ element.Sheekh_id +"\")";
																		else
																			filterQuery = "(Book_id:\""+element.Book_id.elementAt(j)+"\" AND Sheekh_id:\""+ element.Sheekh_id +"\")";
																		break;
									    							}
									    						}
																break;
										    				}
									    				}
									    				*/
													}
												}
											}
										}

										final BooleanQuery booleanQuery = booleanQueryBuilder.build();

										if (/*filterQuery.length() != 0*/ !booleanQuery.toString().isEmpty() || (fullSearch && !checkedNodes.isEmpty()))
										{
											final org.apache.lucene.util.FixedBitSet bits = new org.apache.lucene.util.FixedBitSet((defaultIndexSearchTypeButton.isSelected() ? defaultSearcher : (rootsIndexSearchTypeButton.isSelected() ? rootsSearcher : luceneSearcher)).getIndexReader().maxDoc());
											final SimpleCollector collector = new SimpleCollector() // TODO: improve this, limit the results and score the results
											{
												int docBase;

												@Override
												public void collect(int doc)
												{
													bits.set(doc + docBase);
												}

												@Override
												public void doSetNextReader(LeafReaderContext context)
												{
													this.docBase = context.docBase;
												}

												@Override
												public ScoreMode scoreMode()
												{
													return ScoreMode.COMPLETE_NO_SCORES;
												}
											};

											if (fullSearch)
												(defaultIndexSearchTypeButton.isSelected() ? defaultSearcher : (rootsIndexSearchTypeButton.isSelected() ? rootsSearcher : luceneSearcher)).search(query, collector);
											else
											{
												//final QueryParser filterQueryParser = new QueryParser("", new KeywordAnalyzer());
												final BooleanQuery.Builder finalBooleanQueryBuilder = new BooleanQuery.Builder();
												finalBooleanQueryBuilder.add(query, BooleanClause.Occur.FILTER);
												//finalBooleanQueryBuilder.add(new QueryWrapperFilter(booleanQuery), BooleanClause.Occur.FILTER); // Version 3.3, removed (deprecated). This will help: http://stackoverflow.com/questions/4489033/lucene-filtering-for-documents-not-containing-a-term
												finalBooleanQueryBuilder.add(booleanQuery, BooleanClause.Occur.FILTER); // Version 3.3, TODO recheck
												//System.out.println(finalBooleanQueryBuilder.build());
												//(defaultIndexSearchTypeButton.isSelected()?defaultSearcher:(rootsIndexSearchTypeButton.isSelected()?rootsSearcher:luceneSearcher)).search(query, new QueryWrapperFilter(filterQueryParser.parse(filterQuery)), collector);
												(defaultIndexSearchTypeButton.isSelected() ? defaultSearcher : (rootsIndexSearchTypeButton.isSelected() ? rootsSearcher : luceneSearcher)).search(finalBooleanQueryBuilder.build(), collector);
											}

											if (withHighlighter.isSelected() && !tafreegIndexSearch)
											{
												//final Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("<font color=red>", "</font>"), new QueryScorer(query));
												final Highlighter highlighter = new Highlighter(new SimpleHTMLFormatter("ö", "ö"), new QueryScorer(query));
												if (defaultIndexSearchTypeButton.isSelected())
												{
													for (int j = 0, size = bits.length(), index = 0; j < size && !stopIndexingSearch; j++)
													{
														if (bits.get(j))
														{
															final Document doc = defaultSearcher.doc(j);
															final String txt = doc.get("Line");
															searchResults.addElement(doc.get("Seq") + 'ö' + doc.get("Code"));

															final TokenStream tokenStream = TokenSources.getTokenStream("Line", defaultSearcher.getIndexReader().getTermVectors(j), txt, analyzer, highlighter.getMaxDocCharsToAnalyze() - 1);
															final TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, doc.get("Line"), false, 10);

															String line = "";
															for (TextFragment tf : frag)
																if ((tf != null) && (tf.getScore() > 0F))
																	line = (line.isEmpty() ? "" : (line + " ... ")) + tf.toString();

															if (doc.get("Duration").equals("-1"))
																//searchResultsListModel.add("<HTML><div align=right>("+(++index)+")<font color=maroon> [?] ("+doc.get("Short_sheekh_name")+")("+doc.get("Book_name")+")</font> "+line);
																searchResultsListModel.add("(" + (++index) + ") [?] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + line);
															else
															{
																final int duration = Integer.parseInt(doc.get("Duration"));
																final String minute = String.valueOf(duration / 60 / 1000);
																final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
																//searchResultsListModel.add("<HTML><div align=right>("+(++index)+")<font color=maroon>[" + minute + ':' + second + "] ("+doc.get("Short_sheekh_name")+")("+doc.get("Book_name")+")</font> "+line);
																searchResultsListModel.add("(" + (++index) + ")[" + minute + ':' + second + "] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + line);
															}
														}
													}
												}
												else
												{
													if (rootsIndexSearchTypeButton.isSelected())
													{
														for (int j = 0, size = bits.length(), index = 0; j < size && !stopIndexingSearch; j++)
														{
															if (bits.get(j))
															{
																final Document doc = rootsSearcher.doc(j);
																final String txt = doc.get("Line");
																searchResults.addElement(doc.get("Seq") + 'ö' + doc.get("Code"));

																final TokenStream tokenStream = TokenSources.getTokenStream("Line", rootsSearcher.getIndexReader().getTermVectors(j), txt, rootsAnalyzer, highlighter.getMaxDocCharsToAnalyze() - 1);
																final TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, txt, false, 10);

																String line = "";
																for (TextFragment tf : frag)
																	if ((tf != null) && (tf.getScore() > 0F))
																		line = (line.isEmpty() ? "" : (line + " ... ")) + tf.toString();

																if (doc.get("Duration").equals("-1"))
																	searchResultsListModel.add("(" + (++index) + ") [?] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + line);
																else
																{
																	final int duration = Integer.parseInt(doc.get("Duration"));
																	final String minute = String.valueOf(duration / 60 / 1000);
																	final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
																	searchResultsListModel.add("(" + (++index) + ") [" + minute + ':' + second + "] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + line);
																}
															}
														}
													}
													else
													{
														for (int j = 0, size = bits.length(), index = 0; j < size && !stopIndexingSearch; j++)
														{
															if (bits.get(j))
															{
																final Document doc = luceneSearcher.doc(j);
																final String txt = doc.get("Line");
																searchResults.addElement(doc.get("Seq") + 'ö' + doc.get("Code"));

																final TokenStream tokenStream = TokenSources.getTokenStream("Line", luceneSearcher.getIndexReader().getTermVectors(j), txt, luceneAnalyzer, highlighter.getMaxDocCharsToAnalyze() - 1); // Version 1.9
																final TextFragment[] frag = highlighter.getBestTextFragments(tokenStream, txt, false, 10);

																// Version 2.7
																String line = "";
																for (TextFragment tf : frag)
																	if ((tf != null) && (tf.getScore() > 0F))
																		line = (line.isEmpty() ? "" : (line + " ... ")) + tf.toString();

																if (doc.get("Duration").equals("-1"))
																	searchResultsListModel.add("(" + (++index) + ") [?] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + line);
																else
																{
																	final int duration = Integer.parseInt(doc.get("Duration"));
																	final String minute = String.valueOf(duration / 60 / 1000);
																	final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
																	searchResultsListModel.add("(" + (++index) + ") [" + minute + ':' + second + "] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + line);
																}
															}
														}
													}
												}
											}
											else
											{
												if (defaultIndexSearchTypeButton.isSelected())
												{
													for (int j = 0, size = bits.length(), index = 0; j < size && !stopIndexingSearch; j++)
													{
														if (bits.get(j))
														{
															final Document doc = defaultSearcher.doc(j);
															searchResults.addElement(doc.get("Seq") + 'ö' + doc.get("Code"));

															if (doc.get("Duration").equals("-1"))
																searchResultsListModel.add("(" + (++index) + ") [?] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + doc.get("Line"));
															else
															{
																final int duration = Integer.parseInt(doc.get("Duration"));
																final String minute = String.valueOf(duration / 60 / 1000);
																final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
																searchResultsListModel.add("(" + (++index) + ") [" + minute + ':' + second + "] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + doc.get("Line"));
															}
														}
													}
												}
												else
												{
													if (rootsIndexSearchTypeButton.isSelected())
													{
														for (int j = 0, size = bits.length(), index = 0; j < size && !stopIndexingSearch; j++)
														{
															if (bits.get(j))
															{
																final Document doc = rootsSearcher.doc(j);
																searchResults.addElement(doc.get("Seq") + 'ö' + doc.get("Code"));

																if (doc.get("Duration").equals("-1"))
																	searchResultsListModel.add("(" + (++index) + ") [?] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + doc.get("Line"));
																else
																{
																	final int duration = Integer.parseInt(doc.get("Duration"));
																	final String minute = String.valueOf(duration / 60 / 1000);
																	final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
																	searchResultsListModel.add("(" + (++index) + ") [" + minute + ':' + second + "] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + doc.get("Line"));
																}
															}
														}
													}
													else
													{
														for (int j = 0, size = bits.length(), index = 0; j < size && !stopIndexingSearch; j++)
														{
															if (bits.get(j))
															{
																final Document doc = luceneSearcher.doc(j);
																searchResults.addElement(doc.get("Seq") + 'ö' + doc.get("Code"));

																if (doc.get("Duration").equals("-1"))
																	searchResultsListModel.add("(" + (++index) + ") [?] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + doc.get("Line"));
																else
																{
																	final int duration = Integer.parseInt(doc.get("Duration"));
																	final String minute = String.valueOf(duration / 60 / 1000);
																	final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
																	searchResultsListModel.add("(" + (++index) + ") [" + minute + ':' + second + "] (" + doc.get("Short_sheekh_name") + ")(" + doc.get("Book_name") + ") " + doc.get("Line"));
																}
															}
														}
													}
												}
											}

											Platform.runLater(() -> {
												audioSearchList.setItems(searchResultsListModel);
												searchPanel.setText("البحث [النتائج: " + searchResultsListModel.size() + ']');
											});
										}
										else
										{
											Platform.runLater(() -> {
												alertError("قم بتحديد نطاق البحث.", "تنبيه", "متابعة", null, AlertType.ERROR);
											});
										}
									}
									catch (OutOfMemoryError e)
									{
										e.printStackTrace();

										// Free the memory
										searchResults.removeAllElements();

										Platform.runLater(() -> {
											audioSearchList.getItems().clear();

											alertError("لقد تم نفاد الذاكرة اللازمة لإتمام عملية البحث لكثرة النتائج، لإنجاح العملية قم بتحرير الملف startup.bat" + ls +
													"والموضوع تحت مجلد البرنامج كما يلي:" + ls +
													"- قم بتغيير المقطع من Xmx1024m إلى Xmx2048m أو أكثر إن احتيج إلى ذلك. إبدأ البرنامج بالضغط على startup.bat" + ls +
													"[قم بالتغيير في startup.sh لأنظمة Linux/MacOS]", "تنبيه", "متابعة", e.getMessage(), AlertType.ERROR);
											//if(JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(getContentPane(), variable[124]+ls+variable[125]+ls+variable[126]+ls+variable[127], variable[7], JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{variable[145], variable[4]}, variable[145]))
											//shutdown(); TODO: test and see if OutOfMemoryError will leave the app unstable in javafx as in swing, then decide to give only one option, either shutdown or continue
											searchPanel.setText("البحث");
										});
									}
									catch (Exception e)
									{
										e.printStackTrace();
									}

									searchThreadWork = false;
									Platform.runLater(() -> {
										searchTextField.setDisable(false);
										searchButton.setGraphic(new ImageView(new Image(cl.getResourceAsStream("images/search.png"))));
										searchTextField.requestFocus();
									});
								}
							};
							thread.setName("MyThreads: Index search");
							thread.start();
						}
						else
							stopIndexingSearch = true;
					}
					else
					{
						if (searchTextField.getText().trim().length() == 2 && searchChoice != searchType.WHOLE_ALL_WORDS)
							alertError("لا يمكنك البحث عن حرفين إلا إذا كان خيار البحث 'بالنص المدخل تماما (الخيار الثاني)'.", "خطأ", "إلغاء", null, AlertType.ERROR);
						else
						{
							if (!searchThreadWork)
							{
								final Thread thread = new Thread()
								{
									public void run()
									{
										// This variable is used to stop searching two items at the same time.
										searchThreadWork = true;

										Platform.runLater(() -> {
											searchTextField.setDisable(true);
											historyPopup.hide();
										});

										SearchEngine(searchTextField.getText().trim());

										searchThreadWork = false;

										Platform.runLater(() -> {
											searchTextField.setDisable(false);
											searchTextField.requestFocus();
										});
									}
								};
								thread.start();
							}
						}
					}
				}
			}
		};
		searchTextField.setOnAction(searchListener);
		searchButton.setOnAction(searchListener);

		final ToggleGroup searchRelationGroup = new ToggleGroup();

		// This is for the new search technique in which the user can choose AND, OR relations between the search tokens.
		final RadioMenuItem ANDSearchRelationButton = new RadioMenuItem("تحتوي جميع الكلمات المدخلة (العلاقة `و`)");
		ANDSearchRelationButton.setSelected(true);
		ANDSearchRelationButton.setToggleGroup(searchRelationGroup);

		final RadioMenuItem ORSearchRelationButton = new RadioMenuItem("تحتوي أيّاً من الكلمات المدخلة (العلاقة `أو`)");
		ORSearchRelationButton.setToggleGroup(searchRelationGroup);

		searchRelationGroup.selectedToggleProperty().addListener(new ChangeListener<Toggle>()
		{
			public void changed(ObservableValue<? extends Toggle> ov, Toggle old_toggle, Toggle new_toggle)
			{
				if (searchRelationGroup.getSelectedToggle() != null)
				{
					if (ANDSearchRelationButton.isSelected())
						AND_SearchRelation = true;

					if (ORSearchRelationButton.isSelected())
						AND_SearchRelation = false;
				}
			}
		});

		final ToggleGroup searchGroup = new ToggleGroup();

		final RadioMenuItem matchCaseButton = new RadioMenuItem("أجزاء مطابقة للنص المدخل تماما");
		final RadioMenuItem matchCaseAllWordsButton = new RadioMenuItem("أجزاء من كلمات مدخلة (بحث شامل)");
		final RadioMenuItem wholeAllWordsButton = new RadioMenuItem("بالنص المدخل تماما (كل كلمة بحث مستقل)");
		wholeAllWordsButton.setSelected(true);

		matchCaseButton.setToggleGroup(searchGroup);
		matchCaseAllWordsButton.setToggleGroup(searchGroup);
		wholeAllWordsButton.setToggleGroup(searchGroup);

		searchGroup.selectedToggleProperty().addListener(new ChangeListener<Toggle>()
		{
			public void changed(ObservableValue<? extends Toggle> ov, Toggle old_toggle, Toggle new_toggle)
			{
				if (searchGroup.getSelectedToggle() != null)
				{
					if (matchCaseButton.isSelected())
					{
						searchChoice = searchType.MATCH_CASE;
						ANDSearchRelationButton.setDisable(true);
						ORSearchRelationButton.setDisable(true);
					}
					else
					{
						ANDSearchRelationButton.setDisable(false);
						ORSearchRelationButton.setDisable(false);
					}

					if (matchCaseAllWordsButton.isSelected())
						searchChoice = searchType.MATCH_CASE_ALL_WORDS;

					if (wholeAllWordsButton.isSelected())
						searchChoice = searchType.WHOLE_ALL_WORDS;
				}
			}
		});

		// Version 4.4
		final ToggleGroup indexSearchTypeGroup = new ToggleGroup();

		final RadioMenuItem lineIndexSearchButton = new RadioMenuItem("بحث في الفهرسة");
		lineIndexSearchButton.setToggleGroup(indexSearchTypeGroup);

		final RadioMenuItem tafreegIndexSearchButton = new RadioMenuItem("بحث في التفريغ");
		tafreegIndexSearchButton.setToggleGroup(indexSearchTypeGroup);

		indexSearchTypeGroup.selectedToggleProperty().addListener(new ChangeListener<Toggle>()
		{
			public void changed(ObservableValue<? extends Toggle> ov, Toggle old_toggle, Toggle new_toggle)
			{
				if (indexSearchTypeGroup.getSelectedToggle() != null)
				{
					if (lineIndexSearchButton.isSelected())
					{
						tafreegIndexSearch = false;
						withHighlighter.setDisable(false);
					}

					if (tafreegIndexSearchButton.isSelected())
					{
						tafreegIndexSearch = true;
						withHighlighter.setDisable(true);
					}
				}
			}
		});

		if(tafreegIndexSearch)
			tafreegIndexSearchButton.setSelected(true);
		else
			lineIndexSearchButton.setSelected(true);

		// This is for the new search technique in which we can ignore the tasheek.
		withTashkeelButton = new CheckMenuItem("بحث بمطابقة تشكيل الحروف");
		withTashkeelButton.setSelected(true);

		final Stage searchTreeDialog = new Stage();
		searchTreeDialog.setTitle("تحديد نطاق البحث والتصانيف الفقهية");
		searchTreeDialog.initOwner(primaryStage);
		searchTreeDialog.getIcons().addAll(new javafx.scene.image.Image(cl.getResource("images/icon.png").toString()));

		searchRootNode.setSelected(true);

		final Scene searchScene = new Scene(searchTree);
		searchScene.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

		searchTreeDialog.setScene(searchScene);
		searchTreeDialog.sizeToScene();

		final Button searchRangeButton = new Button(null, new ImageView(new Image(cl.getResourceAsStream("images/search_range.png"))));
		searchRangeButton.setTooltip(new Tooltip("المجال"));
		searchRangeButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				searchTreeDialog.show();
			}
		});

		final ToggleGroup searchTypeGroup = new ToggleGroup();
		defaultIndexSearchTypeButton.setToggleGroup(searchTypeGroup);
		rootsIndexSearchTypeButton.setToggleGroup(searchTypeGroup);
		luceneIndexSearchTypeButton.setToggleGroup(searchTypeGroup);

		final RadioButton dbSearch = new RadioButton("خيارات البحث بقاعدة البيانات");
		dbSearch.setTextFill(Color.RED);
		final CustomMenuItem c1 = new CustomMenuItem(dbSearch);
		c1.setHideOnClick(false);
		//c1.setStyle("-fx-background-color:transparent"); // change the blue focus/selection color by applying style to be transparent. Nor working. no need

		indexSearch = new RadioButton("خيارات البحث بالفهارس");
		indexSearch.setTextFill(Color.RED);
		final CustomMenuItem c2 = new CustomMenuItem(indexSearch);
		c2.setHideOnClick(false);

		final ToggleGroup PathGroup = new ToggleGroup();
		dbSearch.setToggleGroup(PathGroup);
		indexSearch.setToggleGroup(PathGroup);

		indexSearch.selectedProperty().addListener(new ChangeListener<Boolean>()
		{
			@Override
			public void changed(ObservableValue<? extends Boolean> obs, Boolean wasPreviouslySelected, Boolean isNowSelected)
			{
				if (isNowSelected)
				{
					rootsIndexSearchTypeButton.setDisable(false);
					defaultIndexSearchTypeButton.setDisable(false);
					luceneIndexSearchTypeButton.setDisable(false);
					lineIndexSearchButton.setDisable(false);
					tafreegIndexSearchButton.setDisable(false);

					if (lineIndexSearchButton.isSelected())
						withHighlighter.setDisable(false);

					matchCaseButton.setDisable(true);
					wholeAllWordsButton.setDisable(true);
					matchCaseAllWordsButton.setDisable(true);
					withTashkeelButton.setDisable(true);
					ANDSearchRelationButton.setDisable(true);
					ORSearchRelationButton.setDisable(true);
				}
			}
		});

		dbSearch.selectedProperty().addListener(new ChangeListener<Boolean>()
		{
			@Override
			public void changed(ObservableValue<? extends Boolean> obs, Boolean wasPreviouslySelected, Boolean isNowSelected)
			{
				if (isNowSelected)
				{
					matchCaseButton.setDisable(false);
					wholeAllWordsButton.setDisable(false);
					matchCaseAllWordsButton.setDisable(false);
					withTashkeelButton.setDisable(false);
					if (!matchCaseButton.isSelected())
					{
						ANDSearchRelationButton.setDisable(false);
						ORSearchRelationButton.setDisable(false);
					}

					rootsIndexSearchTypeButton.setDisable(true);
					defaultIndexSearchTypeButton.setDisable(true);
					luceneIndexSearchTypeButton.setDisable(true);
					lineIndexSearchButton.setDisable(true);
					tafreegIndexSearchButton.setDisable(true);
					withHighlighter.setDisable(true);
				}
			}
		});

		final ContextMenu searchOptionsPopupMenu = new ContextMenu(
				c1,
				matchCaseButton,
				wholeAllWordsButton,
				matchCaseAllWordsButton,
				new SeparatorMenuItem(),
				ANDSearchRelationButton,
				ORSearchRelationButton,
				new SeparatorMenuItem(),
				withTashkeelButton,
				c2,
				defaultIndexSearchTypeButton,
				rootsIndexSearchTypeButton,
				luceneIndexSearchTypeButton,
				new SeparatorMenuItem(),
				lineIndexSearchButton,
				tafreegIndexSearchButton,
				new SeparatorMenuItem(),
				withHighlighter);
		searchOptionsPopupMenu.setAutoHide(true);

		final Button searchOptionsButton = new Button(null, new ImageView(new Image(cl.getResourceAsStream("images/setting.png"))));
		searchOptionsButton.setTooltip(new Tooltip("الخيارات"));
		searchOptionsButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				searchOptionsPopupMenu.show(searchOptionsButton, Side.BOTTOM, 0, 0);
			}
		});

		final BorderPane topSearchPanel = new BorderPane();
		topSearchPanel.setCenter(searchTextField);
		topSearchPanel.setLeft(searchButton);

		final HBox searchOptionsPanel = new HBox();
		topSearchPanel.setRight(searchOptionsPanel);
		searchOptionsPanel.getChildren().addAll(searchOptionsButton, searchRangeButton);

		audioSearchList = new ListView<>();
		audioSearchList.setCellFactory(new Callback<ListView<String>, ListCell<String>>()
		{
			@Override
			public ListCell<String> call(ListView<String> list)
			{
				final ListCell<String> cell = new ListCell<String>()
				{
					@Override
					public void updateItem(String item, boolean empty)
					{
						super.updateItem(item, empty);

						if (empty || item == null)
						{
							setText(null);
							setGraphic(null);
						}
						else
						{
							if (indexSearch.isSelected() && withHighlighter.isSelected())
							{
								setText(null);
								Platform.runLater(() ->
								{
									/*
										We are getting a lot of NPE because of the below line. we put it in Platform.runLater even though that this is inside the main GUI thread but it seems it is queuing it and help the situation
										https://stackoverflow.com/questions/24043420/why-does-platform-runlater-not-check-if-it-currently-is-on-the-javafx-thread

										NPE:
										Exception in thread "JavaFX Application Thread" java.lang.ArrayIndexOutOfBoundsException: Index 6 out of bounds for length 6
										at javafx.graphics/com.sun.javafx.text.TextRun.getWrapIndex(TextRun.java:291)
										at javafx.graphics/com.sun.javafx.text.PrismTextLayout.layout(PrismTextLayout.java:1089)
									 */
									setGraphic(buildTextFlow(item));
								});
							}
							else
								setText(item);
						}
					}
				};

				return cell;
			}
		});

		// List Selection Listener for Search panel.
		audioSearchList.getSelectionModel().selectedIndexProperty().addListener(new ChangeListener<Number>()
		{
			@Override
			public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue)
			{
				if(oldValue.intValue() != newValue.intValue())
				{
					searchSelectedIndex = newValue.intValue();
					audioSearchListAction();
				}
			}
		});

		// mouse listener is triggered after the above ChangeListener
		audioSearchList.setOnMouseClicked(new EventHandler<MouseEvent>()
		{
			@Override
			public void handle(MouseEvent m)
			{
				/*
				 * This is made the same as in AudioDetailsSharedMouseListenerHandler to get the right period of each index.
				 *
				 * Do the same in all cases i.e. indexSearch = false OR true, since this will reserve some memory by not saving some information with each search result item.
				 * BUG ... One case that will not work, if details list is empty (by clicking on another cassette) or the index # is changed, then click on the SAME search result item will not work correctly.
				 *
				 * Version 2.0, Solved
				 */
				if (!fireSearchListSelectionListener && m.getClickCount() == 1 && searchSelectedIndex != -1 && m.getEventType() == MouseEvent.MOUSE_PRESSED)
				{
					// removed, item is de-selected and not re-selected
					//audioSearchList.getSelectionModel().clearSelection();
					//audioSearchList.getSelectionModel().select(searchSelectedIndex);
					audioSearchListAction();
				}

				if (m.getClickCount() == 2 && searchSelectedIndex != -1)
				{
					int nextOffset;
					if ((detailsSelectedIndex + 1) < audioDetailsOffset.size())
						nextOffset = audioDetailsOffset.elementAt(detailsSelectedIndex + 1);
					else
						// i.e. The last item in the indices.
						nextOffset = -1; // Version 2.4, It was = "null"

					/*
					 * This condition is made to make sure that when we have an in-order index that both indeices
					 * should not be limited by a period. As a result it should be checked with previous index and
					 * the next index. The previous index is checked here where the next index is checked in the player
					 * function since we pass both current and next indices and it is easy to check there for this purpose.
					 */
					if (detailsSelectedIndex != 0)
					{
						if (audioDetailsOffset.elementAt(detailsSelectedIndex) < audioDetailsOffset.elementAt(detailsSelectedIndex - 1))
							nextOffset = -1;
					}

					player(choosedAudioPath + selected_FileName, audioDetailsOffset.elementAt(detailsSelectedIndex), nextOffset);
				}
				//else
				// Version 1.6, searchSelectedIndex!=-1 is added to not be activated when nothing is displayed.
				//if(m.getClickCount()==2 && searchSelectedIndex!=-1)
				//	player(choosedAudioPath + audioSearchMouseSelectionListenerPath, audioSearchMouseSelectionListenerOffset, audioSearchMouseSelectionListenerNextOffset);
			}
		});

		final BorderPane searchPanelPane = new BorderPane();
		searchPanelPane.setPadding(Insets.EMPTY);
		searchPanelPane.setTop(topSearchPanel);
		searchPanelPane.setCenter(audioSearchList);

		searchPanel.setContent(searchPanelPane);

		indexTextArea = new TextArea();
		indexTextArea.setEditable(false);
		indexTextArea.setWrapText(true);
		indexTextArea.setPrefRowCount(4);
		indexTextArea.setPrefColumnCount(0);

		tafreegTextArea = new TextArea();
		tafreegTextArea.setEditable(false);
		tafreegTextArea.setWrapText(true);
		tafreegTextArea.setPrefRowCount(4);
		tafreegTextArea.setPrefColumnCount(0);

		final Tab indexTab = new Tab("الفهرسة", indexTextArea);
		final Tab tafreegTab = new Tab("التفريغ", tafreegTextArea);
		indexTab.setClosable(false);
		tafreegTab.setClosable(false);

		final TabPane indexTabbedPane = new TabPane(indexTab, tafreegTab);
		indexPanel.setContent(indexTabbedPane);

		final MenuItem addMenuItem = new MenuItem("إضافة");
		addMenuItem.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				// For initial case if nothing selected or no cassette is selected
				if (selected_Code == 0)
					alertError("قم بتحديد الشريط الذي تريد أن تضيف إليه فهرسة من قائمة المشايخ والكتب.", "خطأ", "إلغاء", null, AlertType.ERROR);
				else
					new AddIndex(0, false);
			}
		});

		final MenuItem addScientistMenuItem = new MenuItem("إضافة");
		addScientistMenuItem.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				// For initial case if no book is selected.
				if (selected_TreeLevel.isEmpty() || selected_TreeLevel.equals("4"))
				{
					alertError("لم يتم تحديد سلسلة معينة لإضافة شريط، أو شيخ معين لإضافة سلسلة أو كتاب، أو رأس القائمة لإضافة شيخ.", "خطأ", "إلغاء", null, AlertType.ERROR);
					return;
				}

				// To avoid adding when more tree paths are selected.
				final ObservableList<TreeItem<AudioInfo>> treePaths = tree.getSelectionModel().getSelectedItems();
				if (treePaths.size() == 1)
				{
					final Dialog addDialog = new Dialog();
					addDialog.initOwner(primaryStage);
					addDialog.setTitle("إضافة شيخ أو سلسلة أو شريط");
					addDialog.setHeaderText(selected_TreeLevel.equals("0") ?
							"عليك التنبه إلى أنك ستضيف شيخا مع سلسلة واحدة وشريطا واحدا." : (selected_TreeLevel.equals("1") ?
							("عليك التنبه إلى أنك ستضيف سلسلة لـ: " + selected_Sheekh_name + "، وإلا فلن تستطيع الاستماع إلى الشريط.") :
									("عليك التنبه إلى أنك ستضيف شريطا إلى: " + selected_Book_name + " لـ: " + selected_Sheekh_name)));
					addDialog.setResizable(false);

					final BorderPane audioPanel = new BorderPane();
					audioPanel.setLeft(new Label("عنوان الشريط أو اسم الكتاب"));

					final TextField audioTextField = new TextField();
					audioPanel.setRight(audioTextField);

					final BorderPane filePanel = new BorderPane();
					filePanel.setLeft(new Label(" الملف بالصيغة r1.wma , 1.m4a , 1.mp3 , 1.rm"));

					final TextField fileTextField = new TextField();
					filePanel.setRight(fileTextField);

					final BorderPane bookPanel = new BorderPane();
					bookPanel.setLeft(new Label(" اسم السلسلة أو الشرح"));

					final TextField bookTextField = new TextField();
					bookPanel.setRight(bookTextField);

					final BorderPane bookDirectoryPanel = new BorderPane();
					bookDirectoryPanel.setLeft(new Label(" اسم مجلد السلسلة"));

					final TextField bookDirectoryTextField = new TextField();
					bookDirectoryPanel.setRight(bookDirectoryTextField);

					final BorderPane subLevelBookDirectoryPanel = new BorderPane();
					subLevelBookDirectoryPanel.setLeft(new Label("اسم مجلد الكتاب في السلسلة "));

					final TextField subLevelBookDirectoryTextField = new TextField();
					subLevelBookDirectoryTextField.setDisable(true);
					subLevelBookDirectoryPanel.setRight(subLevelBookDirectoryTextField);

					final ToggleButton subLevelBooksButton = new ToggleButton(null, new ImageView(new Image(cl.getResourceAsStream("images/folders.png"))));
					subLevelBooksButton.setTooltip(new Tooltip("اضغط على هذا الخيار إن كانت السلسلة مجزأة إلى كتب فرعية"));
					subLevelBookDirectoryPanel.setCenter(subLevelBooksButton);

					subLevelBooksButton.selectedProperty().addListener((obs, wasSelected, isNowSelected) ->
					{
						if (isNowSelected)
							subLevelBookDirectoryTextField.setDisable(false);
						else
							subLevelBookDirectoryTextField.setDisable(true);
					});

					final BorderPane scientistPanel = new BorderPane();
					scientistPanel.setLeft(new Label(" اسم الشيخ"));

					final TextField scientistTextField = new TextField();
					scientistPanel.setRight(scientistTextField);

					final BorderPane scientistShortNamePanel = new BorderPane();
					scientistShortNamePanel.setLeft(new Label(" اسم الشيخ مختصراً"));

					final TextField scientistShortNameTextField = new TextField();
					scientistShortNamePanel.setRight(scientistShortNameTextField);

					final BorderPane scientistDirectoryPanel = new BorderPane();
					scientistDirectoryPanel.setLeft(new Label(" اسم مجلد الشيخ"));

					final TextField scientistDirectoryTextField = new TextField();
					scientistDirectoryPanel.setRight(scientistDirectoryTextField);

					final BorderPane durationPanel = new BorderPane();
					durationPanel.setLeft(new Label(" مدة الشريط"));

					final UnaryOperator<TextFormatter.Change> modifyChange = c ->
					{
						if (c.isContentChange())
						{
							final String text = c.getControlNewText();
							final int newLength = text.length();
							if (newLength > 2)
							{
								ALERT_AUDIOCLIP.play();
								return null;
							}

							if (text.matches("[0-9]*"))
								return c;
							else
							{
								ALERT_AUDIOCLIP.play();
								return null;
							}
						}
						return c;
					};

					final TextField hourTextField = new TextField();
					hourTextField.setPrefColumnCount(2);
					hourTextField.setTextFormatter(new TextFormatter(modifyChange));

					final TextField minuteTextField = new TextField();
					minuteTextField.setPrefColumnCount(2);
					minuteTextField.setTextFormatter(new TextFormatter(modifyChange));

					final TextField secondTextField = new TextField();
					secondTextField.setPrefColumnCount(2);
					final DecimalFormat format = new DecimalFormat("0.0");
					secondTextField.setTextFormatter(new TextFormatter<>(c ->
					{
						// The same as hours/minutes but accept seconds with fraction of 1 digit
						final String text = c.getControlNewText();
						final int newLength = text.length();
						if (newLength > 4)
						{
							ALERT_AUDIOCLIP.play();
							return null;
						}

						if (text.isEmpty())
						{
							return c;
						}

						final ParsePosition parsePosition = new ParsePosition(0);
						final Number num = format.parse(text, parsePosition);

						if (num == null || parsePosition.getIndex() < text.length())
						{
							ALERT_AUDIOCLIP.play();
							return null;
						}
						else
						{
							return c;
						}
					}));

					final Button autoDuationDetectButton = new Button(null, new ImageView(new Image(cl.getResourceAsStream("images/duration.png"))));
					autoDuationDetectButton.setTooltip(new Tooltip("تحديد تلقائي لمدة الملف الصوتي"));
					autoDuationDetectButton.setOnAction(new EventHandler<ActionEvent>()
					{
						@Override
						public void handle(ActionEvent e)
						{
							final FileChooser fc = new FileChooser();
							fc.getExtensionFilters().addAll(
									new FileChooser.ExtensionFilter("rm", "*.rm"),
									new FileChooser.ExtensionFilter("mp3", "*.mp3"),
									new FileChooser.ExtensionFilter("wma", "*.wma"),
									new FileChooser.ExtensionFilter("m4a", "*.m4a")
							);
							fc.setTitle("استعراض");

							try
							{
								final File file = fc.showOpenDialog(primaryStage);

								if (file != null)
								{
									final int fileDuration = org.jaudiotagger.audio.AudioFileIO.read(file).getAudioHeader().getTrackLength(); // Working ok. but old and not in maven
									//final int fileDuration = (int)jVLC.getLength(file); //not working it gives -1 if the file is not playing

									// It needs MediaInfo native binaries whihc is huge and OS specific. ignore it
									//final MediaInfo mediaInfo = MediaInfo.mediaInfo(file.getAbsolutePath());
									//final Section audio = mediaInfo.first("Audio");
									//final Duration duration = audio.duration("Duration");
									//final int fileDuration = duration.seconds();

									/* This is working, but you will increase the size by 30mb
									//https://ffmpeg.zeranoe.com/forum/viewtopic.php?t=3572&start=40
									// Maven -> net.bramp.ffmpeg:ffmpeg:0.6.2
									FFprobe ffprobe = new FFprobe(new File(AudioCataloger.cl.getResource("bin/ffprobe.exe").toURI()).getAbsolutePath());
									FFmpegProbeResult probeResult = ffprobe.probe(file.getAbsolutePath());

									FFmpegStream stream = probeResult.getStreams().get(0);
									System.out.format("stream.sample_rate "+stream.sample_rate);
									*/

									fileTextField.setText(file.getName());
									hourTextField.setText(String.valueOf(fileDuration / 3600));
									minuteTextField.setText(String.valueOf(fileDuration / 60 - (fileDuration / 3600) * 60));
									secondTextField.setText(String.valueOf(fileDuration - ((int) ((float) fileDuration / 60F) * 60)) + ".0");
								}
								else
									System.out.println("Attachment cancelled by user.");
							}
							catch (Exception ex)
							{
								ex.printStackTrace();
								alertError("لا يمكن تحديد مدة الشريط إما لأن الملف الصوتي معطوب أو أن الملف بصيغة مجهولة. أدخل المدة بشكل يدوي.", "خطأ", "إلغاء", null, AlertType.ERROR);
							}
						}
					});

					if (!selected_TreeLevel.equals("0"))
					{
						try
						{
							final Statement stmt = sharedDBConnection.createStatement();
							if (selected_TreeLevel.equals("1"))
							{
								scientistTextField.setEditable(false);
								scientistDirectoryTextField.setEditable(false);
								scientistShortNameTextField.setEditable(false);

								final ResultSet rs1 = stmt.executeQuery("SELECT Path FROM Chapters WHERE Sheekh_id=" + selected_Sheekh_id);
								rs1.next();

								scientistTextField.setText(selected_Sheekh_name);
								scientistDirectoryTextField.setText(rs1.getString("Path").split("\\\\")[0]);

								final ResultSet rs2 = stmt.executeQuery("SELECT Short_sheekh_name FROM Book WHERE Sheekh_id=" + selected_Sheekh_id);
								rs2.next();

								scientistShortNameTextField.setText(rs2.getString("Short_sheekh_name"));
							}
							else
							{
								if (selected_TreeLevel.equals("2"))
								{
									scientistTextField.setEditable(false);
									scientistDirectoryTextField.setEditable(false);
									scientistShortNameTextField.setEditable(false);
									bookTextField.setEditable(false);
									bookDirectoryTextField.setEditable(false);
									subLevelBooksButton.setDisable(true);

									final ResultSet rs1 = stmt.executeQuery("SELECT Path FROM Chapters WHERE Book_id=" + selected_Book_id); // No need for 'Sheekh_id=selected_Sheekh_id' since there is only one Book_id for one Sheekh_id
									rs1.next();

									final String path = rs1.getString("Path");
									scientistTextField.setText(selected_Sheekh_name);
									scientistDirectoryTextField.setText(path.split("\\\\")[0]);
									bookTextField.setText(selected_Book_name);
									bookDirectoryTextField.setText(path.split("\\\\")[1]);

									final ResultSet rs2 = stmt.executeQuery("SELECT Multi_volume, Short_sheekh_name FROM Book WHERE Book_id=" + selected_Book_id);
									rs2.next();

									scientistShortNameTextField.setText(rs2.getString("Short_sheekh_name"));

									if (rs2.getBoolean("Multi_volume"))
										subLevelBookDirectoryTextField.setDisable(false);
								}
								else // i.e. selected_TreeLevel.equals("3")
								{
									scientistTextField.setEditable(false);
									scientistDirectoryTextField.setEditable(false);
									scientistShortNameTextField.setEditable(false); // Version 2.8
									bookTextField.setEditable(false);
									bookDirectoryTextField.setEditable(false);
									subLevelBooksButton.setDisable(true);
									audioTextField.setEditable(false);

									final ResultSet rs1 = stmt.executeQuery("SELECT Path, Book_name FROM Chapters WHERE Book_id=" + selected_Book_id + " AND Title='" + selected_Book_name + "'"); // No need for 'Sheekh_id=selected_Sheekh_id' since there is only one Book_id for one Sheekh_id
									rs1.next();

									final String path = rs1.getString("Path");

									scientistTextField.setText(selected_Sheekh_name);
									scientistDirectoryTextField.setText(path.split("\\\\")[0]);
									bookTextField.setText(rs1.getString("Book_name"));

									final String bookDirectory = path.substring(path.indexOf('\\') + 1);
									bookDirectoryTextField.setText(bookDirectory.split("\\\\")[0]);

									audioTextField.setText(selected_Book_name);

									final ResultSet rs2 = stmt.executeQuery("SELECT Short_sheekh_name FROM Book WHERE Book_id=" + selected_Book_id);
									rs2.next();

									scientistShortNameTextField.setText(rs2.getString("Short_sheekh_name"));

									//if(rs2.getBoolean("Multi_volume")) // It is a multi-volume since selected_TreeLevel.equals("3") and we are adding to it
									{
										if (bookDirectory.indexOf('\\') > -1)
											subLevelBookDirectoryTextField.setText(bookDirectory.split("\\\\")[1]);
									}
								}
							}
							stmt.close();
						}
						catch (Exception ex)
						{
							ex.printStackTrace();
						}
					}

					final HBox timePanel = new HBox();
					timePanel.getChildren().addAll(secondTextField, new Label(":"), minuteTextField, new Label(":"), hourTextField);

					final BorderPane timePanelDecorate = new BorderPane();
					timePanelDecorate.setRight(autoDuationDetectButton);
					timePanelDecorate.setCenter(timePanel);
					durationPanel.setRight(timePanelDecorate);

					final ButtonType OKButtonType = new ButtonType("أضف", ButtonBar.ButtonData.OK_DONE);
					addDialog.getDialogPane().getButtonTypes().add(OKButtonType);
					final Button OKButton = (Button) addDialog.getDialogPane().lookupButton(OKButtonType);
					OKButton.setOnAction(new EventHandler<ActionEvent>()
					{
						@Override
						public void handle(ActionEvent e)
						{
							if (!(fileTextField.getText().trim().endsWith(".rm") || fileTextField.getText().trim().endsWith(".mp3")
									|| fileTextField.getText().trim().endsWith(".wma") || fileTextField.getText().trim().endsWith(".m4a")) || audioTextField.getText().trim().isEmpty()
									|| bookTextField.getText().trim().isEmpty() || bookDirectoryTextField.getText().trim().isEmpty()
									|| scientistTextField.getText().trim().isEmpty() || scientistDirectoryTextField.getText().trim().isEmpty()
									|| hourTextField.getText().isEmpty() || secondTextField.getText().isEmpty()
									|| minuteTextField.getText().isEmpty() || scientistShortNameTextField.getText().trim().isEmpty()
									|| (subLevelBookDirectoryTextField.getText().isEmpty() && !subLevelBookDirectoryTextField.isDisable()))
							{
								alertError("أحد المداخيل فارغ أو غير صحيح.", "خطأ", "إلغاء", null, AlertType.ERROR);
								return;
							}

							try
							{
								final Statement stmt = sharedDBConnection.createStatement();
								boolean cont = true;
								ResultSet rs;

								if (selected_TreeLevel.equals("0"))
								{
									// Check if the sheekh already exists in Sheekh table.
									rs = stmt.executeQuery("SELECT Sheekh_id FROM Sheekh WHERE Sheekh_name='" + scientistTextField.getText().trim() + "'");
									if (rs.next())
									{
										cont = false;
										alertError("الشيخ المدخل موجود في قاعدة البيانات، قم بتحديد الشيخ من قائمة المشايخ والكتب ثم قم بالإضافة عليه.", "تنبيه", "إلغاء", null, AlertType.ERROR);
									}
									else
									{
										// Check if the folder is used for another sheekh
										rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE SUBSTR(Path, 1, " + (scientistDirectoryTextField.getText().trim().length() + 1) + ") = '" + scientistDirectoryTextField.getText().trim() + "\\'");
										if (rs.next())
										{
											cont = false;
											alertError("مجلد الشيخ مستخدم لشيخ آخر، لا يمكنك تكرار نفس المجلد.", "تنبيه", "إلغاء", null, AlertType.ERROR);
										}
									}
								}

								// Check if the book already exists in Book table.
								if (selected_TreeLevel.equals("1"))
								{
									rs = stmt.executeQuery("SELECT Book_id FROM Book WHERE Book_name='" + bookTextField.getText().trim() + "' AND Sheekh_id=" + selected_Sheekh_id);
									if (rs.next())
									{
										cont = false;
										alertError("الكتاب المدخل موجود في قاعدة البيانات، قم بتحديد الكتاب للشيخ نفسه ثم قم بالإضافة عليه.", "تنبيه", "إلغاء", null, AlertType.ERROR);
									}
									else
									{
										// Version 2.2, Check if the folder is used for another book for the same sheekh
										// Version 3.0, Modified to avoid the multi-volume books folders issues.
										//rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Sheekh_id="+selected_Sheekh_id+" AND SUBSTR(Path, "+(scientistDirectoryTextField.getText().trim().length()+2)+") = '"+bookDirectoryTextField.getText().trim()+"'");
										rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Sheekh_id=" + selected_Sheekh_id + " AND LOCATE('" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + "\\', Path || '\\') = 1"); // No need for WHERE Sheekh_id='...' since Path is checked from start but it might speed up the statement. TODO: change LOCATE with LIKE or any startWith function
										if (rs.next())
										{
											cont = false;
											alertError("مجلد السلسلة مستخدم لسلسلة أخرى لنفس الشيخ، لا يمكنك تكرار نفس المجلد.", "تنبيه", "إلغاء", null, AlertType.ERROR);
										}
									}
								}

								if (selected_TreeLevel.equals("2"))
								{
									// Check in case of sub-book directory exists or not.
									if (!subLevelBookDirectoryTextField.isDisabled())
									{
										rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Book_id=" + selected_Book_id + " AND Title='" + audioTextField.getText().trim() + "'");
										if (rs.next())
										{
											cont = false;
											alertError("اسم الكتاب في السلسلة موجود في قاعدة البيانات، قم بتحديد الكتاب في السلسلة نفسها ثم قم بالإضافة عليه.", "تنبيه", "إلغاء", null, AlertType.ERROR);
										}
										else
										{
											rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Book_id=" + selected_Book_id + " AND LOCATE('" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + '\\' + subLevelBookDirectoryTextField.getText().trim() + "\\', Path || '\\') = 1"); // No need for WHERE Book_id='...' since Path is checked from the start but it might speed up the statement.
											if (rs.next())
											{
												cont = false;
												alertError("مجلد الكتاب في السلسلة مستخدم لكتاب آخر لنفس السلسلة، لا يمكنك تكرار نفس المجلد.", "تنبيه", "إلغاء", null, AlertType.ERROR);
											}
										}
									}
									else
									{
										// Check if the cassette already exists in Chapters table.
										rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Book_id = " + selected_Book_id + " AND Path = '" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + "' AND FileName = '" + fileTextField.getText().trim().split("\\.")[0] + "' AND FileType = '" + fileTextField.getText().trim().split("\\.")[1] + "'"); // No need for 'Sheekh_id=selected_Sheekh_id' since there is only one Book_id for one Sheekh_id
										if (rs.next())
										{
											cont = false;
											alertError("الشريط المدخل موجود في قاعدة البيانات، قم باختيار شريط آخر أو قم بتغيير اسمه أو امتداده.", "تنبيه", "إلغاء", null, AlertType.ERROR);
										}
									}
								}

								// Check if the cassette already exists in Chapters table.
								if (selected_TreeLevel.equals("3"))
								{
									if (subLevelBookDirectoryTextField.getText().isEmpty()) // For some cases in the default DB e.g. mashhor\moslem.
										rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Book_id = " + selected_Book_id + " AND Path = '" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + "' AND FileName = '" + fileTextField.getText().trim().split("\\.")[0] + "' AND FileType = '" + fileTextField.getText().trim().split("\\.")[1] + "'"); // No need for 'Sheekh_id=selected_Sheekh_id' since there is only one Book_id for one Sheekh_id
									else
										rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Book_id = " + selected_Book_id + " AND Path = '" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + '\\' + subLevelBookDirectoryTextField.getText() + "' AND FileName = '" + fileTextField.getText().trim().split("\\.")[0] + "' AND FileType = '" + fileTextField.getText().trim().split("\\.")[1] + "'"); // No need for 'Sheekh_id=selected_Sheekh_id' since there is only one Book_id for one Sheekh_id

									if (rs.next())
									{
										cont = false;
										alertError("الشريط المدخل موجود في قاعدة البيانات، قم باختيار شريط آخر أو قم بتغيير اسمه أو امتداده.", "تنبيه", "إلغاء", null, AlertType.ERROR);
									}
								}

								final int hour = Integer.parseInt(hourTextField.getText());
								final float second = Float.parseFloat(secondTextField.getText());
								final int minute = Integer.parseInt(minuteTextField.getText());
								if (second > 59.9 || minute > 59)
								{
									cont = false;
									alertError("أدخل قيمة صحيحة لعدد الثواني والدقائق (0 - 59).", "تنبيه", "إلغاء", null, AlertType.ERROR);
								}

								if (cont)
								{
									int new_Sheekh_id = 0;
									int new_Code/* = 0*/;
									int new_Book_id = 0;

									if (selected_TreeLevel.equals("0"))
									{
										rs = stmt.executeQuery("SELECT MAX(Sheekh_id) as max_Sheekh_id FROM Sheekh");
										rs.next();
										new_Sheekh_id = rs.getInt("max_Sheekh_id") + 1;
									}

									if (selected_TreeLevel.equals("0") || selected_TreeLevel.equals("1"))
									{
										rs = stmt.executeQuery("SELECT MAX(Book_id) as max_Book_id FROM Book");
										rs.next();
										new_Book_id = rs.getInt("max_Book_id") + 1;
									}

									rs = stmt.executeQuery("SELECT MAX(Code) as max_Code FROM Chapters");
									rs.next();
									new_Code = rs.getInt("max_Code") + 1;

									final int Offset = (hour * 3600 + minute * 60) * 1000 + (int) (second * 1000);
									if (selected_TreeLevel.equals("0"))
									{
										// Order is important
										stmt.execute("INSERT INTO Sheekh VALUES (" + new_Sheekh_id + ", '" + scientistTextField.getText().trim() + "')");
										stmt.execute("INSERT INTO Book VALUES (" + new_Book_id + ", '" + bookTextField.getText().trim() + "'," + new_Sheekh_id + ',' + subLevelBooksButton.isSelected() + ",'" + scientistShortNameTextField.getText().trim() + "')"); // Version 2.7/2.8
										stmt.execute("INSERT INTO Chapters VALUES (" + new_Code + ", " + new_Sheekh_id + ", " + new_Book_id + ", '" + bookTextField.getText().trim() + "', '" + scientistTextField.getText().trim() + "', '" + audioTextField.getText().trim() + "', '" + fileTextField.getText().trim().split("\\.")[0] + "', '" + fileTextField.getText().trim().split("\\.")[1] + "', " + Offset + ", '" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + (subLevelBooksButton.isSelected() ? ('\\' + subLevelBookDirectoryTextField.getText().trim()) : "") + "','dummy')");
									}
									else
									{
										if (selected_TreeLevel.equals("1"))
										{
											stmt.execute("INSERT INTO Book VALUES (" + new_Book_id + ", '" + bookTextField.getText().trim() + "', " + selected_Sheekh_id + ',' + subLevelBooksButton.isSelected() + ", '" + scientistShortNameTextField.getText().trim() + "')"); // Version 2.7/2.8
											stmt.execute("INSERT INTO Chapters VALUES (" + new_Code + ", " + selected_Sheekh_id + ", " + new_Book_id + ", '" + bookTextField.getText().trim() + "', '" + scientistTextField.getText().trim() + "', '" + audioTextField.getText().trim() + "', '" + fileTextField.getText().trim().split("\\.")[0] + "', '" + fileTextField.getText().trim().split("\\.")[1] + "', " + Offset + ", '" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + (subLevelBooksButton.isSelected() ? ('\\' + subLevelBookDirectoryTextField.getText().trim()) : "") + "','dummy')");
										}
										else // i.e. selected_TreeLevel.equals("2") or ("3")
										{
											// Version 3.0
											if (subLevelBookDirectoryTextField.getText().isEmpty())
												stmt.execute("INSERT INTO Chapters VALUES (" + new_Code + ", " + selected_Sheekh_id + ", " + selected_Book_id + ", '" + bookTextField.getText().trim() + "', '" + scientistTextField.getText().trim() + "', '" + audioTextField.getText().trim() + "', '" + fileTextField.getText().trim().split("\\.")[0] + "', '" + fileTextField.getText().trim().split("\\.")[1] + "', " + Offset + ", '" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + "','dummy')");
											else
												stmt.execute("INSERT INTO Chapters VALUES (" + new_Code + ", " + selected_Sheekh_id + ", " + selected_Book_id + ", '" + bookTextField.getText().trim() + "', '" + scientistTextField.getText().trim() + "', '" + audioTextField.getText().trim() + "', '" + fileTextField.getText().trim().split("\\.")[0] + "', '" + fileTextField.getText().trim().split("\\.")[1] + "', " + Offset + ", '" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + '\\' + subLevelBookDirectoryTextField.getText() + "','dummy')");
										}
									}
									stmt.close();

									// To refresh detailList
									createNodes();
									orderButton.fire();

									addDialog.close();
									alertError("تم إضافة الشيخ والسلسلة والشريط إلى قاعدة البيانات فعليك التنبه إلى أن اسم الملف الصوتي لهذا الشريط هو: (" + fileTextField.getText().trim() + ") وإلا فلن تستطيع الاستماع إلى الشريط.", "تنبيه", "متابعة", null, AlertType.ERROR);
								}
								stmt.close();
							}
							catch (Exception ex)
							{
								ex.printStackTrace();
							}
						}
					});

					final ButtonType cancelButtonType = new ButtonType("إلغاء", ButtonBar.ButtonData.CANCEL_CLOSE);
					addDialog.getDialogPane().getButtonTypes().add(cancelButtonType);
					final Button cancelButton = (Button) addDialog.getDialogPane().lookupButton(cancelButtonType);
					cancelButton.setOnAction(new EventHandler<ActionEvent>()
					{
						@Override
						public void handle(ActionEvent e)
						{
							addDialog.close();
						}
					});

					final VBox panel = new VBox();
					addDialog.getDialogPane().setContent(panel);

					panel.getChildren().addAll(scientistPanel,
							scientistShortNamePanel,
							scientistDirectoryPanel,
							new Separator(),
							bookPanel,
							bookDirectoryPanel,
							new Separator(),
							audioPanel,
							subLevelBookDirectoryPanel,
							filePanel,
							durationPanel);

					/* TODO
					filePanel.setPreferredSize(subLevelBookDirectoryPanel.getPreferredSize());
					audioPanel.setPreferredSize(subLevelBookDirectoryPanel.getPreferredSize());
					durationPanel.setPreferredSize(subLevelBookDirectoryPanel.getPreferredSize());
					bookDirectoryPanel.setPreferredSize(subLevelBookDirectoryPanel.getPreferredSize());
					scientistDirectoryPanel.setPreferredSize(subLevelBookDirectoryPanel.getPreferredSize());
					scientistShortNamePanel.setPreferredSize(subLevelBookDirectoryPanel.getPreferredSize());
					scientistPanel.setPreferredSize(subLevelBookDirectoryPanel.getPreferredSize());
					bookPanel.setPreferredSize(subLevelBookDirectoryPanel.getPreferredSize());
					*/

					scientistDirectoryTextField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
					bookDirectoryTextField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
					fileTextField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

					addDialog.show();
				}
				else
					alertError("قم بتحديد سلسلة معينة، أو شيخ معين، أو رأس القائمة فقط لتتم الإضافة ولا تقم بتحديد أكثر من ذلك.", "خطأ", "إلغاء", null, AlertType.ERROR);
			}
		});

		// Editing is not covering everything e.g. cannot edit all parameters for one cassette e.g. CDNumber
		final MenuItem editScientistMenuItem = new MenuItem("تحرير");
		editScientistMenuItem.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				// For initial case if nothing selected
				if (selected_TreeLevel.isEmpty())
				{
					alertError("قم بتحديد ما تريد تحريره من قائمة المشايخ والكتب.", "خطأ", "إلغاء", null, AlertType.ERROR);
					return;
				}

				// If it is tree root
				if (selected_TreeLevel.equals("0"))
				{
					alertError("لا يمكن تحرير رأس القائمة.", "خطأ", "إلغاء", null, AlertType.ERROR);
					return;
				}

				// To avoid editing when more tree paths are selected.
				final ObservableList<TreeItem<AudioInfo>> treePaths = tree.getSelectionModel().getSelectedItems();
				if (treePaths.size() == 1)
				{
					final String tmp = treePaths.get(0).getValue().info6;
					// Disable editing a leaf node (cassette) in case multi-volume book since changing the new name might be available for another sub-book, and in this case there will be merging between the two having the risk of a duplicate files in both. The same goes with editing the folder of this cassette. The only way is to change both, the name and folder to a new name and new folder, and this is not editing anymore it is adding.
					if (selected_TreeLevel.equals("4") && !tmp.equals("sheekhLevel") && !tmp.equals("bookLevel") && !tmp.equals("subBookLevel"))
					{
						alertError("لا يمكن تحرير الشريط من كتاب فرعي في السلسلة", "خطأ", "إلغاء", null, AlertType.ERROR);
						return;
					}

					final Dialog editDialog = new Dialog();
					editDialog.setResizable(false);
					editDialog.initOwner(primaryStage);
					editDialog.setTitle("تحرير قائمة المشايخ والكتب");

					final BorderPane audioPanel = new BorderPane();
					audioPanel.setLeft(new Label(" عنوان الشريط أو اسم الكتاب"));

					final TextField audioTextField = new TextField();
					audioPanel.setRight(audioTextField);

					final BorderPane bookPanel = new BorderPane();
					bookPanel.setLeft(new Label(" اسم السلسلة أو الشرح"));

					final TextField bookTextField = new TextField();
					bookPanel.setRight(bookTextField);

					final BorderPane bookDirectoryPanel = new BorderPane();
					bookDirectoryPanel.setLeft(new Label(" اسم مجلد السلسلة"));

					final TextField bookDirectoryTextField = new TextField();
					bookDirectoryPanel.setRight(bookDirectoryTextField);

					final BorderPane subLevelBookDirectoryPanel = new BorderPane();
					subLevelBookDirectoryPanel.setLeft(new Label(" اسم مجلد الكتاب في السلسلة "));

					final TextField subLevelBookDirectoryTextField = new TextField();
					subLevelBookDirectoryTextField.setDisable(true);
					subLevelBookDirectoryPanel.setRight(subLevelBookDirectoryTextField);

					final ToggleButton subLevelBooksButton = new ToggleButton(null, new ImageView(new Image(cl.getResourceAsStream("images/folders.png"))));
					subLevelBooksButton.setTooltip(new Tooltip("اضغط على هذا الخيار إن كانت السلسلة مجزأة إلى كتب فرعية"));
					subLevelBookDirectoryPanel.setCenter(subLevelBooksButton);
					subLevelBooksButton.selectedProperty().addListener((obs, wasSelected, isNowSelected) ->
					{
						if (isNowSelected)
							subLevelBookDirectoryTextField.setDisable(false);
						else
							subLevelBookDirectoryTextField.setDisable(true);
					});

					final BorderPane scientistPanel = new BorderPane();
					scientistPanel.setLeft(new Label(" اسم الشيخ"));

					final TextField scientistTextField = new TextField();
					scientistPanel.setRight(scientistTextField);

					final BorderPane scientistShortNamePanel = new BorderPane();
					scientistShortNamePanel.setLeft(new Label(" اسم الشيخ مختصراً"));

					final TextField scientistShortNameTextField = new TextField();
					scientistShortNamePanel.setRight(scientistShortNameTextField);

					final BorderPane scientistDirectoryPanel = new BorderPane();
					scientistDirectoryPanel.setLeft(new Label(" اسم مجلد الشيخ"));

					final TextField scientistDirectoryTextField = new TextField();
					scientistDirectoryPanel.setRight(scientistDirectoryTextField);

					final Vector<String> originalInputs = new Vector<>();
					originalInputs.setSize(4); // [0]=scientistDirectoryTextField, [1]=audioTextField, [2]=subLevelBookDirectoryTextField, [3]=bookDirectoryTextField

					final ButtonType OKButtonType = new ButtonType("تحرير", ButtonBar.ButtonData.OK_DONE);
					editDialog.getDialogPane().getButtonTypes().add(OKButtonType);
					final Button OKButton = (Button) editDialog.getDialogPane().lookupButton(OKButtonType);
					OKButton.setOnAction(new EventHandler<ActionEvent>()
					{
						@Override
						public void handle(ActionEvent e)
						{
							if (scientistDirectoryTextField.getText().trim().isEmpty() || scientistTextField.getText().trim().isEmpty() || scientistShortNameTextField.getText().trim().isEmpty())
							{
								alertError("أحد المداخيل فارغ أو غير صحيح.", "خطأ", "إلغاء", null, AlertType.ERROR);
								return;
							}

							if (selected_TreeLevel.equals("2") && (bookTextField.getText().trim().isEmpty() || bookDirectoryTextField.getText().trim().isEmpty()))
							{
								alertError("أحد المداخيل فارغ أو غير صحيح.", "خطأ", "إلغاء", null, AlertType.ERROR);
								return;
							}

							if (selected_TreeLevel.equals("3"))
							{
								if (audioTextField.getText().trim().isEmpty())
								{
									alertError("أحد المداخيل فارغ أو غير صحيح.", "خطأ", "إلغاء", null, AlertType.ERROR);
									return;
								}

								if (!subLevelBookDirectoryTextField.isDisable() && subLevelBookDirectoryTextField.getText().isEmpty())
								{
									alertError("أحد المداخيل فارغ أو غير صحيح.", "خطأ", "إلغاء", null, AlertType.ERROR);
									return;
								}
							}

							if (selected_TreeLevel.equals("4") && audioTextField.getText().trim().isEmpty())
							{
								alertError("أحد المداخيل فارغ أو غير صحيح.", "خطأ", "إلغاء", null, AlertType.ERROR);
								return;
							}

							try
							{
								final Statement stmt = sharedDBConnection.createStatement();
								boolean cont = true;
								ResultSet rs;

								// Check if the sheekh or his folder already exists in Sheekh table.
								if (selected_TreeLevel.equals("1"))
								{
									rs = stmt.executeQuery("SELECT Sheekh_id FROM Sheekh WHERE Sheekh_name='" + scientistTextField.getText().trim() + "' AND Sheekh_id!=" + selected_Sheekh_id);
									if (rs.next())
									{
										cont = false;
										alertError("اسم الشيخ متواجد في قائمة المشايخ والكتب، لا يمكنك تكرار الاسم نفسه.", "تنبيه", "إلغاء", null, AlertType.ERROR);
									}
									else
									{
										// Check if the folder is for another sheekh
										rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Sheekh_id!=" + selected_Sheekh_id + " AND SUBSTR(Path, 1, " + (scientistDirectoryTextField.getText().trim().length() + 1) + ") = '" + scientistDirectoryTextField.getText().trim() + "\\'");
										if (rs.next())
										{
											cont = false;
											alertError("مجلد الشيخ مستخدم لشيخ آخر، لا يمكنك تكرار نفس المجلد.", "تنبيه", "إلغاء", null, AlertType.ERROR);
										}
									}
								}

								// Check if the book already exists in Book table.
								if (selected_TreeLevel.equals("2"))
								{
									rs = stmt.executeQuery("SELECT Book_id FROM Book WHERE Book_name='" + bookTextField.getText().trim() + "' AND Sheekh_id=" + selected_Sheekh_id + " AND Book_id!=" + selected_Book_id);
									if (rs.next())
									{
										cont = false;
										alertError("اسم السلسلة متواجد لنفس الشيخ، لا يمكنك تكرار الاسم نفسه.", "تنبيه", "إلغاء", null, AlertType.ERROR);
									}
									else
									{
										// Check if the folder is for another book for the same sheekh
										// Modified to avoid the multi-volume books folders issues.
										//rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Sheekh_id="+selected_Sheekh_id+" AND Book_id!="+selected_Book_id+" AND SUBSTR(Path, "+(scientistDirectoryTextField.getText().trim().length()+2)+") = '"+bookDirectoryTextField.getText().trim()+"'");
										rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Sheekh_id=" + selected_Sheekh_id + " AND Book_id!=" + selected_Book_id + " AND LOCATE('" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + "\\', Path || '\\') = 1"); // No need for WHERE Sheekh_id='...' since Path is checked from start but it might speed up the statement. TODO: change LOCATE with LIKE or any startWith function
										if (rs.next())
										{
											cont = false;
											alertError("مجلد السلسلة مستخدم لسلسلة أخرى لنفس الشيخ، لا يمكنك تكرار نفس المجلد.", "تنبيه", "إلغاء", null, AlertType.ERROR);
										}
									}
								}

								// Version 3.0
								if (selected_TreeLevel.equals("3"))
								{
									if (!audioTextField.getText().trim().equals(originalInputs.elementAt(1))) // OR String.valueOf(treePaths[0].getPathComponent(3)) OR selected_Book_name
									{
										rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Book_id=" + selected_Book_id + " AND Title='" + audioTextField.getText().trim() + "'");
										if (rs.next())
										{
											cont = false;
											alertError("اسم الكتاب في السلسلة موجود في قاعدة البيانات، لا يمكنك تكرار الاسم نفسه في نفس السلسلة لنفس الشيخ.", "تنبيه", "إلغاء", null, AlertType.ERROR);
										}
									}

									if (cont)
									{
										// Check in case of sub-book directory exists or not.
										if (!subLevelBookDirectoryTextField.isDisabled())
										{
											// Get the original folder to check, if there is a change we will Test it in the DB. Otherwise we should not.
                                            /*
                                            final ResultSet rs1 = stmt.executeQuery("SELECT Path FROM Chapters WHERE Book_id="+selected_Book_id+" AND Title='"+selected_Book_name+"'");
                                            rs1.next();

                                            final String subBookDirectory = rs1.getString("Path").split("\\\\")[2]; // Once reached, there should be a directory for sub-book
                                            */

											if (!subLevelBookDirectoryTextField.getText().trim().equals(originalInputs.elementAt(2))) // OR (subBookDirectory)
											{
												rs = stmt.executeQuery("SELECT Code FROM Chapters WHERE Book_id=" + selected_Book_id + " AND LOCATE('" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + '\\' + subLevelBookDirectoryTextField.getText().trim() + "\\', Path || '\\') = 1"); // No need for WHERE Book_id='...' since Path is checked from the start but it might speed up the statement.
												if (rs.next())
												{
													cont = false;
													alertError("مجلد الكتاب في السلسلة مستخدم لكتاب آخر لنفس السلسلة، لا يمكنك تكرار نفس المجلد.", "تنبيه", "إلغاء", null, AlertType.ERROR);
												}
											}
										}
									}
								}

								if (cont)
								{
									if (selected_TreeLevel.equals("1"))
									{
										stmt.executeUpdate("UPDATE Sheekh SET Sheekh_name='" + scientistTextField.getText().trim() + "' WHERE Sheekh_id=" + selected_Sheekh_id);
										stmt.executeUpdate("UPDATE Chapters SET Sheekh_name='" + scientistTextField.getText().trim() + "', Path='" + scientistDirectoryTextField.getText().trim() + "' || SUBSTR(Path, " + (originalInputs.elementAt(0).length() + 1) + ") WHERE Sheekh_id=" + selected_Sheekh_id);
										stmt.executeUpdate("UPDATE Book SET Short_sheekh_name='" + scientistShortNameTextField.getText().trim() + "' WHERE Sheekh_id=" + selected_Sheekh_id); // Version 2.8

										//defaultSearcher.getIndexReader().deleteDocuments(new Term("Sheekh_id", String.valueOf(selected_Sheekh_id)));
										//rootsSearcher.getIndexReader().deleteDocuments(new Term("Sheekh_id", String.valueOf(selected_Sheekh_id)));

										defaultWriter.deleteDocuments(new Term("Sheekh_id", String.valueOf(selected_Sheekh_id)));
										rootsWriter.deleteDocuments(new Term("Sheekh_id", String.valueOf(selected_Sheekh_id)));
										luceneWriter.deleteDocuments(new Term("Sheekh_id", String.valueOf(selected_Sheekh_id)));
									}

									if (selected_TreeLevel.equals("2"))
									{
										stmt.executeUpdate("UPDATE Book SET Book_name='" + bookTextField.getText().trim() + "' WHERE Book_id=" + selected_Book_id); // Version 2.7/2.8/3.0
										stmt.executeUpdate("UPDATE Chapters SET Book_name='" + bookTextField.getText().trim() + "', CDNumber='dummy', Path='" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + "' || (CASE WHEN (LOCATE('\\', Path, " + (originalInputs.elementAt(0).length() + 1) + ")>0) THEN SUBSTR(Path, " + (originalInputs.elementAt(0).length() + originalInputs.elementAt(3).length() + 2) + ") ELSE '' END) WHERE Book_id=" + selected_Book_id); // Version 3.0, only change the bookDirectory while leaving sub-book

										//defaultSearcher.getIndexReader().deleteDocuments(new Term("Book_id", String.valueOf(selected_Book_id)));
										//rootsSearcher.getIndexReader().deleteDocuments(new Term("Book_id", String.valueOf(selected_Book_id)));

										defaultWriter.deleteDocuments(new Term("Book_id", String.valueOf(selected_Book_id)));
										rootsWriter.deleteDocuments(new Term("Book_id", String.valueOf(selected_Book_id)));
										luceneWriter.deleteDocuments(new Term("Book_id", String.valueOf(selected_Book_id)));
									}

									if (selected_TreeLevel.equals("3"))
									{
										// Removed WHERE Sheekh_id = selected_Sheekh_id since Book_id is for one sheekh only
										// Add ' WHERE ... AND Title ='
										if (!subLevelBookDirectoryTextField.isDisabled()) // OR !subLevelBookDirectoryTextField.getText().isEmpty()
											stmt.executeUpdate("UPDATE Chapters SET Title='" + audioTextField.getText().trim() + "', Path='" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + '\\' + subLevelBookDirectoryTextField.getText().trim() + "' WHERE Book_id=" + selected_Book_id + " AND Title='" + selected_Book_name + "'");
										else
											stmt.executeUpdate("UPDATE Chapters SET Title='" + audioTextField.getText().trim() + "', Path='" + scientistDirectoryTextField.getText().trim() + '\\' + bookDirectoryTextField.getText().trim() + "' WHERE Book_id=" + selected_Book_id + " AND Title='" + selected_Book_name + "'");

										final QueryParser filterQueryParser = new QueryParser("", new KeywordAnalyzer());
										final Query query = filterQueryParser.parse("Book_id:" + selected_Book_id + " AND Title:\"" + selected_Book_name + '\"');
										//final QueryWrapperFilter deletingFilter = new QueryWrapperFilter(query);

										defaultWriter.deleteDocuments(query);
										rootsWriter.deleteDocuments(query);
										luceneWriter.deleteDocuments(query);

                                        /* Version 2.7, Removed
										int docid;
										final DocIdSetIterator docIdSetIterator = deletingFilter.getDocIdSet(defaultSearcher.getIndexReader()).iterator();
										while((docid = docIdSetIterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS)
											defaultSearcher.getIndexReader().deleteDocument(docid);

										final DocIdSetIterator rootsDocIdSetIterator = deletingFilter.getDocIdSet(rootsSearcher.getIndexReader()).iterator();
										while((docid = rootsDocIdSetIterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS)
											rootsSearcher.getIndexReader().deleteDocument(docid);
										*/
									}

									if (selected_TreeLevel.equals("4"))
									{
										stmt.executeUpdate("UPDATE Chapters SET Title='" + audioTextField.getText().trim() + "' WHERE Code=" + selected_Code);

										defaultWriter.deleteDocuments(new Term("Code", String.valueOf(selected_Code)));
										rootsWriter.deleteDocuments(new Term("Code", String.valueOf(selected_Code)));
										luceneWriter.deleteDocuments(new Term("Code", String.valueOf(selected_Code)));
									}

									defaultWriter.commit();
									rootsWriter.commit();
									luceneWriter.commit();
									reopenIndexSearcher();
									stmt.close();

									// To refresh detailList
									createNodes();
									orderButton.fire();
									editDialog.close();
								}
								stmt.close();
							}
							catch (Exception ex)
							{
								ex.printStackTrace();
							}
						}
					});

					final VBox editPanel = new VBox();
					editDialog.getDialogPane().setContent(editPanel);

					try
					{
						final Statement stmt = sharedDBConnection.createStatement();
						if (selected_TreeLevel.equals("1"))
						{
							editPanel.getChildren().add(scientistPanel);
							editPanel.getChildren().add(scientistShortNamePanel);
							editPanel.getChildren().add(scientistDirectoryPanel);

							final ResultSet rs1 = stmt.executeQuery("SELECT Path FROM Chapters WHERE Sheekh_id=" + selected_Sheekh_id);
							rs1.next();

							scientistTextField.setText(selected_Sheekh_name);
							scientistDirectoryTextField.setText(rs1.getString("Path").split("\\\\")[0]);

							final ResultSet rs2 = stmt.executeQuery("SELECT Short_sheekh_name FROM Book WHERE Sheekh_id=" + selected_Sheekh_id);
							rs2.next();

							scientistShortNameTextField.setText(rs2.getString("Short_sheekh_name"));
						}
						else
						{
							if (selected_TreeLevel.equals("2"))
							{
								scientistTextField.setEditable(false);
								scientistDirectoryTextField.setEditable(false);
								scientistShortNameTextField.setEditable(false);

								editPanel.getChildren().add(scientistPanel);
								editPanel.getChildren().add(scientistShortNamePanel);
								editPanel.getChildren().add(scientistDirectoryPanel);
								editPanel.getChildren().add(new Separator());

								editPanel.getChildren().add(bookPanel);
								editPanel.getChildren().add(bookDirectoryPanel);

								//final ResultSet rs = stmt.executeQuery("SELECT Path, Book_level FROM Chapters, Book WHERE Chapters.Book_id = "+selected_Book_id+" AND Chapters.Book_id = Book.Book_id");
								final ResultSet rs1 = stmt.executeQuery("SELECT Path FROM Chapters WHERE Book_id=" + selected_Book_id);
								rs1.next();

								final String path = rs1.getString("Path");
								scientistTextField.setText(selected_Sheekh_name);
								scientistDirectoryTextField.setText(path.split("\\\\")[0]);
								bookTextField.setText(selected_Book_name);
								bookDirectoryTextField.setText(path.split("\\\\")[1]);

								// Version 3.0, Changing the book status to/rom multi-volume is removed since there might be some files with the same names in different sub-folder and cannot be merged
								//if(rs.getInt("Book_level")==2)
								//if(!((DefaultMutableTreeNode)treePaths[0].getLastPathComponent()).getFirstChild().isLeaf())
								//	subLevelBooksButton.setSelected(true);

								final ResultSet rs2 = stmt.executeQuery("SELECT Short_sheekh_name FROM Book WHERE Book_id=" + selected_Book_id);
								rs2.next();

								scientistShortNameTextField.setText(rs2.getString("Short_sheekh_name"));
							}
							else
							{
								editPanel.getChildren().add(scientistPanel);
								editPanel.getChildren().add(scientistShortNamePanel);
								editPanel.getChildren().add(scientistDirectoryPanel);
								editPanel.getChildren().add(new Separator());

								editPanel.getChildren().add(bookPanel);
								editPanel.getChildren().add(bookDirectoryPanel);
								editPanel.getChildren().add(new Separator());

								editPanel.getChildren().add(audioPanel);
								editPanel.getChildren().add(subLevelBookDirectoryPanel);

								scientistTextField.setEditable(false);
								scientistDirectoryTextField.setEditable(false);
								scientistShortNameTextField.setEditable(false);
								bookTextField.setEditable(false);
								bookDirectoryTextField.setEditable(false);
								subLevelBooksButton.setDisable(true);

								if (selected_TreeLevel.equals("3"))
								{
									final ResultSet rs1 = stmt.executeQuery("SELECT Path, Book_name FROM Chapters WHERE Book_id=" + selected_Book_id + " AND Title='" + selected_Book_name + "'");
									rs1.next();

									final String path = rs1.getString("Path");

									scientistTextField.setText(selected_Sheekh_name);
									scientistDirectoryTextField.setText(path.split("\\\\")[0]);
									bookTextField.setText(rs1.getString("Book_name"));

									final String bookDirectory = path.substring(path.indexOf('\\') + 1);
									bookDirectoryTextField.setText(bookDirectory.split("\\\\")[0]);

									audioTextField.setText(selected_Book_name);

									final ResultSet rs2 = stmt.executeQuery("SELECT Short_sheekh_name FROM Book WHERE Book_id=" + selected_Book_id);
									rs2.next();

									scientistShortNameTextField.setText(rs2.getString("Short_sheekh_name"));

									if (bookDirectory.indexOf('\\') > -1)
									{
										subLevelBookDirectoryTextField.setDisable(false);
										subLevelBookDirectoryTextField.setText(bookDirectory.split("\\\\")[1]);
									}
								}
								else // i.e. selected_TreeLevel.equals("4") i.e. leaf node
								{
                                    /*
                                    Editing a leaf node (cassette) in case multi-volume book is
                                    disabled since changing the new name might be available for
                                    another sub-book, and in this case there will be merging
                                    between the two having the risk of a duplicate files in both.
                                    The same goes with editing the folder of this cassette.
                                    The only way is to change both, the name and folder to a new name and new folder,
                                    and this is not editing anymore it is adding.
                                      */
									// The same as above in selected_TreeLevel.equals("3") but in a different way without using the DB a lot.
									final TreeItem<AudioInfo> node = treePaths.get(0);
									final AudioInfo nodeInfo = node.getValue();

									scientistTextField.setText(node.getParent().getParent().getValue().toString()); // TODO, test this
									scientistDirectoryTextField.setText(nodeInfo.info2.split(com.sun.jna.Platform.isWindows() ? "\\\\" : "/")[0]); // "\\" or File.separator will not work in split for windows. it should be "\\\\"
									bookTextField.setText(node.getParent().getValue().toString()); // TODO, test this

									bookDirectoryTextField.setText(nodeInfo.info2.substring(nodeInfo.info2.indexOf(/*'\\'*/File.separator) + 1, nodeInfo.info2.lastIndexOf(/*'\\'*/File.separator)));

									audioTextField.setText(nodeInfo.info1.split("\\(")[0]);

									final ResultSet rs1 = stmt.executeQuery("SELECT Short_sheekh_name FROM Book WHERE Book_id=" + selected_Book_id);
									rs1.next();

									scientistShortNameTextField.setText(rs1.getString("Short_sheekh_name"));
								}
							}
						}
						stmt.close();
						originalInputs.setElementAt(scientistDirectoryTextField.getText(), 0);
						originalInputs.setElementAt(audioTextField.getText(), 1);
						originalInputs.setElementAt(subLevelBookDirectoryTextField.getText(), 2);
						originalInputs.setElementAt(bookDirectoryTextField.getText(), 3);
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
					}

					final ButtonType cancelButtonType = new ButtonType("إلغاء", ButtonBar.ButtonData.CANCEL_CLOSE);
					editDialog.getDialogPane().getButtonTypes().add(cancelButtonType);
					final Button cancelButton = (Button) editDialog.getDialogPane().lookupButton(cancelButtonType);
					cancelButton.setOnAction(new EventHandler<ActionEvent>()
					{
						@Override
						public void handle(ActionEvent e)
						{
							editDialog.close();
						}
					});

					scientistDirectoryTextField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
					bookDirectoryTextField.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

					editDialog.show();
				}
				else
					alertError("لا يمكن تحرير أكثر من فرع واحد (شيخ أو سلسلة أو شريط).", "خطأ", "إلغاء", null, AlertType.ERROR);
			}
		});

		// This button is used to delete one index in the details panel of any audio
		final MenuItem deleteMenuItem = new MenuItem("حذف");
		deleteMenuItem.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				// For initial case if nothing selected or no item is selected
				if (selected_Code == 0 || detailsSelectedIndex == -1)
					alertError("لم يتم تحديد ما يجب حذفه من فهرسة هذا الشريط.", "خطأ", "إلغاء", null, AlertType.ERROR);
				else
				{
					final ButtonType ok = new ButtonType("متابعة", ButtonBar.ButtonData.OK_DONE);
					final Alert alert = new Alert(AlertType.CONFIRMATION, "هل أنت متأكد من حذف هذه الفهرسة (رقم: " + (detailsSelectedIndex + 1) + ") من قائمة المسموعات؟", ok, new ButtonType("إلغاء", ButtonBar.ButtonData.CANCEL_CLOSE));
					alert.setTitle("تنبيه");
					alert.initOwner(primaryStage);
					alert.setHeaderText(null);
					if (alert.showAndWait().get() == ok)
					{
						try
						{
							final Statement stmt = sharedDBConnection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);

							// audioDetailsSeq instead of (detailsSelectedIndex+1)
							stmt.executeUpdate("DELETE FROM Contents WHERE Seq = " + audioDetailsSeq.elementAt(detailsSelectedIndex) + " AND Code = " + selected_Code);

							//stmt.executeUpdate("UPDATE Contents SET Seq = Seq-1 WHERE Seq > "+(detailsSelectedIndex+1)+" AND Code = "+selected_Code); // Version 2.0, Removed
							stmt.executeUpdate("DELETE FROM ContentCat WHERE Seq = " + audioDetailsSeq.elementAt(detailsSelectedIndex) + " AND Code = " + selected_Code);

							if (stmt.executeUpdate("DELETE FROM PlayList WHERE Seq = " + audioDetailsSeq.elementAt(detailsSelectedIndex) + " AND Code = " + selected_Code) != 0)
								jVLC.refreshPlayList();

							// Update the duration of the previous index.
							if (detailsSelectedIndex != 0)
							{
								if (detailsSelectedIndex == audioDetailsOffset.size() - 1) //i.e. The last element
								{
									// Calculate the duration of the cassette.
									if (audioDetailsDuration.elementAt(detailsSelectedIndex) == -1)
										stmt.executeUpdate("UPDATE Contents SET Duration = -1 WHERE Code = " + selected_Code + " AND Seq = " + audioDetailsSeq.elementAt(detailsSelectedIndex - 1)); // Version 2.0
									else
									{
										final int duration = audioDetailsDuration.elementAt(detailsSelectedIndex) + audioDetailsOffset.elementAt(detailsSelectedIndex) - audioDetailsOffset.elementAt(detailsSelectedIndex - 1);
										stmt.executeUpdate("UPDATE Contents SET Duration = " + (duration < 0 ? "-1" : duration) + " WHERE Code = " + selected_Code + " AND Seq = " + audioDetailsSeq.elementAt(detailsSelectedIndex - 1));
									}
								}
								else
								{
									final int duration = audioDetailsOffset.elementAt(detailsSelectedIndex + 1) - audioDetailsOffset.elementAt(detailsSelectedIndex - 1);
									stmt.executeUpdate("UPDATE Contents SET Duration = " + (duration < 0 ? "-1" : duration) + " WHERE Code = " + selected_Code + " AND Seq = " + audioDetailsSeq.elementAt(detailsSelectedIndex - 1)); // Version 2.0
								}
							}

							stmt.close();

							indexTextArea.clear();
							tafreegTextArea.clear();
							indexPanel.setText("عرض الفهرسة");

							// To remove the details list, updating area and search list to avoid selecting or updating deleted items.
							audioSearchList.getItems().clear();

							// Free The memory.
							searchResults.removeAllElements();
							searchPanel.setText("البحث");

							// To refresh detailList
							displayIndexes(selected_FileName);
						}
						catch (Exception ex)
						{
							ex.printStackTrace();
						}

						// To re-index the document again.
						//re_indexingOneDocument(selected_Code); // Version 2.7, Removed
						deleteOneDocument(selected_Code);
					}
				}
			}
		});

		final MenuItem deleteScientistMenuItem = new MenuItem("حذف");
		deleteScientistMenuItem.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				// We cannot use ObservableList since it listen for changes on tree selection. so if we delete multiple items, only the first one will be deleted since this list will be updated in the middle with the first selected path only
				final ObservableList<TreeItem<AudioInfo>> treePathsTemp = tree.getSelectionModel().getSelectedItems();
				final ArrayList<TreeItem<AudioInfo>> treePathsList = new ArrayList<>(treePathsTemp);

				if (!treePathsList.isEmpty())
				{
					String deletedItemsLabel = "";
					for (TreeItem<AudioInfo> element : treePathsList)
					{
						//if(String.valueOf(currentNode).equals(variable[58]))// variable[58] = rootTreeNode
						if (element == treeRootNode)
						{
							deletedItemsLabel = "";
							alertError("لا يمكنك حذف نقطة الأساس: قائمة المشايخ والكتب", "خطأ", "إلغاء", null, AlertType.ERROR);
							break;
						}
						else
						{
							final StringBuilder pathBuilder = new StringBuilder();
							for (TreeItem<AudioInfo> item = element; item != null && item.getParent() != null; )
							{
								pathBuilder.insert(0, item.getValue().toString());
								item = item.getParent();
								if (item != null && item.getParent() != null)
									pathBuilder.insert(0, " ← ");
							}

							deletedItemsLabel = deletedItemsLabel + ls + pathBuilder.toString();
						}
					}

					// Trying to delete Parent tree node i.e. base reference
					if (!deletedItemsLabel.isEmpty())
					{
						final ButtonType ok = new ButtonType("متابعة", ButtonBar.ButtonData.OK_DONE);
						final Alert alert = new Alert(AlertType.CONFIRMATION, "هل أنت متأكد من أنك تريد حذف: " + deletedItemsLabel, ok, new ButtonType("إلغاء", ButtonBar.ButtonData.CANCEL_CLOSE));
						alert.setTitle("تنبيه");
						alert.initOwner(primaryStage);
						alert.setHeaderText(null);

						// Version 1.8, Enable scrolling in JOptionPane when it exceeds screen limit using getOptionPaneScrollablePanel().
						if (alert.showAndWait().get() == ok)
						{
							final Dialog deleteProgressDialog = new Dialog();
							deleteProgressDialog.initOwner(primaryStage);
							deleteProgressDialog.setTitle("حذف البيانات");
							deleteProgressDialog.setHeaderText("   إجراء عملية الحذف من قاعدة البيانات والفهارس ...   ");
							deleteProgressDialog.setResizable(false);

							final ProgressBar deleteProgressBar = new ProgressBar();
							deleteProgressDialog.getDialogPane().setContent(deleteProgressBar);

							// Version 1.5, Make the delete process in a thread to not stuck the GUI.
							// Version 2.6, Thread instead of SwingWorker
							final Thread thread = new Thread()
							{
								public void run()
								{
									// Version 1.8, try{for()} instead of for(){try} for performance tunning (one DB connection).
									try
									{
										final Statement stmt = sharedDBConnection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
										for (final TreeItem<AudioInfo> element : treePathsList)
										{
											runAndWait(() ->
											{
												tree.getSelectionModel().clearSelection();
												tree.getSelectionModel().select(element);
											});

											/*
											 * Version 1.1
											 * change 'select sheekhid from sheekh WHERE sheekhname  ...' to 'select sheekhid from Chapters WHERE Speaker ...'
											 *
											 * Version 1.3
											 * Then it is changed using selected_TreeLevel.
											 */
											if (selected_TreeLevel.equals("1")) // i.e. Scientist level deletion
											{
												stmt.executeUpdate("DELETE FROM Sheekh WHERE Sheekh_id = " + selected_Sheekh_id);

												// Version 2.0, Removed since we are using in the new db primary/foreign keys with 'DELETE CASCADE ON UPDATE CASCADE'
												//stmt.executeUpdate("DELETE FROM Chapters WHERE Sheekh_id = " + selected_Sheekh_id);
												//stmt.executeUpdate("DELETE FROM Contents WHERE Sheekh_id = " + selected_Sheekh_id);

												// Version 1.8, Delete it from index as well.
												// Version 2.7, defaultWriter instead of defaultSearcher.getIndexReader()
												defaultWriter.deleteDocuments(new Term("Sheekh_id", String.valueOf(selected_Sheekh_id)));
												rootsWriter.deleteDocuments(new Term("Sheekh_id", String.valueOf(selected_Sheekh_id))); // Version 2.0
												luceneWriter.deleteDocuments(new Term("Sheekh_id", String.valueOf(selected_Sheekh_id))); // Version 2.7
											}
											else
											{
												/* Version 2.0, Removed
												DefaultMutableTreeNode currentNode = (DefaultMutableTreeNode)element.getLastPathComponent();
												final StringTokenizer tokens = new StringTokenizer(String.valueOf(currentNode), ")(");
												String deleteItem = "";
												String seq = "";

												// To find the chapter(number) of the audio clip
												if(tokens.hasMoreTokens())
												{
													deleteItem = tokens.nextToken();
													if(tokens.hasMoreTokens())
														seq = tokens.nextToken();
												}
												*/

												if (selected_TreeLevel.equals("2"))// i.e. Scientist Books level deletion.
												{
													//stmt.executeUpdate("DELETE FROM Chapters WHERE Book_id = " + selected_Book_id + " AND Sheekh_id = " + selected_Sheekh_id);
													stmt.executeUpdate("DELETE FROM Book WHERE Book_id = " + selected_Book_id);

													// Version 2.0, Removed since we are using in the new db primary/foreign keys with 'DELETE CASCADE ON UPDATE CASCADE'
													//stmt.executeUpdate("DELETE FROM Contents WHERE Book = '" + deleteItem + "' AND sheekhid = " + selected_Sheekh_id);

													/* Version 1.8, Delete it from index as well.
													 * Version 2.0, Removed
													final QueryParser filterQueryParser = new QueryParser("", new KeywordAnalyzer());
													final Query query = filterQueryParser.parse("Book_name:\""+selected_Book_name+"\" AND Sheekh_name:\""+selected_Sheekh_name+"\""); // Version 2.0
													final QueryWrapperFilter deletingFilter = new QueryWrapperFilter(query);
													final BitSet docNums = deletingFilter.bits(indexReader);
													for(int r=0; r<docNums.length(); r++)
												    {
												    	if(docNums.get(r)==true)
												    		indexReader.deleteDocument(r);
												    }
												    */

													// Version 2.7, defaultWriter instead of defaultSearcher.getIndexReader()
													defaultWriter.deleteDocuments(new Term("Book_id", String.valueOf(selected_Book_id))); // Version 2.0
													rootsWriter.deleteDocuments(new Term("Book_id", String.valueOf(selected_Book_id))); // Version 2.0
													luceneWriter.deleteDocuments(new Term("Book_id", String.valueOf(selected_Book_id))); // Version 2.7

													/*
													final Query query = MultiFieldQueryParser.parse(new String[]{"\""+deleteItem+"\"", "\""+selected_Sheekh_name+"\""}, new String[]{"Book", "Speaker"}, new BooleanClause.Occur[]{BooleanClause.Occur.SHOULD, BooleanClause.Occur.MUST}, new KeywordAnalyzer());
													final Query deleteQuery = query.rewrite(indexReader);
													final Set terms = new HashSet();
													deleteQuery.extractTerms(terms);

													final Iterator it = terms.iterator();
												    while (it.hasNext())
												    	indexReader.deleteDocuments((Term)it.next());
												    */
												}
												else
												{
													// Version 2.0, sub-Books level deletion.
													if (selected_TreeLevel.equals("3"))
													{
														//stmt.executeUpdate("DELETE FROM Chapters WHERE Book_id = "+selected_Book_id+" AND Sheekh_id = "+selected_Sheekh_id+" AND Title = '"+selected_Book_name+"'");
														stmt.executeUpdate("DELETE FROM Chapters WHERE Book_id = " + selected_Book_id + " AND Title = '" + selected_Book_name + "'");

														final QueryParser filterQueryParser = new QueryParser("", new KeywordAnalyzer());
														final Query query = filterQueryParser.parse("Book_id:" + selected_Book_id + " AND Title:\"" + selected_Book_name + '\"');

														// Version 2.7
														defaultWriter.deleteDocuments(query);
														rootsWriter.deleteDocuments(query);
														luceneWriter.deleteDocuments(query);

                                                        /*
                                                        final QueryWrapperFilter deletingFilter = new QueryWrapperFilter(query);

														int docid;
														final DocIdSetIterator docIdSetIterator = deletingFilter.getDocIdSet(defaultSearcher.getIndexReader()).iterator();
														while((docid = docIdSetIterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS)
															defaultSearcher.getIndexReader().deleteDocument(docid);

														final DocIdSetIterator rootsDocIdSetIterator = deletingFilter.getDocIdSet(rootsSearcher.getIndexReader()).iterator();
														while((docid = rootsDocIdSetIterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS)
															rootsSearcher.getIndexReader().deleteDocument(docid);
														*/
													}
													else// i.e. Scientist Books episods level deletion ... selected_TreeLevel.equals("") ... leafNode
													{
														stmt.executeUpdate("DELETE FROM Chapters WHERE Code=" + selected_Code);

														// Version 2.7, defaultWriter instead of defaultSearcher.getIndexReader()
														defaultWriter.deleteDocuments(new Term("Code", String.valueOf(selected_Code))); // Version 2.0
														rootsWriter.deleteDocuments(new Term("Code", String.valueOf(selected_Code))); // Version 2.0
														luceneWriter.deleteDocuments(new Term("Code", String.valueOf(selected_Code))); // Version 2.0
													}
												}

												// To check if there is still some entries for Sheekh/Book, otherwise delete them.
												final Statement stmt1 = sharedDBConnection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
												ResultSet rs = stmt.executeQuery("SELECT Book_id FROM Book");
												while (rs.next())
												{
													final ResultSet rs1 = stmt1.executeQuery("SELECT Code FROM Chapters WHERE Book_id=" + rs.getInt("Book_id"));

													if (!rs1.next())// i.e. the book is not empty
														stmt1.executeUpdate("DELETE FROM Book WHERE Book_id=" + rs.getInt("Book_id"));
												}

												rs = stmt.executeQuery("SELECT Sheekh_id FROM Sheekh");
												//stmt1 = sharedDBConnection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE); // Version 2.7, No need for this
												while (rs.next())
												{
													final ResultSet rs1 = stmt1.executeQuery("SELECT Book_id FROM Book WHERE Sheekh_id=" + rs.getInt("Sheekh_id"));

													// Version 2.7
													if (!rs1.next())// i.e. the Sheekh is not empty
														stmt1.executeUpdate("DELETE FROM Sheekh WHERE Sheekh_id=" + rs.getInt("Sheekh_id"));
												}
												stmt1.close();
											}

											selected_Code = 0;
											selected_Sheekh_id = 0;
											selected_Book_duration = 0;
											selected_Sheekh_name = "";
											selected_Book_name = "";
											selected_TreeLevel = "";
										}

										stmt.close();
										defaultWriter.commit();
										rootsWriter.commit();
										luceneWriter.commit();
										reopenIndexSearcher();
									}
									catch (Exception e)
									{
										e.printStackTrace();
									}

									Platform.runLater(() ->
									{
										// To refresh detailList
										createNodes();
										orderButton.fire();

										jVLC.stopButton.fire();
										jVLC.refreshPlayList();

										deleteProgressDialog.setResult(Boolean.TRUE); // hack for closing dialog, otherwise it will not close
										deleteProgressDialog.close();
									});
								}
							};
							thread.setName("MyThreads: deleteScientistMenuItem");
							thread.start();
							deleteProgressDialog.show();
						}
					}
				}
				else
					alertError("لم يتم تحديد ما يجب حذفه من قائمة المشايخ والكتب.", "خطأ", "إلغاء", null, AlertType.ERROR);
			}
		});

		// search the tree
		//final Set<TreeItem<AudioInfo>> tree_matches = new HashSet<>();
		//final Set<TreeItem<FeqhNodeInfo>> feqhtree_matches = new HashSet<>();
		final Vector<TreeItem<AudioInfo>> tree_matches = new Vector<>();
		final Vector<TreeItem<FeqhNodeInfo>> feqhtree_matches = new Vector<>();
		listSearchTextField = new TextField();
		searchDownButton = new Button(null, new ImageView(new Image(cl.getResourceAsStream("images/search_down.png"))));
		searchUpButton = new Button(null, new ImageView(new Image(cl.getResourceAsStream("images/search_up.png"))));
		searchUpButton.setDisable(true);
		searchDownButton.setDisable(true);
		listSearchTextField.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				tree_matches.clear();
				feqhtree_matches.clear();
				listSearchPosition = -1;

				if (listSearchTextField.getText().trim().length() != 0)
				{
					if (treeTabbedPane.getSelectionModel().getSelectedIndex() == 0)
						searchMatchingItems(treeRootNode, tree_matches, listSearchTextField.getText().trim());
					else
						feqhSearchMatchingItems(feqhRootNode, feqhtree_matches, listSearchTextField.getText().trim());

					if(tree_matches.size() != 0 || feqhtree_matches.size() != 0)
					{
						searchDownButton.setDisable(false);
						searchUpButton.setDisable(false);
						searchDownButton.fire();
					}
				}
				else
					alertError("لم تقم بإدخال ما تريد البحث عنه في قائمة المشايخ والكتب أو التصانيف الفقهية.", "خطأ", "إلغاء", null, AlertType.ERROR);
			}

			// find all tree items whose value is a multiple of the search value:
			private void searchMatchingItems(final TreeItem<AudioInfo> searchNode, final Vector<TreeItem<AudioInfo>> matches, final String searchValue)
			{
				if (searchNode.toString().contains(searchValue))
					matches.add(searchNode);

				for (final TreeItem<AudioInfo> child : searchNode.getChildren())
					searchMatchingItems(child, matches, searchValue);
			}

			private void feqhSearchMatchingItems(final TreeItem<FeqhNodeInfo> searchNode, final Vector<TreeItem<FeqhNodeInfo>> matches, final String searchValue)
			{
				if (searchNode.toString().contains(searchValue))
					matches.add(searchNode);

				for (final TreeItem<FeqhNodeInfo> child : searchNode.getChildren())
					feqhSearchMatchingItems(child, matches, searchValue);
			}
		});

		listSearchTextField.setOnKeyPressed((KeyEvent event) ->
		{
			if (event.getCode() != KeyCode.ENTER)
			{
				searchDownButton.setDisable(true);
				searchUpButton.setDisable(true);
			}
		});

		searchDownButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				if (treeTabbedPane.getSelectionModel().getSelectedIndex() == 0)
				{
					if (tree_matches.size() > (listSearchPosition + 1))
					{
						final TreeItem<AudioInfo> node = tree_matches.get(++listSearchPosition);

						tree.getSelectionModel().clearSelection();
						tree.getSelectionModel().select(node);

						final int row = tree.getRow(node);
						tree.scrollTo(row);
					}
				}
				else
				{
					if (feqhtree_matches.size() > (listSearchPosition + 1))
					{
						final TreeItem<FeqhNodeInfo> node = feqhtree_matches.get(++listSearchPosition);

						feqhTree.getSelectionModel().clearSelection();
						feqhTree.getSelectionModel().select(node);

						final int row = feqhTree.getRow(node);
						feqhTree.scrollTo(row);
					}
				}
			}
		});

		searchUpButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				if (treeTabbedPane.getSelectionModel().getSelectedIndex() == 0)
				{
					if ((listSearchPosition - 1) >= 0)
					{
						final TreeItem<AudioInfo> node = tree_matches.get(--listSearchPosition);

						tree.getSelectionModel().clearSelection();
						tree.getSelectionModel().select(node);

						final int row = tree.getRow(node);
						tree.scrollTo(row);
					}
				}
				else
				{
					if ((listSearchPosition - 1) >= 0)
					{
						final TreeItem<FeqhNodeInfo> node = feqhtree_matches.get(--listSearchPosition);

						feqhTree.getSelectionModel().clearSelection();
						feqhTree.getSelectionModel().select(node);

						final int row = feqhTree.getRow(node);
						feqhTree.scrollTo(row);
					}
				}
			}
		});

		final MenuItem editMenuItem = new MenuItem("تحرير");
		editMenuItem.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				// For initial case if nothing selected
				if (selected_Code == 0 || detailsSelectedIndex == -1)
					alertError("لم يتم تحديد ما يجب تحديثه من قائمة المسموعات.", "خطأ", "إلغاء", null, AlertType.ERROR);
				else
				{
					final Dialog editDialog = new Dialog();
					editDialog.initOwner(primaryStage);
					editDialog.setTitle("تحرير الفهرسة");

					final TextArea editTextArea = new TextArea(audioDetailsLine.elementAt(detailsSelectedIndex));
					editTextArea.setWrapText(true);

					final TextArea editTafreegTextArea = new TextArea(audioDetailsTafreeg.elementAt(detailsSelectedIndex));
					editTafreegTextArea.setWrapText(true);

					final Tab editTab = new Tab("الفهرسة", editTextArea);
					final Tab editTafreegTab = new Tab("التفريغ", editTafreegTextArea);
					editTab.setClosable(false);
					editTafreegTab.setClosable(false);

					final TabPane indexTabbedPane = new TabPane();
					indexTabbedPane.setSide(Side.LEFT);
					indexTabbedPane.getTabs().addAll(editTab, editTafreegTab);

					final int hour = audioDetailsOffset.elementAt(detailsSelectedIndex) / 3600 / 1000;
					final int minute = audioDetailsOffset.elementAt(detailsSelectedIndex) / 60 / 1000 - (audioDetailsOffset.elementAt(detailsSelectedIndex) / 3600 / 1000) * 60;
					final float second = (float) audioDetailsOffset.elementAt(detailsSelectedIndex) / 1000 - ((int) ((float) audioDetailsOffset.elementAt(detailsSelectedIndex) / 60F / 1000F) * 60);

					final UnaryOperator<TextFormatter.Change> modifyChange = c -> {
						if (c.isContentChange())
						{
							final String text = c.getControlNewText();
							final int newLength = text.length();
							if (newLength > 2)
							{
								ALERT_AUDIOCLIP.play();
								return null;
							}

							if (text.matches("[0-9]*"))
								return c;
							else
							{
								ALERT_AUDIOCLIP.play();
								return null;
							}
						}
						return c;
					};

					final TextField hourTextField = new TextField(String.valueOf(hour));
					final TextField minuteTextField = new TextField(String.valueOf(minute));
					hourTextField.setTextFormatter(new TextFormatter(modifyChange));
					minuteTextField.setTextFormatter(new TextFormatter(modifyChange));

					final TextField secondTextField = new TextField(String.format("%04.1f", second));

					hourTextField.setPrefColumnCount(2);
					minuteTextField.setPrefColumnCount(2);
					secondTextField.setPrefColumnCount(2);

					final DecimalFormat format = new DecimalFormat("0.0");
					secondTextField.setTextFormatter(new TextFormatter<>(c ->
					{
						// The same as hours/minutes but accept seconds with fraction of 1 digit
						final String text = c.getControlNewText();
						final int newLength = text.length();
						if (newLength > 4)
						{
							ALERT_AUDIOCLIP.play();
							return null;
						}

						if (text.isEmpty())
						{
							return c;
						}

						final ParsePosition parsePosition = new ParsePosition(0);
						final Number num = format.parse(text, parsePosition);

						if (num == null || parsePosition.getIndex() < text.length())
						{
							ALERT_AUDIOCLIP.play();
							return null;
						}
						else
						{
							return c;
						}
					}));

					final HBox timePanel = new HBox();
					timePanel.getChildren().addAll(secondTextField, new Label(":"), minuteTextField, new Label(":"), hourTextField);

					final Vector<Integer> Category_id = new Vector<>();
					final CheckBoxTreeItem<FeqhNodeInfo> categorizeRootNode = new CheckBoxTreeItem<>(new FeqhNodeInfo("التصانيف الفقهية", -1));
					categorizeRootNode.setIndependent(true);

					try
					{
						final Statement stmt = sharedDBConnection.createStatement();
						ResultSet rs = stmt.executeQuery("SELECT * FROM Category");
						while (rs.next())
						{
							final int Category_parent = rs.getInt("Category_parent");
							if (Category_parent == 0)
								categorizeRootNode.getChildren().add(new CheckBoxTreeItem<>(new FeqhNodeInfo(rs.getString("Category_name"), rs.getInt("Category_id"))));
							else
							{
								final CheckTreeIterator<FeqhNodeInfo> iterator = new CheckTreeIterator<>(categorizeRootNode);
								while (iterator.hasNext())
								{
									final CheckBoxTreeItem<FeqhNodeInfo> node = iterator.next();
									final FeqhNodeInfo nodeInfo = node.getValue();
									if (Category_parent == nodeInfo.Category_id)
									{
										node.getChildren().add(new CheckBoxTreeItem<>(new FeqhNodeInfo(rs.getString("Category_name"), rs.getInt("Category_id"))));
										break;
									}
								}
							}
						}

						rs = stmt.executeQuery("SELECT Category_id FROM ContentCat WHERE Code = " + selected_Code + " AND Seq = " + audioDetailsSeq.elementAt(detailsSelectedIndex));
						while (rs.next())
							Category_id.addElement(rs.getInt("Category_id"));
						rs.close();
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
					}

					final TreeView<FeqhNodeInfo> categorizeTree = new TreeView<>(categorizeRootNode);
					categorizeTree.setShowRoot(false);
					categorizeTree.setCellFactory(CheckBoxTreeCell.<FeqhNodeInfo>forTreeView());

					final Stage categorizeDialog = new Stage();
					categorizeDialog.initOwner(editDialog.getOwner());
					categorizeDialog.setTitle("شجرة التصانيف");

					final TextArea pathTextArea = new TextArea();
					pathTextArea.setEditable(false);
					pathTextArea.setPrefRowCount(4);

					final TitledPane pathPanel = new TitledPane();
					pathPanel.setText("التصانيف الفقهية التي تندرج تحتها هذه الفهرسة");
					pathPanel.setCollapsible(false);
					pathPanel.setContent(pathTextArea);

					// Selecting the categories that are related to this index.
					final CheckTreeIterator<FeqhNodeInfo> iterator = new CheckTreeIterator<>(categorizeRootNode);
					while (iterator.hasNext())
					{
						final CheckBoxTreeItem<FeqhNodeInfo> node = iterator.next();
						node.setIndependent(true);
						if (Category_id.contains(node.getValue().Category_id))
						{
							node.setSelected(true);

							final StringBuilder pathBuilder = new StringBuilder();
							for (TreeItem<FeqhNodeInfo> item = node; item != null && item.getParent() != null; )
							{
								pathBuilder.insert(0, item.getValue());
								item = item.getParent();
								if (item != null && item.getParent() != null)
									pathBuilder.insert(0, " ← ");
							}
							pathTextArea.appendText(pathBuilder.toString() + ls);
						}
					}

					final BorderPane decorePanel = new BorderPane();
					decorePanel.setPrefSize(350, 400);
					decorePanel.setCenter(categorizeTree);
					decorePanel.setTop(pathPanel);

					final Scene searchScene = new Scene(decorePanel);
					searchScene.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

					categorizeDialog.setScene(searchScene);
					categorizeDialog.sizeToScene();

					final Button categorizeButton = new Button("تصنيف", new ImageView(new Image(cl.getResourceAsStream("images/categorize.png"))));
					categorizeButton.setOnAction(new EventHandler<ActionEvent>()
					{
						@Override
						public void handle(ActionEvent e)
						{
							categorizeDialog.show();
						}
					});

					final ButtonType OKButtonType = new ButtonType("تحرير", ButtonBar.ButtonData.OK_DONE);
					editDialog.getDialogPane().getButtonTypes().add(OKButtonType);
					final Button OKButton = (Button) editDialog.getDialogPane().lookupButton(OKButtonType);
					OKButton.setOnAction(new EventHandler<ActionEvent>()
					{
						@Override
						public void handle(ActionEvent e)
						{
							if (hourTextField.getText().isEmpty() || secondTextField.getText().isEmpty() || minuteTextField.getText().isEmpty())
							{
								alertError("أحد مداخيل التوقيت فارغ فقم بإملائه.", "تنبيه", "إلغاء", null, AlertType.ERROR);
								return;
							}

							boolean cont = true;
							final int hour = Integer.parseInt(hourTextField.getText());
							final float second = Float.parseFloat(secondTextField.getText());
							final int minute = Integer.parseInt(minuteTextField.getText());
							if (second > 59.9 || minute > 59)
							{
								cont = false;
								alertError("أدخل قيمة صحيحة لعدد الثواني والدقائق (0 - 59).", "خطأ", "إلغاء", null, AlertType.ERROR);
							}

							if (cont)
							{
								final int Offset = (hour * 3600 + minute * 60) * 1000 + (int) (second * 1000);

								// There is no null duration in updating indexes.
								int updatedCurrentIndexDuration = -1;
								int updatedPreviousIndexDuration = -1;

								// Version 1.6, Update the duration of the previous index.
								if (detailsSelectedIndex != 0) // i.e. Not the first element.
								{
									if (detailsSelectedIndex == audioDetailsOffset.size() - 1) // i.e. The last element.
									{
										if (Offset < audioDetailsOffset.elementAt(detailsSelectedIndex - 1) && audioDetailsOffset.elementAt(detailsSelectedIndex) >= audioDetailsOffset.elementAt(detailsSelectedIndex - 1))
										{
											alertError("بداية الفهرسة التي أدخلتها تقل عن الفهرسة السابقة.", "خطأ", "إلغاء", null, AlertType.ERROR);
											cont = false;
										}
										else
										{
											if (audioDetailsDuration.elementAt(detailsSelectedIndex) != -1)
											{
												updatedCurrentIndexDuration = audioDetailsDuration.elementAt(detailsSelectedIndex) - (Offset - audioDetailsOffset.elementAt(detailsSelectedIndex));
												if (updatedCurrentIndexDuration < 0)
												{
													alertError("بداية الفهرسة التي أدخلتها تتجاوز مدة الشريط.", "خطأ", "إلغاء", null, AlertType.ERROR);
													cont = false;
												}
											}

											updatedPreviousIndexDuration = Offset - audioDetailsOffset.elementAt(detailsSelectedIndex - 1);
										}
									}
									else
									{
										if (Offset > audioDetailsOffset.elementAt(detailsSelectedIndex + 1))
										{
											alertError("بداية الفهرسة التي أدخلتها تتجاوز الفهرسة التالية أو مدة الشريط.", "خطأ", "إلغاء", null, AlertType.ERROR);
											cont = false;
										}
										else
										{
											if (Offset < audioDetailsOffset.elementAt(detailsSelectedIndex - 1) && audioDetailsOffset.elementAt(detailsSelectedIndex) >= audioDetailsOffset.elementAt(detailsSelectedIndex - 1))
											{
												alertError("بداية الفهرسة التي أدخلتها تقل عن الفهرسة السابقة.", "خطأ", "إلغاء", null, AlertType.ERROR);
												cont = false;
											}
											else
											{
												updatedCurrentIndexDuration = audioDetailsOffset.elementAt(detailsSelectedIndex + 1) - Offset;
												updatedPreviousIndexDuration = Offset - audioDetailsOffset.elementAt(detailsSelectedIndex - 1);
											}
										}
									}
								}
								else
								{
									// Prevent error when there is only one item and need to be edited.
									if (detailsSelectedIndex == audioDetailsOffset.size() - 1) // i.e. The last element.
									{
										if (audioDetailsDuration.elementAt(detailsSelectedIndex) != -1)
										{
											updatedCurrentIndexDuration = audioDetailsDuration.elementAt(detailsSelectedIndex) - (Offset - audioDetailsOffset.elementAt(detailsSelectedIndex));
											if (updatedCurrentIndexDuration < 0)
											{
												alertError("بداية الفهرسة التي أدخلتها تتجاوز مدة الشريط.", "خطأ", "إلغاء", null, AlertType.ERROR);
												cont = false;
											}
										}
									}
									else
									{
										if (Offset > audioDetailsOffset.elementAt(detailsSelectedIndex + 1))
										{
											alertError("بداية الفهرسة التي أدخلتها تتجاوز الفهرسة التالية أو مدة الشريط.", "خطأ", "إلغاء", null, AlertType.ERROR);
											cont = false;
										}
										else
											updatedCurrentIndexDuration = audioDetailsOffset.elementAt(detailsSelectedIndex + 1) - Offset;
									}
								}

								if (cont)
								{
									try
									{
										final Statement stmt = sharedDBConnection.createStatement();
										final ResultSet rs = stmt.executeQuery("SELECT Sheekh_id, Book_id FROM Chapters WHERE Code = " + selected_Code);
										rs.next();
										final int Sheekh_id = rs.getInt("Sheekh_id");
										final int Book_id = rs.getInt("Book_id");

										//stmt.executeUpdate("UPDATE Contents SET Line = '"+editTextArea.getText()+"', Offset = "+Offset+", Duration = "+(updatedCurrentIndexDuration<0?"-1":updatedCurrentIndexDuration)+" WHERE Seq = "+audioDetailsSeq.elementAt(detailsSelectedIndex)+" AND Code = "+selected_Code); // Version 2.0, audioDetailsSeq instead of 'detailsSelectedIndex'

										// Instead of the above to solve the issue if Line containing '
										final PreparedStatement ps = sharedDBConnection.prepareStatement(derbyInUse?
												"UPDATE Contents SET Line = ?, Tafreeg = ?, Offset = " + Offset + ", Duration = " + (updatedCurrentIndexDuration < 0 ? "-1" : updatedCurrentIndexDuration) + " WHERE Seq = " + audioDetailsSeq.elementAt(detailsSelectedIndex) + " AND Code = " + selected_Code
												:"UPDATE Contents SET Line = ?, Tafreeg = ?, \"Offset\" = " + Offset + ", Duration = " + (updatedCurrentIndexDuration < 0 ? "-1" : updatedCurrentIndexDuration) + " WHERE Seq = " + audioDetailsSeq.elementAt(detailsSelectedIndex) + " AND Code = " + selected_Code);
										ps.setString(1, editTextArea.getText());
										ps.setCharacterStream(2, new StringReader(editTafreegTextArea.getText()));
										ps.execute();
										ps.close();

										if (detailsSelectedIndex != 0) // This will not have any effect if it is the first index.
											stmt.executeUpdate("UPDATE Contents SET Duration = " + (updatedPreviousIndexDuration < 0 ? "-1" : updatedPreviousIndexDuration) + " WHERE Seq = " + audioDetailsSeq.elementAt(detailsSelectedIndex - 1) + " AND Code = " + selected_Code);
										stmt.executeUpdate("DELETE FROM ContentCat WHERE Seq = " + audioDetailsSeq.elementAt(detailsSelectedIndex) + " AND Code = " + selected_Code);

										final java.util.List<CheckBoxTreeItem<FeqhNodeInfo>> checkedNodes = getSelectedNodes_Feqh(categorizeRootNode);
										if (!checkedNodes.isEmpty())
										{
											for (final CheckBoxTreeItem<FeqhNodeInfo> node : checkedNodes)
											{
												final FeqhNodeInfo nodeInfo = node.getValue();
												stmt.execute("INSERT INTO ContentCat VALUES (" + selected_Code + ", " + audioDetailsSeq.elementAt(detailsSelectedIndex) + ", " + nodeInfo.Category_id + ", " + Sheekh_id + ", " + Book_id + ')');
											}
										}

										stmt.close();
										indexTextArea.clear();
										tafreegTextArea.clear();
										indexPanel.setText("عرض الفهرسة");
										editDialog.close();

										// To refresh detailList
										displayIndexes(selected_FileName);
									}
									catch (Exception ex)
									{
										ex.printStackTrace();
									}

									// Version 2.0, To re-index the document again.
									//re_indexingOneDocument(selected_Code); // Version 2.7, Removed
									deleteOneDocument(selected_Code); // Version 2.7
								}
							}
						}
					});

					final ButtonType cancelButtonType = new ButtonType("إلغاء", ButtonBar.ButtonData.OK_DONE);
					editDialog.getDialogPane().getButtonTypes().add(cancelButtonType);
					final Button cancelButton = (Button) editDialog.getDialogPane().lookupButton(cancelButtonType);
					cancelButton.setOnAction(new EventHandler<ActionEvent>()
					{
						@Override
						public void handle(ActionEvent e)
						{
							editDialog.close();
						}
					});

					final VBox editPanel = new VBox();
					editPanel.getChildren().addAll(timePanel, categorizeButton);
					categorizeButton.prefWidthProperty().bind(editPanel.widthProperty());

					final BorderPane mainPanel = new BorderPane();
					mainPanel.setRight(editPanel);
					mainPanel.setCenter(indexTabbedPane);

					editDialog.getDialogPane().setContent(mainPanel);
					editDialog.show();
				}
			}
		});

		// The new feature is to let the user export his database to others.
		final MenuItem exportDatabaseMenuItem = new MenuItem("تصدير    ");
		exportDatabaseMenuItem.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				// Exporting more than one item at once is added as a new feature in this version.
				// Version 4.4, We cannot use ObservableList since it listen for changes on tree selection.
				final ObservableList<TreeItem<AudioInfo>> treePathsTemp = tree.getSelectionModel().getSelectedItems();
				final ArrayList<TreeItem<AudioInfo>> treePathsList = new ArrayList<>(treePathsTemp);
				if (treePathsList != null)
				{
					final Dialog exportDialog = new Dialog();
					exportDialog.setResizable(false);
					exportDialog.initOwner(primaryStage);
					exportDialog.setTitle("عنوان و تفاصيل قاعدة البيانات");

					final TextField titleTextField = new TextField();
					titleTextField.setPromptText("عنوان الجزء المصدر");

					final TextArea descriptionTextArea = new TextArea();
					descriptionTextArea.setPromptText(" التفاصيل");

					final ButtonType OKButtonType = new ButtonType("متابعة", ButtonBar.ButtonData.OK_DONE);
					exportDialog.getDialogPane().getButtonTypes().add(OKButtonType);
					final Button OKButton = (Button) exportDialog.getDialogPane().lookupButton(OKButtonType);

					final CheckBox exportWithAudiosCheckBox = new CheckBox("إرفاق الملفات الصوتية مع الفهارس");
					if (defaultMediaChoice == pathMedia.INTERNET) exportWithAudiosCheckBox.setDisable(true);
					final EventHandler<ActionEvent> OKActionListener = new EventHandler<ActionEvent>()
					{
						@Override
						public void handle(ActionEvent e)
						{
							if (titleTextField.getText().trim().isEmpty())
								alertError("قم بإدخال العنوان والتفاصيل لما سيتم تصديره من قاعدة البيانات.", "خطأ", "إلغاء", null, AlertType.ERROR);
							else
							{
								try
								{
									final Path tempDir = Files.createTempDirectory("audiocataloger");

									final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(tempDir + "/info"), StandardCharsets.UTF_8);
									out.write(titleTextField.getText().trim() + ls);
									out.write(db_version + ls); // Version 2.7, [1] instead of [0] to store the DB version and not system version !
									out.write(descriptionTextArea.getText());
									out.close();

									exportDialog.close();

									final FileChooser fc = new FileChooser();
									fc.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("ملفات تحديث قاعدة البيانات (ACDB)", "*.acdb"));
									fc.setTitle("حدد مكان واسم الملف المصدر");

									final File f = fc.showSaveDialog(primaryStage);

									if (f == null) // i.e. user cancel the save dialog
										return;

									// canWrite() is buggy for Windows:
									//http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4420020
									if (!f.canWrite() && !com.sun.jna.Platform.isWindows())
									{
										alertError("لا تملك الصلاحيات لحفظ الملف في هذا المجلد، قم باختيار غيره.", "خطأ", "إلغاء", null, AlertType.ERROR);
										return;
									}

									// canWrite() is not working in Windows. Work Around is followed as in:
									//http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6203387
									if (com.sun.jna.Platform.isWindows())
									{
										try
										{
											f.createNewFile();
											f.delete();
										}
										catch (Exception ex)
										{
											ex.printStackTrace();
											alertError("لا تملك الصلاحيات لحفظ الملف في هذا المجلد، قم باختيار غيره.", "خطأ", "إلغاء", null, AlertType.ERROR);
											return;
										}
									}

									final Stage progressWindow = new Stage();
									progressWindow.setResizable(false);
									progressWindow.initModality(Modality.NONE);
									progressWindow.initStyle(StageStyle.TRANSPARENT);
									progressWindow.initOwner(primaryStage);

									final ProgressIndicator progressBar = new ProgressIndicator();
									final Scene progressScene = new Scene(progressBar, 100, 100);
									progressScene.setFill(null); // transparent
									progressBar.setBackground(Background.EMPTY); // transparent
									progressWindow.setScene(progressScene);
									progressWindow.show();

									// Disable export and delete buttons when exporting the db.
									exportDatabaseMenuItem.setDisable(true);
									deleteScientistMenuItem.setDisable(true);
									deleteMenuItem.setDisable(true);

									updateMenuItem.setDisable(true);
									editMenuItem.setDisable(true);
									editScientistMenuItem.setDisable(true);
									indexingMenu.setDisable(true); // Even though exporting is not affecting the indexing but because both will disable delete, edit … and enable them after finishing the task, we cannot have both of them running at the same time since one will enable the delete, edit … even before the other one is still running.

									// Make the export process in a thread to not stuck the GUI.
									final Thread thread = new Thread()
									{
										public void run()
										{
											String pathName = f.toString();
											if (!(pathName.endsWith(".acdb") || pathName.endsWith(".ACDB")))
												pathName = pathName + ".acdb";

											boolean exportedAllDatabase = false, includeSubBooks = false;
											String exportChaptersSQL = "", exportContentsSQL = "", exportContentCatSQL, exportBookSQL;
											//String exportedBookOrTitle = "";

											try
											{
												for (final TreeItem<AudioInfo> element : treePathsList)
												{
													//if(String.valueOf(currentNode).equals(variable[58]))// variable[58] = rootTreeNode
													if (element == treeRootNode)
													{
														exportedAllDatabase = true;
														break;
													}

													runAndWait(() ->
													{
														tree.getSelectionModel().clearSelection();
														tree.getSelectionModel().select(element);
													});

													if (selected_TreeLevel.equals("1"))// i.e. Scientist level export
													{
														if (exportChaptersSQL.isEmpty())
															exportChaptersSQL = "Sheekh_id = " + selected_Sheekh_id;
														else
															exportChaptersSQL = exportChaptersSQL + " OR " + "Sheekh_id = " + selected_Sheekh_id;

														if (exportContentsSQL.isEmpty())
															exportContentsSQL = "Sheekh_id = " + selected_Sheekh_id;
														else
															exportContentsSQL = exportContentsSQL + " OR " + "Sheekh_id = " + selected_Sheekh_id;

														// Version 1.8, Removed in Version 1.9, exportChaptersSQL can be assigned (in multiple selection) with '(Title =' in prevoiuse selection.
														//exportContentsSQL = exportChaptersSQL;
													}
													else
													{
														if (selected_TreeLevel.equals("2"))// i.e. Scientist Books level export.
														{
															// Version 2.0, No need for 'Sheekh_id=selected_Sheekh_id' since there is only one Book_id for one Sheekh_id
															if (exportChaptersSQL.isEmpty())
																exportChaptersSQL = "Book_id = " + selected_Book_id;
															else
																exportChaptersSQL = exportChaptersSQL + " OR Book_id = " + selected_Book_id;

															if (exportContentsSQL.isEmpty())
																exportContentsSQL = "Book_id = " + selected_Book_id;
															else
																exportContentsSQL = exportContentsSQL + " OR Book_id = " + selected_Book_id;

															// Version 1.8, Removed in Version 1.9, exportChaptersSQL can be assigned (in multiple selection) with '(Title =' in prevoiuse selection.
															//exportContentsSQL = exportChaptersSQL;
														}
														else
														{
															if (selected_TreeLevel.equals("3"))
															{
																// Version 2.0, No need for 'Sheekh_id=selected_Sheekh_id' since there is only one Book_id for one Sheekh_id
																includeSubBooks = true;
																if (exportChaptersSQL.isEmpty())
																	exportChaptersSQL = "(Book_id = " + selected_Book_id + " AND Title = '" + selected_Book_name + "')";
																else
																	exportChaptersSQL = exportChaptersSQL + " OR (Book_id = " + selected_Book_id + " AND Title = '" + selected_Book_name + "')";
															}
															else // i.e. leafNode, Books episods level export ... selected_TreeLevel.equals("") but not "0" since it is checked at the begining.
															{
																if (exportChaptersSQL.isEmpty())
																	exportChaptersSQL = "Code = " + selected_Code; // Version 2.0, Instead of "(Title = '" + exportedBookOrTitle + "' AND FileName = '" + fileName + "' AND Sheekh_id = " + selected_Sheekh_id + ")"
																else
																	exportChaptersSQL = exportChaptersSQL + " OR Code = " + selected_Code;

																if (exportContentsSQL.isEmpty())
																	exportContentsSQL = "Code = " + selected_Code;
																else
																	exportContentsSQL = exportContentsSQL + " OR Code = " + selected_Code;
															}
														}
													}
												}

												if (includeSubBooks)
													// This will work in all cases but it will reduce the performance so it is used only for exporting sub-books (i.e. level "3")
													exportContentsSQL = "SELECT * FROM Contents WHERE Code IN (SELECT Code FROM Chapters WHERE " + exportChaptersSQL + ") ORDER BY Code, Seq";
												else
													// 'ORDER BY Code' is important since import depends on this.
													exportContentsSQL = "SELECT * FROM Contents WHERE " + exportContentsSQL + " ORDER BY Code, Seq";

												exportBookSQL = "SELECT * FROM Book WHERE Book_id IN (SELECT Book_id FROM Chapters WHERE " + exportChaptersSQL + " GROUP BY Book_id) ORDER BY Book_id";
												exportContentCatSQL = "SELECT * FROM ContentCat WHERE Code IN (SELECT Code FROM Chapters WHERE " + exportChaptersSQL + ") ORDER BY Code, Seq"; // 'ORDER BY Code' is important since import depends on this.
												exportChaptersSQL = "SELECT * FROM Chapters WHERE " + exportChaptersSQL + " ORDER BY Code"; // 'ORDER BY Code' is important since import depends on this.

												// Version 1.9
												PreparedStatement ps;

												// Trying to export Parent tree node (base reference) i.e. The whole database
												if (exportedAllDatabase)
												{
													if (derbyInUse)
														ps = sharedDBConnection.prepareStatement("CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY ('SELECT * FROM Contents ORDER BY Code, Seq', ?, 'ö', 'Ö', 'UTF-8')");
													else
														ps = sharedDBConnection.prepareStatement("CALL CSVWRITE(?, 'SELECT * FROM Contents ORDER BY Code, Seq', 'UTF-8', 'ö', 'Ö')");
													ps.setString(1, tempDir + "/Contents");
													ps.execute();

													if (derbyInUse)
														ps = sharedDBConnection.prepareStatement("CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY ('SELECT * FROM Chapters ORDER BY Code', ?, 'ö', 'Ö', 'UTF-8')");
													else
														ps = sharedDBConnection.prepareStatement("CALL CSVWRITE(?, 'SELECT * FROM Chapters ORDER BY Code', 'UTF-8', 'ö', 'Ö')");
													ps.setString(1, tempDir + "/Chapters");
													ps.execute();

													if (derbyInUse)
														ps = sharedDBConnection.prepareStatement("CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY ('SELECT * FROM ContentCat ORDER BY Code, Seq', ?, 'ö', 'Ö', 'UTF-8')");
													else
														ps = sharedDBConnection.prepareStatement("CALL CSVWRITE(?, 'SELECT * FROM ContentCat ORDER BY Code, Seq', 'UTF-8', 'ö', 'Ö')");
													ps.setString(1, tempDir + "/ContentCat");
													ps.execute();

													if (derbyInUse)
														ps = sharedDBConnection.prepareStatement("CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY ('SELECT * FROM Book ORDER BY Book_id', ?, 'ö', 'Ö', 'UTF-8')");
													else
														ps = sharedDBConnection.prepareStatement("CALL CSVWRITE(?, 'SELECT * FROM Book ORDER BY Book_id', 'UTF-8', 'ö', 'Ö')");
													ps.setString(1, tempDir + "/Book");
													ps.execute();
												}
												else
												{
													if (derbyInUse)
													{
														ps = sharedDBConnection.prepareStatement("CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY (?, ?, 'ö', 'Ö', 'UTF-8')");
														ps.setString(1, exportChaptersSQL); // Version 1.9
														ps.setString(2, tempDir + "/Chapters");
													}
													else
													{
														// Be aware that Two single quotes can be used to create a single quote inside a string i.e. ' -> ''   so you can use exportChaptersSQL.replaceAll("'", "''") in H2
														ps = sharedDBConnection.prepareStatement("CALL CSVWRITE(?, ?, 'UTF-8', 'ö', 'Ö')");
														ps.setString(1, tempDir + "/Chapters");
														ps.setString(2, exportChaptersSQL); // Version 1.9
													}
													ps.execute();

													if (derbyInUse)
													{
														ps = sharedDBConnection.prepareStatement("CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY (?, ?, 'ö', 'Ö', 'UTF-8')");
														ps.setString(1, exportContentsSQL);
														ps.setString(2, tempDir + "/Contents");
													}
													else
													{
														ps = sharedDBConnection.prepareStatement("CALL CSVWRITE(?, ?, 'UTF-8', 'ö', 'Ö')");
														ps.setString(1, tempDir + "/Contents");
														ps.setString(2, exportContentsSQL);
													}

													ps.execute();

													if (derbyInUse)
													{
														ps = sharedDBConnection.prepareStatement("CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY (?, ?, 'ö', 'Ö', 'UTF-8')");
														ps.setString(1, exportContentCatSQL);
														ps.setString(2, tempDir + "/ContentCat");
													}
													else
													{
														ps = sharedDBConnection.prepareStatement("CALL CSVWRITE(?, ?, 'UTF-8', 'ö', 'Ö')");
														ps.setString(1, tempDir + "/ContentCat");
														ps.setString(2, exportContentCatSQL);
													}
													ps.execute();

													if (derbyInUse)
													{
														ps = sharedDBConnection.prepareStatement("CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY (?, ?, 'ö', 'Ö', 'UTF-8')");
														ps.setString(1, exportBookSQL);
														ps.setString(2, tempDir + "/Book");
													}
													else
													{
														ps = sharedDBConnection.prepareStatement("CALL CSVWRITE(?, ?, 'UTF-8', 'ö', 'Ö')");
														ps.setString(1, tempDir + "/Book");
														ps.setString(2, exportBookSQL);
													}

													ps.execute();
												}

												// These are the files to be included in the ZIP file
												final String[] zipfilenames = new String[]{"Chapters", "Contents", "ContentCat", "Book"};

												// Create a buffer for reading the files
												byte[] buf = new byte[1024];
												int len;

												// Create the ZIP file
												final ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tempDir + "/exported_db"));

												// Compress the files
												for (final String zipfilename : zipfilenames)
												{
													final FileInputStream in = new FileInputStream(tempDir + "/" + zipfilename);

													// Add ZIP entry to output stream.
													out.putNextEntry(new ZipEntry(zipfilename));

													// Transfer bytes from the file to the ZIP file
													while ((len = in.read(buf)) > 0)
														out.write(buf, 0, len);

													// Complete the entry
													out.closeEntry();
													in.close();
												}

												// Complete the ZIP file
												out.close();

												// Tar all files
												final TarOutputStream tarFile = new TarOutputStream(new FileOutputStream(pathName), "UTF-8"); // Version 3.1, UTF-8 to avoid issues with Arabic folders/filenames
												tarFile.setLongFileMode(TarOutputStream.LONGFILE_GNU); // Version 3.0, for long file names
												TarEntry tarEntry = new TarEntry("info");
												tarEntry.setSize(new File(tempDir + "/info").length()); // To avoid the exception of '0' byte length TarEntry
												tarFile.putNextEntry(tarEntry);
												FileInputStream in = new FileInputStream(tempDir + "/info");
												while ((len = in.read(buf)) > 0) tarFile.write(buf, 0, len);
												tarFile.closeEntry();
												in.close();

												tarEntry = new TarEntry("exported_db");
												tarEntry.setSize(new File(tempDir + "/exported_db").length());
												tarFile.putNextEntry(tarEntry);
												in = new FileInputStream(tempDir + "/exported_db");
												while ((len = in.read(buf)) > 0) tarFile.write(buf, 0, len);
												tarFile.closeEntry();
												in.close();

												// Version 1.9, Export Audio files.
												if (exportWithAudiosCheckBox.isSelected())
												{
													final Statement stmt = sharedDBConnection.createStatement();
													final ResultSet rs = stmt.executeQuery(/* Version 2.1 */exportedAllDatabase ? "SELECT * FROM Chapters" : exportChaptersSQL);

													while (rs.next())
													{
														String path = rs.getString("Path") + '\\' + rs.getString("FileName") + '.' + rs.getString("FileType"); // Version 2.0
														if (!com.sun.jna.Platform.isWindows())
															path = path.replace('\\', '/');
														if (new File(choosedAudioPath + path).exists()) // Version 2.8, convertPath()
														{
															tarEntry = new TarEntry(path);
															tarEntry.setSize(new File(choosedAudioPath + path).length());
															tarFile.putNextEntry(tarEntry);
															in = new FileInputStream(choosedAudioPath + path);
															while ((len = in.read(buf)) > 0) tarFile.write(buf, 0, len);
															tarFile.closeEntry();
															in.close();
														}
													}
													stmt.close();
												}
												tarFile.close();
											}
											catch (Exception e)
											{
												e.printStackTrace();
											}

											/* Version 4.0, remove cleaning since the folder now is in system temp. OS will take care of it.
											try
											{
												// Clear the temp folder
												//final File deletedFiles [] = FileSystemView.getFileSystemView().getFiles(new File("temp/"), true); // Version 2.6, Removed since it is not working with NIO FileChannel. It is meant only to work with JFileChooser
												final File deletedFiles[] = new File(cl.getResource("temp/").getFile()).listFiles();
												for (File element : deletedFiles)
													element.delete();
												//if(!element.delete())
												// No need for it since we close all the connections in/out explicitly. Otherwise you need to use System.gc()
												// before deleting or use deleteOnExit(). but deleteOnExit() is not recommended since we need to wait to exit
												// and this may cause problems if we Update the DB before exit.
												//deleteOnExit();
											}
											catch (Exception e)
											{
												e.printStackTrace();
											}
											*/

											Platform.runLater(() ->
											{
												progressWindow.close();

												// Enable export and delete buttons when after exporting the db.
												exportDatabaseMenuItem.setDisable(false);
												deleteScientistMenuItem.setDisable(false);
												deleteMenuItem.setDisable(false);

												updateMenuItem.setDisable(false);
												editMenuItem.setDisable(false);
												editScientistMenuItem.setDisable(false);
												indexingMenu.setDisable(false);
											});
										}
									};
									thread.setName("MyThreads: exportDatabaseMenuItem");
									thread.start();
								}
								catch (Exception ex)
								{
									ex.printStackTrace();
								}
							}
						}
					};
					OKButton.setOnAction(OKActionListener);
					titleTextField.setOnAction(OKActionListener);

					BorderPane.setAlignment(exportWithAudiosCheckBox, Pos.CENTER_LEFT);

					final BorderPane mainPanel = new BorderPane();
					mainPanel.setBottom(exportWithAudiosCheckBox);
					mainPanel.setCenter(descriptionTextArea);
					mainPanel.setTop(titleTextField);
					exportDialog.getDialogPane().setContent(mainPanel);
					exportDialog.show();
				}
				else
					alertError("لم يتم تحديد ما يجب تصديره من قائمة المشايخ والكتب.", "خطأ", "إلغاء", null, AlertType.ERROR);
			}
		});

		final EventHandler<ActionEvent> IndexingActionListener = new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				final AtomicInteger indexProgressBarCounter = new AtomicInteger(0);

				final MenuItem source = ((MenuItem) e.getSource());

				// Enable specific indexing.
				// Version 4.4, We cannot use ObservableList since it listen for changes on tree selection.
				final ObservableList<TreeItem<AudioInfo>> treePathsTemp = tree.getSelectionModel().getSelectedItems();
				final ArrayList<TreeItem<AudioInfo>> treePathsList = new ArrayList<>(treePathsTemp);

				// AtomicBoolean instead of global boolean since we can declare it as final (for inner access by threads) and can be set.
				final AtomicBoolean stopIndexing = new AtomicBoolean();

				final Dialog indexProgressDialog = new Dialog();
				indexProgressDialog.setResizable(false);
				indexProgressDialog.initOwner(primaryStage);
				indexProgressDialog.setTitle(source.getText());
				indexProgressDialog.initModality(Modality.NONE);

				final ButtonType exitButtonType = new ButtonType("إيقاف", ButtonBar.ButtonData.CANCEL_CLOSE);
				indexProgressDialog.getDialogPane().getButtonTypes().add(exitButtonType);
				final Button exitButton = (Button) indexProgressDialog.getDialogPane().lookupButton(exitButtonType);
				exitButton.setOnAction(new EventHandler<ActionEvent>()
				{
					@Override
					public void handle(ActionEvent e)
					{
						stopIndexing.set(true);
					}
				});

				final ProgressBar indexProgressBar = new ProgressBar();
				indexProgressBar.setPrefSize(270, 22);

				final BorderPane indexProgressPanel = new BorderPane();
				indexProgressPanel.setCenter(indexProgressBar);
				indexProgressPanel.setBottom(exitButton);
				indexProgressDialog.getDialogPane().setContent(indexProgressPanel);

				((MenuItem) e.getSource()).setDisable(true);

				// writer should be local. global will prevent parallel indexing since the same writer will be assign many times causing duplicate indexing.
				final IndexWriter writer = e.getSource().equals(treePopupIndexingMenuItem) ? defaultWriter : (e.getSource().equals(treePopupRootsIndexingMenuItem) ? rootsWriter : luceneWriter);

				updateMenuItem.setDisable(true);
				exportDatabaseMenuItem.setDisable(true);
				deleteScientistMenuItem.setDisable(true);
				deleteMenuItem.setDisable(true);
				editMenuItem.setDisable(true);
				editScientistMenuItem.setDisable(true);

				final Thread thread = new Thread()
				{
					int indexingThreadCount = 0;

					public void run()
					{
						Statement stmt = null;
						try
						{
							//final Statement stmt = sharedDBConnection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
							//stmt.setFetchSize(9000);
							stmt = sharedDBConnection.createStatement();

							boolean indexingAllDatabase = false;
							String indexSQL = "";

							//if(treePaths != null) // No need for it since you cannot click indexing menuItem until there is a tree path selected.
							//{
							for (final TreeItem<AudioInfo> element : treePathsList)
							{
								//if(String.valueOf(currentNode).equals(variable[58]))// variable[58] = rootTreeNode
								if (element == treeRootNode)
								{
									indexingAllDatabase = true;
									break;
								}

								runAndWait(() ->
								{
									tree.getSelectionModel().clearSelection();
									tree.getSelectionModel().select(element);
								});

								if (selected_TreeLevel.equals("1"))// i.e. Scientist level export
								{
									writer.deleteDocuments(new Term("Sheekh_id", String.valueOf(selected_Sheekh_id)));

									/* Version 2.7, Replaced by above.
									// Version 2.2, Deleting index to make it possible to add to index and not to format it each time.
									if(source.getText().equals(variable[78])) // Version 2.3, Only delete from the index type that you re-indexing it.
										rootsSearcher.getIndexReader().deleteDocuments(new Term("Sheekh_id", String.valueOf(selected_Sheekh_id)));
									else
										defaultSearcher.getIndexReader().deleteDocuments(new Term("Sheekh_id", String.valueOf(selected_Sheekh_id)));
									*/

									if (indexSQL.isEmpty())
										indexSQL = "Chapters.Sheekh_id = " + selected_Sheekh_id;
									else
										indexSQL = indexSQL + " OR " + "Chapters.Sheekh_id = " + selected_Sheekh_id;
								}
								else
								{
									if (selected_TreeLevel.equals("2"))// i.e. Scientist Books level export.
									{
										writer.deleteDocuments(new Term("Book_id", String.valueOf(selected_Book_id)));

										/*
										if(source.getText().equals(variable[78]))
											rootsSearcher.getIndexReader().deleteDocuments(new Term("Book_id", String.valueOf(selected_Book_id)));
										else
											defaultSearcher.getIndexReader().deleteDocuments(new Term("Book_id", String.valueOf(selected_Book_id)));
										*/

										if (indexSQL.isEmpty())
											indexSQL = "Chapters.Book_id = " + selected_Book_id;
										else
											indexSQL = indexSQL + " OR Chapters.Book_id = " + selected_Book_id;
									}
									else
									{
										if (selected_TreeLevel.equals("3"))
										{
											final QueryParser filterQueryParser = new QueryParser("", new KeywordAnalyzer());
											final Query query = filterQueryParser.parse("Book_id:" + selected_Book_id + " AND Title:\"" + selected_Book_name + '\"');
											//final QueryWrapperFilter deletingFilter = new QueryWrapperFilter(query);

											writer.deleteDocuments(query);

											/*
											int docid;
											if(source.getText().equals(variable[78]))
											{
												final DocIdSetIterator rootsDocIdSetIterator = deletingFilter.getDocIdSet(rootsSearcher.getIndexReader()).iterator();
												while((docid = rootsDocIdSetIterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS)
													rootsSearcher.getIndexReader().deleteDocument(docid);
											}
											else
											{
												final DocIdSetIterator docIdSetIterator = deletingFilter.getDocIdSet(defaultSearcher.getIndexReader()).iterator();
												while((docid = docIdSetIterator.nextDoc()) != DocIdSetIterator.NO_MORE_DOCS)
													defaultSearcher.getIndexReader().deleteDocument(docid);
											}
											*/

											if (indexSQL.isEmpty())
												indexSQL = "(Chapters.Book_id = " + selected_Book_id + " AND Chapters.Title = '" + selected_Book_name + "')";
											else
												indexSQL = indexSQL + " OR (Chapters.Book_id = " + selected_Book_id + " AND Chapters.Title = '" + selected_Book_name + "')";
										}
										else // i.e. leafNode, Books episods level export ... selected_TreeLevel.equals("") but not "0" since it is checked at the begining.
										{
											writer.deleteDocuments(new Term("Code", String.valueOf(selected_Code)));

											/*
											if(source.getText().equals(variable[78]))
												rootsSearcher.getIndexReader().deleteDocuments(new Term("Code", String.valueOf(selected_Code)));
											else
												defaultSearcher.getIndexReader().deleteDocuments(new Term("Code", String.valueOf(selected_Code)));
											*/

											if (indexSQL.isEmpty())
												indexSQL = "Chapters.Code = " + selected_Code;
											else
												indexSQL = indexSQL + " OR Chapters.Code = " + selected_Code;
										}
									}
								}
							}
							/*
							}
							else
							{
								// No need for this anymore since it will not be the case because it will not be fired until button is enabled. but check before delete.
								if(JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(getContentPane(), variable[68], "تنبيه", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, new String[]{variable[59], variable[60]}, variable[59]))
									indexingAllDatabase = true;
								else
								{
									// Version 2.7, Check if there is another indxing thread, in this case updateMenuItem should remain disable.
									final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
									final ThreadInfo[] threadInfos = threads.getThreadInfo(threads.getAllThreadIds());
									for(ThreadInfo element : threadInfos)
										if(element.getThreadName().startsWith("MyThreads: IndexingActionListener"))
											indexingThreadCount++;

									// Version 2.6
									SwingUtilities.invokeLater(new Runnable(){public void run()
									{
										indexProgressDialog.dispose();

										if(source.getText().equals(variable[78]))
											treePopupRootsIndexingMenuItem.setEnabled(true);
										else
											treePopupIndexingMenuItem.setEnabled(true);
										if(indexingThreadCount==1)updateMenuItem.setEnabled(true); // Version 2.7
									}});
									return;
								}
							}
							*/
							writer.commit();

							final int indexProgressBarMax;

							ResultSet rs;
							if (indexingAllDatabase)
							{
								// 'count(Code)' instead of 'count(*)' to speed up the process and take advantage of indexes [Code is an index column] but Code should be NOT NULL.
								// 'count(*)' instead of 'count(Code)' since it is optimized by default for most DB engines.
								rs = stmt.executeQuery("SELECT COUNT(*) FROM Contents");
								rs.next();
								indexProgressBarMax = rs.getInt(1);

								// Modified to include Book table to get Short_sheekh_name instead of CDNumber.
								rs = stmt.executeQuery("SELECT Chapters.Sheekh_id, Chapters.Book_id, Chapters.Code, Book.Short_sheekh_name, Chapters.Book_name, Chapters.Title, Contents.Seq, Contents.Duration, Contents.Line, Contents.Tafreeg FROM Chapters, Contents, Book WHERE Chapters.Code = Contents.Code AND Chapters.Book_id = Book.Book_id");
							}
							else
							{
								// Moved here, Modified to include Book table to get Short_sheekh_name instead of CDNumber.
								String counterSQL = indexSQL;
								indexSQL = "SELECT Chapters.Sheekh_id, Chapters.Book_id, Chapters.Code, Book.Short_sheekh_name, Chapters.Book_name, Chapters.Title, Contents.Seq, Contents.Duration, Contents.Line, Contents.Tafreeg FROM Chapters, Contents, Book WHERE Chapters.Code = Contents.Code AND Chapters.Book_id = Book.Book_id AND (" + indexSQL + ')';

								// 'count(*)' instead of 'count(Chapters.Code)' since it is optimized by default for most DB engines.
								rs = stmt.executeQuery("SELECT COUNT(*) FROM Chapters, Contents WHERE Chapters.Code = Contents.Code AND (" + counterSQL + ')');
								rs.next();
								indexProgressBarMax = rs.getInt(1);
								rs = stmt.executeQuery(indexSQL);
							}

                            /* Version 2.7
							if(source.getText().equals(variable[78])) // Version 2.3, To enable search while indexing
								rootsSearcher.close();
							else
								defaultSearcher.close();
							*/

							//final IndexWriter defaultWriter = (source.getText().equals(variable[78]))?new IndexWriter(FSDirectory.open(new File("rootsIndex")), rootsAnalyzer, indexingAllDatabase?true:false, IndexWriter.MaxFieldLength.UNLIMITED):new IndexWriter(FSDirectory.open(new File("index")), analyzer, indexingAllDatabase?true:false, IndexWriter.MaxFieldLength.UNLIMITED);//false to add to the index, true to rebuild and remove the old
							//defaultWriter.setMaxFieldLength(1500);// No need for the default i.e. 10,000 (Integer.MAX_VALUE)
							//defaultWriter.setMergeFactor(100);
							//defaultWriter.setMaxMergeDocs(10000);
							//defaultWriter.setMaxBufferedDocs(1000);

							long startTime = System.nanoTime();

							indexProgressBarCounter.set(1);
							while (rs.next() && !stopIndexing.get())
							{
								final Document doc = new Document();

								// StringField.TYPE_STORED instead of "Field.Store.YES, Field.Indexed.NOT_ANALYZED_NO_NORMS", No Norms is better for performance and boosting is not important especially for Arabic
								// http://lists.deri.org/pipermail/siren/2010-May/000034.html
								// http://stackoverflow.com/questions/3640094/what-does-field-index-not-analyzed-no-norms-mean
								doc.add(new Field("Sheekh_id", rs.getString("Sheekh_id"), StringField.TYPE_STORED));
								doc.add(new Field("Book_id", rs.getString("Book_id"), StringField.TYPE_STORED));
								doc.add(new Field("Code", rs.getString("Code"), StringField.TYPE_STORED));
								doc.add(new Field("Book_name", rs.getString("Book_name"), StringField.TYPE_STORED)); // Version 1.8, Index.UN_TOKENIZED instead of Index.NO to allow search scope
								doc.add(new Field("Short_sheekh_name", rs.getString("Short_sheekh_name"), StringField.TYPE_STORED)); // Version 2.8, Short_sheekh_name instead of CDNumber
								doc.add(new Field("Title", rs.getString("Title"), StringField.TYPE_STORED));
								doc.add(new Field("Seq", rs.getString("Seq"), StringField.TYPE_STORED));
								doc.add(new Field("Duration", rs.getString("Duration"), StringField.TYPE_STORED));
								doc.add(new Field("Line", rs.getString("Line"), org.apache.lucene.document.TextField.TYPE_STORED/* TODO: Not speeding up the highlighter  , Field.TermVector.WITH_POSITIONS_OFFSETS*/)); // Field.Store.YES, Field.Index.ANALYZED
								doc.add(new Field("Tafreeg", DBString(rs.getCharacterStream("Tafreeg")), org.apache.lucene.document.TextField.TYPE_STORED)); // Version 4.4

								writer.addDocument(doc);
								Platform.runLater(() -> {
									indexProgressBar.setProgress((float) indexProgressBarCounter.incrementAndGet() / indexProgressBarMax);
								});
							}
							writer.commit();
							System.out.println("Time to index: " + ((double) (System.nanoTime() - startTime) / 1000000000.0));

							// Refresh the connection to take care of the updates
							reopenIndexSearcher();

                            /* Version 2.7, Removed since reopen all indexes is not costly
							if(source.getText().equals(variable[78]))
								rootsSearcher = new IndexSearcher(FSDirectory.open(new File("rootsIndex")), false);
							else
								defaultSearcher = new IndexSearcher(FSDirectory.open(new File("index")), false);
							*/

							stmt.close();
						}
						catch (OutOfMemoryError e)
						{
							e.printStackTrace();

							try
							{
								if (stmt != null) stmt.close();
								writer.rollback();
							}
							catch (Exception ex)
							{
								ex.printStackTrace();
							}

							alertError("لقد تم نفاد الذاكرة اللازمة لإتمام عملية البحث لكثرة النتائج، لإنجاح العملية قم بتحرير الملف startup.bat" + ls +
									"والموضوع تحت مجلد البرنامج كما يلي:" + ls +
									"- قم بتغيير المقطع من Xmx1024m إلى Xmx2048m أو أكثر إن احتيج إلى ذلك. إبدأ البرنامج بالضغط على startup.bat" + ls +
									"[قم بالتغيير في startup.sh لأنظمة Linux/MacOS]", "تنبيه", "متابعة", null, AlertType.ERROR);
							//if(JOptionPane.YES_OPTION == JOptionPane.showOptionDialog(getContentPane(), variable[124]+ls+variable[125]+ls+variable[126]+ls+variable[127]variable[124]+ls+variable[125]+ls+variable[126]+ls+variable[127], "تنبيه", JOptionPane.YES_NO_OPTION, JOptionPane.ERROR_MESSAGE, null, new String[]{variable[145], "متابعة"}, variable[145]))
							//    shutdown(); TODO: test and see if OutOfMemoryError will leave the app unstable in javafx as in swing, then decide to give only one option, either shutdown or continue
						}
						catch (Exception e)
						{
							e.printStackTrace();
						}

						// Version 2.7, Check if there is another indxing thread, in this case updateMenuItem should remain disable.
						final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
						final ThreadInfo[] threadInfos = threads.getThreadInfo(threads.getAllThreadIds());
						for (ThreadInfo element : threadInfos)
							if (element.getThreadName().startsWith("MyThreads: IndexingActionListener"))
								indexingThreadCount++;

						Platform.runLater(() ->
						{
							indexProgressDialog.hide();
							((MenuItem) e.getSource()).setDisable(false);
                            /*
							if(source.getText().equals(variable[79]))
                                treePopupIndexingMenuItem.setEnabled(true);
                            else
                                if(source.getText().equals(variable[78]))
								    treePopupRootsIndexingMenuItem.setEnabled(true);
							    else
								    treePopupLuceneIndexingMenuItem.setEnabled(true);
						    */

							if (indexingThreadCount == 1)
							{
								updateMenuItem.setDisable(false);
								exportDatabaseMenuItem.setDisable(false);
								deleteScientistMenuItem.setDisable(false);
								deleteMenuItem.setDisable(false);
								editMenuItem.setDisable(false);
								editScientistMenuItem.setDisable(false);
							}
						});
					}
				};
				thread.setName("MyThreads: IndexingActionListener");
				thread.start();
				indexProgressDialog.show();

				/*
				final IndexModifier indexModifier  = new IndexModifier("index", new KeywordAnalyzer(), false);

				for(int i=0; i<treePaths.length; i++)
				{
					tree.setSelectionPath(treePaths[i]);
					if(selected_TreeLevel.equals("1"))// i.e. Scientist level deletion
					{
						stmt.executeUpdate("SELECT FROM Contents WHERE sheekhid = " + selected_Sheekh_id);
						stmt.executeUpdate("DELETE FROM sheekh WHERE sheekhid = " + selected_Sheekh_id);
						stmt.executeUpdate("DELETE FROM Chapters WHERE sheekhid = " + selected_Sheekh_id);

						// Version 1.8, Delete it from index as well.
						indexModifier.deleteDocuments(new Term("Speaker", selected_Sheekh_name));
						indexModifier.optimize();
					}
					else
					{
						currentNode = (DefaultMutableTreeNode)(treePaths[i].getLastPathComponent());
						final StringTokenizer tokens = new StringTokenizer(String.valueOf(currentNode), ")(");
						String deleteItem = "";
						String seq = "";

						// To find the chapter(number) of the audio clip
						if(tokens.hasMoreTokens())
						{
							deleteItem = tokens.nextToken();
							if(tokens.hasMoreTokens())
								seq = tokens.nextToken();
						}

						if(selected_TreeLevel.equals("2"))// i.e. Scientist Books level deletion.
						{
							stmt.executeUpdate("DELETE FROM Contents WHERE Book = '" + deleteItem + "' AND sheekhid = " + selected_Sheekh_id);
							stmt.executeUpdate("DELETE FROM Chapters WHERE Book = '" + deleteItem + "' AND sheekhid = " + selected_Sheekh_id);
						}
						else// i.e. Scientist Books episods level deletion ... selected_TreeLevel.equals("") ... leafNode
						{
							stmt.executeUpdate("DELETE FROM Chapters WHERE Title = '" + deleteItem + "' AND Seq = '" + seq + "'");
							stmt.executeUpdate("DELETE FROM Contents WHERE Code = " + selected_Code);
						}
					}

					selected_Code = 0;
					selected_Sheekh_id = 0;
					selected_Book_duration = 0;
					selected_Sheekh_name = "";
					selected_Book_name = "";
					selected_TreeLevel = "";
				}

				stmt.close();
				con.close();
				indexModifier.close();
				*/
			}
		};
		treePopupRootsIndexingMenuItem.setOnAction(IndexingActionListener);
		treePopupIndexingMenuItem.setOnAction(IndexingActionListener);
		treePopupLuceneIndexingMenuItem.setOnAction(IndexingActionListener);

		// Order the feqhtree indexes according to duration
		final MenuItem orderMenuItem = new MenuItem("ترتيب حسب مدة الفهرسة");
		orderMenuItem.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				if (feqhTreeSelected) // This condition is not important since orderMenuItem will not be displayed unless feqhTreeSelected is true. But I put in case ...
				{
					final int size = audioDetailsDuration.size();
					final Vector<String> new_audioDetailsLine = new Vector<>(size);
					final Vector<String> new_audioDetailsTafreeg = new Vector<>(size); // Version 3.0
					final Vector<Integer> new_audioDetailsOffset = new Vector<>(size);
					final Vector<Integer> new_audioDetailsCode = new Vector<>(size);
					final Vector<Integer> new_audioDetailsDuration = new Vector<>(size);

					// Sorting
					for (int i = 0, index; i < size; i++)
					{
						index = audioDetailsDuration.indexOf(Collections.min(audioDetailsDuration));
						new_audioDetailsDuration.add(audioDetailsDuration.remove(index));
						new_audioDetailsLine.add(audioDetailsLine.remove(index));
						new_audioDetailsTafreeg.add(audioDetailsTafreeg.remove(index)); // Version 3.0
						new_audioDetailsOffset.add(audioDetailsOffset.remove(index));
						new_audioDetailsCode.add(audioDetailsCode.remove(index));
					}

					audioDetailsDuration.addAll(new_audioDetailsDuration);
					audioDetailsLine.addAll(new_audioDetailsLine);
					audioDetailsTafreeg.addAll(new_audioDetailsTafreeg);
					audioDetailsOffset.addAll(new_audioDetailsOffset);
					audioDetailsCode.addAll(new_audioDetailsCode);

					String LineDetailed;
					final Vector<String> list = new Vector<>();
					for (int i = 0; i < size; i++)
					{
						if (audioDetailsDuration.elementAt(i) == -1)
							LineDetailed = '(' + String.valueOf(i + 1) + ") [?] " + audioDetailsLine.elementAt(i);
						else
						{
							final int duration = audioDetailsDuration.elementAt(i);
							final String minute = String.valueOf(duration / 60 / 1000);
							final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
							LineDetailed = '(' + String.valueOf(i + 1) + ") [" + minute + ':' + second + "] " + audioDetailsLine.elementAt(i);
						}
						list.add(LineDetailed);
					}
					audioDetailsListModel.setAll(list);
				}
			}
		});

		// Version 2.4, add bookmark (audioDetailsPopupMenu) to the new JMPlayer/JVLC
		final MenuItem bookmarkMenuItem = new MenuItem("إلى المفضلة");
		bookmarkMenuItem.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				//if(detailsSelectedIndex!=-1) // No need, audioDetailsPopupMenu hides bookmarkMenuItem in all cases.
				try
				{
                    /*
					final Statement stmt = sharedDBConnection.createStatement();
					stmt.execute("INSERT INTO PlayList VALUES ("+selected_Code+", "+audioDetailsSeq.elementAt(detailsSelectedIndex)+", "+audioDetailsOffset.elementAt(detailsSelectedIndex)+", '"+audioDetailsLine.elementAt(detailsSelectedIndex)+"')");
					stmt.close();
					*/

					// Version 2.9, Instead of the above to solve the issue if Line containing '
					final PreparedStatement ps = sharedDBConnection.prepareStatement("INSERT INTO PlayList VALUES (" + selected_Code + ", " + audioDetailsSeq.elementAt(detailsSelectedIndex) + ", " + audioDetailsOffset.elementAt(detailsSelectedIndex) + ", ?)");
					ps.setString(1, audioDetailsLine.elementAt(detailsSelectedIndex));
					ps.execute();
					ps.close();
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}

				jVLC.refreshPlayList();
			}
		});

		// add bookmark (treePopupMenu) to the new JMPlayer
		final MenuItem bookmarkScientistMenuItem = new MenuItem("إلى المفضلة");
		bookmarkScientistMenuItem.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				final StringBuilder pathBuilder = new StringBuilder();
				for (TreeItem<AudioInfo> item = tree.getSelectionModel().getSelectedItem(); item != null && item.getParent() != null; )
				{
					pathBuilder.insert(0, item.getValue().toString());
					item = item.getParent();
					if (item != null && item.getParent() != null)
						pathBuilder.insert(0, " ← ");
				}
				final String line = pathBuilder.toString();

				try
				{
					final PreparedStatement ps = sharedDBConnection.prepareStatement("INSERT INTO PlayList VALUES (" + selected_Code + ", -1, 0, ?)");
					ps.setString(1, line);
					ps.execute();
				}
				catch (Exception ex)
				{
					ex.printStackTrace();
				}

				jVLC.refreshPlayList();
			}
		});

		tree.setOnKeyPressed(new EventHandler<KeyEvent>()
		{
			public void handle(KeyEvent e)
			{
				if (e.getCode() == KeyCode.F5) orderButton.fire();

				// We should check that all of these actions (delete, edit ... ) have internal check if it is allowed or not e.g. you cannot delete the root, this check should be there in deleteButton. Otherwise we should have the conditions here in same way treePopupMenu is displayed.
				if (e.getCode() == KeyCode.DELETE) deleteScientistMenuItem.fire();
				if (e.getCode() == KeyCode.F2) editScientistMenuItem.fire();
			}
		});

		feqhTree.setOnKeyPressed(new EventHandler<KeyEvent>()
		{
			public void handle(KeyEvent e)
			{
				if (e.getCode() == KeyCode.F5) orderButton.fire();
			}
		});

		// Popup menu for the scientists tree
		final ContextMenu treePopupMenu = new ContextMenu();
		tree.setContextMenu(treePopupMenu);
		tree.setOnMouseClicked(new EventHandler<MouseEvent>()
		{
			@Override
			public void handle(MouseEvent m)
			{
				// This function is reshaped after some changes done in the TreeSelectionListener.
				if (m.getClickCount() == 2 && leafNode)
				{
					if (rootNode)
						alertError("لا يمكن الاستماع إلى هذا، فهو ليس سلسلة لأحد المشايخ وإنما رأس الشجرة التي تتفرع منها قائمة المشايخ والكتب.", "تنبيه", "إلغاء", null, AlertType.ERROR);
					else
						player(choosedAudioPath + selected_FileName, 0, -1);
				}

				//Popup
				final ObservableList<TreeItem<AudioInfo>> paths = tree.getSelectionModel().getSelectedItems();
				treePopupMenu.getItems().clear();

				if (paths != null)
				{
					if (paths.size() > 1)
					{
						treePopupMenu.getItems().addAll(
								deleteScientistMenuItem,
								exportDatabaseMenuItem,
								indexingMenu);
					}
					else
					{
						if (rootNode)
						{
							if (leafNode)
								treePopupMenu.getItems().add(addScientistMenuItem);
							else
								treePopupMenu.getItems().addAll(
										addScientistMenuItem,
										exportDatabaseMenuItem,
										indexingMenu);
						}
						else
						{
							if (leafNode)
							{
								final TreeItem<AudioInfo> node = tree.getSelectionModel().getSelectedItem();

								// Disable editing a leaf node (cassette) in case multi-volume book
								if (node.getValue().info6.equals("volume"))
									treePopupMenu.getItems().addAll(
											deleteScientistMenuItem,
											exportDatabaseMenuItem,
											bookmarkScientistMenuItem,
											indexingMenu);
								else
									treePopupMenu.getItems().addAll(
											deleteScientistMenuItem,
											editScientistMenuItem,
											exportDatabaseMenuItem,
											bookmarkScientistMenuItem,
											indexingMenu);
							}
							else
							{
								treePopupMenu.getItems().addAll(
										deleteScientistMenuItem,
										addScientistMenuItem,
										editScientistMenuItem,
										exportDatabaseMenuItem,
										indexingMenu);
							}
						}
					}
				}
			}
		});

		final ContextMenu audioDetailsPopupMenu = new ContextMenu();
		audioDetailsList.setContextMenu(audioDetailsPopupMenu);
		audioDetailsList.setOnMouseClicked(new EventHandler<MouseEvent>()
		{
			@Override
			public void handle(MouseEvent m)
			{
				// (detailsSelectedIndex!=-1) is added to the condition to avoid problems when nothing displayed and being clicked.
				if (m.getClickCount() == 2 && detailsSelectedIndex != -1)
				{
					if (feqhTreeSelected)
						player(choosedAudioPath + selected_FileName, audioDetailsOffset.elementAt(detailsSelectedIndex), audioDetailsDuration.elementAt(detailsSelectedIndex) == -1 ? -1 : (audioDetailsDuration.elementAt(detailsSelectedIndex) + audioDetailsOffset.elementAt(detailsSelectedIndex))); // Version 2.4/2.8
					else
					{
						int nextOffset;
						if ((detailsSelectedIndex + 1) < audioDetailsOffset.size())
							nextOffset = audioDetailsOffset.elementAt(detailsSelectedIndex + 1);
						else
							// i.e. The last item in the indices.
							nextOffset = -1;

						/*
						 * This condition is made to make sure that when we have not-ordered index that both indeices
						 * should not be limited by a period. As a result it should be checked with previous index and
						 * the next index. The previous index is checked here where the next index is checked in the player
						 * function since we pass both current and next indices and it is easy to check there for this perpose.
						 */
						if (detailsSelectedIndex != 0)
							if (audioDetailsOffset.elementAt(detailsSelectedIndex) < audioDetailsOffset.elementAt(detailsSelectedIndex - 1))
								nextOffset = -1;

						player(choosedAudioPath + selected_FileName, audioDetailsOffset.elementAt(detailsSelectedIndex), nextOffset);
					}
				}

				// Popup
				audioDetailsPopupMenu.getItems().clear();
				if (feqhTreeSelected)
				{
					if (!audioDetailsCode.isEmpty())
					{
						final int selectedIndex = audioDetailsList.getSelectionModel().getSelectedIndex();
						if (selectedIndex != -1)
						{
							audioDetailsPopupMenu.getItems().addAll(
									orderMenuItem,
									bookmarkMenuItem);
						}
						else
						{
							audioDetailsPopupMenu.getItems().addAll(
									orderMenuItem);

						}
					}
				}
				else
				{
					if (selected_Code != 0)
					{
						final int selectedIndex = audioDetailsList.getSelectionModel().getSelectedIndex();
						if (selectedIndex != -1)
						{
							audioDetailsPopupMenu.getItems().addAll(
									addMenuItem,
									editMenuItem,
									deleteMenuItem,
									bookmarkMenuItem);
						}
						else
						{
							audioDetailsPopupMenu.getItems().addAll(
									addMenuItem);
						}
					}
				}
			}
		});

		final HBox hbox = new HBox();
		hbox.getChildren().addAll(searchDownButton, searchUpButton, orderButton);

		final BorderPane scientistSearchPanel = new BorderPane();
		scientistSearchPanel.setRight(hbox);
		scientistSearchPanel.setCenter(listSearchTextField);
		listPanelBox.setBottom(scientistSearchPanel);

		final GridPane decoratePanel1 = new GridPane();
		decoratePanel1.add(detailPanel, 0, 0);
		decoratePanel1.add(searchPanel, 1, 0);
		detailPanel.setPrefSize(screenSize.getWidth() / 2.0, screenSize.getHeight()); // TODO find better way
		searchPanel.setPrefSize(screenSize.getWidth() / 2.0, screenSize.getHeight());

		final BorderPane decoratePanel2 = new BorderPane();
		decoratePanel2.setTop(indexPanel);
		decoratePanel2.setCenter(decoratePanel1);

		final BorderPane root = new BorderPane();
		root.setTop(menuBar);
		root.setLeft(listPanel);
		root.setCenter(decoratePanel2);

		listPanel.setPadding(new Insets(2.0, 2.0, 2.0, 2.0));
		indexPanel.setPadding(new Insets(2.0, 2.0, 2.0, 0.0));
		detailPanel.setPadding(new Insets(0.0, 2.0, 2.0, 0.0));
		searchPanel.setPadding(new Insets(0.0, 2.0, 2.0, 0.0));

		final Scene scene = new Scene(root);
		scene.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

		stage.setTitle("المفهرس لمسموعات أهل العلم من أهل السنة والجماعة");
		stage.setScene(scene);
		stage.setOnCloseRequest(new EventHandler<WindowEvent>()
		{
			@Override
			public void handle(WindowEvent we)
			{
				shutdown();
				we.consume();
			}
		});

		stage.setMaximized(true);
		stage.getIcons().addAll(
				new javafx.scene.image.Image(cl.getResource("images/icon.png").toString()),
				new javafx.scene.image.Image(cl.getResource("images/icon_32.png").toString()),
				new javafx.scene.image.Image(cl.getResource("images/icon_64.png").toString()),
				new javafx.scene.image.Image(cl.getResource("images/icon_128.png").toString())
		);

		stage.show();

		if(goldenVersion)
		{
			indexSearch.setSelected(true);
			withHighlighter.setSelected(true);
			indexSearch.fire();
		}
		else
		{
			dbSearch.setSelected(true);
			dbSearch.fire();
		}

		// getPadding() will not work unless it is visible
		final Insets i = searchUpButton.getPadding();
		final double p = i.getTop();
		searchUpButton.setPadding(new Insets(p,0.0,p,0.0));
		searchDownButton.setPadding(new Insets(p,0.0,p,0.0));

		if(!jVLC.nativeVlcReady())
			alertError("برنامج VLC غير مثبت على نظامك، عليك بتنصيب النسخة الرسمية ليعمل مشغل الصوتيات الداخلي", "تنبيه", "متابعة", null, AlertType.ERROR);
	}

	void audioSearchListAction()
	{
		/*
		 * happens when the selection of the previous search and the new search delete all the previous
		 * one. Need to have exception to not load the first one directly -> make the condition for the whole block.
		 * In addition, displayIndexes is added to indicate the list that the searched item cames from.
		 */
		if (searchSelectedIndex != -1)
		{
			/*
			 * This code is used to connect all panels (i.e. search, tree and details panels) with each other
			 * i.e. when selecting a searched item, it will select the item itself from the detail panel
			 * and at the same time the title will be selected from the tree.
			 *
			 * searchResults contains only Seq,Code
			 */
			final String[] searchInfo = searchResults.elementAt(searchSelectedIndex).split("ö");

			/*
			 * Since the user can change the path after one click (selection), so there will be error
			 * since it will accept the new change in the choosedAudioPath because double-clicking will
			 * not trigger the selection again.
			 *
			 * As a results we will remove choosedAudioPath from the begining of audioSearchMouseSelectionListenerPath,
			 * and we will add it in the AudioSearchSharedMouseListenerHandler.
			 *
			 * Originally it was:
			 * audioSearchMouseSelectionListenerPath=choosedAudioPath + AudioPath + fileSeparator + AudioFileName;
			 *
			 * Version 2.0, Removed
			 */
			//audioSearchMouseSelectionListenerPath = AudioPath + fileSeparator + AudioFileName;

			// select tree path by code.
			selectTreeNode(Integer.parseInt(searchInfo[1]));

			Platform.runLater(() ->
			{
				//audioDetailsList.setSelectedIndex(Integer.parseInt(searchSeq)-1);
				audioDetailsList.getSelectionModel().select(audioDetailsSeq.indexOf(Integer.parseInt(searchInfo[0])));
				audioDetailsList.scrollTo(audioDetailsSeq.indexOf(Integer.parseInt(searchInfo[0])));
			});

			fireSearchListSelectionListener = true;
		}
	}

	class HistoryListCell extends ListCell<String>
	{
		final HBox hbox = new HBox();
		final Label label = new Label();
		final Pane pane = new Pane();
		final Button button = new Button();
		String lastItem;

		public HistoryListCell(PopupControl historyPopup)
		{
			super();

			button.setGraphic(new ImageView(new Image(cl.getResourceAsStream("images/delete.png"))));
			button.setPadding(Insets.EMPTY);
			button.setBackground(null);

			button.setFocusTraversable(false);
			label.setFocusTraversable(false);
			pane.setFocusTraversable(false);

			hbox.getChildren().addAll(label, pane, button);
			HBox.setHgrow(pane, Priority.ALWAYS);
			button.setOnAction(new EventHandler<ActionEvent>()
			{
				@Override
				public void handle(ActionEvent event)
				{
					int index = displayedHistoryListModel.indexOf(lastItem);
					if (index != -1)
					{
						historyListModel.remove(displayedHistoryListModel.get(index));
						displayedHistoryListModel.remove(index);

						if (displayedHistoryListModel.isEmpty())
							historyPopup.hide();
					}
				}
			});
		}

		@Override
		protected void updateItem(String item, boolean empty)
		{
			super.updateItem(item, empty);
			setText(null);  // No text in label of super class
			if (empty)
			{
				lastItem = null;
				setGraphic(null);
			}
			else
			{
				lastItem = item;
				label.setText(item != null ? item : "<null>");
				setGraphic(hbox);
			}
		}
	}

	private static TextFlow buildTextFlow(final String text)
	{
		final TextFlow tf = new TextFlow();
		final String[] t = text.split("ö");
		for (int i = 0; i < t.length; i++)
		{
			final Text normal = new Text(t[i++]);
			if (i < t.length)
			{
				final Text colored = new Text(t[i]);
				colored.setFill(Color.MAROON);
				tf.getChildren().addAll(normal, colored);
			}
			else
				tf.getChildren().add(normal);
		}
		tf.setPrefWidth(0); // 4.4, solve a lot of RTL rendering issues and exceptions
		return tf;
	}

	/*
	@Override
	public void stop()
	{
		shutdown(); // done in stage.setOnCloseRequest()
	}
	*/

	private List<CheckBoxTreeItem<AudioInfo>> getSelectedNodes(CheckBoxTreeItem<AudioInfo> root)
	{
		final List<CheckBoxTreeItem<AudioInfo>> ls = new ArrayList<>();
		isSelected(root, ls);
		return ls;
	}

	private void isSelected(CheckBoxTreeItem<AudioInfo> node, List<CheckBoxTreeItem<AudioInfo>> ls)
	{
		// TODO: isSelected() should be enough but there is a bug, that in some cases it will return true when isIndeterminate() is true as well.
		// e.g. select parent, then de-select one of the sub-nodes.
		// TODO: http://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8177861
		if (node.isSelected() && !node.isIndeterminate())
			ls.add(node);
		else
			for (TreeItem<AudioInfo> item : node.getChildren())
				isSelected((CheckBoxTreeItem<AudioInfo>) item, ls);
	}

	private List<CheckBoxTreeItem<FeqhNodeInfo>> getSelectedNodes_Feqh(CheckBoxTreeItem<FeqhNodeInfo> root)
	{
		final List<CheckBoxTreeItem<FeqhNodeInfo>> ls = new ArrayList<>();
		isSelected_Feqh(root, ls);
		return ls;
	}

	private void isSelected_Feqh(CheckBoxTreeItem<FeqhNodeInfo> node, List<CheckBoxTreeItem<FeqhNodeInfo>> ls)
	{
		// TODO: isSelected() should be enough but there is a bug, that in some cases it will return true when isIndeterminate() is true as well.
		// e.g. select parent, then de-select one of the sub-nodes.
		// TODO: http://bugs.java.com/bugdatabase/view_bug.do?bug_id=JDK-8177861
		if (node.isSelected() && !node.isIndeterminate())
			ls.add(node);
		else
			for (TreeItem<FeqhNodeInfo> item : node.getChildren())
				isSelected_Feqh((CheckBoxTreeItem<FeqhNodeInfo>) item, ls);
	}

	static void alertError(String message, String title, String buttonText, String exception, AlertType alertType)
	{
		// TODO: better resize of the textArea
		final Alert alert = new Alert(alertType, message, new ButtonType(buttonText, ButtonBar.ButtonData.OK_DONE));
		alert.setTitle(title);
		alert.setHeaderText(null);
		alert.initOwner(primaryStage);
		//alert.getDialogPane().setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

		if (exception != null)
		{
			final TextArea textArea = new TextArea(exception);
			textArea.setEditable(false);
			textArea.setWrapText(true);
			textArea.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
			alert.getDialogPane().setExpandableContent(textArea);
		}

		alert.showAndWait();
	}

	class AddIndex extends Dialog
	{
		AddIndex(int offset_in, boolean fromPlayer)
		{
			super();
			initOwner(primaryStage);

			/*
			 * In case adding the new index is done from MPlayer Dialog, the main Frame will not be blocked
			 * so that the user will have the ability to control the mplayer. On the other hand this results
			 * in many  unnecessary behavior e.g.:
			 *  1- AddIndex Dialog is not specific to the first running clip (at the time AddIndex is created). If
			 *     the user select another clip, it will just add to that clip. In case no selection (by for example
			 *     orderButton.click()), exception will be thrown to the user.
			 *  2- The user is notified from the beginning that he cannot add unless there is a running clip. On the
			 *     other hand once AddIndex is created he can stop the running clip or change it.
			 */
			if (fromPlayer) initModality(Modality.NONE);
			setTitle("أضف فهرسة");

			final int minHour = offset_in / 3600 / 1000;
			final int minMinute = offset_in / 60 / 1000 - (offset_in / 3600 / 1000) * 60;
			final float minSecond = (float) offset_in / 1000 - ((int) ((float) offset_in / 60F / 1000F) * 60); // Version 3.0

			final TextArea addTextArea = new TextArea();
			//addTextArea.setPrefRowCount(3);
			//addTextArea.setPrefColumnCount(50);
			addTextArea.setWrapText(true);

			final TextArea addTafreegTextArea = new TextArea();
			addTafreegTextArea.setWrapText(true);

			final Tab addTab = new Tab("الفهرسة", addTextArea);
			final Tab addTafreegTab = new Tab("التفريغ", addTafreegTextArea);
			addTab.setClosable(false);
			addTafreegTab.setClosable(false);

			final TabPane indexTabbedPane = new TabPane(addTab, addTafreegTab);
			indexTabbedPane.setSide(Side.LEFT);
			final UnaryOperator<TextFormatter.Change> modifyChange = c -> {
				if (c.isContentChange())
				{
					final String text = c.getControlNewText();
					final int newLength = text.length();
					if (newLength > 2)
					{
						ALERT_AUDIOCLIP.play();
						return null;
					}

					if (text.matches("[0-9]*"))
						return c;
					else
					{
						ALERT_AUDIOCLIP.play();
						return null;
					}
				}
				return c;
			};

			final TextField hourTextField = new TextField(String.valueOf(minHour));
			final TextField minuteTextField = new TextField(String.valueOf(minMinute));
			hourTextField.setTextFormatter(new TextFormatter(modifyChange));
			minuteTextField.setTextFormatter(new TextFormatter(modifyChange));

			final TextField secondTextField = new TextField(String.format("%04.1f", minSecond));

			hourTextField.setPrefColumnCount(2);
			minuteTextField.setPrefColumnCount(2);
			secondTextField.setPrefColumnCount(2);

			final DecimalFormat format = new DecimalFormat("0.0");
			secondTextField.setTextFormatter(new TextFormatter<>(c ->
			{
				// The same as hours/minutes but accept seconds with fraction of 1 digit
				final String text = c.getControlNewText();
				final int newLength = text.length();
				if (newLength > 4)
				{
					ALERT_AUDIOCLIP.play();
					return null;
				}

				if (text.isEmpty())
				{
					return c;
				}

				final ParsePosition parsePosition = new ParsePosition(0);
				final Number num = format.parse(text, parsePosition);

				if (num == null || parsePosition.getIndex() < text.length())
				{
					ALERT_AUDIOCLIP.play();
					return null;
				}
				else
					return c;
			}));

			final HBox timePanel = new HBox();
			timePanel.getChildren().addAll(secondTextField, new Label(":"), minuteTextField, new Label(":"), hourTextField);

			final CheckBoxTreeItem<FeqhNodeInfo> categorizeRootNode = new CheckBoxTreeItem<>(new FeqhNodeInfo("التصانيف الفقهية", -1));
			categorizeRootNode.setIndependent(true);

			try
			{
				final Statement stmt = sharedDBConnection.createStatement();
				final ResultSet rs = stmt.executeQuery("SELECT * FROM Category");
				while (rs.next())
				{
					final int Category_parent = rs.getInt("Category_parent");
					if (Category_parent == 0)
					{
						final CheckBoxTreeItem<FeqhNodeInfo> tmp = new CheckBoxTreeItem<>(new FeqhNodeInfo(rs.getString("Category_name"), rs.getInt("Category_id")));
						tmp.setIndependent(true);
						categorizeRootNode.getChildren().add(tmp);
					}
					else
					{
						final CheckTreeIterator<FeqhNodeInfo> iterator = new CheckTreeIterator<>(categorizeRootNode);
						while (iterator.hasNext())
						{
							final CheckBoxTreeItem<FeqhNodeInfo> node = iterator.next();
							final FeqhNodeInfo nodeInfo = node.getValue();
							if (Category_parent == nodeInfo.Category_id)
							{
								final CheckBoxTreeItem<FeqhNodeInfo> tmp = new CheckBoxTreeItem<>(new FeqhNodeInfo(rs.getString("Category_name"), rs.getInt("Category_id")));
								tmp.setIndependent(true);

								node.getChildren().add(tmp);
								break;
							}
						}
					}
				}
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}

			final TreeView<FeqhNodeInfo> categorizeTree = new TreeView<>(categorizeRootNode);
			categorizeTree.setCellFactory(CheckBoxTreeCell.<FeqhNodeInfo>forTreeView());
			categorizeTree.setShowRoot(false);
			/* No Need for it, we will just hide the root
			categorizeTree.setCellFactory(p -> new CheckBoxTreeCell<FeqhNodeInfo>()
			{
				@Override
				public void updateItem(FeqhNodeInfo item, boolean empty) {
					super.updateItem(item, empty);
					if (item != null)
					{
						setText(item.toString());
						if(item.Category_id==-1) // root
							setGraphic(null);
					}
				}
			});
			*/
			//categorizeTree.setToggleClickCount(1); TODO

			final Stage categorizeDialog = new Stage();
			categorizeDialog.initOwner(this.getOwner());
			categorizeDialog.setTitle("شجرة التصانيف");

			final Scene searchScene = new Scene(categorizeTree);
			searchScene.setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

			categorizeDialog.setScene(searchScene);
			categorizeDialog.sizeToScene();

			final Button categorizeButton = new Button("تصنيف", new ImageView(new Image(cl.getResourceAsStream("images/categorize.png"))));
			categorizeButton.setOnAction(new EventHandler<ActionEvent>()
			{
				@Override
				public void handle(ActionEvent e)
				{
					categorizeDialog.show();
				}
			});

			final ButtonType OKButtonType = new ButtonType("أضف", ButtonBar.ButtonData.OK_DONE);
			getDialogPane().getButtonTypes().add(OKButtonType);
			final Button OKButton = (Button) getDialogPane().lookupButton(OKButtonType);
			OKButton.setOnAction(new EventHandler<ActionEvent>()
			{
				@Override
				public void handle(ActionEvent e)
				{
					if (hourTextField.getText().isEmpty() || secondTextField.getText().isEmpty() || minuteTextField.getText().isEmpty())
					{
						alertError("أحد مداخيل التوقيت فارغ فقم بإملائه.", "تنبيه", "إلغاء", null, AlertType.ERROR);
						return;
					}

					final int hour = Integer.parseInt(hourTextField.getText());
					final float second = Float.parseFloat(secondTextField.getText());
					final int minute = Integer.parseInt(minuteTextField.getText());

					if (second > 59.9f || minute > 59)
					{
						alertError("أدخل قيمة صحيحة لعدد الثواني والدقائق (0 - 59).", "تنبيه", "إلغاء", null, AlertType.ERROR);
						return;
					}

					final int offset = (hour * 3600 + minute * 60) * 1000 + (int) (second * 1000);

					try
					{
						final Statement stmt = sharedDBConnection.createStatement(ResultSet.TYPE_SCROLL_SENSITIVE, ResultSet.CONCUR_UPDATABLE);
						final ResultSet rs = stmt.executeQuery("SELECT Sheekh_id, Book_id, Duration FROM Chapters WHERE Code = " + selected_Code);
						rs.next();
						final int fileDuration = rs.getInt("Duration");
						final int Sheekh_id = rs.getInt("Sheekh_id");
						final int Book_id = rs.getInt("Book_id");

						// Version 1.6, Check the limit of the offset that can be used.
						//if(fileDuration!=/*-1*/0) // Version 3.0, Removed
						if (fileDuration > 0) // Version 3.0
						{
							if (offset >= fileDuration)
							{
								alertError("بداية الفهرسة التي أدخلتها تتجاوز مدة الشريط.", "تنبيه", "إلغاء", null, AlertType.ERROR);
								stmt.close();
								return;
							}
						}

						int i = 0;
						for (; i < audioDetailsOffset.size(); i++)
							if (offset < audioDetailsOffset.elementAt(i))
								break;

						int addedDuration, addedSeq;
						if (audioDetailsOffset.isEmpty())
						{
							addedSeq = 1;
							addedDuration = (fileDuration == -1) ? -1 : (fileDuration - offset);
						}
						else if (i == audioDetailsOffset.size())
						{
							addedSeq = audioDetailsSeq.elementAt(i - 1) + 1; // It will be at the end of all indexes.
							addedDuration = (fileDuration == -1) ? -1 : (fileDuration - offset);
						}
						else
						{
							addedSeq = audioDetailsSeq.elementAt(i);
							addedDuration = audioDetailsOffset.elementAt(i) - offset;
						}

						stmt.executeUpdate("UPDATE Contents SET Seq = Seq+1 WHERE Seq >= " + addedSeq + " AND Code = " + selected_Code);
						//stmt.execute("INSERT INTO Contents VALUES ("+selected_Code+", "+Sheekh_id+", "+Book_id+", "+addedSeq+", "+offset+", "+addedDuration+", '"+addTextArea.getText()+"')");

						// Version 2.9, Instead of the above to solve the issue if Line containing '
						final PreparedStatement ps = sharedDBConnection.prepareStatement("INSERT INTO Contents VALUES (" + selected_Code + ", " + Sheekh_id + ", " + Book_id + ", " + addedSeq + ", " + offset + ", " + addedDuration + ", ?, ?)");
						ps.setString(1, addTextArea.getText());
						ps.setCharacterStream(2, new StringReader(addTafreegTextArea.getText()));
						ps.execute();

						// Version 1.6, Update the duration of the previous index.
						//final int previousOffset=(minHour*3600+minMinute*60+minSecond)*1000;
						if (i != 0)
							stmt.executeUpdate("UPDATE Contents SET Duration = " + (offset - audioDetailsOffset.elementAt(i - 1)) + " WHERE Code = " + selected_Code + " AND Seq = " + audioDetailsSeq.elementAt(i - 1));

						final java.util.List<CheckBoxTreeItem<FeqhNodeInfo>> checkedNodes = getSelectedNodes_Feqh(categorizeRootNode);
						if (!checkedNodes.isEmpty())
						{
							for (final CheckBoxTreeItem<FeqhNodeInfo> node : checkedNodes)
							{
								final FeqhNodeInfo nodeInfo = node.getValue();
								stmt.execute("INSERT INTO ContentCat VALUES (" + selected_Code + ", " + addedSeq + ", " + nodeInfo.Category_id + ", " + Sheekh_id + ", " + Book_id + ')');
							}
						}

						stmt.close();
						close();

						// To refresh detailList
						displayIndexes(selected_FileName);
					}
					catch (Exception ex)
					{
						ex.printStackTrace();
						alertError("حدث خطأ أثناء إضافة الفهرسة، يمكن أن يكون السبب هو عدم تحديد شريط معين من قوائم الكتب والسلاسل العلمية ليتم الإضافة عليه.", "تنبيه", "إلغاء", null, AlertType.ERROR);
					}
				}
			});

			final ButtonType cancelButtonType = new ButtonType("إلغاء", ButtonBar.ButtonData.OK_DONE);
			getDialogPane().getButtonTypes().add(cancelButtonType);
			final Button cancelButton = (Button) getDialogPane().lookupButton(cancelButtonType);
			cancelButton.setOnAction(new EventHandler<ActionEvent>()
			{
				@Override
				public void handle(ActionEvent e)
				{
					close();
				}
			});

			final VBox addPanel = new VBox();
			addPanel.getChildren().addAll(timePanel, categorizeButton);
			categorizeButton.prefWidthProperty().bind(addPanel.widthProperty());

			final BorderPane mainPanel = new BorderPane();
			mainPanel.setRight(addPanel);
			mainPanel.setCenter(indexTabbedPane);
			getDialogPane().setContent(mainPanel);

			show();
		}
	}

	void selectTreeNode(final int code)
	{
		final TreeIterator<AudioInfo> iterator = new TreeIterator<>(treeRootNode);
		while (iterator.hasNext())
		{
			final TreeItem<AudioInfo> node = iterator.next();
			if (node.isLeaf())
			{
				final AudioInfo nodeInfo = node.getValue();
				if (code == nodeInfo.info4)
				{
					if (feqhTreeSelected)
						treeTabbedPane.getSelectionModel().select(0);

					tree.getSelectionModel().clearSelection();
					tree.getSelectionModel().select(node);

					final int row = tree.getRow(node);
					tree.scrollTo(row);
					//tree.getSelectionModel().clearAndSelect(row); will not work unless it is visible

					break;
				}
			}
		}
	}

	private static void deleteOneDocument(final int Code)
	{
		final Thread thread = new Thread()
		{
			public void run()
			{
				try
				{
					defaultWriter.deleteDocuments(new Term("Code", String.valueOf(Code)));
					rootsWriter.deleteDocuments(new Term("Code", String.valueOf(Code)));
					luceneWriter.deleteDocuments(new Term("Code", String.valueOf(Code)));

					defaultWriter.commit();
					rootsWriter.commit();
					luceneWriter.commit();

					reopenIndexSearcher();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
			}
		};
		thread.start();
	}

    /* Version 2.7, Removed since we cannot know the number of deleted documents when using writer.deleteDocuments.
	// Version 2.0
	public void re_indexingOneDocument(final int Code)
	{
		// Version 2.6, Thread instead of SwingWorker
		final Thread thread = new Thread()
		{
			public void run()
			{
				try
				{
                    // Version 2.7
                    System.out.println("D1: "+defaultWriter.hasDeletions());
                    defaultWriter.deleteDocuments(new Term("Code", String.valueOf(Code)));
                    if(defaultWriter.hasDeletions()) // Not working as expected
                    {
                        defaultWriter.commit();
                        System.out.println("D2: "+defaultWriter.hasDeletions());
                        indexingOneDocument(Code, defaultWriter);
                    }
                    System.out.println("D3: "+defaultWriter.hasDeletions());

                    rootsWriter.deleteDocuments(new Term("Code", String.valueOf(Code)));
                    if(rootsWriter.hasDeletions())
                    {
                        rootsWriter.commit();
                        indexingOneDocument(Code, rootsWriter);
                    }

                    luceneWriter.deleteDocuments(new Term("Code", String.valueOf(Code)));
                    if(luceneWriter.hasDeletions())
                    {
                        luceneWriter.commit();
                        indexingOneDocument(Code, luceneWriter);
                    }

                    /*
					if(defaultSearcher.getIndexReader().deleteDocuments(new Term("Code", String.valueOf(Code)))>0)
					{
						defaultSearcher.close(); // To release the lock. We didn't use it in IndexingActionListener since we create the index from scratch, here we append.
						indexingOneDocument(Code, new IndexWriter(FSDirectory.open(new File("index")), analyzer, false, IndexWriter.MaxFieldLength.UNLIMITED)); //false to add to the index, true to rebuild and remove the old
					}

					if(rootsSearcher.getIndexReader().deleteDocuments(new Term("Code", String.valueOf(Code)))>0)
					{
						rootsSearcher.close();
						indexingOneDocument(Code, new IndexWriter(FSDirectory.open(new File("rootsIndex")), rootsAnalyzer, false, IndexWriter.MaxFieldLength.UNLIMITED));
					}
					*

					reopenIndexSearcher();
				}
				catch(Exception e){e.printStackTrace();}
			}
		};
		thread.setName("MyThreads: re_indexingOneDocument");
		thread.start();
	}

	public void indexingOneDocument(final int Code, final IndexWriter writer)
	{
		try
		{
			final Statement stmt = sharedDBConnection.createStatement();
			final ResultSet rs = stmt.executeQuery("SELECT Chapters.Sheekh_id, Chapters.Book_id, Chapters.Code, Chapters.Book_name, Chapters.Title, Contents.Seq, Contents.Duration, Contents.Line FROM Chapters, Contents WHERE Chapters.Code = "+Code+" AND Chapters.Code = Contents.Code");

			while(rs.next())
			{
				final Document doc = new Document();
				doc.add(new Field("Sheekh_id", rs.getString("Sheekh_id"), Field.Store.YES, Field.Index.NOT_ANALYZED));
				doc.add(new Field("Book_id", rs.getString("Book_id"), Field.Store.YES, Field.Index.NOT_ANALYZED));
				doc.add(new Field("Code", rs.getString("Code"), Field.Store.YES, Field.Index.NOT_ANALYZED));
				doc.add(new Field("Book_name", rs.getString("Book_name"), Field.Store.YES, Field.Index.NOT_ANALYZED)); // Version 1.8, Index.UN_TOKENIZED instead of Index.NO to allow search scope
				doc.add(new Field("Title" , rs.getString("Title"), Field.Store.YES, Field.Index.NOT_ANALYZED));
				doc.add(new Field("Seq", rs.getString("Seq"), Field.Store.YES, Field.Index.NOT_ANALYZED));
				doc.add(new Field("Duration", rs.getString("Duration"), Field.Store.YES, Field.Index.NOT_ANALYZED));
				doc.add(new Field("Line" , rs.getString("Line"), Field.Store.YES, Field.Index.ANALYZED));
				writer.addDocument(doc);
			}

			stmt.close();
			writer.commit();
		}
		catch(Exception e){e.printStackTrace();}
	}
	*/

	// Version 2.7, ASCII Encoder to check the audio path. In case of unicode, realplayer should handle it since MPlayer is not able to do it in windows.
	//private static final java.nio.charset.CharsetEncoder asciiEncoder = java.nio.charset.Charset.forName("US-ASCII").newEncoder(); // or "ISO-8859-1" for ISO Latin 1

	void player(String pathName, final int offset, final int nextOffset)
	{
		//if(!windows)pathName = pathName.replace('\\', '/'); // Version 2.3, Repeated. See below

		//System.out.println("pathName: "+pathName+", offset: "+offset+", nextOffset: "+nextOffset);
		if (defaultMediaChoice != pathMedia.INTERNET)
		{
			try
			{
				// Testing for files with *.rm extension
				File file = new File(pathName);

				if (!file.exists())
				{
					if (pathName.endsWith(".mp3") || pathName.endsWith(".wma") || pathName.endsWith(".m4a"))
						throw (new FileNotFoundException());
					else // i.e. .rm
					{
						// Testing for files with *.ram extension due to converting .rm to .ram when downloading
						file = new File(pathName.substring(0, pathName.length() - 1) + "am");
						if (!file.exists())
							throw (new FileNotFoundException());
						else
							// changing the pathname to have .ram extension
							pathName = pathName.substring(0, pathName.length() - 1) + "am";
					}
				}
			}
			catch (FileNotFoundException e)
			{
				e.printStackTrace();

				final TextArea textArea = new TextArea("IOERROR: File NOT Found: " + pathName);
				textArea.setEditable(false);
				textArea.setWrapText(true);
				textArea.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);

				String message = null;

				// To avoid display it for internet path
				if (defaultMediaChoice == pathMedia.DIRECTRY)
					message = "تأكد من وجود المادة الصوتية تحت المجلد: " + ls + pathName + ls + ls + "أو قم بإعادة تحديد موضع الملفات الصوتية من القائمة `إعدادات`";

				if (message != null)
				{
					final ButtonType ok = new ButtonType("إعادة", ButtonBar.ButtonData.YES);
					final Alert alert = new Alert(AlertType.CONFIRMATION, message, ok, new ButtonType("إلغاء", ButtonBar.ButtonData.NO));
					alert.setTitle("تنبيه");
					alert.setHeaderText(null);
					alert.initOwner(primaryStage);
					alert.getDialogPane().setExpandableContent(textArea);
					if (alert.showAndWait().get() == ok)
						player(pathName, offset, nextOffset);
				}

				return;
			}
		}

		// Version 2.3, Removed here. Then it is deleted and replaced by replace() at the begining of the program in init tree and everywhere else.
		//pathName = pathName.replace('\\', '/');

		{
            /* Replaced with PrintStream(mplayerProcess, true, "CP1256") in JMPlayer
			// Version 2.7, MPlayer cannot loadfile arabic paths.
			if(!asciiEncoder.canEncode(pathName) && isWindows) // Linux/Unix accept Unicode Path
			{
				JOptionPane.showOptionDialog(getContentPane(), variable[8], variable[3],JOptionPane.OK_OPTION, JOptionPane.INFORMATION_MESSAGE, null, new String[]{variable[10]}, variable[10]);
				final String settings[] = StreamConverter("setting/setting.txt");
				try
				{
					final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream("setting/setting.txt"), "UTF-8");
					out.write(settings[0]+ls);
					out.write(settings[1]+ls);
					out.write(settings[2]+ls);
					out.write(settings[3]+ls);
					out.write(settings[4]+ls);
					out.write("false"+ls);
					out.write(String.valueOf(settings[6]));
					out.close();
				}
				catch(Exception e){e.printStackTrace();}
				defaultPlayer = false;
				player(pathName, offset, nextOffset);
				return;
			}
			*/

			// Version 2.5, we removed SwingWorker since it causes the program to not shutdown in case maplyer is not in the path (linux) and mplayer.open is called. Use normal thread if you want this.
			if (nextOffset != -1)
			{
				if (nextOffset > offset)
					jVLC.open(pathName, offset, nextOffset, selected_Book_duration);
				else
				{
					// In this case we will not terminate the length of the file since it should continue to the end.
					if (nextOffset == offset)
					{
						final ButtonType ok = new ButtonType("نعم", ButtonBar.ButtonData.YES);
						final Alert alert = new Alert(AlertType.CONFIRMATION, "هذه الفهرسة ليست سوى مجرد تنبيه لبداية حديث أو كتاب أو فصل أو ما شابه. فهل تريد المتابعة؟", ok, new ButtonType("لا", ButtonBar.ButtonData.NO));
						alert.setTitle("تنبيه");
						alert.initOwner(primaryStage);
						alert.setHeaderText(null);

						if (alert.showAndWait().get() == ok)
							jVLC.open(pathName, offset, -1, selected_Book_duration);
					}
					else
					{
						System.out.println("This index is not in order.");
						jVLC.open(pathName, offset, -1, selected_Book_duration);
					}
				}
			}
			else
			{
				jVLC.open(pathName, offset, -1, selected_Book_duration); // This index maybe the last one or it is the whole file (from tree)
			}
		}
	}

	String selected_FileName;
	int selected_Code;

	// Version 1.3, These variables are used to add new indexes to a specific cassette.
	private int selected_Sheekh_id = 0; // Can be also info4
	private String selected_Sheekh_name = ""; // Can be also info1, Book_name, Title, leafTitle
	private String selected_Book_name = "";
	private String selected_TreeLevel = ""; // Can be 1 (Sheekh level), 2 (Book level), 3 (Sub-Book level), 4 (leaf) or empty
	private int selected_Book_id = 0; // Can be also info5
	private int selected_Book_duration = 0; // Version 2.3, To update the JMplayer slider length instead of calculating it each time, improved user reaction.
	private final Vector<String> audioDetailsLine = new Vector<>();
	private final Vector<String> audioDetailsTafreeg = new Vector<>(); // Version 3.0
	final Vector<Integer> audioDetailsOffset = new Vector<>();
	final Vector<Integer> audioDetailsSeq = new Vector<>(); // Version 2.0
	private final Vector<Integer> audioDetailsDuration = new Vector<>(); // Version 1.6

	// Version 2.0, Used for Feqh Tree selection due to the fact that each index is belong to different Code.
	private final Vector<Integer> audioDetailsCode = new Vector<>();

	void displayIndexes(final String FileName_Temp)
	{
		audioDetailsLine.removeAllElements();
		audioDetailsTafreeg.removeAllElements();
		audioDetailsOffset.removeAllElements();
		audioDetailsDuration.removeAllElements();
		audioDetailsSeq.removeAllElements();

		selected_FileName = FileName_Temp;

		try
		{
			final Statement stmt = sharedDBConnection.createStatement();

			/*
			 * Version 1.5
			 * The new SQL statement is developed to have 'ORDER BY Seq' to avoid the problem when we add an index
			 * in which it appears in the beginning of the indexes where it should be in the last. This also affects
			 * the sequence and the timing. On the other hand, performance is a bit degraded(speed).
			 */
			final ResultSet rs = stmt.executeQuery(derbyInUse?
					"SELECT Seq, Offset, Duration, Line, Tafreeg FROM Contents WHERE Code = " + selected_Code + " ORDER BY Seq"
					:"SELECT Seq, \"Offset\", Duration, Line, Tafreeg FROM Contents WHERE Code = " + selected_Code + " ORDER BY Seq");

			while (rs.next())
			{
				audioDetailsSeq.addElement(rs.getInt("Seq")); // Version 2.0
				audioDetailsOffset.addElement(rs.getInt("Offset"));
				audioDetailsDuration.addElement(rs.getInt("Duration")); // Version 1.6
				audioDetailsLine.addElement(rs.getString("Line"));
				audioDetailsTafreeg.addElement(DBString(rs.getCharacterStream("Tafreeg"))); // Version 3.0
			}
			stmt.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}

		String LineDetailed;
		final Vector<String> list = new Vector<>();
		for (int i = 0, size = audioDetailsOffset.size(); i < size; i++)
		{
			/*
			 * Version 1.1
			 * Color is added to the list to make more attractive using <html> at the begining (very important
			 * to be at the begining or it will not work). Then it is removed for performance issues.
			 * It was discovered that it doesn't affecet the performance but it causes many exceptions when resizing.
			 */
			// Version 1.6, -ve offset set as ? instead of Math.abs() since this will act in the same way as activating it to listen to the clip (i.e. listen to the end). In addition it will help to trace the missing.
			// Version 2.3, Color is done again after discovering that Tahoma font does not have this issue. Check [http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6591386]
			// Then it is removed again due to the exceptions and errors that it caused. Also it casues slowness in scrolling.
			// N.B. JList.setPrototypeCellValue is causing the issue.
			if (audioDetailsDuration.elementAt(i) == -1)
				//LineDetailed = "<HTML>(" + String.valueOf(i+1) + ") <font color=maroon>[?]</font> "+ audioDetailsLine.elementAt(i);
				LineDetailed = "(" + (i + 1) + ") [?] " + audioDetailsLine.elementAt(i);
			else
			{
				final int duration = audioDetailsDuration.elementAt(i);
				final String minute = String.valueOf(duration / 60 / 1000);
				final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
				//LineDetailed = "<HTML>(" + String.valueOf(i+1) + ") <font color=maroon>[" + minute + ':' + second + "]</font> " + audioDetailsLine.elementAt(i);
				LineDetailed = "(" + (i + 1) + ") [" + minute + ':' + second + "] " + audioDetailsLine.elementAt(i);
			}
			list.add(LineDetailed);
		}

		Platform.runLater(() ->
		{
			audioDetailsListModel.setAll(list);
			indexTextArea.clear();
			tafreegTextArea.clear();
			indexPanel.setText("عرض الفهرسة");
		});
	}

	void createNodes()
	{
		treeRootNode.getChildren().clear();
		searchRootNode.getChildren().clear();

		try
		{
			final Statement stmt1 = sharedDBConnection.createStatement();
			final Statement stmt2 = sharedDBConnection.createStatement();
			final Statement stmt3 = sharedDBConnection.createStatement();
			final Statement stmt4 = sharedDBConnection.createStatement();

			// Version 2.4, Added: ORDER BY Sheekh_id  to make Quran the first item.
			// Version 2.5, Removed ' ORDER BY Sheekh_id' after inserting the Quran at the beginning in CreateAudioCatalogerDatabase
			// Version 3.0, Revert back ' ORDER BY Sheekh_id'
			final ResultSet rs1 = stmt1.executeQuery("SELECT * FROM Sheekh ORDER BY Sheekh_id");

			while (rs1.next())
			{
				final String Sheekh_name = rs1.getString("Sheekh_name");
				final int Sheekh_id = rs1.getInt("Sheekh_id");

				// Version 1.3, "1" is an indicator that this node is the first level in the tree which is the scientists level for adding purposes.
				final TreeItem<AudioInfo> sheekhLevel = new TreeItem<>(new AudioInfo(Sheekh_name, "", Sheekh_id, 0, "sheekhLevel"));
				final CheckBoxTreeItem<AudioInfo> searchSheekhLevel = new CheckBoxTreeItem<>(new AudioInfo(Sheekh_name, "", Sheekh_id, 0, "sheekhLevel"));
				treeRootNode.getChildren().add(sheekhLevel);
				searchRootNode.getChildren().add(searchSheekhLevel);

				//final ResultSet rs2 = stmt2.executeQuery("SELECT Book_id FROM Chapters WHERE Sheekh_id = "+Sheekh_id+" GROUP BY Book_id");
				final ResultSet rs2 = stmt2.executeQuery("SELECT Book_id, Book_name, Multi_volume FROM Book WHERE Sheekh_id = " + Sheekh_id);

				while (rs2.next())
				{
					final int Book_id = rs2.getInt("Book_id");

					// Version 2.0
					final String Book_name = rs2.getString("Book_name");
					final boolean Multi_volume = rs2.getBoolean("Multi_volume");

					// Version 1.3, "2" is an indicator that this node is the second level in the tree which is the scientists books level for adding purposes.
					final TreeItem<AudioInfo> bookLevel = new TreeItem<>(new AudioInfo(Book_name, Sheekh_name, Sheekh_id, Book_id, "bookLevel"));
					sheekhLevel.getChildren().add(bookLevel);
					searchSheekhLevel.getChildren().add(new CheckBoxTreeItem<>(new AudioInfo(Book_name, Sheekh_name, Sheekh_id, Book_id, "bookLevel")));

					// Version 2.0, To have a multi-layer books e.g. bloog
					if (Multi_volume)
					{
						// Version 2.0, No need for 'Sheekh_id=Sheekh_id' since there is only one Book_id for one Sheekh_id
						final ResultSet rs4 = stmt4.executeQuery("SELECT Title FROM Chapters WHERE Book_id = " + Book_id + " GROUP BY Title");
						while (rs4.next())
						{
							final String Title = rs4.getString("Title");

							// "3" is an indicator that this node is the third level in the tree which is the sub-books level for adding perposes.
							final TreeItem<AudioInfo> subBookLevel = new TreeItem<>(new AudioInfo(Title, Sheekh_name, Sheekh_id, Book_id, "subBookLevel"));
							bookLevel.getChildren().add(subBookLevel);

							// Version 2.0, No need for 'Sheekh_id=Sheekh_id' since there is only one Book_id for one Sheekh_id
							final ResultSet rs3 = stmt3.executeQuery("SELECT Title, FileName, FileType, Path, code, Duration FROM Chapters WHERE (Book_id = " + Book_id + " AND Title = '" + Title + "') ORDER BY FileName"); // Version 3.0, 'ORDER BY FileName'
							if (com.sun.jna.Platform.isWindows()) // Version 2.5, make the condition outside the loop for performance.
							{
								while (rs3.next())
									subBookLevel.getChildren().add(new TreeItem<>(new AudioInfo(rs3.getString("Title") + '(' + rs3.getString("FileName") + ')', rs3.getString("Path") + '\\' + rs3.getString("FileName") + '.' + rs3.getString("FileType"), rs3.getInt("code"), rs3.getInt("Duration"), "volume"))); // Version 2.6, Regression bug: subBookLevel instead of bookLevel.
							}
							else
							{
								while (rs3.next())
									subBookLevel.getChildren().add(new TreeItem<>(new AudioInfo(rs3.getString("Title") + '(' + rs3.getString("FileName") + ')', rs3.getString("Path").replace('\\', '/') + '/' + rs3.getString("FileName") + '.' + rs3.getString("FileType"), rs3.getInt("code"), rs3.getInt("Duration"), "volume")));
							}
							rs3.close();
						}
						rs4.close();
					}
					else
					{
						/*
						 * Version 1.5
						 * Adding 'ORDER BY Seq' to the SQL statment.
						 * Then it is removed since it causes a lot of in-ordering indexes when
						 * you have many books in the catagory (e.g. bokhary).
						 *
						 * Version 2.0, No need for 'Sheekh_id=selected_Sheekh_id' since there is only one Book_id for one Sheekh_id
						 */
						final ResultSet rs3 = stmt3.executeQuery("SELECT Title, FileName, FileType, Path, code, Duration FROM Chapters WHERE Book_id = " + Book_id + " ORDER BY FileName"); // Version 3.0, 'ORDER BY FileName'
						if (com.sun.jna.Platform.isWindows()) // Version 2.5, make the condition outside the loop for performance.
						{
							while (rs3.next())
								bookLevel.getChildren().add(new TreeItem<>(new AudioInfo(rs3.getString("Title") + '(' + rs3.getString("FileName") + ')', rs3.getString("Path") + '\\' + rs3.getString("FileName") + '.' + rs3.getString("FileType"), rs3.getInt("code"), rs3.getInt("Duration"), "leaf")));
						}
						else
						{
							while (rs3.next())
								bookLevel.getChildren().add(new TreeItem<>(new AudioInfo(rs3.getString("Title") + '(' + rs3.getString("FileName") + ')', rs3.getString("Path").replace('\\', '/') + '/' + rs3.getString("FileName") + '.' + rs3.getString("FileType"), rs3.getInt("code"), rs3.getInt("Duration"), "leaf")));
						}
						rs3.close();
					}
				}
				rs2.close();
			}

			rs1.close();
			stmt1.close();
			stmt2.close();
			stmt3.close();
			stmt4.close();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	/*
	 * In this release we will use only one Vector for all of them using 'ö' as a separator instead of using
	 * a Vector for each one. This is very important since the we end with a critical problem with memory:
	 *
	 * Exception in thread "AWT-EventQueue-0" java.lang.OutOfMemoryError: Java heap space. when the search results
	 * exceeds 5000 items. This is not practical at all. In addition the speed is really bad.
	 *
	 * The sequence of variables [from left to right]:
	 * Offset, Seq, Path+FileName+FileType, FileName, Book_name, Title, Sheekh_name, NextOffset.
	 *
	 * Version 2.0, Removed Offset, Path+FileName+FileType, NextOffset, Title, Sheekh_name, FileName, Book_name
	 * -> Seq, Code
	 */
	private final Vector<String> searchResults = new Vector<>(700, 300);

	private void SearchEngine(final String Search)
	{
		String SQL_Combination = null;
		boolean wholeDB = true;

		final java.util.List<CheckBoxTreeItem<AudioInfo>> checkedNodes = getSelectedNodes(searchRootNode);
		if (!checkedNodes.isEmpty())
		{
			for (final CheckBoxTreeItem<AudioInfo> infoNode : checkedNodes)
			{
				final AudioInfo info = infoNode.getValue();
				if (info.info6.equals("sheekhLevel")) // sheekhLevel
				{
					wholeDB = false;
					if (SQL_Combination != null)
						SQL_Combination = SQL_Combination + " OR Sheekh_id = " + info.info4;
					else
						SQL_Combination = "Sheekh_id = " + info.info4;
				}
				else
				{
					if (infoNode.isLeaf() && infoNode != searchRootNode) // Version 3.2, leaf/book level. old path.getPathCount()==3
					{
						wholeDB = false;
						if (SQL_Combination != null)
							SQL_Combination = SQL_Combination + " OR Book_id = " + info.info5; // No need for ' AND Sheekh_id ='
						else
							SQL_Combination = "Book_id = " + info.info5;
					}
				}
			}
		}

		if (SQL_Combination != null || (wholeDB && !checkedNodes.isEmpty())) // Version 3.2
		{
			searchResults.removeAllElements();
			final ObservableList<String> listModel = FXCollections.observableArrayList();

			Platform.runLater(() ->
			{
				searchPanel.setText("البحث");
			});

			int count = 1;
			try
			{
				/*
				java.util.Properties props = new java.util.Properties();
				props.put("defaultRowPrefetch", "60000");
				props.put("defaultBatchValue", "1000");
				final Connection searchConnection = DriverManager.getConnection(dbURL, props);

				// Version 1.8, To speed up the process.
				searchConnection.setReadOnly(true);
				searchConnection.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
				*/

				// Version 2.0, Removed since they reduce the performance and cause memory issues.
				//final Statement stmt = sharedDBConnection.createStatement(ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
				//stmt.setFetchSize(50000);

				final Statement stmt = sharedDBConnection.createStatement();
				//stmt.setPoolable(true); // Version 2.7, Not implemented in H2. implemented in derby. Performance seems to be the same here. no recurring of using the stmt

				/*
				 * Version 1.5
				 * Adding 'ORDER BY Code, Seq' solve the problem of ordering the results to get the correct
				 * time and seq. On the other hand it increases the processing time.
				 *
				 * Version 1.6
				 * A condition is added to the SQL statement to not include SQL_Combination when all the database is selected. This
				 * helps a lot in freeing the memory when it reaches the max. limit. In addition it speeds up the
				 * search process. On the other hand, the problem with memory is still when some part of the database
				 * is selected.
				 * N.B. This condition didn't free the memory since the problem was with 'ORDER BY Code, Seq' NOT 'WHERE' clause.
				 */
				//final ResultSet rs = stmt.executeQuery(wholeDB?"SELECT * FROM Contents ORDER BY Code, Seq":("SELECT * FROM Contents WHERE Code IN (SELECT Code FROM Chapters WHERE " + SQL_Combination + ") ORDER BY Code, Seq")); // Very slow

				// Version 2.0, Removed Seq from 'ORDER BY Code, Seq' for performance. It dosn't have any affect!
				// Version 2.4, Removed 'ORDER BY Code'. It increase the performance. Already the DB in order (at least in H2).
				final ResultSet rs = stmt.executeQuery(wholeDB ? "SELECT Code, Seq, Book_id, Duration, Line FROM Contents" : ("SELECT Code, Seq, Book_id, Duration, Line FROM Contents WHERE " + SQL_Combination));
				// Slightly slow: final ResultSet rs = stmt.executeQuery(wholeDB?"SELECT Code, Seq, Duration, Line, Short_sheekh_name, Book_name FROM Contents, Book WHERE Contents.Book_id=Book.Book_id ORDER BY Code":("SELECT Code, Seq, Book_id, Duration, Line FROM Contents WHERE " + SQL_Combination + " ORDER BY Code"));
				//rs.setFetchSize(1000);

				//final PreparedStatement stmt1 = searchConnection.prepareStatement("SELECT Path, FileName, FileType, Title, Book_name, Sheekh_name FROM Chapters, Sheekh, Book WHERE Code = ? AND Chapters.Book_id=Book.Book_id AND Sheekh.Sheekh_id=Chapters.Sheekh_id");
				final PreparedStatement ps = sharedDBConnection.prepareStatement("SELECT Short_sheekh_name, Book_name FROM Book WHERE Book_id = ?");
				ResultSet rs1;

				final Pattern pattern = Pattern.compile("[\u0650\u064D\u064E\u064B\u064F\u064C\u0652\u0651]"); // Version 2.7, Performance gain
				//http://myhowto.org/under-the-hood/32-hidden-costs-of-using-java-string-methods/
				//http://www.javalobby.org/forums/thread.jspa?threadID=16822&tstart=0
				if (searchChoice == searchType.MATCH_CASE)
				{
					while (rs.next())
					{
						// Version 1.5, This variable is used to remove the tashkeel from the line. This is a new feature in the search technique. This code will remove the tashkeel:
						// inputLine.replaceAll("[\u0650\u064D\u064E\u064B\u064F\u064C\u0652\u0651]", "")
						final String inputLine = rs.getString("Line");
						//if((withTashkeelButton.isSelected()?inputLine:inputLine.replaceAll("[\u0650\u064D\u064E\u064B\u064F\u064C\u0652\u0651]", "")).indexOf(Search)>-1)
						if ((withTashkeelButton.isSelected() ? inputLine : pattern.matcher(inputLine).replaceAll("")).contains(Search)) // Version 2.7
						{
							ps.setInt(1, rs.getInt("Book_id"));
							rs1 = ps.executeQuery();
							rs1.next();

							searchResults.addElement(rs.getString("Seq") + 'ö' + rs.getInt("Code"));

							// Version 1.6, Storing the period in the database instead of calculating it each time.
							final int duration = rs.getInt("Duration"); // Version 2.7
							if (duration == -1)
								listModel.add('(' + String.valueOf(count++) + ") [?] (" + rs1.getString("Short_sheekh_name") + ") (" + rs1.getString("Book_name") + ") " + inputLine);
								//listModel.add("<html>("+String.valueOf(count++)+") <font color=green>[?]</font> <font color=maroon>("+rs1.getString("Short_sheekh_name")+")</font> <font color=blue>("+rs1.getString("Book_name")+")</font> "+inputLine);
							else
							{
								final String minute = String.valueOf(duration / 60 / 1000);
								final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
								listModel.add('(' + String.valueOf(count++) + ") [" + minute + ':' + second + "] (" + rs1.getString("Short_sheekh_name") + ") (" + rs1.getString("Book_name") + ") " + inputLine);
								//listModel.add("<html>("+String.valueOf(count++)+") <font color=green>[" + minute + ":" + second + "]</font> <font color=maroon>("+rs1.getString("Short_sheekh_name")+")</font> <font color=blue>("+rs1.getString("Book_name")+")</font> "+inputLine);
							}
						}
					}
				}

				if (searchChoice == searchType.WHOLE_ALL_WORDS)
				{
					// you can use split() but it will consider empty spaces, that why we will not use it here.
					//http://www.coderanch.com/t/409659/java/java/Difference-between-StringTokenizer-split
					final StringTokenizer searchTokens = new StringTokenizer(Search, " ?,:'");
					final String[] searchWords = new String[searchTokens.countTokens()];
					int searchWordsIndex = 0;
					boolean found = false;

					while (searchTokens.hasMoreTokens())
						searchWords[searchWordsIndex++] = searchTokens.nextToken();

					while (rs.next())
					{
						final String inputLine = rs.getString("Line");

						//final StringTokenizer tokens = new StringTokenizer(withTashkeelButton.isSelected()?inputLine:inputLine.replaceAll("[\u0650\u064D\u064E\u064B\u064F\u064C\u0652\u0651]", ""), " ?,:'\"\n.()[]-;0123456789،؟؛}{!"); // Version 2.3, '\n' is added
						// TODO change StringTokenizer to String.split and use precompiled Pattern for performance gain. Problem is that split will ignore empty tokens.
						// TODO it can be Solved by splite(regx, -1)
						// Use split("[delim_characters]+"). The plus sign (+) is used to indicate that consecutive delimiters should be treated as one. On the other hand, if delim is at the beginning it will still consider it empty token.
						final StringTokenizer tokens = new StringTokenizer(withTashkeelButton.isSelected() ? inputLine : pattern.matcher(inputLine).replaceAll(""), " ?,:'\".()[]-;0123456789،؟؛}{!" + ls); // Version 2.7
						final String[] lineWords = new String[tokens.countTokens()];
						int lineWordsIndex = 0;

						while (tokens.hasMoreTokens())
							lineWords[lineWordsIndex++] = tokens.nextToken();

						if (AND_SearchRelation)
						{
							for (String element : searchWords)
							{
								found = false;
								for (String e : lineWords)
								{
									if (element.equals(e))
									{
										found = true;
										break;
									}
								}
								if (!found) break;
							}
						}
						else //i.e. OR relation
						{
							found = false;
							for (String element : searchWords)
							{
								for (String e : lineWords)
								{
									if (element.equals(e))
									{
										found = true;
										break;
									}
								}
								if (found) break;
							}
						}

						if (found)
						{
							ps.setInt(1, rs.getInt("Book_id"));
							rs1 = ps.executeQuery();
							rs1.next();

							searchResults.addElement(rs.getString("Seq") + 'ö' + rs.getInt("Code"));

							final int duration = rs.getInt("Duration");
							if (duration == -1)
								listModel.add('(' + String.valueOf(count++) + ") [?] (" + rs1.getString("Short_sheekh_name") + ") (" + rs1.getString("Book_name") + ") " + inputLine);
								//listModel.add("<html>("+String.valueOf(count++)+") <font color=green>[?]</font> <font color=maroon>("+rs1.getString("Short_sheekh_name")+")</font> <font color=blue>("+rs1.getString("Book_name")+")</font> "+inputLine);
							else
							{
								final String minute = String.valueOf(duration / 60 / 1000);
								final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
								listModel.add('(' + String.valueOf(count++) + ") [" + minute + ':' + second + "] (" + rs1.getString("Short_sheekh_name") + ") (" + rs1.getString("Book_name") + ") " + inputLine);
								//listModel.add("<html>("+String.valueOf(count++)+") <font color=green>[" + minute + ":" + second + "]</font> <font color=maroon>("+rs1.getString("Short_sheekh_name")+")</font> <font color=blue>("+rs1.getString("Book_name")+")</font> "+inputLine);
							}
						}
					}
				}

				if (searchChoice == searchType.MATCH_CASE_ALL_WORDS)
				{
					final StringTokenizer searchTokens = new StringTokenizer(Search, " ?,:'");
					final String[] searchWords = new String[searchTokens.countTokens()];
					int searchWordsIndex = 0;
					boolean found;

					while (searchTokens.hasMoreTokens())
						searchWords[searchWordsIndex++] = searchTokens.nextToken();

					while (rs.next())
					{
						found = false;
						final String inputLine = rs.getString("Line");
						final String tashkeelLine = withTashkeelButton.isSelected() ? inputLine : pattern.matcher(inputLine).replaceAll(""); // Version 2.7

						if (AND_SearchRelation)
						{
							for (String element : searchWords)
							{
								found = tashkeelLine.contains(element);
								if (!found) break;
							}
						}
						else // i.e. OR relation
						{
							for (String element : searchWords)
							{
								found = tashkeelLine.contains(element);
								if (found) break;
							}
						}

						if (found)
						{
							ps.setInt(1, rs.getInt("Book_id"));
							rs1 = ps.executeQuery();
							rs1.next();

							searchResults.addElement(rs.getString("Seq") + 'ö' + rs.getInt("Code"));

							final int duration = rs.getInt("Duration");
							if (duration == -1)
								listModel.add('(' + String.valueOf(count++) + ") [?] (" + rs1.getString("Short_sheekh_name") + ") (" + rs1.getString("Book_name") + ") " + inputLine);
								//listModel.add("<html>("+String.valueOf(count++)+") <font color=green>[?]</font> <font color=maroon>("+rs1.getString("Short_sheekh_name")+")</font> <font color=blue>("+rs1.getString("Book_name")+")</font> "+inputLine);
							else
							{
								final String minute = String.valueOf(duration / 60 / 1000);
								final String second = String.valueOf(duration / 1000 - ((int) ((float) duration / 60F / 1000F) * 60));
								listModel.add('(' + String.valueOf(count++) + ") [" + minute + ':' + second + "] (" + rs1.getString("Short_sheekh_name") + ") (" + rs1.getString("Book_name") + ") " + inputLine);
								//listModel.add("<html>("+String.valueOf(count++)+") <font color=green>[" + minute + ":" + second + "]</font> <font color=maroon>("+rs1.getString("Short_sheekh_name")+")</font> <font color=blue>("+rs1.getString("Book_name")+")</font> "+inputLine);
							}
						}
					}
				}
				stmt.close();
				ps.close();

				Platform.runLater(() ->
				{
					audioSearchList.setItems(listModel);
					searchPanel.setText("البحث [النتائج: " + listModel.size() + ']');
				});
			}
			catch (Throwable t) // Instead of both OutOfMemoryError, SQLException
			{
				t.printStackTrace();
				if (t.toString().contains("java.lang.OutOfMemoryError") || t.toString().contains("Out of memory")) // Version 2.7, add 'org.h2.jdbc.JdbcSQLException: Out of memory' into condition. In most cases both will be thrown but not always.
				{
					// Free the memory.
					searchResults.removeAllElements();

					Platform.runLater(() ->
					{
						listModel.clear();

						// Shutting down the database will not free the memory in derby 10.9
						//try{sharedDBConnection.close();}//DriverManager.getConnection("jdbc:derby:;shutdown=true");}
						//catch(Exception e){e.printStackTrace();}

						final ButtonType ok = new ButtonType("إغلاق", ButtonBar.ButtonData.YES);
						final Alert alert = new Alert(AlertType.CONFIRMATION, "لقد تم نفاد الذاكرة اللازمة لإتمام عملية البحث لكثرة النتائج، لإنجاح العملية قم بتحرير الملف startup.bat" + ls +
								"والموضوع تحت مجلد البرنامج كما يلي:" + ls +
								"- قم بتغيير المقطع من Xmx1024m إلى Xmx2048m أو أكثر إن احتيج إلى ذلك. إبدأ البرنامج بالضغط على startup.bat" + ls +
								"[قم بالتغيير في startup.sh لأنظمة Linux/MacOS]", ok, new ButtonType("متابعة", ButtonBar.ButtonData.NO));
						alert.setTitle("تنبيه");
						alert.setHeaderText(null);
						alert.initOwner(primaryStage);

						if (alert.showAndWait().get() == ok)
							shutdown();

						searchPanel.setText("البحث");
					});
				}
			}
		}
		else
		{
			Platform.runLater(() ->
			{
				alertError("قم بتحديد نطاق البحث.", "تنبيه", "متابعة", null, AlertType.ERROR);
			});
		}
	}

	// Refresh the connection to take care of the updates
	static void reopenIndexSearcher()
	{
		try
		{
			defaultSearcher.getIndexReader().close();
			rootsSearcher.getIndexReader().close();
			luceneSearcher.getIndexReader().close();

			defaultSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(cl.getResource("index").toURI()).toPath())));
			rootsSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(cl.getResource("rootsIndex").toURI()).toPath())));
			luceneSearcher = new IndexSearcher(DirectoryReader.open(FSDirectory.open(new File(cl.getResource("luceneIndex").toURI()).toPath())));
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	// Save the history of the search entries
	private void pre_shutdown() throws Exception
	{
		// Version 1.8, To store the history of the search entries
		final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(new File(cl.getResource("setting/history.txt").toURI())), StandardCharsets.UTF_8);
		for (int i = 0; i < historyListModel.size() - 1; i++)
			out.write(historyListModel.get(i) + ls);

		// In case it is one element or the last element.
		if (!historyListModel.isEmpty())
			out.write(historyListModel.get(historyListModel.size() - 1));

		out.close();
	}

	void shutdown()
	{
		// moved out of the thread.
		final Dialog progressWindow = new Dialog();
		progressWindow.setResizable(false);
		progressWindow.initOwner(primaryStage);
		progressWindow.setTitle("جاري الانتهاء من العمليات المتبقية ...");

		final Button exitButton = new Button("إجباري");
		exitButton.setOnAction(new EventHandler<ActionEvent>()
		{
			@Override
			public void handle(ActionEvent e)
			{
				System.exit(1);
			}
		});

		// Exit abnormally
		progressWindow.setOnCloseRequest(new EventHandler<WindowEvent>()
		{
			public void handle(WindowEvent we)
			{
				exitButton.fire();
			}
		});
		final ProgressBar progressBar = new ProgressBar();
		progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
		progressBar.setPadding(new Insets(0.0, 0.0, 10.0, 0.0));
		progressBar.setPrefWidth(250.0);

		final VBox mainProgressPanel = new VBox();
		mainProgressPanel.setAlignment(Pos.CENTER);
		mainProgressPanel.getChildren().addAll(progressBar, exitButton);

		progressWindow.getDialogPane().setContent(mainProgressPanel);
		//progressWindow.getDialogPane().setPrefSize(300.0, 100.0);
		progressWindow.show();

		// Version 2.5, Change from SwingWorker to normal thread since there is no need to fire (EDT) for any GUI-related activities
		final Thread ShutdownThread = new Thread()
		{
			public void run()
			{
				/*
				 * Version 1.6
				 * This will prevent breaking the DB connection of the creating folders thread when you shutdown the program directly
				 * after this leading to not creating the folders. Another solution is using the DB in multi-user mode.
				 * On the other hand Derby doesn't support multi-user mode.
				 */
				try
				{
					pre_shutdown();

					Platform.runLater(() -> {
						jVLC.close();
					});

					defaultWriter.close();
					rootsWriter.close();
					luceneWriter.close();

					// Version 2.5, Should be the in the last to close all the threads before it.
					// Handle thread info
					final ThreadMXBean threads = ManagementFactory.getThreadMXBean();

					while (true)
					{
						final ThreadInfo[] threadInfos = threads.getThreadInfo(threads.getAllThreadIds()); // Parse each thread

						int myThreadCount = 0;
						for (ThreadInfo element : threadInfos)
							if (element.getThreadName().startsWith("MyThreads:"))
								myThreadCount++;

						if (myThreadCount == 0) break;
						Thread.sleep(400L);
					}

					// Shutting down the database
					if (derbyInUse)
						DriverManager.getConnection("jdbc:derby:;shutdown=true"); // It should be the last since it will through exception in normal case "Derby system shutdown."
					else
						//sharedDBConnection.close(); // Close h2 database, Version 2.5, No need for it since it will be closed when System.exit(0). Sometimes this statement throughs exception in case search is stopped in the middle then exit.
						sharedDBConnection.createStatement().execute("SHUTDOWN"); // shutdown it here instead of when System.exit(0).
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
				Platform.exit();

				// To terminate the app without using System.exit(0). you need to stop all the threads or confiugre all threads as thread.setDaemon(true); which means that when your main program ends all deamon threads will terminate too.
				System.exit(0);
			}
		};
		//ShutdownThread.setName("MyThreads: ShutdownThread"); // It will stop itself from terminating
		ShutdownThread.start();
	}

	private static String DBString(final Reader reader)
	{
		final char[] arr = new char[4 * 1024]; // 4K at a time
		final StringBuilder buf = new StringBuilder(10);
		int numChars;

		try
		{
			while ((numChars = reader.read(arr, 0, arr.length)) > 0)
				buf.append(arr, 0, numChars);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		return buf.toString();
	}

	// Taken from the official implementation of PlatformImpl.runAndWait()
	// https://github.com/openjdk/jfx/blob/e3e600b561f9dc749b27b828c330171f8efe83af/modules/javafx.graphics/src/main/java/com/sun/javafx/application/PlatformImpl.java
	private static void runAndWait(final Runnable r)
	{
		final CountDownLatch doneLatch = new CountDownLatch(1);
		Platform.runLater(() ->
		{
			try
			{
				r.run();
			}
			finally
			{
				doneLatch.countDown();
			}
		});

		try
		{
			doneLatch.await();
		}
		catch (InterruptedException ex)
		{
			ex.printStackTrace();
		}
	}

	// Version 3.2, To be able to refer to folders outside the Jar file correctly in Mac dmg.
	// http://stackoverflow.com/questions/320542/how-to-get-the-path-of-a-running-jar-file
	// This will be used when you need to create a file with reference to the main class path. ClassLoader.getResource() will not work since it will return null
	//final static String programFolder = AudioCataloger.class.getProtectionDomain().getCodeSource().getLocation().getPath();
	//static String programFolder;// = AudioCataloger.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();

	// This works but only if the file exists.
	//final static ClassLoader cl = AudioCataloger.class.getClassLoader();
    /* Usage:
        File -> cl.getResource(<relative path>).toURI()
        URL -> cl.getResource(<>)
        FileInputStream -> new File(cl.getResource(<>).toURI())
        InputStreamReader && Properties.load -> cl.getResourceAsStream(<>)
        FileOutputStream && RandomAccessFile && FileWriter -> new File(cl.getResource(<>).toURI())       Mostly this will not work since the file might not be exist so you need to use the above programFolder i.e. new File(programFolder+<relative path>)
        Runtime.getRuntime().exec -> new File(cl.getResource().toURI()).getAbsolutePath()
    */
	static ClassLoader cl;
	public static void main(String []args)
	{
		cl = AudioCataloger.class.getClassLoader();

		// Version 2.7, Used to allow passing the files when clicking on them while the program is running.
		try
		{
			ALERT_AUDIOCLIP = new AudioClip(cl.getResource("bin/alert.wav").toURI().toString());
			//programFolder = new File(AudioCataloger.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath()+"/";

			/* Not working anymore
			if(isWindows)
				uk.co.caprica.vlcj.binding.LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", new File(cl.getResource("bin/vlc/plugins").getFile()).getAbsolutePath(), 1);
			else
			{
				if (isMAC)
				{
					// To solve this issue: https://github.com/caprica/vlcj/issues/349
					uk.co.caprica.vlcj.binding.LibC.INSTANCE.setenv("VLC_PLUGIN_PATH", new File(cl.getResource("bin/vlc/Contents/MacOS/plugins").getFile()).getAbsolutePath(), 1); // VLC.app is renamed to vlc to prevent making Mac OSX consider it as an application
				}
			}
			*/
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
		launch(args);
	}
}