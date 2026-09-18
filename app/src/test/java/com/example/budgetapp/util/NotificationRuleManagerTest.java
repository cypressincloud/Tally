package com.example.budgetapp.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class NotificationRuleManagerTest {
    @Test
    public void recentScreenDuplicate_requiresMatchingAmountWithinWindow() {
        long now = 20_000L;

        assertTrue(NotificationRuleManager.isRecentScreenDuplicate(
                now, 15_000L, 15.0, 15.0, 10_000L));
        assertFalse(NotificationRuleManager.isRecentScreenDuplicate(
                now, 15_000L, 15.0, 16.0, 10_000L));
        assertFalse(NotificationRuleManager.isRecentScreenDuplicate(
                now, 10_000L, 15.0, 15.0, 10_000L));
        assertFalse(NotificationRuleManager.isRecentScreenDuplicate(
                now, 0L, 15.0, 15.0, 10_000L));
    }
}
