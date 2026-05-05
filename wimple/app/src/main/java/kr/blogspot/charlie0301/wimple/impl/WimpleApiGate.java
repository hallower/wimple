package kr.blogspot.charlie0301.wimple.impl;

import java.util.Hashtable;
import java.util.concurrent.Semaphore;

final class WimpleApiGate {

    private final Hashtable<String, Semaphore> gates = new Hashtable<>();

    WimpleApiGate() {
        register("getAllEntries");
        // Because of delayed account list display, ignore multiple Account get request and response received situation.
        //register("getAllAccounts");
        register("getFinancialState");
        register("getIncomeAndExpense");
        register("getIncomeBudget");
        register("getExpenseBudget");
        register("postNews");
        register("postPayments");
    }

    Semaphore get(String key) {
        return gates.get(key);
    }

    boolean tryAcquire(String key) {
        Semaphore gate = get(key);
        return gate != null && gate.tryAcquire();
    }

    void release(String key) {
        Semaphore gate = get(key);
        if (gate != null) {
            gate.release();
        }
    }

    private void register(String key) {
        gates.put(key, new Semaphore(1));
    }
}
