package io.github.sweetcornna.universe.core.civ;

/** NPC 的角色（ADR-0027 §三）。 */
public enum NpcRole {
    /** 访客：对话、送礼、请教词。 */
    VISITOR("visitor"),
    /** 技术商：也能对话，"看货"第二块做。 */
    TECH_MERCHANT("tech_merchant"),
    /** 公会使者：也能对话，公会任务第二块做。 */
    GUILD_ENVOY("guild_envoy");

    private final String id;

    NpcRole(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }
}
