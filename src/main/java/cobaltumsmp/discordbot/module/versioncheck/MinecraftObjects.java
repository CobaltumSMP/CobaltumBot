package cobaltumsmp.discordbot.module.versioncheck;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Object representations of JSON data from {@link Config#MINECRAFT_URL}.
 */
public final class MinecraftObjects {
    private static final SimpleDateFormat DATE_PARSER =
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX");

    /**
     * Parse dates in {@code yyyy-MM-dd'T'HH:mm:ssXXX} format.
     */
    public static Date parseDate(String input) {
        try {
            return DATE_PARSER.parse(input);
        } catch (ParseException e) {
            return new Date(System.currentTimeMillis() - 300 * 1000); // 5 minutes ago
        }
    }

    /**
     * The full response of {@link Config#MINECRAFT_URL}.
     */
    public static final class Response {
        @JsonProperty
        public Latest latest;
        @JsonProperty
        public List<Version> versions;
    }

    /**
     * The {@link Response#latest} object. Represents the latest
     * {@linkplain #release} and {@linkplain #snapshot}
     */
    public static final class Latest {
        @JsonProperty
        public String release;
        @JsonProperty
        public String snapshot;
    }

    /**
     * A single object from the {@linkplain Response#versions Response versions} array.
     */
    public static final class Version {
        @JsonProperty
        public String id;
        @JsonProperty
        public String type;
        @JsonProperty
        public String url;
        @JsonProperty
        public String time;
        @JsonProperty
        public String releaseTime;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Version) {
                return ((Version) obj).id.equals(this.id);
            }

            return super.equals(obj);
        }
    }
}
