package cobaltumsmp.discordbot.module.ticketsystem.auto;

import cobaltumsmp.discordbot.module.ticketsystem.TicketConfig;
import org.apache.cayenne.BaseDataObject;
import org.apache.cayenne.exp.Property;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Class _Ticket was generated by Cayenne.
 * It is probably a good idea to avoid changing this class manually,
 * since it may be overwritten next time code is regenerated.
 * If you need to make any customizations, please use subclass.
 */
@SuppressWarnings("checkstyle:TypeName")
public abstract class _Ticket extends BaseDataObject {

    private static final long serialVersionUID = 1L; 

    public static final String GLOBAL_TICKET_ID_PK_COLUMN = "globalTicketId";

    public static final Property<Long> BOT_MSG_ID = Property.create("botMsgId", Long.class);
    public static final Property<Long> CHANNEL_ID = Property.create("channelId", Long.class);
    public static final Property<Boolean> CLOSED = Property.create("closed", Boolean.class);
    public static final Property<Long> OWNER_ID = Property.create("ownerId", Long.class);
    public static final Property<Integer> TICKET_ID = Property.create("ticketId", Integer.class);
    public static final Property<TicketConfig> TICKET_CONFIG = Property.create("ticketConfig",
            TicketConfig.class);

    protected long botMsgId;
    protected long channelId;
    protected boolean closed;
    protected long ownerId;
    protected int ticketId;

    protected Object ticketConfig;

    public void setBotMsgId(long botMsgId) {
        beforePropertyWrite("botMsgId", this.botMsgId, botMsgId);
        this.botMsgId = botMsgId;
    }

    public long getBotMsgId() {
        beforePropertyRead("botMsgId");
        return this.botMsgId;
    }

    public void setChannelId(long channelId) {
        beforePropertyWrite("channelId", this.channelId, channelId);
        this.channelId = channelId;
    }

    public long getChannelId() {
        beforePropertyRead("channelId");
        return this.channelId;
    }

    public void setClosed(boolean closed) {
        beforePropertyWrite("closed", this.closed, closed);
        this.closed = closed;
    }

    public boolean isClosed() {
        beforePropertyRead("closed");
        return this.closed;
    }

    public void setOwnerId(long ownerId) {
        beforePropertyWrite("ownerId", this.ownerId, ownerId);
        this.ownerId = ownerId;
    }

    public long getOwnerId() {
        beforePropertyRead("ownerId");
        return this.ownerId;
    }

    public void setTicketId(int ticketId) {
        beforePropertyWrite("ticketId", this.ticketId, ticketId);
        this.ticketId = ticketId;
    }

    public int getTicketId() {
        beforePropertyRead("ticketId");
        return this.ticketId;
    }

    public void setTicketConfig(TicketConfig ticketConfig) {
        setToOneTarget("ticketConfig", ticketConfig, true);
    }

    public TicketConfig getTicketConfig() {
        return (TicketConfig) readProperty("ticketConfig");
    }

    @Override
    public Object readPropertyDirectly(String propName) {
        if (propName == null) {
            throw new IllegalArgumentException();
        }

        switch (propName) {
            case "botMsgId":
                return this.botMsgId;
            case "channelId":
                return this.channelId;
            case "closed":
                return this.closed;
            case "ownerId":
                return this.ownerId;
            case "ticketId":
                return this.ticketId;
            case "ticketConfig":
                return this.ticketConfig;
            default:
                return super.readPropertyDirectly(propName);
        }
    }

    @Override
    public void writePropertyDirectly(String propName, Object val) {
        if (propName == null) {
            throw new IllegalArgumentException();
        }

        switch (propName) {
            case "botMsgId":
                this.botMsgId = val == null ? 0 : (long) val;
                break;
            case "channelId":
                this.channelId = val == null ? 0 : (long) val;
                break;
            case "closed":
                this.closed = val == null ? false : (boolean) val;
                break;
            case "ownerId":
                this.ownerId = val == null ? 0 : (long) val;
                break;
            case "ticketId":
                this.ticketId = val == null ? 0 : (int) val;
                break;
            case "ticketConfig":
                this.ticketConfig = val;
                break;
            default:
                super.writePropertyDirectly(propName, val);
        }
    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        writeSerialized(out);
    }

    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        readSerialized(in);
    }

    @Override
    protected void writeState(ObjectOutputStream out) throws IOException {
        super.writeState(out);
        out.writeLong(this.botMsgId);
        out.writeLong(this.channelId);
        out.writeBoolean(this.closed);
        out.writeLong(this.ownerId);
        out.writeInt(this.ticketId);
        out.writeObject(this.ticketConfig);
    }

    @Override
    protected void readState(ObjectInputStream in) throws IOException, ClassNotFoundException {
        super.readState(in);
        this.botMsgId = in.readLong();
        this.channelId = in.readLong();
        this.closed = in.readBoolean();
        this.ownerId = in.readLong();
        this.ticketId = in.readInt();
        this.ticketConfig = in.readObject();
    }

}
