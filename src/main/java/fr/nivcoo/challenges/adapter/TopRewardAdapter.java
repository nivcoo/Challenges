package fr.nivcoo.challenges.adapter;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.nivcoo.challenges.challenges.TopReward;
import fr.nivcoo.utilsz.redis.RedisTypeAdapter;

import java.util.ArrayList;
import java.util.List;

public class TopRewardAdapter implements RedisTypeAdapter<TopReward> {

    @Override
    public JsonObject serialize(TopReward reward) {
        JsonObject json = new JsonObject();
        json.addProperty("place", reward.place());
        json.addProperty("message", reward.message());

        JsonArray commands = new JsonArray();
        for (String cmd : reward.commands()) {
            commands.add(cmd);
        }
        json.add("commands", commands);

        return json;
    }

    @Override
    public TopReward deserialize(JsonObject json) {
        int place = json.get("place").getAsInt();
        String message = json.get("message").getAsString();

        List<String> commands = new ArrayList<>();
        JsonArray commandsJSON = json.getAsJsonArray("commands");
        for (int i = 0; i < commandsJSON.size(); i++) {
            commands.add(commandsJSON.get(i).getAsString());
        }

        return new TopReward(place, message, commands);
    }
}
