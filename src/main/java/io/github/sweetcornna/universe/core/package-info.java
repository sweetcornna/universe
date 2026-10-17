/**
 * 纯 Java 核心：星系生成、轨道、传送带与电网模拟、戴森球计算等不依赖游戏本体的逻辑。
 *
 * <p>本包及子包禁止引用 {@code net.minecraft}、{@code com.mojang}、{@code net.neoforged}，
 * 由 {@code CoreIsolationTest} 检查。这样核心逻辑能用普通单元测试覆盖，
 * 跟进新的 Minecraft 版本时也基本不用改。详见 docs/adr/0003-core-decoupling.md。
 */
package io.github.sweetcornna.universe.core;
