package com.asp0902.mobilegameassistant.capture;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CaptureSessionTest {
    @Test
    public void cancelledConsentReturnsToIdle() {
        CaptureSession session = new CaptureSession();

        session.awaitConsent();
        session.idle("화면 공유가 취소되었습니다.");

        assertEquals(
                new TrackingState.Idle("화면 공유가 취소되었습니다."),
                session.getState().getValue()
        );
    }
}
