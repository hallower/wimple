package kr.blogspot.charlie0301.wimple.impl;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertEquals;

@RunWith(RobolectricTestRunner.class)
public class WimpleAuthStoreTest {

    private WimpleAuthStore store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        store = new WimpleAuthStore(context);
        store.clear();
    }

    @Test
    public void saveAuth_persistsTokenSecretAndUserId() {
        store.saveAuth("token-1", "secret-1", "user-1");

        WimpleAuthStore.StoredSession session = store.load();

        assertEquals("token-1", session.token);
        assertEquals("secret-1", session.tokenSecret);
        assertEquals("user-1", session.userID);
    }

    @Test
    public void saveSection_persistsDefaultSection() {
        store.saveSection("s123", "Household");

        WimpleAuthStore.StoredSession session = store.load();

        assertEquals("s123", session.sectionID);
        assertEquals("Household", session.sectionName);
    }

    @Test
    public void clear_blanksStoredSessionValues() {
        store.saveAuth("token-1", "secret-1", "user-1");
        store.saveSection("s123", "Household");

        store.clear();
        WimpleAuthStore.StoredSession session = store.load();

        assertEquals("", session.token);
        assertEquals("", session.tokenSecret);
        assertEquals("", session.userID);
        assertEquals("", session.sectionID);
        assertEquals("", session.sectionName);
    }
}
