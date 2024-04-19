package dev.tim9h.rcp.tts;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.google.inject.Inject;

import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.CCardFactory;

public class TtsViewFactory implements CCardFactory {

	public static final String SETTING_TTS_ENGINE_STARTER = "tts.engine.starter";

	public static final String SETTING_TTS_ENGINE_API = "tts.engine.api";

	public static final String SETTING_TTS_VOLUME = "tts.volume";

	public static final String SETTING_MODES = "core.modes";

	@Inject
	private TtsView view;

	@Override
	public String getId() {
		return "tts";
	}

	@Override
	public CCard createCCard() {
		return view;
	}

	@Override
	public Map<String, String> getSettingsContributions() {
		Map<String, String> settings = new HashMap<>();
		settings.put(SETTING_TTS_ENGINE_STARTER, StringUtils.EMPTY);
		settings.put(SETTING_TTS_ENGINE_API, StringUtils.EMPTY);
		settings.put(SETTING_TTS_VOLUME, "1.0");
		return settings;
	}

}
