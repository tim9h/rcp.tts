package dev.tim9h.rcp.tts.media;

import static java.lang.ProcessBuilder.Redirect.PIPE;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.tts.TtsViewFactory;

@Singleton
public class TtsEngine {

	private final Pattern quoteSplitter;

	@Inject
	private EventManager eventManager;

	@Inject
	private Settings settings;

	@InjectLogger
	private Logger logger;

	private Process process;

	private String engineStarter;

	private AtomicBoolean engineLogging;

	public TtsEngine() {
		quoteSplitter = Pattern.compile("([^\"]\\S*|\"[^\"]++\")\\s*");
		engineLogging = new AtomicBoolean(false);
	}

	public void start() {
		eventManager.showWaitingIndicator();
		CompletableFuture.runAsync(() -> {
			if (getEngineStarter() != null) {
				var args = quoteSplitter.matcher(engineStarter).results().map(t -> t.group().trim())
						.toArray(String[]::new);
				try {
					process = new ProcessBuilder(args).redirectOutput(PIPE).redirectError(PIPE).start();
					handleStreamOutput(process.getErrorStream(), Level.ERROR);
					logger.info(() -> "TTS engine started - PID: " + process.pid());
					eventManager.echoAsync("Speech output enabled");
				} catch (IOException e) {
					logger.error(() -> "Unable to start TTS engine", e);
					eventManager.echoAsync("Unable to start speech output");
				}
			}
		});
	}

	private void handleStreamOutput(InputStream inputStream, Level level) {
		try (var reader = new BufferedReader(new InputStreamReader(inputStream))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.contains("ERROR in app")) {
					engineLogging.set(true);
				}
				if (engineLogging.get()) {
					logger.atLevel(level).log("[glados-tts] " + line);
				}
			}
		} catch (Exception e) {
			logger.error(() -> "Error while reading engine output", e);
		}
	}

	public void stop() {
		eventManager.showWaitingIndicatorAsync();
		CompletableFuture.runAsync(() -> {
			if (process != null && process.isAlive()) {
				process.destroy();
			}
			logger.info(() -> "TTS engine stopped");
			eventManager.echoAsync("Speech output disabled");
		}).exceptionally(e -> {
			logger.warn(() -> "Unable to stop speech engine", e);
			return null;
		});
	}

	public boolean isRunning() {
		return process != null && process.isAlive();
	}

	private String getEngineStarter() {
		if (StringUtils.isBlank(engineStarter)) {
			engineStarter = settings.getString(TtsViewFactory.SETTING_TTS_ENGINE_STARTER);
			if (StringUtils.isBlank(engineStarter)) {
				logger.warn(() -> "No TTS engine starter found");
				eventManager.echo("Engine starter not configured");
			}
		}
		return engineStarter;
	}

	public void onSettingsChanged() {
		engineStarter = null;
	}

	public void disableLogging() {
		engineLogging.set(false);
	}

}
