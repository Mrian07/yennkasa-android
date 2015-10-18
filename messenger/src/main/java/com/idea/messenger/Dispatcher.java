package com.idea.messenger;

import java.io.Closeable;
import java.util.Collection;

/**
 * an interface that defines how one can {@code dispatch()}
 * a given data. this can be via a network socket or other means.
 * <p>
 * it forms the basis for all dispatchers.
 * </p>
 *
 * @author Null-Pointer on 5/26/2015.
 * @see AbstractMessageDispatcher
 * @see ParseDispatcher
 */
interface Dispatcher<T> extends Closeable {
    void dispatch(T t);

    void dispatch(Collection<T> t);

    boolean cancelDispatchMayPossiblyFail(T t);

    void addMonitor(DispatcherMonitor monitor);

    void removeMonitor(DispatcherMonitor monitor);

    void close(); //overriden to undo the throws IOException signature

    /**
     * an interface to be implemented if one wants to monitor
     * a given dispatcher. Example an analytic class can implement
     * this interface and track all messages sent. or a stats class
     * can implement this interface and track the number of bytes  transferred by a given
     * dispatcher.
     *
     * @author Null-Pointer on 5/26/2015.
     */
    interface DispatcherMonitor {
        void onDispatchFailed(String id, String reason);

        void onDispatchSucceed(String id);

        void onProgress(String id, int progress,int max);

        void onAllDispatched();
    }
}
