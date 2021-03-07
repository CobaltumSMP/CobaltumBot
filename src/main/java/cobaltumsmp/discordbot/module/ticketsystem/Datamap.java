package cobaltumsmp.discordbot.module.ticketsystem;

import cobaltumsmp.discordbot.module.ticketsystem.auto._Datamap;

public class Datamap extends _Datamap {

    private static Datamap instance;

    private Datamap() {}

    public static Datamap getInstance() {
        if(instance == null) {
            instance = new Datamap();
        }

        return instance;
    }
}
