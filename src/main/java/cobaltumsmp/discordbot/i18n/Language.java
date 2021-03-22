package cobaltumsmp.discordbot.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A list of linked translation keys and Strings for i18n.
 */
public class Language {
    public static final Logger LOGGER = LogManager.getLogger();
    private static Language instance;
    private final Map<String, String> translations;

    private Language(Map<String, String> translations) {
        this.translations = translations;
    }

    /**
     * Load a language json file from a language code.
     *
     * @param langCode the language code to load the language from
     * @return the Language
     * @throws IOException thrown by {@link ObjectMapper#readTree}
     */
    public static Language load(String langCode) throws IOException {
        String langFilePath = String.format("lang/%s.json", langCode);

        InputStreamReader langFileReader;
        try {
            langFileReader = new FileReader(langFilePath);
        } catch (FileNotFoundException e) {
            InputStream langFileStream = Language.class.getResourceAsStream(langFilePath);
            if (langFileStream == null) {
                LOGGER.warn("Could not find a language file for '{}'", langCode);
                return null;
            }

            langFileReader = new InputStreamReader(langFileStream);
        }

        Map<String, String> translations = new HashMap<>();
        JsonNode langJson = new ObjectMapper().readTree(langFileReader);

        Iterator<Map.Entry<String, JsonNode>> keyIterator = langJson.fields();
        while (keyIterator.hasNext()) {
            Map.Entry<String, JsonNode> key = keyIterator.next();
            translations.put(key.getKey(), key.getValue().asText());
        }

        Language lang = new Language(translations);
        if (instance == null) {
            instance = lang;
        }

        return lang;
    }

    public static Language getInstance() {
        return instance;
    }

    public String get(String translationKey) {
        return this.translations.getOrDefault(translationKey, translationKey);
    }
}
