package fr.nivcoo.challenges.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MainConfigHudTest {
    @Test
    void defaultHudConfigurationIsValid() {
        assertDoesNotThrow(new MainConfig()::validate);
    }

    @Test
    void disabledHudDoesNotConsumeItsSubsections() {
        MainConfig config = new MainConfig();
        config.hud.enabled = false;
        config.hud.animation = null;
        config.hud.rankingBadge = null;

        assertDoesNotThrow(config::validate);
    }

    @Test
    void enabledAnimationRejectsInvalidColorFrames() {
        MainConfig config = new MainConfig();
        config.hud.animation.colors = List.of("yellow", "#FFFFFF");

        assertThrows(IllegalArgumentException.class, config::validate);
    }

    @Test
    void disabledRankingBadgeDoesNotConsumeItsTimingFields() {
        MainConfig config = new MainConfig();
        config.hud.rankingBadge.enabled = false;
        config.hud.rankingBadge.priority = Integer.MAX_VALUE;
        config.hud.rankingBadge.durationTicks = 0;
        config.hud.rankingBadge.accumulationWindowTicks = 0;

        assertDoesNotThrow(config::validate);
    }
}
