package meteor

import meteor.ui.components.LineComponent
import meteor.ui.overlay.*
import net.runelite.api.MenuAction
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics2D
import java.time.Clock
import java.time.ZoneId

class jCrabFighterOverlay(var plugin: jCrabFighterPlugin, var config: jCrabFighterConfig) : OverlayPanel() {
    private val OPTION_CONFIGURE = "Configure"
    val clock = Clock.tickSeconds(ZoneId.systemDefault())
    init {
        position = OverlayPosition.BOTTOM_LEFT
        priority = OverlayPriority.HIGH
        layer = OverlayLayer.ABOVE_SCENE
        menuEntries.add(OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Crabs overlay"))
    }

    override fun render(graphics: Graphics2D): Dimension? {
        var current_runtime = clock.instant().minusMillis(plugin.startTime).toString()

        current_runtime = current_runtime.removeRange(0, 12).split(Regex.fromLiteral("."), 10)[0]

        if(plugin.afkTicker != -1) {
            panelComponent.children.add(
                LineComponent.Builder().left("afk_ticker: ").leftColor(Color.cyan).right("${plugin.afkTicker}")
                    .rightColor(Color.pink).build()
            )
        }
        if(plugin.dormantMonsters.size > 0) {
            panelComponent.children.add(
                LineComponent.Builder().left("dormant monsters: ").leftColor(Color.cyan)
                    .right("${plugin.dormantMonsters.size}").rightColor(Color.pink).build()
            )
        }
        if(plugin.attackMonsters.size > 0 ) {
            panelComponent.children.add(
                LineComponent.Builder().left("attacking monsters: ").leftColor(Color.cyan)
                    .right("${plugin.attackMonsters.size}").rightColor(Color.pink).build()
            )
        }

        if(plugin.hopTicker > 0) {
            panelComponent.children.add(LineComponent.Builder().left("hop_ticker: ").leftColor(Color.cyan).right(plugin.hopTicker.toString()).rightColor(Color.pink).build())
        }

        panelComponent.children.add(LineComponent.Builder().left("time running: ").leftColor(Color.cyan).right(current_runtime).rightColor(Color.pink).build())
        panelComponent.children.add(LineComponent.Builder().left("log: ").leftColor(Color.cyan).right(plugin.log).rightColor(Color.pink).build())

        graphics.color = Color.cyan
        graphics.background = Color.darkGray

        return super.render(graphics)
    }
}