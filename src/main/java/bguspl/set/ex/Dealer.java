package bguspl.set.ex;

import bguspl.set.Config;
import bguspl.set.Env;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * This class manages the dealer's threads and data
 */
public class Dealer implements Runnable {

    /**
     * The game environment object.
     */
    private final Env env;
    boolean init = false;
    public BlockingQueue<int[]> queue;
    public BlockingQueue<int[]> queueHuman = new LinkedBlockingQueue<>();

    /**
     * Game entities.
     */
    public Semaphore fairness = new Semaphore(1, true);
    public long five;
    public Object lock = new Object();
    private final Table table;
    public final Player[] players;
    public Thread dealerTH;
    public boolean checkSet = false;// check if there is a set need to check
    public boolean update = false;
    public boolean preventTokens = true;// prevent from playes puts tokens before the cards
    public boolean into = true;
    /**
     * The list of card ids that are left in the dealer's deck.
     */
    public List<Integer> slotsShuffel = new LinkedList<>();
    private final List<Integer> deck;
    public List<Integer> list = new LinkedList<>();
    /**
     * True iff game should be terminated due to an external event.
     */
    private volatile boolean terminate;

    /**
     * The time when the dealer needs to reshuffle the deck due to turn timeout.
     */
    private long reshuffleTime;

    public Dealer(Env env, Table table, Player[] players) {
        init = true;
        this.env = env;
        this.table = table;
        this.players = players;
        deck = IntStream.range(0, env.config.deckSize).boxed().collect(Collectors.toList());
        for (int i = 0; i < 12 && i < deck.size(); i++) {
            slotsShuffel.add(i);
        }
        queue = new LinkedBlockingQueue<>(players.length);
        five = env.config.turnTimeoutWarningMillis;
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis;

    }

    /**
     * The dealer thread starts here (main loop for the dealer thread).
     */

    @Override
    public void run() {

        dealerTH = Thread.currentThread();
        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " starting.");
        for (int i = 0; i < players.length; i++) {
            Thread Thread_player = new Thread(players[i]);
            Thread_player.start();
        }

        while (!shouldFinish()) {
            placeCardsOnTable();
            timerLoop();
            removeAllCardsFromTable();
        }
        announceWinners();

        env.logger.log(Level.INFO, "Thread " + Thread.currentThread().getName() + " terminated.");

    }

    /**
     * The inner loop of the dealer thread that runs as long as the countdown did
     * not time out.
     */
    public long reshuff() {
        return reshuffleTime;
    }

    private void timerLoop() {
        while (!terminate && System.currentTimeMillis() < reshuffleTime) {
            sleepUntilWokenOrTimeout();
            updateTimerDisplay(false);
            removeCardsFromTable();

        }
    }

    /**
     * Called when the game should be terminated due to an external event.
     */
    public void terminate() {
        for (int i = players.length - 1; i >= 0; i--) {

            players[i].terminate();

        }

        terminate = true;
        try {
            dealerTH.join();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }

    /**
     * Check if the game should be terminated or the game end conditions are met.
     *
     * @return true iff the game should be finished.
     */
    private boolean shouldFinish() {
        return terminate || env.util.findSets(deck, 1).size() == 0;
    }

    /**
     * Checks cards should be removed from the table and removes them.
     */
    public void SET() {

        if (!queue.isEmpty()) {

            int[] dataslot = queue.peek();
            int playerId = dataslot[3];
            int[] ans = { table.slotToCard[dataslot[0]], table.slotToCard[dataslot[1]], table.slotToCard[dataslot[2]] };
            if (env.util.testSet(ans)
                    && (dataslot[0] != dataslot[1] || dataslot[1] != dataslot[2] || dataslot[0] != dataslot[2])) {

                players[playerId].point();

                for (int i = 0; i < 12 && i < deck.size(); i++) {
                    if (ans[0] == deck.get(i) | ans[1] == deck.get(i) | ans[2] == deck.get(i)) {
                        deck.remove(i);
                        i = i - 1;
                    }
                }
                for (int i = 0; i < players.length; i++) {// update the players queue when
                    if (i != playerId) {
                        int count = players[i].toSet.size();
                        for (int j = 0; j < count; j++) {
                            int card_a = players[i].toSet.remove();
                            int card_b = -1;
                            if (!players[i].getHuman() && queue.size() > 0) {
                                card_b = players[i].queue.remove();
                            }
                            if (dataslot[0] == card_a | dataslot[1] == card_a | dataslot[2] == card_a) {
                            } else {
                                players[i].toSet.offer(card_a);
                                if (card_b != -1) {
                                    players[i].queue.add(card_b);
                                }
                            }
                        }
                    }
                }

                for (int i = 0; i < 3; i++) {
                    table.removeCard(dataslot[i]);
                    env.ui.removeTokens(dataslot[i]);
                }
                if (deck.size() > 11) {

                    table.placeCard(deck.get(9), dataslot[0]);
                    table.placeCard(deck.get(10), dataslot[1]);
                    table.placeCard(deck.get(11), dataslot[2]);

                } else {
                    slotsShuffel.remove(table.cardToSlot[ans[0]]);
                    slotsShuffel.remove(table.cardToSlot[ans[1]]);
                    slotsShuffel.remove(table.cardToSlot[ans[2]]);
                }
                players[playerId].set[0] = -1;
                players[playerId].set[1] = -1;
                players[playerId].set[2] = -1;
                reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 300;
                players[playerId].success = true;

            } else {

                players[playerId].success = false;

            }
            synchronized (players[playerId]) {
                players[playerId].notifyAll();

            }
            checkSet = false;
            update = true;
            queue.remove();

        }

        update = true;
        if (reshuffleTime > System.currentTimeMillis()) {
            preventTokens = false;
        } else {
            preventTokens = true;
        }
        if (env.util.findSets(deck, 20).size() == 0) {
            terminate = true;
        }

    }

    public void SETHuman() {

        if (!queueHuman.isEmpty()) {

            int[] dataslot = queueHuman.peek();
            int playerId = dataslot[3];
            if (players[playerId].toSet.size() == 3) {
                int[] ans = { table.slotToCard[dataslot[0]], table.slotToCard[dataslot[1]],
                        table.slotToCard[dataslot[2]] };
                if (env.util.testSet(ans)) {

                    players[playerId].point();

                    for (int i = 0; i < 12 && i < deck.size(); i++) {
                        if (ans[0] == deck.get(i) | ans[1] == deck.get(i) | ans[2] == deck.get(i)) {
                            deck.remove(i);

                            i = i - 1;
                        }
                    }

                    for (int i = 0; i < players.length; i++) {// update the players queue when

                        int count = players[i].toSet.size();
                        for (int j = 0; j < count; j++) {
                            int card_a = players[i].toSet.remove();
                            int card_b = -1;
                            if (!players[i].getHuman() && queue.size() > 0) {
                                card_b = players[i].queue.remove();
                            }
                            if (dataslot[0] == card_a | dataslot[1] == card_a | dataslot[2] == card_a) {

                            } else {
                                players[i].toSet.offer(card_a);
                                if (card_b != -1) {
                                    players[i].queue.add(card_b);
                                }
                            }
                        }
                    }

                    for (int i = 0; i < 3; i++) {
                        table.removeCard(dataslot[i]);
                        env.ui.removeTokens(dataslot[i]);

                    }
                    if (deck.size() > 11) {

                        table.placeCard(deck.get(9), dataslot[0]);

                        table.placeCard(deck.get(10), dataslot[1]);

                        table.placeCard(deck.get(11), dataslot[2]);

                    } else {
                        slotsShuffel.remove(dataslot[0]);
                        slotsShuffel.remove(dataslot[1]);
                        slotsShuffel.remove(dataslot[2]);
                    }
                    players[playerId].set[0] = -1;
                    players[playerId].set[1] = -1;
                    players[playerId].set[2] = -1;

                    reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 300;
                    players[playerId].success = true;

                } else {

                    players[playerId].success = false;

                }
                checkSet = false;

                synchronized (players[playerId]) {
                    players[playerId].notifyAll();

                }
                update = true;

                queueHuman.remove();

            }

            update = true;
            if (reshuffleTime > System.currentTimeMillis()) {
                preventTokens = false;
            } else {
                preventTokens = true;
            }
            if (env.util.findSets(deck, 20).size() == 0) {
                terminate = true;
            }

        }
    }

    private void removeCardsFromTable() {
        // TODO implement
        if (reshuffleTime <= System.currentTimeMillis()) {
            env.ui.setCountdown(0, true);
        }

    }

    /**
     * Check if any cards can be removed from the deck and placed on the table.
     */
    private void placeCardsOnTable() {
        preventTokens = true;
        env.ui.removeTokens();
        while (!queue.isEmpty()) {
            queue.remove();
        }
        while (!queueHuman.isEmpty()) {
            queueHuman.remove();
        }
        for (int i = 0; i < players.length; i++) {
            players[i].freeze = false;
            while (!players[i].queue.isEmpty()) {
                players[i].queue.remove();
            }
            while (!players[i].toSet.isEmpty()) {
                players[i].toSet.remove();
            }
        }

        Collections.shuffle(deck);
        Collections.shuffle(slotsShuffel);
        for (int i = 0; i < 12 && i < deck.size(); i++) {
            table.placeCard(deck.get(i), slotsShuffel.get(i));

        }

        env.ui.setCountdown(env.config.turnTimeoutMillis, terminate);
        reshuffleTime = System.currentTimeMillis() + env.config.turnTimeoutMillis + 200;

        preventTokens = false;
        five = env.config.turnTimeoutWarningMillis;
        for (int i = 0; i < players.length; i++) {
            if (players[i].wa) {
                synchronized (players[i]) {
                    players[i].notifyAll();
                }
            }
        }
    }

    /**
     * Sleep for a fixed amount of time or until the thread is awakened for some
     * purpose.
     */

    private void sleepUntilWokenOrTimeout() {

        try {
            if (reshuffleTime - env.config.turnTimeoutWarningMillis + 100 <= System.currentTimeMillis()) {
                dealerTH.sleep(10);
            } else {
                dealerTH.sleep(1000);
            }
        } catch (InterruptedException e) {
            preventTokens = true;
            checkSet = true;
            if (!terminate) {
                if (!queue.isEmpty())
                    SET();

            }
        }

    }

    /**
     * Reset and/or update the countdown and the countdown display.
     */
    private void updateTimerDisplay(boolean reset) {

        if (env.util.findSets(deck, 1).size() == 0) {
            terminate = true;

        }
        boolean red = false;
        if (reshuffleTime - System.currentTimeMillis() > 0) {
            long keep = reshuffleTime - System.currentTimeMillis();
            if (reshuffleTime - env.config.turnTimeoutWarningMillis <= System.currentTimeMillis() + 150) {
                red = true;

            }
            if (!red) {
                if (!checkSet)
                    env.ui.setCountdown(((reshuffleTime - System.currentTimeMillis() + 150) / 1000) * 1000, red);
                else if (update) {
                    reshuffleTime = keep + System.currentTimeMillis();
                    env.ui.setCountdown(((reshuffleTime - System.currentTimeMillis() + 150) / 1000) * 1000, red);
                    update = false;
                }
            } else {

                if (queue.isEmpty()) {
                    env.ui.setCountdown(five, red);
                    five = ((reshuffleTime - System.currentTimeMillis() + 3) / 10) * 10;
                } else if (update) {

                    keep = reshuffleTime - System.currentTimeMillis();
                    update = false;
                    reshuffleTime = keep + System.currentTimeMillis();
                    five = ((reshuffleTime - System.currentTimeMillis() + 3) / 10) * 10;
                    env.ui.setCountdown(five, red);
                }
                // reshuffleTime = keep + System.currentTimeMillis();

                update = false;
            }
        } else {
            env.ui.setCountdown(0, red);
        }

    }

    /**
     * Returns all the cards from the table to the deck.
     */
    private void removeAllCardsFromTable() {
        // TODO implement
        if (env.util.findSets(deck, 1).size() == 0) {
            terminate = true;
        }

        preventTokens = true;
        env.ui.removeTokens();
        for (int i = 0; i < 12; i++) {
            table.removeCard(i);

        }

    }

    /**
     * Check who is/are the winner/s and displays them.
     */
    private void announceWinners() {
        // TODO implement
        List<Integer> check = new LinkedList<>();
        int max = players[0].getScore();
        int id = 0;
        for (int i = 1; i < players.length; i++) {
            if (max < players[i].getScore()) {
                max = players[i].getScore();
                id = i;
            }
        }

        check.add(id);
        for (int i = 0; i < players.length; i++) {
            if (players[id].getScore() == players[i].getScore()) {
                if (i != id) {
                    check.add(i);
                }
            }
        }
        int[] winners = new int[check.size()];
        for (int i = 0; i < winners.length; i++) {
            winners[i] = check.remove(0);
        }

        env.ui.announceWinner(winners);
        env.ui.removeTokens();
        if (env.util.findSets(deck, 1).size() == 0) {
            for (int i = players.length - 1; i >= 0; i--) {
                players[i].terminate();
            }
            terminate = true;
        }
    }

    public boolean GetTerminate() {
        return terminate;
    }
}
