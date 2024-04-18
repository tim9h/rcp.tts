package dev.tim9h.rcp.tts.media;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.tts.TtsViewFactory;
import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;

@Singleton
public class MediaQueuePlayer implements Runnable {

	private final BlockingQueue<Media> queue;

	private MediaPlayer currentPlayer;

	private volatile boolean stopRequested = false;

	@Inject
	private Settings settings;

	@InjectLogger
	private Logger logger;

	@Inject
	private TtsEngine engine;

	public MediaQueuePlayer() {
		this.queue = new LinkedBlockingQueue<>();
		new Thread(this, "ttsQueue").start();
	}

	@Override
	public void run() {
		while (!stopRequested) {
			try {
				var media = queue.take();
				playMedia(media);
			} catch (InterruptedException e) {
				logger.error(() -> "Error in TTS queue loop", e);
				Thread.currentThread().interrupt();
				break;
			}
		}
	}

	private void playNext() {
		var nextMedia = queue.poll();
		if (nextMedia != null) {
			Platform.runLater(() -> {
				if (currentPlayer != null) {
					currentPlayer.stop();
					currentPlayer.dispose();
				}
				playMedia(nextMedia);
			});
		}
	}

	private void playMedia(Media media) {
		logger.debug(() -> "Playing media");
		if (!settings.getStringSet(TtsViewFactory.SETTING_MODES).contains("dnd")) {
			currentPlayer = new MediaPlayer(media);
			currentPlayer.setOnEndOfMedia(this::playNext);
			currentPlayer.setOnError(() -> handlePlayerError(media, currentPlayer.getError()));
			currentPlayer.play();
		} else {
			logger.debug(() -> "Suppressing TTS output (DND mode)");
		}
	}

	public void stop() {
		stopRequested = true;
		if (currentPlayer != null) {
			currentPlayer.stop();
			currentPlayer.dispose();
		}
	}

	public boolean addMedia(Media media) {
		engine.disableLogging();
		var success = queue.offer(media);
		if (!success) {
			logger.warn(() -> "Unable to add media to queue");
		}
		return success;
	}

	private void handlePlayerError(Media media, MediaException error) {
		if (error == null || error.getMessage() == null) {
			logger.error(() -> "Error during media playback");
		} else {
			logger.error(
					() -> String.format("Error during media playback (%s): %s", media.getSource(), error.getMessage()));
		}
	}

	public boolean isRunning() {
		return !stopRequested;
	}

}
