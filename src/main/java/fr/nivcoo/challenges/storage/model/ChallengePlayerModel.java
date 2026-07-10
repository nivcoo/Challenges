package fr.nivcoo.challenges.storage.model;

import fr.nivcoo.utilsz.core.database.ColumnType;
import fr.nivcoo.utilsz.core.database.DatabaseModel;
import fr.nivcoo.utilsz.core.database.DatabaseRow;
import fr.nivcoo.utilsz.core.database.ModelSchema;

import java.util.UUID;

public record ChallengePlayerModel(UUID playerUuid, String playerName) {
    public static final DatabaseModel<ChallengePlayerModel> MODEL = new DatabaseModel<>() {
        @Override
        public ModelSchema<ChallengePlayerModel> schema() {
            return ModelSchema.<ChallengePlayerModel>of("challenge_players")
                    .column("player_uuid", ColumnType.UUID, "PRIMARY KEY", ChallengePlayerModel::playerUuid)
                    .column("player_name", ColumnType.TEXT, ChallengePlayerModel::playerName);
        }

        @Override
        public ChallengePlayerModel from(DatabaseRow row) {
            return new ChallengePlayerModel(row.getUuid("player_uuid"), row.getString("player_name"));
        }
    };
}
