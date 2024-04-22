package dev.tim9h.rcp.tts.dictionary;

import java.awt.Desktop;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

import org.apache.logging.log4j.Logger;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import dev.tim9h.rcp.logging.InjectLogger;
import dev.tim9h.rcp.settings.Settings;
import dev.tim9h.rcp.tts.TtsViewFactory;

@Singleton
public class DictionaryService {

	private String dictName;

	@InjectLogger
	private Logger logger;

	@Inject
	private Settings settings;

	private Properties dictionary;

	private Path propertiesPath;

	public String get(String dictId) {
		var values = getDictionary().get(dictId);
		if (values == null) {
			logger.warn(() -> String.format("Dictionary entry missing for%s/%s", getDictName(), dictId));
			getDictionary().put(dictId, dictId);
			persistDictionary();
			return dictId;
		}
		var tokens = ((String) values).split(";");
		return tokens[new SecureRandom().nextInt(tokens.length)];
	}

	private void persistDictionary() {
		logger.info(() -> "Updating persisted dictionary " + getDictName());
		try (OutputStream outputStream = Files.newOutputStream(propertiesPath)) {
			dictionary.store(outputStream, null);
		} catch (IOException e) {
			logger.error(() -> "Unable to persist dictionary in " + propertiesPath.toString() + ": " + e.getMessage());
		}
	}

	private String getDictName() {
		if (dictName == null) {
			dictName = settings.getString(TtsViewFactory.SETTING_DICT_NAME);
		}
		return dictName;
	}

	public void onSettingsChanged() {
		dictName = null;
		dictionary = null;
	}

	private Properties getDictionary() {
		if (dictionary == null) {
			dictionary = new Properties();
			propertiesPath = Path.of(System.getProperty("user.home"), "rcp", "dictionaries",
					getDictName() + ".rcpdict");

			logger.debug(() -> "Loading dictionary from " + propertiesPath);

			if (!Files.exists(propertiesPath)) {
				try {
					Files.createDirectories(propertiesPath.getParent());
					Files.createFile(propertiesPath);
				} catch (IOException e) {
					logger.error(() -> "Unable to create dictionary file: " + e.getMessage());
				}
			}

			try (var inputStream = Files.newInputStream(propertiesPath)) {
				dictionary.load(inputStream);
			} catch (IOException e) {
				logger.error(() -> String.format("Unable to load dictionary: %s", e.getMessage()));
			}
		}
		return dictionary;

	}

	public void edit() {
		if (System.getProperty("os.name").startsWith("Windows")) {
			CompletableFuture.runAsync(() -> {
				try {
					new ProcessBuilder("cmd", "/c", "start", "/wait", "notepad",
							propertiesPath.toFile().getAbsolutePath()).start().waitFor();
					dictionary = null;
				} catch (InterruptedException | IOException e) {
					logger.warn(() -> "Unable to open dictionary file: " + e.getMessage());
					Thread.currentThread().interrupt();
				}
			});
		} else {
			try {
				Desktop.getDesktop().edit(propertiesPath.toFile());
				dictionary = null;
			} catch (IOException e) {
				logger.warn(() -> "Unable to open dictionary: " + e.getMessage());
			}
		}
	}

}
