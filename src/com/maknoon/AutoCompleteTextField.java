package com.maknoon;

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Side;
import javafx.scene.control.*;

import java.util.LinkedList;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

// This class is a TextField which implements an "autocomplete" functionality, based on a supplied list of entries
public class AutoCompleteTextField extends TextField
{
	// The existing autocomplete entries
	private final SortedSet<String> entries;

	// The popup used to select an entry
	private final ContextMenu entriesPopup;

	public AutoCompleteTextField()
	{
		super();
		entries = new TreeSet<>();
		entriesPopup = new ContextMenu();
		setContextMenu(entriesPopup);
		textProperty().addListener(new ChangeListener<String>()
		{
			@Override
			public void changed(ObservableValue<? extends String> observableValue, String s, String s2)
			{
				if (getText().isEmpty())
					entriesPopup.hide();
				else
				{
					// Search for the text at the start of the string in history list
					//final LinkedList<String> searchResult = new LinkedList<>(entries.subSet(getText(), getText() + Character.MAX_VALUE));

					// Search for the text in the whole String in history list
					final LinkedList<String> searchResult = new LinkedList<>();
					final String[] input = entries.toArray(new String[0]);
					for (int i = 0; i < input.length; i++)
					{
						if (input[i].contains(getText()))
							searchResult.add(input[i]);
					}

					if (!searchResult.isEmpty())
					{
						populatePopup(searchResult);
						//if (!entriesPopup.isShowing())
						{
							//entriesPopup.setStyle("-fx-max-width: 200;");
							System.out.println(entriesPopup.getWidth());
							entriesPopup.show(AutoCompleteTextField.this, Side.BOTTOM, entriesPopup.getWidth(), 0.0);
						}
					}
					else
						entriesPopup.hide();
				}
			}
		});

		focusedProperty().addListener(new ChangeListener<Boolean>()
		{
			@Override
			public void changed(ObservableValue<? extends Boolean> observableValue, Boolean aBoolean, Boolean aBoolean2)
			{
				entriesPopup.hide();
			}
		});
	}

	// Get the existing set of autocomplete entries
	public SortedSet<String> getEntries()
	{
		return entries;
	}

	/**
	 * Populate the entry set with the given search results.  Display is limited to 10 entries, for performance.
	 * @param searchResult The set of matching strings.
	 */
	private void populatePopup(List<String> searchResult)
	{
		//final List<CustomMenuItem> menuItems = new LinkedList<>();
		final List<MenuItem> menuItems = new LinkedList<>();
		int maxEntries = 10; // If you'd like more entries, modify this line
		int count = Math.min(searchResult.size(), maxEntries);
		for (int i = 0; i < count; i++)
		{
			final String result = searchResult.get(i);
			//final CustomMenuItem item = new CustomMenuItem(new Label(result), true);
			//item.getContent().setNodeOrientation(NodeOrientation.RIGHT_TO_LEFT);

			final Label lbl = new Label(result);
			//lbl.setPrefWidth(200.0);
			//lbl.setMaxWidth(200.0);
			//lbl.setMinWidth(200.0);
			lbl.setWrapText(true);

			final MenuItem item = new MenuItem();
			item.setGraphic(lbl);

			item.setOnAction(new EventHandler<ActionEvent>()
			{
				@Override
				public void handle(ActionEvent actionEvent)
				{
					setText(result);
					entriesPopup.hide();
				}
			});
			menuItems.add(item);
		}
		entriesPopup.getItems().clear();
		entriesPopup.getItems().addAll(menuItems);
	}
}