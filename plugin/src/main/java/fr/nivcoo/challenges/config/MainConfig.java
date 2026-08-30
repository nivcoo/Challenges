package fr.nivcoo.challenges.config;

import fr.nivcoo.challenges.challenges.ChallengeRole;
import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.core.config.annotations.Comment;
import fr.nivcoo.utilsz.core.config.annotations.Section;
import fr.nivcoo.utilsz.core.config.common.DatabaseConfig;
import fr.nivcoo.utilsz.core.config.common.MessagingConfig;
import fr.nivcoo.utilsz.core.config.validation.Validatable;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("unused")
public final class MainConfig implements Validatable {
    public DatabaseConfig database = new DatabaseConfig("sqlite", "database.db", "challenges", "root");
    public MessagingConfig messaging = new MessagingConfig(false, "challenges");
    public Cluster cluster = new Cluster();
    public int interval = 0;
    public int timeout = 1200;
    public int countdownNumber = 10;
    public int playersNeeded = 1;
    public Sounds sound = new Sounds();
    public Hud hud = new Hud();
    public List<Integer> whitelistedHours = List.of();
    public List<String> blacklistedWorld = List.of("invest", "auto_jump");
    public Rewards rewards = new Rewards();
    public Messages messages = new Messages();

    @Override
    public void validate() {
        if (database == null || messaging == null || sound == null || hud == null || rewards == null
                || messages == null || rewards.forAll == null || rewards.top == null) {
            throw new IllegalArgumentException("Required Challenges configuration sections are missing.");
        }
        if (sound.messages == null || sound.messages.isBlank() || sound.add == null || sound.add.isBlank()
                || sound.remove == null || sound.remove.isBlank() || sound.messages.length() > 128
                || sound.add.length() > 128 || sound.remove.length() > 128) {
            throw new IllegalArgumentException("Challenge sound names must not be blank.");
        }
        if (cluster == null || cluster.role == null) {
            throw new IllegalArgumentException("Challenges cluster role must be configured.");
        }
        if (cluster.heartbeatInterval <= 0 || cluster.participantTimeout < cluster.heartbeatInterval
                || cluster.settlementGrace <= 0
                || cluster.settlementTimeout < cluster.settlementGrace) {
            throw new IllegalArgumentException("Invalid Challenges cluster timing configuration.");
        }
        if (interval < 0 || timeout <= 0 || countdownNumber < 0 || playersNeeded < 0) {
            throw new IllegalArgumentException("Challenge timings and player limits must be positive.");
        }
        if (whitelistedHours == null || blacklistedWorld == null) {
            throw new IllegalArgumentException("Challenge world/hour policies must not be null.");
        }
        if (whitelistedHours.stream().anyMatch(hour -> hour == null || hour < 0 || hour > 23)) {
            throw new IllegalArgumentException("whitelistedHours must contain values between 0 and 23.");
        }
        if (blacklistedWorld.stream().anyMatch(world -> world == null || world.isBlank() || world.length() > 128)) {
            throw new IllegalArgumentException("blacklistedWorld contains an invalid world name.");
        }
        validateHud();
    }

    private void validateHud() {
        if (!hud.enabled) return;
        if (hud.animation == null || hud.rankingBadge == null) {
            throw new IllegalArgumentException("hud.animation and hud.rankingBadge are required.");
        }
        if (hud.priority < -10_000 || hud.priority > 10_000
                || hud.retentionPriority < -10_000 || hud.retentionPriority > 10_000) {
            throw new IllegalArgumentException("HUD priorities must be between -10000 and 10000.");
        }
        requireOneOf(hud.region, "hud.region", Set.of("TOP_LEFT", "TOP_CENTER", "TOP_RIGHT"));
        requireOneOf(hud.capacityPolicy, "hud.capacityPolicy", Set.of("STANDARD", "OPPORTUNISTIC"));
        requireSemanticId(hud.layout, "hud.layout");
        requireSemanticId(hud.countdownStyle, "hud.countdownStyle");
        requireSemanticId(hud.activeStyle, "hud.activeStyle");
        requireSemanticId(hud.drainingStyle, "hud.drainingStyle");
        if (hud.showIcon) requireSemanticId(hud.icon, "hud.icon");
        if (hud.showCountdownProgress) {
            requireSemanticId(hud.countdownProgressStyle, "hud.countdownProgressStyle");
        }
        if (hud.showActiveProgress) {
            requireSemanticId(hud.activeProgressStyle, "hud.activeProgressStyle");
        }
        if (hud.title == null || hud.objectiveLine == null || hud.countdownLine == null
                || hud.scoreLine == null || hud.timerLine == null || hud.drainingLine == null
                || hud.unrankedPlace == null) {
            throw new IllegalArgumentException("HUD text templates must not be null.");
        }
        validateHudAnimation();
        if (!hud.rankingBadge.enabled) return;
        if (hud.rankingBadge.priority < -10_000 || hud.rankingBadge.priority > 10_000) {
            throw new IllegalArgumentException("HUD ranking badge priority must be between -10000 and 10000.");
        }
        if (hud.rankingBadge.durationTicks <= 0
                || hud.rankingBadge.accumulationWindowTicks <= 0) {
            throw new IllegalArgumentException("HUD ranking badge durations must be positive.");
        }
        requireOneOf(hud.rankingBadge.policy, "hud.rankingBadge.policy",
                Set.of("REPLACE", "ACCUMULATE", "QUEUE"));
    }

    private void validateHudAnimation() {
        Hud.Animation animation = hud.animation;
        if (!animation.enabled) return;
        if (animation.frameDurationTicks <= 0 || animation.frameDurationTicks > 1200) {
            throw new IllegalArgumentException("hud.animation.frameDurationTicks must be between 1 and 1200.");
        }
        requireOneOf(animation.line, "hud.animation.line",
                Set.of("TITLE", "OBJECTIVE", "COUNTDOWN", "SCORE", "TIMER", "DRAINING"));
        if (animation.colors == null || animation.colors.size() < 2 || animation.colors.size() > 32
                || animation.colors.stream().anyMatch(color ->
                color == null || !color.matches("#[0-9a-fA-F]{6}"))) {
            throw new IllegalArgumentException(
                    "hud.animation.colors must contain between 2 and 32 hexadecimal colors.");
        }
        Set<String> phases = Set.of("COUNTDOWN", "ACTIVE", "DRAINING");
        if (animation.phases == null || animation.phases.isEmpty()
                || animation.phases.stream().anyMatch(phase -> phase == null
                || !phases.contains(phase.strip().toUpperCase(Locale.ROOT)))) {
            throw new IllegalArgumentException(
                    "hud.animation.phases must contain COUNTDOWN, ACTIVE or DRAINING.");
        }
    }

    private static void requireOneOf(String value, String field, Set<String> accepted) {
        String normalized = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
        if (!accepted.contains(normalized)) {
            throw new IllegalArgumentException(field + " must be one of " + accepted + '.');
        }
    }

    private static void requireSemanticId(String value, String field) {
        String normalized = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_-]*")) {
            throw new IllegalArgumentException(field + " must be a semantic identifier.");
        }
    }

    @Section
    public static final class Cluster {
        public ChallengeRole role = ChallengeRole.PARTICIPANT;
        public int heartbeatInterval = 10;
        public int participantTimeout = 45;
        public int settlementGrace = 8;
        public int settlementTimeout = 60;
    }

    @Section
    public static final class Sounds {
        public String messages = "ENTITY_EXPERIENCE_ORB_PICKUP";
        public String add = "ENTITY_EXPERIENCE_ORB_PICKUP";
        public String remove = "BLOCK_ANVIL_BREAK";
    }

    @Section
    public static final class Hud {
        @Comment("Utilise une card EdenHUD lorsque la feature HUD est disponible pour le joueur. L'action bar reste le fallback.")
        public boolean enabled = true;
        public String region = "TOP_CENTER";
        public int priority = 1_200;
        public int retentionPriority = 1_200;
        public String capacityPolicy = "STANDARD";
        @Comment("Le layout wide est la grande card prévue pour les objectifs longs.")
        public String layout = "wide";
        public boolean showIcon = true;
        public String icon = "star";
        public String countdownStyle = "warning";
        public String activeStyle = "info";
        public String drainingStyle = "muted";
        public boolean showCountdownProgress = true;
        public String countdownProgressStyle = "warning";
        public boolean showActiveProgress = true;
        public String activeProgressStyle = "accent";
        @Comment("Variables : {challenge}, {challenge_id}, {objective}, {score}, {place}, {place_number}, {time}, {time_value}, {time_unit}, {remaining_seconds}, {total_seconds}, {phase}.")
        public Component title = text("&eDéfi journalier");
        public Component objectiveLine = text("{objective}");
        public Component countdownLine = text("&eLancement dans &f{time}");
        public Component scoreLine = text("&bScore &f{score} &8• &aClassement &f{place}");
        public Component timerLine = text("&eTemps restant &f{time}");
        public Component drainingLine = text("&eClassement en cours de calcul…");
        public Component unrankedPlace = text("&7Non classé");
        public Animation animation = new Animation();
        public RankingBadge rankingBadge = new RankingBadge();

        @Section
        public static final class Animation {
            @Comment("Séquence pilotée par l'horloge unique d'EdenHUD ; aucun scheduler n'est créé dans Challenges.")
            public boolean enabled = true;
            public int frameDurationTicks = 8;
            @Comment("Ligne animée : TITLE, OBJECTIVE, COUNTDOWN, SCORE, TIMER ou DRAINING.")
            public String line = "TITLE";
            public List<String> colors = List.of("#F6C945", "#FFE58A", "#FFF4C2", "#FFE58A");
            public List<String> phases = List.of("COUNTDOWN", "ACTIVE");
        }

        @Section
        public static final class RankingBadge {
            @Comment("Affiche sur la card le nombre de places gagnées : +1, +2, etc.")
            public boolean enabled = true;
            public boolean showLosses = false;
            public int priority = 0;
            public int durationTicks = 60;
            public int accumulationWindowTicks = 60;
            public String policy = "ACCUMULATE";
        }
    }

    @Section
    public static final class Rewards {
        public boolean giveForAllRewardToTop = false;
        public RewardGroup forAll = new RewardGroup("1.000$ + 1 Pièce du Marché Noir",
                List.of("eco give %player% 1000", "cr give to %player% piece 1"));
        public boolean addAllTopIntoDb = true;
        public Map<String, RewardGroup> top = defaultTopRewards();
    }

    @Section
    public static final class RewardGroup {
        public Component message = Component.empty();
        public List<String> commands = new ArrayList<>();

        public RewardGroup() {
        }

        public RewardGroup(String message, List<String> commands) {
            this.message = text(message);
            this.commands = commands;
        }
    }

    @Section
    public static final class Messages {
        public Placeholders placeholders = new Placeholders();
        public Commands commands = new Commands();
        public Global global = new Global();
        public TitleMessages title = new TitleMessages();
        public ActionBar actionBar = new ActionBar();
        public Chat chat = new Chat();
        public RewardMessages rewards = new RewardMessages();
    }

    @Section
    public static final class Placeholders {
        public Countdown currentChallengeCountdown = new Countdown();
        public Place currentChallengePlace = new Place();

        @Section
        public static final class Countdown {
            public Component started = text("{0} {1}");
            public Component stop = text("&c✖");
        }

        @Section
        public static final class Place {
            public Component none = text("&f??");
        }
    }

    @Section
    public static final class Commands {
        public Component incorrectUsage = text("&fCorrect Usage : {0}");
        public Component noPermission = text("&fCommande inconnue.");
        public List<Component> help = List.of(
                text("&7&m------------------&8[&6Help Panel&8]&7&m------------------"),
                text("{!challenges.command.start}&6/clgs start &estart a challenge !"),
                text("{!challenges.command.stop}&6/clgs stop &estop the current challenge !"),
                text("{!challenges.command.end}&6/clgs end &estop the current challenge with rewards !"),
                text("{!challenges.command.start_interval}&6/clgs start_interval &estart challenge interval !"),
                text("{!challenges.command.stop_interval}&6/clgs stop_interval &estop challenge interval !"),
                text("{!challenges.command.reload}&6/clgs reload &ereload the plugin !"),
                text("{!challenges.command.delete_datas}&6/clgs delete_datas &eclear the db !"),
                text("&7&m----------------------------------------------")
        );
        public Component successStart = text("&7[&c&lES&7] Le challenge a été lancé avec succès !");
        public Component successStop = text("&7[&c&lES&7] Le challenge a été stoppé avec succès !");
        public Component successEnd = text("&7[&c&lES&7] Le challenge a été stoppé et les récompenses ont été données avec succès !");
        public Component successStartInterval = text("&7[&c&lES&7] L'interval des challenges a été lancé avec succès !");
        public Component successStopInterval = text("&7[&c&lES&7] L'interval des challenges a été stoppé avec succès !");
        public Component successReload = text("&7[&c&lES&7] Le plugin vient d'être reload !");
        public Component successDeleteDatas = text("&7[&c&lES&7] La base de données vient d'être vidée !");
    }

    @Section
    public static final class Global {
        public Component none = text("&c✖");
        public String second = "seconde";
        public String seconds = "secondes";
        public String minute = "minute";
        public String minutes = "minutes";
        public String hour = "heure";
        public String hours = "heures";
    }

    @Section
    public static final class TitleMessages {
        public Start start = new Start();
        public Countdown countdown = new Countdown();

        @Section
        public static final class Start {
            public int stay = 10;
            public int fadeInTick = 10;
            public int fadeOutTick = 20;
            public Component title = text("&eVous avez &6{0} {1}");
            public Component subtitle = text("&epour : &a{2} &e!");
        }

        @Section
        public static final class Countdown {
            public Component title = text("&aDéfi Journalier");
            public Component subtitle = text("&eLancement dans &6&n{0} {1}&e !");
        }
    }

    @Section
    public static final class ActionBar {
        public Running running = new Running();
        public Component countdown = text("&e&lLancement du défi dans &6&l{0} {1}&e&l !");

        @Section
        public static final class Running {
            public Component message = text("&a&n{0} :&b&l {1} &7&l| &e&nTemps restant :&6&l {2} {3} {4} &b/defi");
            public Component place = text("&7&l| &a&nPlace :&b&l {0}");
        }
    }

    @Section
    public static final class Chat {
        public Component startMessage = text("&7[&c&lES&7] &eVous avez &6{0} {1} &epour : &c{2} !&a Soyez le meilleur pour obtenir des récompenses ! &b/defi\n \n&c&lATTENTION : &cLes blocs précédement posés ne fonctionnent pas dans les défis !");
        public Component noPlayer = text("&7[&c&lES&7] Aucun joueur n'a fait le défi !");
        public Top top = new Top();

        @Section
        public static final class Top {
            public Component template = text("&f&l| &bN°{0} &e{1} &7- &6{2} &a{3} &7- &d{4}");
            public TemplatePoints templatePoints = new TemplatePoints();
            public List<Component> message = List.of(
                    text("&7---»"),
                    Component.empty(),
                    text("&dLe défi Journalier est terminé !"),
                    text("&7 ({0})"),
                    Component.empty(),
                    text("&a&l&nCLASSEMENT DÉFI :"),
                    Component.empty(),
                    text("{1}"),
                    Component.empty(),
                    text("&7---»")
            );
        }

    @Section
    public static final class TemplatePoints {
            public String point = "Point";
            public String points = "Points";
            public Component display = text("(+{0} {1})");
            public Component defaultValue = Component.empty();
        }
    }

    @Section
    public static final class RewardMessages {
        public Component forAll = text("&7[&c&lES&7] &eVous avez participé au défi, vous gagnez &a{0}&e !");
        public Component top = text("&7[&c&lES&7] &eVous avez participé au défi, vous êtes &aTOP {0} &e! Vous gagnez : &a{1} &e!");
    }

    public static Component text(String raw) {
        return ConfigManager.parseDynamic(raw);
    }

    private static Map<String, RewardGroup> defaultTopRewards() {
        Map<String, RewardGroup> top = new LinkedHashMap<>();
        top.put("1", new RewardGroup("1 Clé Ancienne + 7.500$", List.of("cr give to %player% Ancienne 1", "eco give %player% 7500")));
        top.put("2", new RewardGroup("2 Clés Argent + 5.000$", List.of("cr give to %player% Argent 2", "eco give %player% 5000")));
        top.put("3", new RewardGroup("1 Clé Argent + 2.500$", List.of("cr give to %player% Argent 1", "eco give %player% 2500")));
        top.put("4", new RewardGroup("2 Clés Saisonnière + 2.500$", List.of("cr give to %player% saisonniere 2", "eco give %player% 2500")));
        top.put("5", new RewardGroup("2 Clés de Quête + 2.500$", List.of("cr give to %player% quete 2", "eco give %player% 2500")));
        return top;
    }
}
