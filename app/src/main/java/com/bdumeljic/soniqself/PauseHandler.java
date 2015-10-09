package com.bdumeljic.soniqself;

import android.os.Handler;
import android.os.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Message Handler class that supports buffering up of messages when the activity is paused i.e. in the background.
 */
public class PauseHandler extends Handler {

    /**
     * Message Queue Buffer
     */
    private final List<Message> messageQueueBuffer = Collections.synchronizedList(new ArrayList<Message>());
    private final List<Runnable> runnableQueueBuffer = Collections.synchronizedList(new ArrayList<Runnable>());
    private final List<Long> runnableDelayQueueBuffer = Collections.synchronizedList(new ArrayList<Long>());

    /**
     * Flag indicating the pause state
     */
    private boolean paused;

    /**
     * Resume the handler
     */
    final public void resume() {
        paused = false;

        while (runnableQueueBuffer.size() > 0) {
            final Runnable rnble = runnableQueueBuffer.get(0);
            runnableQueueBuffer.remove(0);
            final long delay = runnableDelayQueueBuffer.get(0);
            runnableDelayQueueBuffer.remove(0);
            postDelayed(rnble, delay);
        }
    }

    /**
     * Pause the handler
     */
    final public void pause() {
        paused = true;
    }

    /**
     * Store the runnable if we have been paused, otherwise handle it now.
     */
    public void postPausedDelayed(Runnable r, long delayMillis) {
        if (paused) {
            runnableQueueBuffer.add(r);
            runnableDelayQueueBuffer.add(delayMillis);
        } else {
            postDelayed(r, delayMillis);
        }
    }
}
