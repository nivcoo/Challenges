package fr.nivcoo.challenges.utils.time;

public class TimePair<U, V> {

    private final U first;

    private final V second;

    public TimePair(U first, V second) {
        this.first = first;
        this.second = second;
    }

    public U getFirst() {
        return first;
    }

    public V getSecond() {
        return second;
    }

}
