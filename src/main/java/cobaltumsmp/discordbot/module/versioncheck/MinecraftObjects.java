package cobaltumsmp.discordbot.module.versioncheck;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class MinecraftObjects {
    public static class Response {
        @JsonProperty
        public Latest latest;
        @JsonProperty
        public List<Version> versions;
    }

    public static class Latest {
        @JsonProperty
        public String release;
        @JsonProperty
        public String snapshot;
    }

    public static class Version {
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
