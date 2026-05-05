package kr.blogspot.charlie0301.wimple.impl;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class WimpleApiQuotaTest {

    @Test
    public void setRemaining_parsesRestOfApiCount() {
        WimpleApiQuota quota = new WimpleApiQuota();

        quota.setRemaining("17");

        assertEquals(17, quota.getRemaining());
    }

    @Test
    public void setTotalByApiCountLevel_mapsKnownWhooingLevels() {
        WimpleApiQuota quota = new WimpleApiQuota();

        quota.setTotalByApiCountLevel(2);
        assertEquals(200, quota.getTotal());

        quota.setTotalByApiCountLevel(3);
        assertEquals(1000, quota.getTotal());

        quota.setTotalByApiCountLevel(1);
        assertEquals(30, quota.getTotal());
    }
}
