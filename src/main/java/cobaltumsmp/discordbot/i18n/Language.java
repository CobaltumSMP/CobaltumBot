package cobaltumsmp.discordbot.i18n;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
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
        Reader reader;

        if (!file.exists()) {
            // Find in lang sub directory of working one
            file = new File("lang", name);

            if (!file.exists()) {
                // Find in classpath
                InputStream stream = getResourceAsStream(name);

                if (stream == null) {
                    stream = getResourceAsStream("lang/" + name);

                    if (stream == null) {
                        return null;
                    }
                }

                reader = new InputStreamReader(stream);
            } else {
                reader = new FileReader(file);
            }
        } else {
            reader = new FileReader(file);
        }

        Map<String, String> translations = new HashMap<>();
        JsonNode langJson = new ObjectMapper().readTree(reader);

        Iterator<Map.Entry<String, JsonNode>> keyIterator = langJson.fields();
        while (keyIterator.hasNext()) {
            Map.Entry<String, JsonNode> key = keyIterator.next();
            translations.put(key.getKey(), key.getValue().asText());
        }

        Language lang = new Language(translations);
        instance = lang;

        return lang;
    }

    private static InputStream getResourceAsStream(String name) {
        // Try with system class loader
        InputStream stream = ClassLoader.getSystemClassLoader().getResourceAsStream(name);

        if (stream == null) {
            // Try with Class#getClassLoader();
            stream = Language.class.getResourceAsStream(name);
        }

        return stream;
    }

    public static Language getInstance() {
        return instance;
    }

    public String get(String translationKey) {
        return this.translations.getOrDefault(translationKey, translationKey);
    }
}
