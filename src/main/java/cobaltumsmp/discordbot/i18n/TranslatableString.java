package cobaltumsmp.discordbot.i18n;

/**
 * A string that can be translated to different {@linkplain Language languages}.
 */
public class TranslatableString {
    public final String translationKey;

    protected TranslatableString(String translationKey) {
        this.translationKey = translationKey;
    }

    public String get() {
        return this.toString();
    }

    @Override
    public String toString() {
        return Language.getInstance().get(this.translationKey);
    }

    @Override
    public int hashCode() {
        return this.translationKey.hashCode();
    }
}
