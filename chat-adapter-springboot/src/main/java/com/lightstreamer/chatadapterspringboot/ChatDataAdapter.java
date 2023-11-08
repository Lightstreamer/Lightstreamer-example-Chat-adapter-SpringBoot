package com.lightstreamer.chatadapterspringboot;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.lightstreamer.adapters.remote.DataProvider;
import com.lightstreamer.adapters.remote.DataProviderException;
import com.lightstreamer.adapters.remote.FailureException;
import com.lightstreamer.adapters.remote.ItemEventListener;
import com.lightstreamer.adapters.remote.SubscriptionException;

public class ChatDataAdapter implements DataProvider {

    private static final int DEFAULT_FLUSH_INTERVAL = 30 * 60 * 1000;

    private static final String ITEM_NAME = "chat_room";

    /**
     * A static map, to be used by the Metadata Adapter to find the data
     * adapter instance; this allows the Metadata Adapter to forward client
     * messages to the adapter.
     * The map allows multiple instances of this Data Adapter to be included
     * in different Adapter Sets. Each instance is identified with the name
     * of the related Adapter Set; defining multiple instances in the same
     * Adapter Set is not allowed.
     */
    public static final ConcurrentHashMap<String, ChatDataAdapter> feedMap = new ConcurrentHashMap<String, ChatDataAdapter>();

    /**
     * Used to enqueue the calls to the listener.
     */
    private final ExecutorService executor;

    /**
     * An object representing the subscription.
     */
    private boolean subscribed;

    /**
     * Boolean flag for periodic flush of snapshot (call clearSnaphot).
     */
    private boolean flushSnapshot;

    /**
     * Interval period (in millis) for snapshot flush.
     */
    private int flushInterval;

    /**
     * Timer for snapshot flush.
     */
    private Timer myTimer;

    private String name;

    private boolean messagesPresence;

    private ItemEventListener listener;

    public ChatDataAdapter(String name, String flushi) {

        this.executor = Executors.newSingleThreadExecutor();

        this.name = name;

        this.flushSnapshot = true;

        int interval = 0;

        try {
            interval = Integer.parseInt(flushi);
        } catch (NumberFormatException nfe) {
            // ops....
        }
        if ((interval > 0) && (interval < Integer.MAX_VALUE)) {
            this.flushInterval = interval;
        } else {
            this.flushInterval = DEFAULT_FLUSH_INTERVAL;
        }

        this.messagesPresence = false;

        System.out.println("ChatDataAdapter initialized");

    }

    @Override
    public void init(Map<String, String> params, String arg1) throws DataProviderException {

        feedMap.put(name, this);

        // Adapter ready
        System.out.println("ChatDataAdapter ready");

        return;
    }

    @Override
    public boolean isSnapshotAvailable(String arg0) throws SubscriptionException {
        return false;
    }

    @Override
    public void setListener(ItemEventListener listener) {
        this.listener = listener;
    }

    @Override
    public void subscribe(String item) throws SubscriptionException, FailureException {

        if (!item.equals(ITEM_NAME)) {
            // only one item for a unique chat room is managed
            throw new SubscriptionException("No such item");
        }

        assert (subscribed == false);

        subscribed = true;

        if (this.flushSnapshot) {
            // Start Thread for periodic flush of the snapshot.
            myTimer = new Timer(true);

            myTimer.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    clearHistory();
                }
            }, new Date(System.currentTimeMillis() + this.flushInterval), this.flushInterval);
        }
    }

    @Override
    public void unsubscribe(String arg0) throws SubscriptionException, FailureException {
        assert (subscribed == true);

        subscribed = false;

        if (myTimer != null) {
            myTimer.cancel();
            myTimer.purge();
            myTimer = null;
        }
    }

    // used in case of flush_snapshot set to true.
    public void clearHistory() {

        System.out.println("Clear snapshot triggered: " + this.subscribed);

        if (this.subscribed == false || this.messagesPresence == false) {
            return;
        }

        System.out.println("Clear snapshot ran.");
        // If we have a listener create a new Runnable to be used as a task to pass the
        // event to the listener
        Runnable updateTask = new Runnable() {
            public void run() {

                System.out.println("Clear snapshot run -2- ");

                // call the update on the listener;
                // in case the listener has just been detached,
                // the listener should detect the case
                listener.clearSnapshot(ITEM_NAME);

                System.out.println("Clear snapshot run -3- ");

            }
        };

        executor.execute(updateTask);

        this.messagesPresence = false;
    }

    /**
     * Accepts message submission for the unique chat room.
     * The sender is identified by an IP address and a nickname.
     */
    public boolean sendMessage(String IP, String nick, String message) {
        final Object currSubscribed = subscribed;
        if (currSubscribed == null) {
            return false;
        }

        // NB no anti-flood control

        if (message == null || message.length() == 0) {
            System.out.println("Received empty or null message");
            return false;
        }
        if (nick == null || nick.length() == 0) {
            System.out.println("Received empty or null nick");
            return false;
        }
        if (IP == null || IP.length() == 0) {
            System.out.println("Received empty or null IP");
            return false;
        }

        this.messagesPresence = true;

        Date now = new Date();
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(now);
        long raw_timestamp = now.getTime();

        System.out.println(timestamp + "|New message: " + IP + "->" + nick + "->" + message);

        final HashMap<String, String> update = new HashMap<String, String>();
        update.put("nick", nick);
        update.put("message", message);
        update.put("timestamp", timestamp);
        update.put("raw_timestamp", String.valueOf(raw_timestamp));
        update.put("IP", IP);

        // If we have a listener create a new Runnable to be used as a task to pass the
        // new update to the listener
        Runnable updateTask = new Runnable() {
            public void run() {
                // call the update on the listener;
                // in case the listener has just been detached,
                // the listener should detect the case
                listener.update(ITEM_NAME, update, false);
            }
        };

        // We add the task on the executor to pass to the listener the actual status
        executor.execute(updateTask);

        return true;
    }
}
