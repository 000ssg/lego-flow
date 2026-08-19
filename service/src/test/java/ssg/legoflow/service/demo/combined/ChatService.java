package ssg.legoflow.service.demo.combined;

import ssg.legoflow.blocks.Context;
import ssg.legoflow.service.AbstractService;
import ssg.legoflow.service.ServiceContext;
import ssg.legoflow.service.ServiceDescriptor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
/**
 * Complex demo: multi-session chat with pub/sub-like broadcasting.
 * Manages rooms, sessions, and message distribution combining
 * procedural service lifecycle with functional subscriber callbacks.
 *
 * @since 0.1
 */
public class ChatService extends AbstractService<String, String> {

    private final Map<String, List<Consumer<String>>> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> sessions = new ConcurrentHashMap<>();
    private final List<String> messageLog = new CopyOnWriteArrayList<>();

    public ChatService() {
        super(String.class, String.class, new ServiceDescriptor("chat", "Multi-room chat service"));
    }

    public void createRoom(String roomName) {
        rooms.putIfAbsent(roomName, new CopyOnWriteArrayList<>());
    }

    public void joinRoom(String sessionId, String roomName) {
        sessions.put(sessionId, roomName);
    }

    public void subscribe(String roomName, Consumer<String> listener) {
        rooms.computeIfAbsent(roomName, _ -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public void broadcast(String roomName, String message) {
        messageLog.add("[" + roomName + "] " + message);
        var subscribers = rooms.get(roomName);
        if (subscribers != null) {
            subscribers.forEach(s -> s.accept(message));
        }
    }

    public void sendToRoom(String sessionId, String message) {
        var room = sessions.get(sessionId);
        if (room != null) {
            broadcast(room, message);
        }
    }

    public List<String> getMessageLog() {
        return List.copyOf(messageLog);
    }

    public List<String> getRooms() {
        return List.copyOf(rooms.keySet());
    }

    public String getSessionRoom(String sessionId) {
        return sessions.get(sessionId);
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String[] convertToOutput(Context ctx, String... input) {
        for (var msg : input) {
            messageLog.add(msg);
        }
        return input;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected String[] convertToInput(Context ctx, String... output) {
        return output;
    }

    @Override
    protected void doDisconnect(ServiceContext ctx) {
        sessions.clear();
        rooms.clear();
        messageLog.clear();
    }
}
