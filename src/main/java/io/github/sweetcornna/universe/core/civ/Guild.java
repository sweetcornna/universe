package io.github.sweetcornna.universe.core.civ;

/** 三个公会（ADR-0027 §六）。声望和种族一样记，第二块才有公会任务。 */
public enum Guild {
    MERCHANTS("merchants"),
    MERCENARIES("mercenaries"),
    EXPLORERS("explorers");

    private final String id;

    Guild(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
