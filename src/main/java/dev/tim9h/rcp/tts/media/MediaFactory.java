package dev.tim9h.rcp.tts.media;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.tts.TtsViewFactory;
import javafx.scene.media.Media;

@Singleton
public class MediaFactory {

	@Inject
	private EventManager eventManager;

	@InjectLogger
	private Logger logger;

	@Inject
	private Settings settings;

	private String engineApi;

	private final Pattern illegalCharRemover;

	public MediaFactory() {
		illegalCharRemover = Pattern.compile("[^\\w \\.\\-\\,\\!]");
	}

	public Media query(String text) {
		if (StringUtils.isBlank(text)) {
			eventManager.echo("What?");
			return null;
		}
		if (getEngineApi() == null) {
			return null;
		}
		var response = removeIllegalCharacters(text);
		if (response == null) {
			return null;
		}
		var url = String.format(engineApi, response);
		logger.debug(() -> "Audio url: " + url);
		return new Media(url);
	}

	private String getEngineApi() {
		if (StringUtils.isBlank(engineApi)) {
			engineApi = settings.getString(TtsViewFactory.SETTING_TTS_ENGINE_API);
			if (StringUtils.isBlank(engineApi)) {
				logger.warn(() -> "No TTS engine API found");
				eventManager.echo("TTS not configured");
			}
		}
		return engineApi;
	}

	public void onSettingsChanged() {
		engineApi = null;
	}

	private String removeIllegalCharacters(String text) {
		var noaccents = StringUtils.stripAccents(text);
		var safeChars = illegalCharRemover.matcher(noaccents).replaceAll(StringUtils.SPACE).strip();
		return URLEncoder.encode(safeChars, StandardCharsets.UTF_8);
	}

}
