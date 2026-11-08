package org.Cry_Legende.sHIslandHolo;

/**
 * Intervalle d'entiers écrit dans tips.yml : « 0-71 », « 150+ » ou « 300 ». Absent = tout.
 */
record Range(int min, int max) {

    static final Range ANY = new Range(Integer.MIN_VALUE, Integer.MAX_VALUE);

    static Range parse(String text) {
        if (text == null || text.isBlank()) {
            return ANY;
        }
        String value = text.trim();
        if (value.endsWith("+")) {
            return new Range(Integer.parseInt(value.substring(0, value.length() - 1).trim()), Integer.MAX_VALUE);
        }
        int dash = value.indexOf('-', 1);
        if (dash > 0) {
            return new Range(Integer.parseInt(value.substring(0, dash).trim()), Integer.parseInt(value.substring(dash + 1).trim()));
        }
        int exact = Integer.parseInt(value);
        return new Range(exact, exact);
    }

    boolean contains(int value) {
        return value >= min && value <= max;
    }
}
