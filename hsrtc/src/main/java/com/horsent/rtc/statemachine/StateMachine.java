package com.horsent.rtc.statemachine;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.function.Predicate;

/**
 * 状态机
 */
public class StateMachine {
    // Name of the state machine and used as logging tag
    private String mName;

    /** 退出标志*/
    private static final int SM_QUIT_CMD = -1;

    /** 初始化标志 */
    private static final int SM_INIT_CMD = -2;

    /**
     * Convenience constant that maybe returned by processMessage
     * to indicate the the message was processed and is not to be
     * processed by parent states
     */
    public static final boolean HANDLED = true;

    /**
     * Convenience constant that maybe returned by processMessage
     * to indicate the the message was NOT processed and is to be
     * processed by parent states
     */
    public static final boolean NOT_HANDLED = false;

    private static class SmHandler extends Handler {

        /** true if StateMachine has quit */
        private boolean mHasQuit = false;

        /** The debug flag */
        private boolean mDbg = false;

        /** The SmHandler object, identifies that message is internal */
        private static final Object mSmHandlerObj = new Object();

        /** The current message */
        private Message mMsg;

        /** true if construction of the state machine has not been completed */
        private boolean mIsConstructionCompleted;

        /** Stack used to manage the current hierarchy of states */
        private StateInfo mStateStack[];

        /** Top of mStateStack */
        private int mStateStackTopIndex = -1;

        /** A temporary stack used to manage the state stack */
        private StateInfo mTempStateStack[];

        /** The top of the mTempStateStack */
        private int mTempStateStackCount;

        /** State used when state machine is halted */
        private HaltingState mHaltingState = new HaltingState();

        /** State used when state machine is quitting */
        private QuittingState mQuittingState = new QuittingState();

        /** Reference to the StateMachine */
        private StateMachine mSm;

        /**
         * Information about a state.
         * Used to maintain the hierarchy.
         */
        private class StateInfo {
            /** The state */
            State state;

            /** The parent of this state, null if there is no parent */
            StateInfo parentStateInfo;

            /** True when the state has been entered and on the stack */
            boolean active;

            /**
             * Convert StateInfo to string
             */
            @Override
            public String toString() {
                return "state=" + state.getName() + ",active=" + active + ",parent="
                        + ((parentStateInfo == null) ? "null" : parentStateInfo.state.getName());
            }
        }

        /** The map of all of the states in the state machine */
        private HashMap<State, StateInfo> mStateInfo = new HashMap<State, StateInfo>();

        /** The initial state that will process the first message */
        private State mInitialState;

        /** The destination state when transitionTo has been invoked */
        private State mDestState;

        /**
         * Indicates if a transition is in progress
         *
         * This will be true for all calls of State.exit and all calls of State.enter except for the
         * last enter call for the current destination state.
         */
        private boolean mTransitionInProgress = false;

        /** The list of deferred messages */
        private ArrayList<Message> mDeferredMessages = new ArrayList<Message>();

        /**
         * State entered when transitionToHaltingState is called.
         */
        private class HaltingState extends State {
            @Override
            public boolean processMessage(Message msg) {
                mSm.haltedProcessMessage(msg);
                return true;
            }
        }

        /**
         * State entered when a valid quit message is handled.
         */
        private class QuittingState extends State {
            @Override
            public boolean processMessage(Message msg) {
                return NOT_HANDLED;
            }
        }

        /**
         * Handle messages sent to the state machine by calling
         * the current state's processMessage. It also handles
         * the enter/exit calls and placing any deferred messages
         * back onto the queue when transitioning to a new state.
         */
        @Override
        public final void handleMessage(Message msg) {
            if (!mHasQuit) {
                if (mSm != null && msg.what != SM_INIT_CMD && msg.what != SM_QUIT_CMD) {
                    mSm.onPreHandleMessage(msg);
                }

                if (mDbg) mSm.log("handleMessage: E msg.what=" + msg.what);

                /** Save the current message */
                mMsg = msg;

                /** State that processed the message */
                State msgProcessedState = null;
                if (mIsConstructionCompleted || (mMsg.what == SM_QUIT_CMD)) {
                    /** Normal path */
                    msgProcessedState = processMsg(msg);
                } else if (!mIsConstructionCompleted && (mMsg.what == SM_INIT_CMD)
                        && (mMsg.obj == mSmHandlerObj)) {
                    /** Initial one time path. */
                    mIsConstructionCompleted = true;
                    invokeEnterMethods(0);
                } else {
                    throw new RuntimeException("StateMachine.handleMessage: "
                            + "The start method not called, received msg: " + msg);
                }
                performTransitions(msgProcessedState, msg);

                // We need to check if mSm == null here as we could be quitting.
                if (mDbg && mSm != null) mSm.log("handleMessage: X");

                if (mSm != null && msg.what != SM_INIT_CMD && msg.what != SM_QUIT_CMD) {
                    mSm.onPostHandleMessage(msg);
                }
            }
        }

        /**
         * Do any transitions
         * @param msgProcessedState is the state that processed the message
         */
        private void performTransitions(State msgProcessedState, Message msg) {
            /**
             * If transitionTo has been called, exit and then enter
             * the appropriate states. We loop on this to allow
             * enter and exit methods to use transitionTo.
             */
            State orgState = mStateStack[mStateStackTopIndex].state;

            State destState = mDestState;
            if (destState != null) {
                /**
                 * Process the transitions including transitions in the enter/exit methods
                 */
                while (true) {
                    if (mDbg) mSm.log("handleMessage: new destination call exit/enter");

                    /**
                     * Determine the states to exit and enter and return the
                     * common ancestor state of the enter/exit states. Then
                     * invoke the exit methods then the enter methods.
                     */
                    StateInfo commonStateInfo = setupTempStateStackWithStatesToEnter(destState);
                    // flag is cleared in invokeEnterMethods before entering the target state
                    mTransitionInProgress = true;
                    invokeExitMethods(commonStateInfo);
                    int stateStackEnteringIndex = moveTempStateStackToStateStack();
                    invokeEnterMethods(stateStackEnteringIndex);

                    /**
                     * Since we have transitioned to a new state we need to have
                     * any deferred messages moved to the front of the message queue
                     * so they will be processed before any other messages in the
                     * message queue.
                     */
                    moveDeferredMessageAtFrontOfQueue();

                    if (destState != mDestState) {
                        // A new mDestState so continue looping
                        destState = mDestState;
                    } else {
                        // No change in mDestState so we're done
                        break;
                    }
                }
                mDestState = null;
            }

            /**
             * After processing all transitions check and
             * see if the last transition was to quit or halt.
             */
            if (destState != null) {
                if (destState == mQuittingState) {
                    /**
                     * Call onQuitting to let subclasses cleanup.
                     */
                    mSm.onQuitting();
                    cleanupAfterQuitting();
                } else if (destState == mHaltingState) {
                    /**
                     * Call onHalting() if we've transitioned to the halting
                     * state. All subsequent messages will be processed in
                     * in the halting state which invokes haltedProcessMessage(msg);
                     */
                    mSm.onHalting();
                }
            }
        }

        /**
         * Cleanup all the static variables and the looper after the SM has been quit.
         */
        private final void cleanupAfterQuitting() {
            if (mSm.mSmThread != null) {
                // If we made the thread then quit looper which stops the thread.
                getLooper().quit();
                mSm.mSmThread = null;
            }

            mSm.mSmHandler = null;
            mSm = null;
            mMsg = null;
            mStateStack = null;
            mTempStateStack = null;
            mStateInfo.clear();
            mInitialState = null;
            mDestState = null;
            mDeferredMessages.clear();
            mHasQuit = true;
        }

        /**
         * Complete the construction of the state machine.
         */
        private final void completeConstruction() {
            if (mDbg) mSm.log("completeConstruction: E");

            /**
             * Determine the maximum depth of the state hierarchy
             * so we can allocate the state stacks.
             */
            int maxDepth = 0;
            for (StateInfo si : mStateInfo.values()) {
                int depth = 0;
                for (StateInfo i = si; i != null; depth++) {
                    i = i.parentStateInfo;
                }
                if (maxDepth < depth) {
                    maxDepth = depth;
                }
            }
            if (mDbg) mSm.log("completeConstruction: maxDepth=" + maxDepth);

            mStateStack = new StateInfo[maxDepth];
            mTempStateStack = new StateInfo[maxDepth];
            setupInitialStateStack();

            /** Sending SM_INIT_CMD message to invoke enter methods asynchronously */
            sendMessageAtFrontOfQueue(obtainMessage(SM_INIT_CMD, mSmHandlerObj));

            if (mDbg) mSm.log("completeConstruction: X");
        }

        /**
         * Process the message. If the current state doesn't handle
         * it, call the states parent and so on. If it is never handled then
         * call the state machines unhandledMessage method.
         * @return the state that processed the message
         */
        private final State processMsg(Message msg) {
            StateInfo curStateInfo = mStateStack[mStateStackTopIndex];
            if (mDbg) {
                mSm.log("processMsg: " + curStateInfo.state.getName());
            }

            if (isQuit(msg)) {
                transitionTo(mQuittingState);
            } else {
                while (!curStateInfo.state.processMessage(msg)) {
                    /**
                     * Not processed
                     */
                    curStateInfo = curStateInfo.parentStateInfo;
                    if (curStateInfo == null) {
                        /**
                         * No parents left so it's not handled
                         */
                        mSm.unhandledMessage(msg);
                        break;
                    }
                    if (mDbg) {
                        mSm.log("processMsg: " + curStateInfo.state.getName());
                    }
                }
            }
            return (curStateInfo != null) ? curStateInfo.state : null;
        }

        /**
         * Call the exit method for each state from the top of stack
         * up to the common ancestor state.
         */
        private final void invokeExitMethods(StateInfo commonStateInfo) {
            while ((mStateStackTopIndex >= 0)
                    && (mStateStack[mStateStackTopIndex] != commonStateInfo)) {
                State curState = mStateStack[mStateStackTopIndex].state;
                if (mDbg) mSm.log("invokeExitMethods: " + curState.getName());
                curState.exit();
                mStateStack[mStateStackTopIndex].active = false;
                mStateStackTopIndex -= 1;
            }
        }

        /**
         * Invoke the enter method starting at the entering index to top of state stack
         */
        private final void invokeEnterMethods(int stateStackEnteringIndex) {
            for (int i = stateStackEnteringIndex; i <= mStateStackTopIndex; i++) {
                if (stateStackEnteringIndex == mStateStackTopIndex) {
                    // Last enter state for transition
                    mTransitionInProgress = false;
                }
                if (mDbg) mSm.log("invokeEnterMethods: " + mStateStack[i].state.getName());
                mStateStack[i].state.enter();
                mStateStack[i].active = true;
            }
            mTransitionInProgress = false; // ensure flag set to false if no methods called
        }

        /**
         * Move the deferred message to the front of the message queue.
         */
        private final void moveDeferredMessageAtFrontOfQueue() {
            /**
             * The oldest messages on the deferred list must be at
             * the front of the queue so start at the back, which
             * as the most resent message and end with the oldest
             * messages at the front of the queue.
             */
            for (int i = mDeferredMessages.size() - 1; i >= 0; i--) {
                Message curMsg = mDeferredMessages.get(i);
                if (mDbg) mSm.log("moveDeferredMessageAtFrontOfQueue; what=" + curMsg.what);
                sendMessageAtFrontOfQueue(curMsg);
            }
            mDeferredMessages.clear();
        }

        /**
         * Move the contents of the temporary stack to the state stack
         * reversing the order of the items on the temporary stack as
         * they are moved.
         *
         * @return index into mStateStack where entering needs to start
         */
        private final int moveTempStateStackToStateStack() {
            int startingIndex = mStateStackTopIndex + 1;
            int i = mTempStateStackCount - 1;
            int j = startingIndex;
            while (i >= 0) {
                if (mDbg) mSm.log("moveTempStackToStateStack: i=" + i + ",j=" + j);
                mStateStack[j] = mTempStateStack[i];
                j += 1;
                i -= 1;
            }

            mStateStackTopIndex = j - 1;
            if (mDbg) {
                mSm.log("moveTempStackToStateStack: X mStateStackTop=" + mStateStackTopIndex
                        + ",startingIndex=" + startingIndex + ",Top="
                        + mStateStack[mStateStackTopIndex].state.getName());
            }
            return startingIndex;
        }

        /**
         * Setup the mTempStateStack with the states we are going to enter.
         *
         * This is found by searching up the destState's ancestors for a
         * state that is already active i.e. StateInfo.active == true.
         * The destStae and all of its inactive parents will be on the
         * TempStateStack as the list of states to enter.
         *
         * @return StateInfo of the common ancestor for the destState and
         * current state or null if there is no common parent.
         */
        private final StateInfo setupTempStateStackWithStatesToEnter(State destState) {
            /**
             * Search up the parent list of the destination state for an active
             * state. Use a do while() loop as the destState must always be entered
             * even if it is active. This can happen if we are exiting/entering
             * the current state.
             */
            mTempStateStackCount = 0;
            StateInfo curStateInfo = mStateInfo.get(destState);
            do {
                mTempStateStack[mTempStateStackCount++] = curStateInfo;
                curStateInfo = curStateInfo.parentStateInfo;
            } while ((curStateInfo != null) && !curStateInfo.active);

            if (mDbg) {
                mSm.log("setupTempStateStackWithStatesToEnter: X mTempStateStackCount="
                        + mTempStateStackCount + ",curStateInfo: " + curStateInfo);
            }
            return curStateInfo;
        }

        /**
         * Initialize StateStack to mInitialState.
         */
        private final void setupInitialStateStack() {
            if (mDbg) {
                mSm.log("setupInitialStateStack: E mInitialState=" + mInitialState.getName());
            }

            StateInfo curStateInfo = mStateInfo.get(mInitialState);
            for (mTempStateStackCount = 0; curStateInfo != null; mTempStateStackCount++) {
                mTempStateStack[mTempStateStackCount] = curStateInfo;
                curStateInfo = curStateInfo.parentStateInfo;
            }

            // Empty the StateStack
            mStateStackTopIndex = -1;

            moveTempStateStackToStateStack();
        }

        /**
         * @return current message
         */
        private final Message getCurrentMessage() {
            return mMsg;
        }

        /**
         * @return current state
         */
        private final IState getCurrentState() {
            return mStateStack[mStateStackTopIndex].state;
        }

        /**
         * Add a new state to the state machine. Bottom up addition
         * of states is allowed but the same state may only exist
         * in one hierarchy.
         *
         * @param state the state to add
         * @param parent the parent of state
         * @return stateInfo for this state
         */
        private final StateInfo addState(State state, State parent) {
            if (mDbg) {
                mSm.log("addStateInternal: E state=" + state.getName() + ",parent="
                        + ((parent == null) ? "" : parent.getName()));
            }
            StateInfo parentStateInfo = null;
            if (parent != null) {
                parentStateInfo = mStateInfo.get(parent);
                if (parentStateInfo == null) {
                    // Recursively add our parent as it's not been added yet.
                    parentStateInfo = addState(parent, null);
                }
            }
            StateInfo stateInfo = mStateInfo.get(state);
            if (stateInfo == null) {
                stateInfo = new StateInfo();
                mStateInfo.put(state, stateInfo);
            }

            // Validate that we aren't adding the same state in two different hierarchies.
            if ((stateInfo.parentStateInfo != null)
                    && (stateInfo.parentStateInfo != parentStateInfo)) {
                throw new RuntimeException("state already added");
            }
            stateInfo.state = state;
            stateInfo.parentStateInfo = parentStateInfo;
            stateInfo.active = false;
            if (mDbg) mSm.log("addStateInternal: X stateInfo: " + stateInfo);
            return stateInfo;
        }

        /**
         * Remove a state from the state machine. Will not remove the state if it is currently
         * active or if it has any children in the hierarchy.
         * @param state the state to remove
         */
        private void removeState(State state) {
            final StateInfo stateInfo = mStateInfo.get(state);
            if (stateInfo == null || stateInfo.active) {
                return;
            }
            boolean isParent = false;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                isParent = mStateInfo.values().stream()
                        .anyMatch(new Predicate<StateInfo>() {
                            @Override
                            public boolean test(StateInfo si) {
                                return si.parentStateInfo == stateInfo;
                            }
                        });
            }else{
                Iterator<StateInfo> iterator = mStateInfo.values().iterator();
                while (iterator.hasNext()) {
                    StateInfo stateInfo1 = iterator.next();
                    if (stateInfo1.parentStateInfo == null) {
                        continue;
                    }

                    if (stateInfo1.parentStateInfo == stateInfo) {
                        isParent = true;
                        break;
                    }
                }
            }
            if (isParent) {
                return;
            }
            mStateInfo.remove(state);
        }

        /**
         * Constructor
         *
         * @param looper for dispatching messages
         * @param sm the hierarchical state machine
         */
        private SmHandler(Looper looper, StateMachine sm) {
            super(looper);
            mSm = sm;

            addState(mHaltingState, null);
            addState(mQuittingState, null);
        }

        /** @see StateMachine#setInitialState(State) */
        private final void setInitialState(State initialState) {
            if (mDbg) mSm.log("setInitialState: initialState=" + initialState.getName());
            mInitialState = initialState;
        }

        /** @see StateMachine#transitionTo(IState) */
        private final void transitionTo(IState destState) {
            if (mTransitionInProgress) {
                Log.wtf(mSm.mName, "transitionTo called while transition already in progress to " +
                        mDestState + ", new target state=" + destState);
            }
            mDestState = (State) destState;
            if (mDbg) mSm.log("transitionTo: destState=" + mDestState.getName());
        }

        /** @see StateMachine#deferMessage(Message) */
        private final void deferMessage(Message msg) {
            if (mDbg) mSm.log("deferMessage: msg=" + msg.what);

            /* Copy the "msg" to "newMsg" as "msg" will be recycled */
            Message newMsg = obtainMessage();
            newMsg.copyFrom(msg);

            mDeferredMessages.add(newMsg);
        }

        /** @see StateMachine#quit() */
        private final void quit() {
            if (mDbg) mSm.log("quit:");
            sendMessage(obtainMessage(SM_QUIT_CMD, mSmHandlerObj));
        }

        /** @see StateMachine#quitNow() */
        private final void quitNow() {
            if (mDbg) mSm.log("quitNow:");
            sendMessageAtFrontOfQueue(obtainMessage(SM_QUIT_CMD, mSmHandlerObj));
        }

        /** Validate that the message was sent by quit or quitNow. */
        private final boolean isQuit(Message msg) {
            return (msg.what == SM_QUIT_CMD) && (msg.obj == mSmHandlerObj);
        }

        /** @see StateMachine#isDbg() */
        private final boolean isDbg() {
            return mDbg;
        }

        /** @see StateMachine#setDbg(boolean) */
        private final void setDbg(boolean dbg) {
            mDbg = dbg;
        }

    }

    private SmHandler mSmHandler;
    private HandlerThread mSmThread;

    /**
     * Initialize.
     *
     * @param looper for this state machine
     * @param name of the state machine
     */
    private void initStateMachine(String name, Looper looper) {
        mName = name;
        mSmHandler = new SmHandler(looper, this);
    }

    /**
     * Constructor creates a StateMachine with its own thread.
     *
     * @param name of the state machine
     */
    protected StateMachine(String name) {
        mSmThread = new HandlerThread(name);
        mSmThread.start();
        Looper looper = mSmThread.getLooper();

        initStateMachine(name, looper);
    }

    /**
     * Constructor creates a StateMachine using the looper.
     *
     * @param name of the state machine
     */
    protected StateMachine(String name, Looper looper) {
        initStateMachine(name, looper);
    }

    /**
     * Constructor creates a StateMachine using the handler.
     *
     * @param name of the state machine
     */
    protected StateMachine(String name, Handler handler) {
        initStateMachine(name, handler.getLooper());
    }

    /**
     * Notifies subclass that the StateMachine handler is about to process the Message msg
     * @param msg The message that is being handled
     */
    protected void onPreHandleMessage(Message msg) {
    }

    /**
     * Notifies subclass that the StateMachine handler has finished processing the Message msg and
     * has possibly transitioned to a new state.
     * @param msg The message that is being handled
     */
    protected void onPostHandleMessage(Message msg) {
    }

    /**
     * Add a new state to the state machine
     * @param state the state to add
     * @param parent the parent of state
     */
    public final void addState(State state, State parent) {
        mSmHandler.addState(state, parent);
    }

    /**
     * Add a new state to the state machine, parent will be null
     * @param state to add
     */
    public final void addState(State state) {
        mSmHandler.addState(state, null);
    }

    /**
     * Removes a state from the state machine, unless it is currently active or if it has children.
     * @param state state to remove
     */
    public final void removeState(State state) {
        mSmHandler.removeState(state);
    }

    /**
     * Set the initial state. This must be invoked before
     * and messages are sent to the state machine.
     *
     * @param initialState is the state which will receive the first message.
     */
    public final void setInitialState(State initialState) {
        mSmHandler.setInitialState(initialState);
    }

    /**
     * @return current message
     */
    public final Message getCurrentMessage() {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return null;
        return smh.getCurrentMessage();
    }

    /**
     * @return current state
     */
    public final IState getCurrentState() {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return null;
        return smh.getCurrentState();
    }

    /**
     * transition to destination state. Upon returning
     * from processMessage the current state's exit will
     * be executed and upon the next message arriving
     * destState.enter will be invoked.
     *
     * this function can also be called inside the enter function of the
     * previous transition target, but the behavior is undefined when it is
     * called mid-way through a previous transition (for example, calling this
     * in the enter() routine of a intermediate node when the current transition
     * target is one of the nodes descendants).
     *
     * @param destState will be the state that receives the next message.
     */
    public final void transitionTo(IState destState) {
        mSmHandler.transitionTo(destState);
    }

    /**
     * transition to halt state. Upon returning
     * from processMessage we will exit all current
     * states, execute the onHalting() method and then
     * for all subsequent messages haltedProcessMessage
     * will be called.
     */
    public final void transitionToHaltingState() {
        mSmHandler.transitionTo(mSmHandler.mHaltingState);
    }

    /**
     * Defer this message until next state transition.
     * Upon transitioning, all deferred messages will be
     * placed on the queue and reprocessed in the original
     * order. (i.e. The next state the oldest messages will
     * be processed first)
     *
     * @param msg is deferred until the next transition.
     */
    public final void deferMessage(Message msg) {
        mSmHandler.deferMessage(msg);
    }

    /**
     * Called when message wasn't handled
     *
     * @param msg that couldn't be handled.
     */
    protected void unhandledMessage(Message msg) {
        if (mSmHandler.mDbg) loge(" - unhandledMessage: msg.what=" + msg.what);
    }

    /**
     * Called for any message that is received after
     * transitionToHalting is called.
     */
    protected void haltedProcessMessage(Message msg) {
    }

    /**
     * This will be called once after handling a message that called
     * transitionToHalting. All subsequent messages will invoke
     * {@link StateMachine#haltedProcessMessage(Message)}
     */
    protected void onHalting() {
    }

    /**
     * This will be called once after a quit message that was NOT handled by
     * the derived StateMachine. The StateMachine will stop and any subsequent messages will be
     * ignored. In addition, if this StateMachine created the thread, the thread will
     * be stopped after this method returns.
     */
    protected void onQuitting() {
    }

    /**
     * @return the name
     */
    public final String getName() {
        return mName;
    }

    /**
     * @return the string for msg.what
     */
    protected String getWhatToString(int what) {
        return null;
    }

    /**
     * @return Handler, maybe null if state machine has quit.
     */
    public final Handler getHandler() {
        return mSmHandler;
    }

    /**
     * Get a message and set Message.target state machine handler.
     *
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @return  A Message object from the global pool
     */
    public final Message obtainMessage() {
        return Message.obtain(mSmHandler);
    }

    /**
     * Get a message and set Message.target state machine handler, what.
     *
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @param what is the assigned to Message.what.
     * @return  A Message object from the global pool
     */
    public final Message obtainMessage(int what) {
        return Message.obtain(mSmHandler, what);
    }

    /**
     * Get a message and set Message.target state machine handler,
     * what and obj.
     *
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @param what is the assigned to Message.what.
     * @param obj is assigned to Message.obj.
     * @return  A Message object from the global pool
     */
    public final Message obtainMessage(int what, Object obj) {
        return Message.obtain(mSmHandler, what, obj);
    }

    /**
     * Get a message and set Message.target state machine handler,
     * what, arg1 and arg2
     *
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @param what  is assigned to Message.what
     * @param arg1  is assigned to Message.arg1
     * @return  A Message object from the global pool
     */
    public final Message obtainMessage(int what, int arg1) {
        // use this obtain so we don't match the obtain(h, what, Object) method
        return Message.obtain(mSmHandler, what, arg1, 0);
    }

    /**
     * Get a message and set Message.target state machine handler,
     * what, arg1 and arg2
     *
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @param what  is assigned to Message.what
     * @param arg1  is assigned to Message.arg1
     * @param arg2  is assigned to Message.arg2
     * @return  A Message object from the global pool
     */
    public final Message obtainMessage(int what, int arg1, int arg2) {
        return Message.obtain(mSmHandler, what, arg1, arg2);
    }

    /**
     * Get a message and set Message.target state machine handler,
     * what, arg1, arg2 and obj
     *
     * Note: The handler can be null if the state machine has quit,
     * which means target will be null and may cause a AndroidRuntimeException
     * in MessageQueue#enqueMessage if sent directly or if sent using
     * StateMachine#sendMessage the message will just be ignored.
     *
     * @param what  is assigned to Message.what
     * @param arg1  is assigned to Message.arg1
     * @param arg2  is assigned to Message.arg2
     * @param obj is assigned to Message.obj
     * @return  A Message object from the global pool
     */
    public final Message obtainMessage(int what, int arg1, int arg2, Object obj) {
        return Message.obtain(mSmHandler, what, arg1, arg2, obj);
    }

    /**
     * Enqueue a message to this state machine.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(int what) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessage(obtainMessage(what));
    }

    /**
     * Enqueue a message to this state machine.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(int what, Object obj) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessage(obtainMessage(what, obj));
    }

    /**
     * Enqueue a message to this state machine.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(int what, int arg1) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessage(obtainMessage(what, arg1));
    }

    /**
     * Enqueue a message to this state machine.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(int what, int arg1, int arg2) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessage(obtainMessage(what, arg1, arg2));
    }

    /**
     * Enqueue a message to this state machine.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(int what, int arg1, int arg2, Object obj) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessage(obtainMessage(what, arg1, arg2, obj));
    }

    /**
     * Enqueue a message to this state machine.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessage(Message msg) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessage(msg);
    }

    /**
     * Enqueue a message to this state machine after a delay.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(int what, long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageDelayed(obtainMessage(what), delayMillis);
    }

    /**
     * Enqueue a message to this state machine after a delay.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(int what, Object obj, long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageDelayed(obtainMessage(what, obj), delayMillis);
    }

    /**
     * Enqueue a message to this state machine after a delay.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(int what, int arg1, long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageDelayed(obtainMessage(what, arg1), delayMillis);
    }

    /**
     * Enqueue a message to this state machine after a delay.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(int what, int arg1, int arg2, long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageDelayed(obtainMessage(what, arg1, arg2), delayMillis);
    }

    /**
     * Enqueue a message to this state machine after a delay.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(int what, int arg1, int arg2, Object obj,
                                   long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageDelayed(obtainMessage(what, arg1, arg2, obj), delayMillis);
    }

    /**
     * Enqueue a message to this state machine after a delay.
     *
     * Message is ignored if state machine has quit.
     */
    public void sendMessageDelayed(Message msg, long delayMillis) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageDelayed(msg, delayMillis);
    }

    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     *
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(int what) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageAtFrontOfQueue(obtainMessage(what));
    }

    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     *
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(int what, Object obj) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageAtFrontOfQueue(obtainMessage(what, obj));
    }

    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     *
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(int what, int arg1) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageAtFrontOfQueue(obtainMessage(what, arg1));
    }


    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     *
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(int what, int arg1, int arg2) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageAtFrontOfQueue(obtainMessage(what, arg1, arg2));
    }

    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     *
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(int what, int arg1, int arg2, Object obj) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageAtFrontOfQueue(obtainMessage(what, arg1, arg2, obj));
    }

    /**
     * Enqueue a message to the front of the queue for this state machine.
     * Protected, may only be called by instances of StateMachine.
     *
     * Message is ignored if state machine has quit.
     */
    protected final void sendMessageAtFrontOfQueue(Message msg) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.sendMessageAtFrontOfQueue(msg);
    }

    /**
     * Removes a message from the message queue.
     * Protected, may only be called by instances of StateMachine.
     */
    protected final void removeMessages(int what) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.removeMessages(what);
    }

    /**
     * Removes a message from the deferred messages queue.
     */
    protected final void removeDeferredMessages(int what) {
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        Iterator<Message> iterator = smh.mDeferredMessages.iterator();
        while (iterator.hasNext()) {
            Message msg = iterator.next();
            if (msg.what == what) iterator.remove();
        }
    }

    /**
     * Check if there are any pending messages with code 'what' in deferred messages queue.
     */
    protected final boolean hasDeferredMessages(int what) {
        SmHandler smh = mSmHandler;
        if (smh == null) return false;

        Iterator<Message> iterator = smh.mDeferredMessages.iterator();
        while (iterator.hasNext()) {
            Message msg = iterator.next();
            if (msg.what == what) return true;
        }

        return false;
    }

    /**
     * Check if there are any pending posts of messages with code 'what' in
     * the message queue. This does NOT check messages in deferred message queue.
     */
    protected final boolean hasMessages(int what) {
        SmHandler smh = mSmHandler;
        if (smh == null) return false;

        return smh.hasMessages(what);
    }

    /**
     * Validate that the message was sent by
     * {@link StateMachine#quit} or {@link StateMachine#quitNow}.
     * */
    protected final boolean isQuit(Message msg) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return msg.what == SM_QUIT_CMD;

        return smh.isQuit(msg);
    }

    /**
     * Quit the state machine after all currently queued up messages are processed.
     */
    public final void quit() {
        // mSmHandler can be null if the state machine is already stopped.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.quit();
    }

    /**
     * Quit the state machine immediately all currently queued messages will be discarded.
     */
    public final void quitNow() {
        // mSmHandler can be null if the state machine is already stopped.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.quitNow();
    }

    /**
     * @return if debugging is enabled
     */
    public boolean isDbg() {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return false;

        return smh.isDbg();
    }

    /**
     * Set debug enable/disabled.
     *
     * @param dbg is true to enable debugging.
     */
    public void setDbg(boolean dbg) {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        smh.setDbg(dbg);
    }

    /**
     * Start the state machine.
     */
    public void start() {
        // mSmHandler can be null if the state machine has quit.
        SmHandler smh = mSmHandler;
        if (smh == null) return;

        /** Send the complete construction message */
        smh.completeConstruction();
    }

    @Override
    public String toString() {
        String name = "(null)";
        String state = "(null)";
        try {
            name = mName.toString();
            state = mSmHandler.getCurrentState().getName().toString();
        } catch (NullPointerException npe) {
            // Will use default(s) initialized above.
        }
        return "name=" + name + " state=" + state;
    }

    /**
     * Log with debug
     *
     * @param s is string log
     */
    protected void log(String s) {
        if(useLog()) {
            Log.i(mName, s);
        }
    }

    /**
     * Log with error attribute
     *
     * @param s is string log
     */
    protected void loge(String s) {
        if(useLog()) {
            Log.e(mName, s);
        }
    }

    protected boolean useLog() {
        return true;
    }
}
