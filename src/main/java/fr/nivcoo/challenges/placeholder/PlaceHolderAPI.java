package fr.nivcoo.challenges.placeholder;

import org.bukkit.OfflinePlayer;

import fr.nivcoo.challenges.Challenges;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;

public class PlaceHolderAPI extends PlaceholderExpansion {

	private Challenges challenges;

	public PlaceHolderAPI() {
		challenges = Challenges.get();
	}

	@Override
	public String getAuthor() {
		return "nivcoo";
	}

	@Override
	public String getIdentifier() {
		return "challenges";
	}

	@Override
	public String getVersion() {
		return "0.0.1";
	}

	@Override
	public String onRequest(OfflinePlayer player, String identifier) {

		if (identifier.equals("get_count")) {
			int count = challenges.getCacheManager().getPlayerCount(player.getUniqueId());
			return String.valueOf(count);
		}

		return null;
	}

}
