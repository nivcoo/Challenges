package fr.nivcoo.challenges.config;

import fr.nivcoo.challenges.challenges.challenges.Types;
import fr.nivcoo.utilsz.core.config.ConfigManager;
import fr.nivcoo.utilsz.core.config.annotations.Section;
import fr.nivcoo.utilsz.core.config.common.DatabaseConfig;
import fr.nivcoo.utilsz.core.config.common.MessagingConfig;
import net.kyori.adventure.text.Component;
import org.bukkit.Sound;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public final class MainConfig {
    public DatabaseConfig database = new DatabaseConfig("sqlite", "database.db", "challenges", "root");
    public MessagingConfig messaging = new MessagingConfig(false, "challenges");
    public int interval = 0;
    public int timeout = 1200;
    public int countdownNumber = 10;
    public int playersNeeded = 1;
    public Sounds sound = new Sounds();
    public List<Integer> whitelistedHours = List.of();
    public List<String> blacklistedWorld = List.of("invest", "auto_jump");
    public Map<String, ChallengeEntry> challenges = defaultChallenges();
    public Rewards rewards = new Rewards();
    public Messages messages = new Messages();
    public Hooks hooks = new Hooks();

    @Section
    public static final class Hooks {
        public Hook placeholderApi = new Hook();
        public Hook wildTools = new Hook();
        public Hook wildStacker = new Hook();
    }

    @Section
    public static final class Hook {
        public boolean enabled = true;
    }

    @Section
    public static final class Sounds {
        public Sound messages = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        public Sound add = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        public Sound remove = Sound.BLOCK_ANVIL_BREAK;
    }

    @Section
    public static final class ChallengeEntry {
        public Component message = Component.empty();
        public Types challenge = Types.BLOCK_BREAK;
        public List<String> requirements = new ArrayList<>();
        public boolean countPreviousBlocks = false;

        public ChallengeEntry() {
        }

        public ChallengeEntry(String message, Types challenge, List<String> requirements) {
            this.message = text(message);
            this.challenge = challenge;
            this.requirements = requirements;
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

    private static Map<String, ChallengeEntry> defaultChallenges() {
        Map<String, ChallengeEntry> challenges = new LinkedHashMap<>();
        challenges.put("0", new ChallengeEntry("Casser de la Stone", Types.BLOCK_BREAK, List.of("STONE:0")));
        return challenges;
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
