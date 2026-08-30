package fr.nivcoo.challenges.hook.integration.edenhud;

import fr.nivcoo.challenges.Challenges;
import fr.nivcoo.challenges.config.MainConfig;
import fr.nivcoo.challenges.service.ChallengeHudBridge;
import fr.nivcoo.challenges.service.ChallengeHudView;
import fr.nivcoo.edenhud.api.AEdenHUD;
import fr.nivcoo.edenhud.api.EdenHUDAPI;
import fr.nivcoo.edenhud.api.model.HudAnimationSequence;
import fr.nivcoo.edenhud.api.model.HudBadge;
import fr.nivcoo.edenhud.api.model.HudBadgeDelta;
import fr.nivcoo.edenhud.api.model.HudBadgePolicy;
import fr.nivcoo.edenhud.api.model.HudIcon;
import fr.nivcoo.edenhud.api.model.HudLine;
import fr.nivcoo.edenhud.api.model.HudLineRole;
import fr.nivcoo.edenhud.api.model.HudProgress;
import fr.nivcoo.edenhud.api.model.HudStyle;
import fr.nivcoo.edenhud.api.model.HudWidgetCapacityPolicy;
import fr.nivcoo.edenhud.api.model.HudWidgetContext;
import fr.nivcoo.edenhud.api.model.HudWidgetDefinition;
import fr.nivcoo.edenhud.api.model.HudWidgetState;
import fr.nivcoo.edenhud.api.model.HudWidgetView;
import fr.nivcoo.edenhud.api.model.HudZone;
import fr.nivcoo.edenhud.api.service.HudRegistrationScope;
import fr.nivcoo.edenhud.api.service.HudService;
import fr.nivcoo.utilsz.core.config.ConfigManager;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;

final class EdenHudIntegration implements ChallengeHudBridge {
    private static final Key WIDGET_KEY = Key.key("challenges", "current");
    private static final PlainTextComponentSerializer PLAIN =
            PlainTextComponentSerializer.plainText();

    private final Challenges plugin;
    private final AEdenHUD api;
    private final HudService hud;
    private final HudRegistrationScope scope;
    private final Optional<HudAnimationSequence<TextColor>> colorAnimation;
    private final AtomicBoolean closed = new AtomicBoolean();

    private EdenHudIntegration(Challenges plugin, AEdenHUD api) {
        this.plugin = plugin;
        this.api = api;
        hud = api.hud();
        scope = api.registrations().forPlugin(plugin);
        MainConfig.Hud settings = settings();
        colorAnimation = colorAnimation(settings.animation);
        try {
            scope.registerWidget(new HudWidgetDefinition(
                    WIDGET_KEY,
                    zone(settings.region),
                    settings.priority,
                    capacityPolicy(settings.capacityPolicy),
                    settings.retentionPriority,
                    colorAnimation.map(HudAnimationSequence::frameDurationTicks).orElse(0)
            ), this::provide);
            scope.subscribeAvailability((previous, current) -> refreshDisplay(current.playerId()));
        } catch (RuntimeException | LinkageError error) {
            close();
            throw error;
        }
    }

    static EdenHudIntegration create(Challenges plugin) {
        AEdenHUD api = EdenHUDAPI.find().orElseGet(() -> {
            RegisteredServiceProvider<AEdenHUD> registration =
                    Bukkit.getServicesManager().getRegistration(AEdenHUD.class);
            return registration == null ? null : registration.getProvider();
        });
        if (api == null) throw new IllegalStateException("EdenHUD API is unavailable.");
        return new EdenHudIntegration(plugin, api);
    }

    @Override
    public boolean active(UUID playerUuid) {
        if (closed.get() || playerUuid == null || !settings().enabled) return false;
        Plugin edenHud = Bukkit.getPluginManager().getPlugin("EdenHUD");
        if (edenHud == null || !edenHud.isEnabled()) return false;
        try {
            return hud.isAvailable(playerUuid);
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    @Override
    public void invalidate(UUID playerUuid) {
        if (closed.get() || playerUuid == null) return;
        try {
            hud.invalidate(playerUuid, WIDGET_KEY);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Override
    public void clear(UUID playerUuid) {
        if (closed.get() || playerUuid == null) return;
        Player player = Bukkit.getPlayer(playerUuid);
        try {
            if (player != null) scope.clearBadges(player);
        } catch (RuntimeException | LinkageError ignored) {
        }
        invalidate(playerUuid);
    }

    @Override
    public void rankingChanged(
            UUID playerUuid,
            UUID runUuid,
            int previousPlace,
            int currentPlace
    ) {
        if (closed.get() || playerUuid == null || runUuid == null
                || previousPlace <= 0 || currentPlace <= 0 || previousPlace == currentPlace) return;
        int movement = previousPlace - currentPlace;
        MainConfig.Hud.RankingBadge settings = settings().rankingBadge;
        if (!settings.enabled || (movement < 0 && !settings.showLosses) || !active(playerUuid)) return;
        Player player = Bukkit.getPlayer(playerUuid);
        if (player == null || !player.isOnline()) return;
        Duration duration = ticks(settings.durationTicks);
        Duration accumulationWindow = ticks(settings.accumulationWindowTicks);
        try {
            scope.showBadge(player, new HudBadge(
                    Key.key("challenges", "ranking/" + runUuid),
                    WIDGET_KEY,
                    settings.priority,
                    new HudBadgeDelta(movement),
                    duration,
                    accumulationWindow,
                    badgePolicy(settings.policy)
            ));
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            scope.close();
        } catch (RuntimeException | LinkageError ignored) {
        }
        try {
            api.registrations().unregister(plugin);
        } catch (RuntimeException | LinkageError ignored) {
        }
    }

    private CompletionStage<HudWidgetView> provide(HudWidgetContext context) {
        if (closed.get() || !context.availability().available()) {
            return CompletableFuture.completedFuture(HudWidgetView.hidden());
        }
        Optional<ChallengeHudView> current =
                plugin.getChallengesManager().hudView(context.player());
        return CompletableFuture.completedFuture(current
                .map(view -> view(view, context))
                .orElseGet(HudWidgetView::hidden));
    }

    private HudWidgetView view(ChallengeHudView current, HudWidgetContext context) {
        MainConfig.Hud settings = settings();
        Map<String, Object> placeholders = placeholders(current, settings);
        List<HudLine> lines = new ArrayList<>();
        addLine(lines, settings.title, HudLineRole.LABEL, "TITLE",
                current, context, settings, placeholders);
        addLine(lines, settings.objectiveLine, HudLineRole.BODY, "OBJECTIVE",
                current, context, settings, placeholders);
        switch (current.phase()) {
            case COUNTDOWN ->
                    addLine(lines, settings.countdownLine, HudLineRole.META, "COUNTDOWN",
                            current, context, settings, placeholders);
            case ACTIVE -> {
                addLine(lines, settings.scoreLine, HudLineRole.VALUE, "SCORE",
                        current, context, settings, placeholders);
                addLine(lines, settings.timerLine, HudLineRole.META, "TIMER",
                        current, context, settings, placeholders);
            }
            case DRAINING -> {
                addLine(lines, settings.scoreLine, HudLineRole.VALUE, "SCORE",
                        current, context, settings, placeholders);
                addLine(lines, settings.drainingLine, HudLineRole.META, "DRAINING",
                        current, context, settings, placeholders);
            }
        }
        Optional<HudProgress> progress = progress(current, settings);
        if (lines.isEmpty() && progress.isEmpty()) return HudWidgetView.hidden();
        return new HudWidgetView(
                state(current.phase()),
                HudStyle.of(settings.layout, style(current.phase(), settings)),
                settings.showIcon
                        ? Optional.of(icon(settings.icon))
                        : Optional.empty(),
                lines,
                progress
        );
    }

    private Map<String, Object> placeholders(
            ChallengeHudView current,
            MainConfig.Hud settings
    ) {
        var time = plugin.getTimeUtil().getTimeAndTypeBySecond(current.remainingSeconds());
        Component formattedTime = Component.text(time.getFirst() + " " + time.getSecond());
        Component place = current.place() > 0
                ? Component.text("#" + current.place())
                : settings.unrankedPlace;
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("challenge", current.challengeName());
        values.put("challenge_id", current.challengeId());
        values.put("objective", current.objective());
        values.put("score", current.score());
        values.put("place", place);
        values.put("place_number", current.place());
        values.put("time", formattedTime);
        values.put("time_value", time.getFirst());
        values.put("time_unit", time.getSecond());
        values.put("remaining_seconds", current.remainingSeconds());
        values.put("total_seconds", current.totalSeconds());
        values.put("phase", current.phase().name().toLowerCase(Locale.ROOT));
        return Map.copyOf(values);
    }

    private void addLine(
            List<HudLine> lines,
            Component template,
            HudLineRole role,
            String line,
            ChallengeHudView current,
            HudWidgetContext context,
            MainConfig.Hud settings,
            Map<String, Object> placeholders
    ) {
        Component rendered = ConfigManager.fmt(template, placeholders);
        if (colorAnimation.isPresent()
                && settings.animation.line.equalsIgnoreCase(line)
                && settings.animation.phases.stream().anyMatch(phase ->
                phase.equalsIgnoreCase(current.phase().name()))) {
            rendered = recolor(rendered, colorAnimation.orElseThrow().frame(context));
        }
        if (!PLAIN.serialize(rendered).isBlank()) {
            lines.add(new HudLine(rendered, role));
        }
    }

    private static Component recolor(Component component, TextColor color) {
        return component.color(color).children(component.children().stream()
                .map(child -> recolor(child, color))
                .toList());
    }

    private static Optional<HudProgress> progress(
            ChallengeHudView current,
            MainConfig.Hud settings
    ) {
        if (current.phase() == ChallengeHudView.Phase.DRAINING) return Optional.empty();
        if (current.phase() == ChallengeHudView.Phase.COUNTDOWN
                && !settings.showCountdownProgress) return Optional.empty();
        if (current.phase() == ChallengeHudView.Phase.ACTIVE
                && !settings.showActiveProgress) return Optional.empty();
        String variant = current.phase() == ChallengeHudView.Phase.COUNTDOWN
                ? settings.countdownProgressStyle : settings.activeProgressStyle;
        return Optional.of(new HudProgress(
                current.remainingSeconds(),
                current.totalSeconds(),
                Optional.empty(),
                variant
        ));
    }

    private void refreshDisplay(UUID playerUuid) {
        if (closed.get() || playerUuid == null || !plugin.isEnabled()) return;
        Runnable refresh = () -> {
            if (closed.get() || plugin.getChallengesManager() == null) return;
            plugin.getChallengesManager().refreshDisplay(playerUuid);
        };
        if (Bukkit.isPrimaryThread()) refresh.run();
        else Bukkit.getScheduler().runTask(plugin, refresh);
    }

    private MainConfig.Hud settings() {
        return plugin.cfg().hud;
    }

    private static HudIcon icon(String configured) {
        String id = semanticId(configured, "star");
        return new HudIcon(
                Key.key("edensky", "hud/" + id),
                Optional.of(Component.text("★"))
        );
    }

    private static HudWidgetState state(ChallengeHudView.Phase phase) {
        return switch (phase) {
            case COUNTDOWN, DRAINING -> HudWidgetState.WARNING;
            case ACTIVE -> HudWidgetState.ACTIVE;
        };
    }

    private static String style(
            ChallengeHudView.Phase phase,
            MainConfig.Hud settings
    ) {
        return switch (phase) {
            case COUNTDOWN -> settings.countdownStyle;
            case ACTIVE -> settings.activeStyle;
            case DRAINING -> settings.drainingStyle;
        };
    }

    private static HudZone zone(String configured) {
        return HudZone.valueOf(configured.strip().toUpperCase(Locale.ROOT));
    }

    private static HudWidgetCapacityPolicy capacityPolicy(String configured) {
        return HudWidgetCapacityPolicy.valueOf(
                configured.strip().toUpperCase(Locale.ROOT));
    }

    private static HudBadgePolicy badgePolicy(String configured) {
        return HudBadgePolicy.valueOf(configured.strip().toUpperCase(Locale.ROOT));
    }

    private static Duration ticks(int configured) {
        return Duration.ofMillis(Math.max(1L, configured) * 50L);
    }

    private static Optional<HudAnimationSequence<TextColor>> colorAnimation(
            MainConfig.Hud.Animation settings
    ) {
        if (!settings.enabled) return Optional.empty();
        return Optional.of(new HudAnimationSequence<>(settings.colors.stream()
                .map(EdenHudIntegration::color)
                .toList(), settings.frameDurationTicks));
    }

    private static TextColor color(String configured) {
        return TextColor.color(Integer.parseInt(configured.substring(1), 16));
    }

    private static String semanticId(String configured, String fallback) {
        String normalized = configured == null
                ? ""
                : configured.strip().toLowerCase(Locale.ROOT).replace(' ', '-');
        return normalized.matches("[a-z][a-z0-9_-]*") ? normalized : fallback;
    }
}
