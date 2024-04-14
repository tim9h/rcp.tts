package dev.tim9h.rcp.tts;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;

import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.Mode;
import dev.tim9h.rcp.spi.TreeNode;
import javafx.scene.media.AudioClip;

public class TtsView implements CCard {

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Inject
	private Settings settings;

	private Process engine;

	private final Pattern quoteSplitter;

	private final Pattern illegalCharRemover;

	private String engineApi;

	private Queue<AudioClip> speechQueue;

	private Thread voiceThread;

	public TtsView() {
		quoteSplitter = Pattern.compile("([^\"]\\S*|\"[^\"]++\")\\s*");
		illegalCharRemover = Pattern.compile("[^a-zA-Z0-9,\\.\\!ß]");
	}

	@Override
	public String getName() {
		return "tts";
	}

	@Override
	public Optional<List<Mode>> getModes() {
		return Optional.of(Arrays.asList(new Mode() {

			@Override
			public void onEnable() {
				startEngine();
			}

			@Override
			public void onDisable() {
				shutdownEngine();
			}

			@Override
			public String getName() {
				return "speech";
			}
		}));
	}

	private void startEngine() {
		eventManager.showWaitingIndicator();
		CompletableFuture.runAsync(() -> {
			var engineStarter = settings.getString(TtsViewFactory.SETTING_TTS_ENGINE_STARTER);
			var args = quoteSplitter.matcher(engineStarter).results().map(t -> t.group().trim()).toArray(String[]::new);
			try {
				engine = new ProcessBuilder(args).start();
				logger.info(() -> "TTS engine started - PID: " + engine.pid());
				eventManager.echoAsync("Speech output enabled");
			} catch (IOException e) {
				logger.error(() -> "Unable to start TTS engine", e);
				eventManager.echoAsync("Unable to start speech output");
			}
		});
	}

	private void shutdownEngine() {
		eventManager.showWaitingIndicatorAsync();
		CompletableFuture.runAsync(() -> {
			if (engine != null && engine.isAlive()) {
				engine.destroy();
			}
			logger.info(() -> "TTS engine thread stopped");
			eventManager.echoAsync("Speech output disabled");
		}).exceptionally(e -> {
			logger.warn(() -> "Unable to stop speech engine", e);
			return null;
		});
	}

	@Override
	public void onShutdown() {
		shutdownEngine();
	}

	@Override
	public void initBus(EventManager em) {
		CCard.super.initBus(eventManager);
//		em.listen(CcEvent.EVENT_CLI_RESPONSE, data -> say(StringUtils.join(data)));
		em.listen("tts", data -> say(StringUtils.join(data, StringUtils.SPACE)));
	}

	private void say(String text) {
		if (StringUtils.isBlank(text)) {
			eventManager.echo("What?");
			return;
		}
		if (engine == null || !engine.isAlive()) {
			eventManager.echo("TTS engine not started", "Start TTS engine with 'speech' mode");
		}

		if (speechQueue == null) {
			speechQueue = new LinkedBlockingQueue<>();
		}
		if (engineApi == null) {
			engineApi = settings.getString(TtsViewFactory.SETTING_TTS_ENGINE_API);
			if (StringUtils.isBlank(engineApi)) {
				logger.warn(() -> "No TTS engine API found");
				eventManager.echo("TTS not configured");
				return;
			}
		}
		initSpeechThread();

		var response = removeIllegalCharacters(text);
		var url = String.format(engineApi, response);

		CompletableFuture.runAsync(() -> {
			logger.debug(() -> "Fetching audio response from: " + url);
			var speech = new AudioClip(url);
			var added = speechQueue.offer(speech);
			logger.debug(() -> "Added to queue: " + added);
		}).exceptionally(e -> {
			logger.error(() -> "Unable to fetch speech output from " + url, e);
			return null;
		});
	}

	private String removeIllegalCharacters(String text) {
		var noaccents = StringUtils.stripAccents(text);
		var safeChars = illegalCharRemover.matcher(noaccents).replaceAll(StringUtils.SPACE).strip();
		var encoded = URLEncoder.encode(safeChars, StandardCharsets.UTF_8);
		logger.debug(() -> String.format("Decoded for TTS:%n Input:  %s%n Output: %s", text, encoded));
		return encoded;
	}

	private void initSpeechThread() {
		if (voiceThread == null) {
			voiceThread = new Thread(() -> {
				logger.info(() -> "Speech thread initialized");
				while (engine.isAlive()) {
					handleSpeechQueue();
				}
				logger.info(() -> "TTS engine stopped. Terminating speech thread.");
			}, "tts");
			voiceThread.start();
		}
	}

	private void handleSpeechQueue() {
		if (!speechQueue.isEmpty()) {
			try {
				var speech = speechQueue.poll();
				if (speech != null) {
					if (!settings.getStringSet(TtsViewFactory.SETTING_MODES).contains("dnd")) {
						speech.play();
					} else {
						logger.debug(() -> "Suppressing tts output (dnd mode)");
					}
					while (speech.isPlaying()) {
						Thread.sleep(500);
					}
				}
				Thread.sleep(500);
			} catch (InterruptedException e) {
				logger.warn(() -> "Error in speech queue", e);
				Thread.currentThread().interrupt();
			}
		}
	}

	@Override
	public void onSettingsChanged() {
		engineApi = null;
	}

	@Override
	public Optional<TreeNode<String>> getModelessCommands() {
		return Optional.of(new TreeNode<>("tts"));
	}

}
