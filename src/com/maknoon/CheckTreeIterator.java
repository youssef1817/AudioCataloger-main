package com.maknoon;

import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.TreeItem;

import java.util.Iterator;
import java.util.Stack;

public class CheckTreeIterator<T> implements Iterator<CheckBoxTreeItem<T>>
{
	private final Stack<CheckBoxTreeItem<T>> stack = new Stack<>();

	CheckTreeIterator(CheckBoxTreeItem<T> root)
	{
		stack.push(root);
	}

	@Override
	public boolean hasNext()
	{
		return !stack.isEmpty();
	}

	@Override
	public CheckBoxTreeItem<T> next()
	{
		final CheckBoxTreeItem<T> nextItem = stack.pop();
		for(final TreeItem<T> item : nextItem.getChildren())
			stack.push((CheckBoxTreeItem<T>)item);

		return nextItem;
	}
}