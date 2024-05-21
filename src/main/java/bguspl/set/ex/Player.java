package bguspl.set.ex;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import bguspl.set.Env;

/**
 * This class manages the players' threads and data
 *
 * @inv id >= 0
 * @inv score >= 0
 */
public class Player implements Runnable {
    private Dealer dealer;
    /**
     * The game environment object.
     */
    private final Env env;

    /**
     * Game entities.
     */
    private final Table table;

    /**
     * The id of the player (starting from 0).
     */
    public final int id;

    /**
     * The thread representing the current player.
     */
    private Thread playerThread;

    /**
     * The thread of the AI (computer) player (an additional thread used to generate
     * key presses).
     */
    private Thread aiThread;

    /**
     * True iff the player is human (not a computer player).
     */
    private final boolean human;

    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    public int[] set = new int[4];
    public boolean freeze = false;// check if need to pause the player
    public int sumForCo = 0;// numbers of keys for computers
    public boolean success = false;
    public int press = -1;
    public BlockingQueue<Integer> queue = new LinkedBlockingQueue<>();
    public BlockingQueue<BlockingQueue> toCom2 = new LinkedBlockingQueue<>();
    public boolean freezeCo = false;
    public List<Integer> listCo = new LinkedList<>();
    public BlockingQueue<Integer> toSet = new LinkedBlockingQueue<>(3);
    public boolean wa = false;

    /**
     * The current score of the player.
     */
    private int score;

    /**
     * The class constructor.
     *
     * @param env    - the environment object.
     * @param dealer - the dealer object.
     * @param table  - the table object.
     * @param id     - the id of the player.
     * @param human  - true iff the player is a human player (i.e. input is provided
     *               manually, via the keyboard).
     */
    public Player(Env env, Dealer dealer, Table table, int id, boolean human) {
        this.env = env;
        this.table = table;
        this.dealer = dealer;
        this.id = id;
        this.human = human;
        for (int i = 0; i < set.length; i++) {
            set[i] = -1;

        }

    }

    /**
     * The main player thread of each player starts here (main loop for the player
     * thread).
     */
    @Override
    public void run() {
        playerThread = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + "starting.");
        if (!human)
            createArtificialIntelligence();
        while (!terminate) {
            if (queue.size() > 0 && !dealer.preventTokens) {
                if (human)
                    work();
                else if (queue.size() >= 3) {

                    int a = queue.remove();
                    int b = queue.remove();
                    int c = queue.remove();
                    set[0] = a;
                    set[1] = b;
                    set[2] = c;
                    set[3] = id;
                    table.placeToken(id, a);
                    table.placeToken(id, b);
                    table.placeToken(id, c);
                    if (dealer.queue.offer(set)) {

                        synchronized (this) {
                            dealer.dealerTH.interrupt();
                            try {
                                wa = true;
                                wait();
                                wa = false;

                            } catch (InterruptedException e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }

                            if (success) {

                                freeze = true;
                                env.ui.setFreeze(id, env.config.pointFreezeMillis);

                                long curr = System.currentTimeMillis() + env.config.pointFreezeMillis;

                                while (curr > System.currentTimeMillis()) {
                                    env.ui.setFreeze(id, curr - System.currentTimeMillis() + 1000);
                                }
                                env.ui.setFreeze(id, 0);
                                success = false;
                                freeze = false;

                            } else {
                                penalty();
                                freeze = false;

                            }
                            table.removeToken(id, a);
                            table.removeToken(id, b);
                            table.removeToken(id, c);
                        }

                    }
                    freeze = false;
                }
            }
        }
        if (!human)
            try {
                aiThread.join();
            } catch (InterruptedException ignored) {
            }
        try {
            playerThread.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");

    }

    public boolean getHuman() {
        return human == true;
    }

    public void work() {
        int a = queue.peek();
        if (toSet.contains(a)) {
            table.removeToken(id, a);
            toSet.remove(a);
            freeze = false;
        } else if (toSet.size() == 2) {
            toSet.add(a);
            table.placeToken(id, a);
            set[0] = toSet.remove();
            set[1] = toSet.remove();
            set[2] = toSet.remove();
            set[3] = id;
            toSet.offer(set[0]);
            toSet.offer(set[1]);
            toSet.offer(set[2]);
            if (dealer.queue.offer(set)) {
                freeze = true;
                dealer.checkSet = true;
                synchronized (this) {
                    try {
                        dealer.dealerTH.interrupt();
                        wait();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                if (success) {

                    freeze = true;
                    env.ui.setFreeze(id, env.config.pointFreezeMillis);

                    long curr = System.currentTimeMillis() + env.config.pointFreezeMillis;

                    while (curr > System.currentTimeMillis()) {
                        env.ui.setFreeze(id, curr - System.currentTimeMillis() + 1000);
                    }
                    env.ui.setFreeze(id, 0);
                    success = false;
                    freeze = false;
                } else {
                    penalty();
                    freeze = false;

                }
            }
        } else if (toSet.size() < 3) {
            toSet.offer(a);
            table.placeToken(id, a);
        }
        queue.remove((Object) a);

    }

    /**
     * Creates an additional thread for an AI (computer) player. The main loop of
     * this thread repeatedly generates
     * key presses. If the queue of key presses is full, the thread waits until it
     * is not full.
     */

    public void comPress() {

        Random rnd = new Random();
        int i = rnd.nextInt(dealer.slotsShuffel.size());
        int a = dealer.slotsShuffel.get(i);
        while (!dealer.slotsShuffel.contains(a)) {
            i = rnd.nextInt(dealer.slotsShuffel.size());
            a = dealer.slotsShuffel.get(i);
        }
        int b = a;

        while ((b == a) || !dealer.slotsShuffel.contains(b)) {
            i = rnd.nextInt(dealer.slotsShuffel.size());
            b = dealer.slotsShuffel.get(i);
        }
        int c = b;
        while (b == c || c == a || !dealer.slotsShuffel.contains(c)) {
            i = rnd.nextInt(dealer.slotsShuffel.size());
            c = dealer.slotsShuffel.get(i);
        }
        keyPressed(a);
        keyPressed(b);
        keyPressed(c);

    }

    private void createArtificialIntelligence() {
        // note: this is a very very smart AI (!)
        aiThread = new Thread(() -> {
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");

            while (!terminate) {
                // TODO implement player key press simulator

                if (!dealer.checkSet) {
                    if (toSet.size() < 3)
                        comPress();

                }

            }
            env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");
        }, "computer-" + id);
        aiThread.start();
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public Thread getThread() {
        if (human)
            return playerThread;
        else
            return aiThread;
    }

    public void terminate() {
        terminate = true;
    }

    /**
     * This method is called when a key is pressed.
     *
     * @param slot - the slot corresponding to the key pressed.
     */
    public void keyPressed(int slot) {
        if (!dealer.preventTokens) {
            if (dealer.reshuff() > System.currentTimeMillis()) {

                if (!freeze) {
                    if (queue.size() < 3)
                        queue.add(slot);

                }
            }
        }
    }

    /**
     * Award a point to a player and perform other related actions.
     *
     * @post - the player's score is increased by 1.
     * @post - the player's score is updated in the ui.
     */
    public void point() {
        // TODO implement

        int ignored = table.countCards(); // this part is just for demonstration in the unit tests
        env.ui.setScore(id, ++score);
    }

    /**
     * Penalize a player and perform other related actions.
     */
    public void penalty() {

        freeze = true;
        env.ui.setFreeze(id, env.config.penaltyFreezeMillis);

        long curr = System.currentTimeMillis() + env.config.penaltyFreezeMillis;

        while (curr > System.currentTimeMillis()) {
            env.ui.setFreeze(id, curr - System.currentTimeMillis() + 1000);
        }
        env.ui.setFreeze(id, 0);
        success = false;
        freeze = false;
    }

    public int getScore() {
        return score;
    }
}
