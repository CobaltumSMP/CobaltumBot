package cobaltumsmp.discordbot.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A list of linked translation keys and Strings for i18n.
 */
public class Language {
    public static final Logger LOGGER = LogManager.getLogger();
    private static Language instance = new Language(new HashMap<>());
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
        String name = String.format("%s.json", langCode);

        // Find in working dir
        File file = new File(name);

        if (!file.exists()) {
            // Find in lang sub directory of working one
            file = new File("lang", name);

            if (!file.exists()) {
                // Find in classpath
                URL fileUrl = ClassLoader.getSystemClassLoader().getResource(name);

                if (fileUrl == null) {
                    // Find in lang directory in classpath
                    fileUrl = ClassLoader.getSystemClassLoader().getResource("lang/" + name);

                    if (fileUrl == null) {
                        return null;
                    }
                }

                file = new File(fileUrl.getFile());
            }
        }

        Map<String, String> translations = new HashMap<>();
        JsonNode langJson = new ObjectMapper().readTree(file);

        Iterator<Map.Entry<String, JsonNode>> keyIterator = langJson.fields();
        while (keyIterator.hasNext()) {
            Map.Entry<String, JsonNode> key = keyIterator.next();
            translations.put(key.getKey(), key.getValue().asText());
        }

        Language lang = new Language(translations);
        instance = lang;

        return lang;
    }

    public static Language getInstance() {
        return instance;
    }

    public String get(String translationKey) {
        return this.translations.getOrDefault(translationKey, translationKey);
    }
}
