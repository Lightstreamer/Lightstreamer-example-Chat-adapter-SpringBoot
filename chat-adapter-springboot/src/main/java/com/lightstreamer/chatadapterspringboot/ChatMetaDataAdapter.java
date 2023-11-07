package com.lightstreamer.chatadapterspringboot;

import java.util.Map;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.OverridingMethodsMustInvokeSuper;

import org.springframework.boot.origin.SystemEnvironmentOrigin;

import com.lightstreamer.adapters.remote.AccessException;
import com.lightstreamer.adapters.remote.CreditsException;
import com.lightstreamer.adapters.remote.DataProviderException;
import com.lightstreamer.adapters.remote.ItemsException;
import com.lightstreamer.adapters.remote.MetadataProvider;
import com.lightstreamer.adapters.remote.MetadataProviderAdapter;
import com.lightstreamer.adapters.remote.MetadataProviderException;
import com.lightstreamer.adapters.remote.Mode;
import com.lightstreamer.adapters.remote.MpnDeviceInfo;
import com.lightstreamer.adapters.remote.MpnSubscriptionInfo;
import com.lightstreamer.adapters.remote.NotificationException;
import com.lightstreamer.adapters.remote.SchemaException;
import com.lightstreamer.adapters.remote.TableInfo;
import com.lightstreamer.adapters.remote.metadata.LiteralBasedProvider;

public class ChatMetaDataAdapter extends LiteralBasedProvider {

    /**
     * Keeps the client context information supplied by Lightstreamer on the
     * new session notifications.
     * Session information is needed to uniquely identify each client.
     */
    private ConcurrentHashMap<String, Map<String, String>> sessions = new ConcurrentHashMap<String, Map<String, String>>();

    /**
     * Used as a Set, to keep unique identifiers for the currently connected
     * clients.
     * Each client is uniquely identified by the client IP address and the
     * HTTP user agent; in case of conflicts, a custom progressive is appended
     * to the user agent. This set lists the concatenations of the current
     * IP and user agent pairs, to help determining uniqueness.
     */
    private ConcurrentHashMap<String, Object> uaIpPairs = new ConcurrentHashMap<String, Object>();

    /**
     * The associated feed to which messages will be forwarded;
     * it is the Data Adapter itself.
     */
    private volatile ChatDataAdapter chatFeed;

    private String name;

    public ChatMetaDataAdapter(String name2) {
        this.name = name2;
    }

    @Override
    public void init(Map<String, String> params, String arg1) throws MetadataProviderException {
        System.out.println("Metadata Adapter initialized.");
    }

    /**
     * Triggered by a client "sendMessage" call.
     * The message encodes a chat message from the client.
     */
    @Override
    public void notifyUserMessage(String user, String session, String message)
            throws NotificationException, CreditsException {

        // we won't introduce blocking operations, hence we can proceed inline

        if (message == null) {
            System.out.println("Null message received");
            throw new NotificationException("Null message received");
        }

        // Split the string on the | character
        // The message must be of the form "CHAT|message"
        String[] pieces = message.split("\\|");

        this.loadChatFeed();
        this.handleChatMessage(pieces, message, session);

        return;
    }

    @Override
    public void notifyNewSession(String user, String session, Map sessionInfo)
            throws CreditsException, NotificationException {

        // we can't have duplicate sessions
        assert (!sessions.containsKey(session));

        /*
         * If needed, modify the user agent and store it directly
         * in the session infos object.
         * Note: we are free to change and store the received object.
         */
        uniquelyIdentifyClient(sessionInfo);

        // Register the session details on the sessions HashMap.
        sessions.put(session, sessionInfo);

    }

    @Override
    public void notifySessionClose(String session) throws NotificationException {
        // the session must exist to be closed
        assert (sessions.containsKey(session));

        // we have to remove session information from the session HashMap
        // and from the pairs "Set"

        Map<String, String> sessionInfo = sessions.get(session);
        String ua = sessionInfo.get("USER_AGENT");
        String IP = sessionInfo.get("REMOTE_IP");

        uaIpPairs.remove(IP + " " + ua);
        sessions.remove(session);
    }

    /**
     * Modifies the clientContext to provide a unique identification
     * for the client session.
     */
    private void uniquelyIdentifyClient(Map clientContext) {

        // extract user agent and ip from session infos
        String ua = (String) clientContext.get("USER_AGENT");
        String ip = (String) clientContext.get("REMOTE_IP");

        /*
         * we need to ensure that each pair IP-User Agent is unique so that
         * the sender can be identified on the client, if such pair is not
         * unique we add a counter on the user agent string
         */

        String count = "";
        int c = 0;
        // synchronize to ensure that we do not generate two identical pairs
        synchronized (uaIpPairs) {
            while (uaIpPairs.containsKey(ip + " " + ua + " " + count)) {
                c++;
                count = "[" + c + "]";
            }

            ua = ua + " " + count;

            uaIpPairs.put(ip + " " + ua, new Object());
        }

        clientContext.put("USER_AGENT", ua);
    }

    private void loadChatFeed() throws CreditsException {
        if (this.chatFeed == null) {
            try {
                // Get the ChatDataAdapter instance to bind it with this
                // Metadata Adapter and send chat messages through it
                this.chatFeed = ChatDataAdapter.feedMap.get(this.name);
            } catch (Throwable t) {
                // It can happen if the Chat Data Adapter jar was not even
                // included in the Adapter Set lib directory (the Chat
                // Data Adapter could not be included in the Adapter Set as well)
                System.out.println("ChatDataAdapter class was not loaded: " + t);
                throw new CreditsException(0, "No chat feed available", "No chat feed available");
            }

            if (this.chatFeed == null) {
                // The feed is not yet available on the static map, maybe the
                // Chat Data Adapter was not included in the Adapter Set
                System.out.println("ChatDataAdapter not found");
                throw new CreditsException(0, "No chat feed available", "No chat feed available");
            }
        }
    }

    private void handleChatMessage(String[] pieces, String message, String session) throws NotificationException {
        // extract session infos

        if (pieces.length != 2) {
            System.out.println("Wrong message received: " + message);
            throw new NotificationException("Wrong message received");
        }

        Map<String, String> sessionInfo = sessions.get(session);
        if (sessionInfo == null) {
            System.out.println("Message received from non-existent session: " + message);
            throw new NotificationException("Wrong message received");
        }
        // read from infos the IP and the user agent of the user
        String ua = sessionInfo.get("USER_AGENT");
        String ip = sessionInfo.get("REMOTE_IP");

        // Check the message, it must be of the form "CHAT|message"
        if (pieces[0].equals("CHAT")) {
            // and send it to the feed
            if (!this.chatFeed.sendMessage(ip, ua, pieces[1])) {
                System.out.println("Wrong message received: " + message);
                throw new NotificationException("Wrong message received");
            }
        } else {
            System.out.println("Wrong message received: " + message);
            throw new NotificationException("Wrong message received");
        }

    }

}
