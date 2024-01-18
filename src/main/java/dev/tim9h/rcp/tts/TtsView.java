package dev.tim9h.rcp.tts;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;

import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.Mode;

public class TtsView implements CCard {

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Inject
	private Settings settings;

	private Process engine;

	private static Pattern quoteSplitter = Pattern.compile("([^\"]\\S*|\"[^\"]++\")\\s*");

	@Override
	public String getName() {
		return "tts";
	}

	@Override
	public Optional<List<Mode>> getModes() {
		return Optional.of(Arrays.asList(new Mode() {

			@Override
			public void onEnable() {
				eventManager.showWaitingIndicator();
				var engineStarter = settings.getString(TtsViewFactory.SETTING_TTS_ENGINE_STARTER);
				var args = quoteSplitter.matcher(engineStarter).results().map(t -> t.group().trim())
						.toArray(String[]::new);

				try {
					engine = new ProcessBuilder(args).start();
					logger.debug(() -> "TTS engine started - PID: " + engine.pid());
				} catch (IOException e) {
					logger.error(() -> "Unable to start TTS engine", e);
				}
				eventManager.echo("Speech output enabled");
			}

			@Override
			public void onDisable() {
				eventManager.showWaitingIndicator();
				if (engine != null && engine.isAlive()) {
					engine.destroy();
				}
				eventManager.echo("Speech output disabled");
			}

			@Override
			public String getName() {
				return "speech";
			}
		}));
	}

}
