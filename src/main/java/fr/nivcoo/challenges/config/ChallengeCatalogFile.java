package fr.nivcoo.challenges.config;

import fr.nivcoo.utilsz.core.config.annotations.ConfigStructure;
import fr.nivcoo.utilsz.core.config.annotations.Optional;
import fr.nivcoo.utilsz.core.config.annotations.RejectUnknownKeys;
import fr.nivcoo.utilsz.core.config.annotations.Required;
import fr.nivcoo.utilsz.core.config.validation.Validatable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigStructure
@RejectUnknownKeys
public final class ChallengeCatalogFile implements Validatable {
    @Required
    public List<Entry> challenges = List.of();

    @Override
    public void validate() {
        if (challenges == null || challenges.isEmpty()) {
            throw new IllegalArgumentException("challenges.yml must define at least one challenge.");
        }
    }

    @ConfigStructure
    @RejectUnknownKeys
    public static final class Entry implements Validatable {
        @Required
        public String id = "";
        @Optional
        public boolean enabled = true;
        @Required
        public Component message = Component.empty();
        @Required
        public Objective objective = new Objective();

        @Override
        public void validate() {
            if (id == null || id.isBlank()) {
                throw new IllegalArgumentException("Challenge id must not be blank.");
            }
            if (message == null || LegacyComponentSerializer.legacySection().serialize(message).isBlank()) {
                throw new IllegalArgumentException("Challenge '" + id + "' must define a non-empty message.");
            }
            if (objective == null || objective.type == null || objective.type.isBlank()) {
                throw new IllegalArgumentException("Challenge '" + id + "' must define one objective type.");
            }
            if (objective.parameters == null) {
                throw new IllegalArgumentException("Challenge '" + id + "' objective parameters must not be null.");
            }
        }
    }

    @ConfigStructure
    @RejectUnknownKeys
    public static final class Objective {
        @Required
        public String type = "";
        @Optional
        public Map<String, Object> parameters = new LinkedHashMap<>();
    }
}
