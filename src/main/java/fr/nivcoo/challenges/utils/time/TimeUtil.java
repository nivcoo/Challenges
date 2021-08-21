package fr.nivcoo.challenges.utils.time;

public class TimeUtil {

	String second;
	String seconds;
	String minute;
	String minutes;
	String hour;
	String hours;

	public TimeUtil(String second, String seconds, String minute, String minutes, String hour, String hours) {

		this.second = second;
		this.seconds = seconds;
		this.minute = minute;
		this.minutes = minutes;
		this.hour = hours;
	}

	public TimePair<Long, String> getTimeAndTypeBySecond(long s) {
		long m = Math.round(s / 60);
		long h = Math.round(m / 60);
		long number = 0;
		String type = null;
		if (h >= 1) {
			number = h;
			type = hour;
			if (h > 1)
				type = hours;

		} else if (m >= 1) {
			number = m;
			type = minute;
			if (m > 1)
				type = minutes;

		} else {
			number = s;
			type = second;
			if (s > 1)
				type = seconds;
		}
		
		return new TimePair<>(number, type);
	}

}
