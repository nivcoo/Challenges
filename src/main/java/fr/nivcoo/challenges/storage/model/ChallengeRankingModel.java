package fr.nivcoo.challenges.storage.model;

import fr.nivcoo.utilsz.core.database.ColumnType;
import fr.nivcoo.utilsz.core.database.DatabaseModel;
import fr.nivcoo.utilsz.core.database.DatabaseRow;
import fr.nivcoo.utilsz.core.database.ModelSchema;

import java.util.UUID;

public record ChallengeRankingModel(UUID uuid, int score) {
    public static final DatabaseModel<ChallengeRankingModel> MODEL = new DatabaseModel<>() {
        @Override
        public ModelSchema<ChallengeRankingModel> schema() {
            return ModelSchema.<ChallengeRankingModel>of("challenge_ranking")
                    .column("uuid", ColumnType.UUID, "PRIMARY KEY", ChallengeRankingModel::uuid)
                    .column("score", ColumnType.INT, "DEFAULT 0", ChallengeRankingModel::score);
        }

        @Override
        public ChallengeRankingModel from(DatabaseRow row) {
            return new ChallengeRankingModel(row.getUuid("uuid"), row.getInt("score"));
        }
    };
}
