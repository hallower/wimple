package kr.blogspot.charlie0301.wimple.impl;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class WimpleApiGateTest {

    @Test
    public void tryAcquire_allowsOneInFlightCallPerKey() {
        WimpleApiGate gate = new WimpleApiGate();

        assertTrue(gate.tryAcquire("getAllEntries"));
        assertFalse(gate.tryAcquire("getAllEntries"));

        gate.release("getAllEntries");
        assertTrue(gate.tryAcquire("getAllEntries"));
    }

    @Test
    public void get_returnsSemaphoreForLegacyManagerInterface() {
        WimpleApiGate gate = new WimpleApiGate();

        assertNotNull(gate.get("getAllEntries"));
    }
}
