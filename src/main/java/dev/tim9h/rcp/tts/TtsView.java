package dev.tim9h.rcp.tts;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;

import dev.tim9h.rcp.event.CcEvent;
import dev.tim9h.rcp.event.EventManager;
import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.spi.CCard;
import dev.tim9h.rcp.spi.Mode;
import dev.tim9h.rcp.spi.TreeNode;
import dev.tim9h.rcp.tts.media.MediaFactory;
import dev.tim9h.rcp.tts.media.MediaQueuePlayer;
import dev.tim9h.rcp.tts.media.TtsEngine;

public class TtsView implements CCard {

	@InjectLogger
	private Logger logger;

	@Inject
	private EventManager eventManager;

	@Inject
	private TtsEngine engine;

	@Inject
	private MediaQueuePlayer queue;

	@Inject
	private MediaFactory mediaFactory;

	@Override
	public String getName() {
		return "tts";
	}

	@Override
	public Optional<List<Mode>> getModes() {
		return Optional.of(Arrays.asList(new Mode() {

			@Override
			public void onEnable() {
//				engine.start().thenRun(() -> Platform.runLater(() -> say("ohai")));
				engine.start();
			}

			@Override
			public void onDisable() {
				engine.stop();
			}

			@Override
			public String getName() {
				return "speech";
			}
		}));
	}

	@Override
	public void onShutdown() {
		engine.stop();
	}

	@Override
	public void initBus(EventManager em) {
		CCard.super.initBus(eventManager);
		em.listen("tts", data -> say(StringUtils.join(data, StringUtils.SPACE)));
		em.listen(CcEvent.EVENT_CLOSING, data -> say("kay thanks.bye"));
		em.listen("MODE_AFK", data -> {
			var state = StringUtils.join(data);
			if ("off".equals(state)) {
				say("welcome back");
			} else if ("on".equals(state)) {
				say("see you.later");
			}
		});
		em.listen("MODE_ALERT", data -> {
			if (StringUtils.join(data).equals("on")) {
				say("alert!");
			}
		});
	}

	private void say(String text) {
		if (!engine.isRunning()) {
			eventManager.echo("TTS engine not started", "Start TTS engine with 'speech' mode");
			return;
		}
		var media = mediaFactory.query(text);
		if (media != null) {
			logger.debug(() -> "TTS: " + text);
			queue.addMedia(media);
		} else {
			logger.debug(() -> "Error during TTS media generation for: " + text);
		}
	}

	@Override
	public Optional<TreeNode<String>> getModelessCommands() {
		return Optional.of(new TreeNode<>("tts"));
	}

	@Override
	public void onSettingsChanged() {
		engine.onSettingsChanged();
		mediaFactory.onSettingsChanged();
	}

}
