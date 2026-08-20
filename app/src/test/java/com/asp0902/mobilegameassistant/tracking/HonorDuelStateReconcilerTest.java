package com.asp0902.mobilegameassistant.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class HonorDuelStateReconcilerTest {
    @Test
    public void appliesRequiredStateSourcePriority() {
        ObservedValue<Integer> low = new ObservedValue<>(10, ReconciliationSource.OBSERVED_LOW, .9f);
        ObservedValue<Integer> prior = new ObservedValue<>(20, ReconciliationSource.PREVIOUS_SNAPSHOT, .5f);
        ObservedValue<Integer> detail = new ObservedValue<>(30, ReconciliationSource.DETAIL_POPUP, .95f);
        ObservedValue<Integer> user = new ObservedValue<>(40, ReconciliationSource.USER_CORRECTION, 1f);

        assertEquals(Integer.valueOf(20), HonorDuelStateReconciler.INSTANCE.select(prior, low).getValue());
        assertEquals(Integer.valueOf(30), HonorDuelStateReconciler.INSTANCE.select(prior, detail).getValue());
        assertEquals(Integer.valueOf(40), HonorDuelStateReconciler.INSTANCE.select(detail, user).getValue());
    }

    @Test
    public void equalSourceKeepsHigherConfidenceValue() {
        ObservedValue<Integer> older = new ObservedValue<>(27, ReconciliationSource.OBSERVED_HIGH, .95f);
        ObservedValue<Integer> weaker = new ObservedValue<>(15, ReconciliationSource.OBSERVED_HIGH, .90f);

        assertEquals(Integer.valueOf(27), HonorDuelStateReconciler.INSTANCE.select(older, weaker).getValue());
    }
}
