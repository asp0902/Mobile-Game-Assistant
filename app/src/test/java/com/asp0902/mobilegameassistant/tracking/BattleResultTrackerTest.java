package com.asp0902.mobilegameassistant.tracking;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class BattleResultTrackerTest {
    @Test
    public void ninthWinCompletesAndRepeatedResultDoesNotIncrement() {
        RunProgress start = new RunProgress(8, 9, RunStatus.ACTIVE, null);
        BattleResultObservation win = BattleResultRecognizer.INSTANCE.recognize("전투 승리");
        RunProgress completed = BattleResultTracker.INSTANCE.apply(start, win);

        assertEquals(Integer.valueOf(9), completed.getWins());
        assertEquals(RunStatus.COMPLETED, completed.getStatus());
        assertEquals(Integer.valueOf(9), BattleResultTracker.INSTANCE.apply(completed, win).getWins());
    }

    @Test
    public void lossAndUnknownNeverIncreaseWins() {
        RunProgress start = new RunProgress(3, 9, RunStatus.ACTIVE, null);

        assertEquals(Integer.valueOf(3), BattleResultTracker.INSTANCE.apply(start, BattleResultRecognizer.INSTANCE.recognize("전투 패배")).getWins());
        assertEquals(Integer.valueOf(3), BattleResultTracker.INSTANCE.apply(start, BattleResultRecognizer.INSTANCE.recognize("결과 확인 필요")).getWins());
    }
}
