package fr.nivcoo.challenges.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.nivcoo.challenges.challenges.Challenge;
import fr.nivcoo.challenges.challenges.TopReward;
import fr.nivcoo.challenges.challenges.challenges.Types;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

import java.util.ArrayList;
import java.util.List;

public class ChallengeAdapter implements RedisTypeAdapter<Challenge> {

    @Override
    public JsonObject serialize(Challenge challenge) {
        JsonObject json = new JsonObject();

        json.addProperty("type", challenge.challengeType().name());
        json.addProperty("message", challenge.message());
        json.addProperty("countPreviousBlocks", challenge.countPreviousBlocks());

        JsonArray reqs = new JsonArray();
        for (String r : challenge.requirements()) {
            reqs.add(r);
        }
        json.add("requirements", reqs);

        JsonArray rewards = new JsonArray();
        for (TopReward reward : challenge.topRewards()) {
            JsonObject rewardJson = new JsonObject();
            rewardJson.addProperty("place", reward.place());
            rewardJson.addProperty("message", reward.message());

            JsonArray cmds = new JsonArray();
            for (String cmd : reward.commands()) {
                cmds.add(cmd);
            }
            rewardJson.add("commands", cmds);

            rewards.add(rewardJson);
        }
        json.add("topRewards", rewards);

        json.addProperty("forAllMessage", challenge.forAllMessage());

        JsonArray allCmds = new JsonArray();
        for (String cmd : challenge.forAllCommands()) {
            allCmds.add(cmd);
        }
        json.add("forAllCommands", allCmds);

        json.addProperty("giveForAllRewardToTop", challenge.giveForAllRewardToTop());

        return json;
    }

    @Override
    public Challenge deserialize(JsonObject json) {
        Types type = Types.valueOf(json.get("type").getAsString());
        String message = json.get("message").getAsString();
        boolean countPrevious = json.get("countPreviousBlocks").getAsBoolean();

        List<String> requirements = new ArrayList<>();
        for (var el : json.getAsJsonArray("requirements")) {
            requirements.add(el.getAsString());
        }

        List<TopReward> rewards = new ArrayList<>();
        for (var el : json.getAsJsonArray("topRewards")) {
            JsonObject rewardJson = el.getAsJsonObject();
            int place = rewardJson.get("place").getAsInt();
            String msg = rewardJson.get("message").getAsString();

            List<String> cmds = new ArrayList<>();
            for (var c : rewardJson.getAsJsonArray("commands")) {
                cmds.add(c.getAsString());
            }

            rewards.add(new TopReward(place, msg, cmds));
        }

        String forAllMessage = json.has("forAllMessage") ? json.get("forAllMessage").getAsString() : "";
        boolean giveToTop = json.has("giveForAllRewardToTop") && json.get("giveForAllRewardToTop").getAsBoolean();

        List<String> forAllCommands = new ArrayList<>();
        if (json.has("forAllCommands")) {
            for (var c : json.getAsJsonArray("forAllCommands")) {
                forAllCommands.add(c.getAsString());
            }
        }

        return new Challenge(type, requirements, message, countPrevious, rewards, forAllMessage, forAllCommands, giveToTop);
    }
}
