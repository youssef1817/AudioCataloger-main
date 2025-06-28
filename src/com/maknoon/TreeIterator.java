package com.maknoon;

import javafx.scene.control.TreeItem;

import java.util.Iterator;
import java.util.Stack;

public class TreeIterator<T> implements Iterator<TreeItem<T>>
{
	private final Stack<TreeItem<T>> stack = new Stack<>();

	TreeIterator(TreeItem<T> root)
	{
		stack.push(root);
	}

	@Override
	public boolean hasNext()
	{
		return !stack.isEmpty();
	}

	@Override
	public TreeItem<T> next()
	{
		TreeItem<T> nextItem = stack.pop();
		nextItem.getChildren().forEach(stack::push);

		return nextItem;
	}
}