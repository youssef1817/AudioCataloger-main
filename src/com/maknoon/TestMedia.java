package com.maknoon;

import javafx.application.Application;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.stage.Stage;
import javafx.util.Duration;

public class TestMedia extends Application
{
	// Should be global variable
	MediaPlayer mp;

	@Override
	public void start(Stage stage) throws Exception
	{
		//final Media media = new Media(new File("E:\\AudioCataloger\\resources\\bin_X\\001.m4a").toURI().toString());
		final Media media = new Media("http://www.maknoon.com/audios.mp3/alalbani/alnoor/001o.m4a");

		media.setOnError(new Runnable() {
			public void run() {
				// Handle asynchronous error in Media object.
				System.out.println(media.getError().getMessage());
			}
		});

		mp = new MediaPlayer(media);
		mp.setAutoPlay(true);
		mp.setStartTime(Duration.millis(0));
		mp.setStopTime(Duration.millis(550000));
		//mp.seek(Duration.millis(0));

		mp.setOnStopped(new Runnable() {
			@Override
			public void run() {
			}
		});

		mp.setOnError(new Runnable() {
			public void run() {
				System.out.println(mp.getError().getMessage());
			}
		});

		mp.statusProperty().addListener(new InvalidationListener() {
			@Override
			public void invalidated(Observable o) {
				System.out.println(mp.getStatus());
			}
		});

		mp.setOnReady(new Runnable() {
			public void run() {
				mp.play();
			}
		});

		mp.setOnPlaying(new Runnable() {
			public void run() {
			}
		});

		mp.currentTimeProperty().addListener(new InvalidationListener() {
			public void invalidated(Observable ov) {
				System.out.println(mp.getCurrentTime().toString());
			}
		});

		mp.volumeProperty().addListener(
				new InvalidationListener() {
					@Override
					public void invalidated(Observable o) {
						System.out.println(Double.doubleToLongBits(mp.getVolume()));
					}
				});

		mp.totalDurationProperty().addListener(
				new InvalidationListener() {
					@Override
					public void invalidated(Observable o) {
						System.out.println(mp.getTotalDuration().toMillis());
					}
				});

		mp.setOnPaused(new Runnable() {
			public void run() {
			}
		});

		mp.setOnEndOfMedia(new Runnable() {
			public void run() {
			}
		});
	}

	public static void main(String[] args) {
		launch(args);
	}
}