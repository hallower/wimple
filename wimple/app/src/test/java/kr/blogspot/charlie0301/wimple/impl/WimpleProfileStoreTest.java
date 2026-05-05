package kr.blogspot.charlie0301.wimple.impl;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class WimpleProfileStoreTest {

    private WimpleProfileStore store;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.getApplication();
        store = new WimpleProfileStore(context);
    }

    @Test
    public void getProfilePath_sanitizesEmailLikeUserId() {
        String path = store.getProfilePath("user@example.com");

        assertTrue(path.endsWith("/user_example.com.bin"));
    }
}
