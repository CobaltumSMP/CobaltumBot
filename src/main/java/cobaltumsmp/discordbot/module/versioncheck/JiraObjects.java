package cobaltumsmp.discordbot.module.versioncheck;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Object representations of JSON data from {@link Config#JIRA_URL}.
 */
public final class JiraObjects {
    /**
     * The full response of {@link Config#JIRA_URL}.
     */
    public static final class Response {
        @JsonProperty
        public String expand;
        @JsonProperty
        public String self;
        @JsonProperty
        public String id;
        @JsonProperty
        public String key;
        @JsonProperty
        public String description;
        @JsonProperty
        public Object lead;
        @JsonProperty
        public List<Object> components = null;
        @JsonProperty
        public List<Object> issueTypes = null;
        @JsonProperty
        public String url;
        @JsonProperty
        public String assigneeType;
        @JsonProperty
        public List<Version> versions = null;
        @JsonProperty
        public String name;
        @JsonProperty
        public Object roles;
        @JsonProperty
        public Object avatarUrls;
        @JsonProperty
        public Object projectCategory;
        @JsonProperty
        public String projectTypeKey;
        @JsonProperty
        public boolean archived;
    }

    /**
     * A single object from the {@linkplain Response#versions Response versions} array.
     */
    public static final class Version {
        @JsonProperty
        public String self;
        @JsonProperty
        public String id;
        @JsonProperty
        public String description;
        @JsonProperty
        public String name;
        @JsonProperty
        public boolean archived;
        @JsonProperty
        public boolean released;
        @JsonIgnore
        public String startDate;
        @JsonProperty
        public String releaseDate;
        @JsonIgnore
        public String userStartDate;
        @JsonProperty
        public String userReleaseDate;
        @JsonProperty
        public Integer projectId;

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Version) {
                return ((Version) obj).id.equals(this.id);
            }

            return super.equals(obj);
        }
    }
}
