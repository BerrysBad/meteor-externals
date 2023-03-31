package meteor


import dev.hoot.api.game.Worlds
import dev.hoot.api.movement.Movement
import eventbus.events.*
import meteor.api.Objects
import meteor.plugins.Plugin
import meteor.plugins.PluginDescriptor
import net.runelite.api.*
import net.runelite.api.coords.WorldPoint
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.LinkedBlockingQueue
import kotlin.collections.ArrayList
import kotlin.random.Random

enum class CrabHome(
    var home: WorldPoint,
    val location: String,
    val npc1: String,
    val npc2: String,
    val distance: Int,
    val deaggro: WorldPoint?
) {
    RELLEKA_NORTH(
        WorldPoint(2704, 3726, 0),
        "Rellekka",
        "Rock Crab",
        "Rocks",
        2,
        null
    ),
    RELLEKA_SOUTH(
        WorldPoint(2701, 3719, 0),
        "Rellekka",
        "Rock Crab",
        "Rocks",
        2,
        null
    ),
    //    AMMONITE_SOUTH(WorldPoint(0,0,0), "Fossil Island"),
//        AMMONITE_CENTER(WorldPoint(3716,3880,0), "Fossil Island", "Ammonite Crab"),
//    AMMONITE_NORTH(WorldPoint(0,0,0), "Fossil Island"),
    KOUREND_3(WorldPoint(1770, 3467, 0 ),
        "Kourend",
    "Sand Crab",
        "Sandy rocks",
        2,
        WorldPoint(1799, 3493, 0)
        ),
//    MORYTANIA_(WorldPoint(0,0,0), "Kourend"),
}

//    enum class Style(val style: AttackStyle, val switchRunnable: Runnable) {
//        ATTACK(AttackStyle.ACCURATE, { Switch}),
//        STRENGTH(AttackStyle.AGGRESSIVE),
//        DEFENCE(AttackStyle.DEFENSIVE)
//    }


@PluginDescriptor(
    name = "jCrabFighter",
    description = "Automatically aggros crabs",
    enabledByDefault = false,
    external = true)
class jCrabFighterPlugin : Plugin() {
    val config = configuration<jCrabFighterConfig>()
    private val overlay = overlay(jCrabFighterOverlay(this, config))
    var log = ""

    var hopTicker = -1
    var afkTicker = -1

    // Get location and npc info from the config file
    private var homePoint = config.home().home
        get() = config.home().home
    private var deaggroPoint = config.home().deaggro
        get() = config.home().deaggro
    private var attackRadius = config.home().distance
        get() = config.home().distance
    private var dormantNpc = config.home().npc2
        get() = config.home().npc2
    private var attackNpc = config.home().npc1
        get() = config.home().npc1
    private var areaName = config.home().location
        get() = config.home().location
    private var kourendSpots: MutableList<WorldPoint> = Collections.emptyList()

    // Initialize lists for dormant and attack monsters
    var dormantMonsters = ArrayList<NPC>()
    var attackMonsters = ArrayList<NPC>()

    // Get the player's health and initialize executors and queues
    private var health = 0.0
    val queue = LinkedBlockingQueue<Runnable>()
    var startTime = System.currentTimeMillis()
    var initialize_num = -1

    var spot: WorldPoint? = null

    /**
     * Initializes the bot and runs any pending tasks.
     *
     * @implNote Calls `initialize()` and `immediate?.run()`.
     */
    override fun onGameTick(it: GameTick) {
        if (queue.size > 0) {
            println("queue size: ${queue.size}")
        }
        initialize()

        println("spot: $spot")
        println("homePoint: $homePoint")

        if (client.localPlayer!!.isIdle && afkTicker <= 0) {
            if (this.dormantMonsters.size > 0 && spot != null) {
                deaggro()
            } else {
                kourend_findHome()
                if (spot == null && client.localPlayer!!.worldLocation.distanceTo(homePoint) <= 10) { hop() }
            }
            afkTicker = Random.nextInt(25, 60)
        }
        afkTicker --
        println("running queue")
        queue.poll()?.run()

    }


    /**
     * Handles de-aggroing for specific areas.
     *
     * @implNote Sets `state_num` to 3 and logs "running -> deagg".
     *           Calls `relekka_deaggro()` for the area "Rellekka".
     */
    private fun deaggro() {
        println("deaggrooing")
        println(areaName)
        when (areaName) {
            "Rellekka" -> {
                relekka_deaggro()
            }

            "Kourend" -> {
                kourend_deaggro()
            }
        }
    }

    private fun kourend_deaggro() {
        var point = randWorldPoint(this.deaggroPoint!!, 3)

        loop(
            { go_home() },
            {
                walk_to(point)
            },
            Random.nextInt(2, 5),
            "Kourend deaggro",
            3
        )
    }

    private fun kourend_findHome() {
        var playerIsHome = client.localPlayer!!.distanceTo(this.spot ?: homePoint) <= 3
        println("kourend_findHome")

        if(playerIsHome) {
            loop({ findOpenSpot() }, "Finding open spot")
        } else {
            go_home()
        }
    }

    private fun findOpenSpot(): Boolean {
        println("finding spot")
        when (areaName) {
            "Kourend" -> {
                println("finding open spot")
                kourendSpots = kourend3()

                if(client.localPlayer!!.worldLocation.distanceTo(homePoint) > 15) {
                    go_home()
                }

                println("spots: ${kourendSpots.size}")

                for(crab_spots in kourend3()) {
                    for(player in client.players) {
                        if(player.worldLocation == crab_spots && player.name != client.localPlayer!!.name) {
                            println("spot taken: $crab_spots by ${player.name}")
                            kourendSpots.remove(crab_spots)
                            break
                        }
                    }
                }
                if (kourendSpots.isNotEmpty()) {
                    println("received world point successfully")
                    config.home().home = kourendSpots.first()
                    this.spot = kourendSpots.first()
                    return true
                }
            }

            "Rellekka" -> {
                spot = this.homePoint
            }
        }
        return false
    }

    fun randWorldPoint(point: WorldPoint, random: Int): WorldPoint {
        val rand_x = Random.nextInt(-random, random)
        val rand_y = Random.nextInt(-random, random)

        return WorldPoint(point.x + rand_x, point.y + rand_y, 0)
    }

    fun kourend2(): MutableList<WorldPoint> {
        return mutableListOf(
            WorldPoint(1863, 3543, 0),
            WorldPoint(1876, 3555, 0),
            WorldPoint(1868, 3556, 0),
            WorldPoint(1862, 3536, 0)
        )
    }

    fun kourend3(): MutableList<WorldPoint> {
        return mutableListOf(
            WorldPoint(1765, 3468, 0),
            WorldPoint(1776, 3468, 0),
            WorldPoint(1773, 3461, 0)
        )
    }


    /**
     * Checks if a monster has spawned and adds it to the list of nearby monsters if applicable.
     *
     * @param it the `NpcSpawned` event.
     * @implNote Calls `monster_nearby()` with the spawned monster.
     */
    override fun onNpcSpawned(it: NpcSpawned) {
        monster_nearby(it.npc)
    }

    /**
     * Updates the list of attack monsters and dormant monsters.
     *
     * @param it the `HitsplatApplied` event.
     * @implNote Updates the `attack_monsters` and `dormant_monsters` lists based on the current location of the player and the NPCs.
     */
    override fun onHitsplatApplied(it: HitsplatApplied) {
        if (this.spot != null) {
            this.attackMonsters =
                client.npcs.filter { it.distanceTo(client.localPlayer!!.worldLocation) <= 2 && attackNpc == it.name } as ArrayList<NPC>
            this.dormantMonsters =
                client.npcs.filter { it.name == dormantNpc && it.distanceTo(this.homePoint) <= this.attackRadius } as ArrayList<NPC>
            health = (client.localPlayer!!.healthRatio).toDouble()
            log = "attacking ${this.attackMonsters.size} crabs"
        }
    }

    /**
     * Updates the list of attack monsters and dormant monsters and checks if a monster has despawned.
     *
     * @param it the `NpcDespawned` event.
     * @implNote Calls `monster_nearby()` with the despawned monster and updates the `attack_monsters` list.
     */
    override fun onNPCDespawned(it: NpcDespawned) {
        monster_nearby(it.npc)
        this.attackMonsters =
            client.npcs.filter { it.distanceTo(client.localPlayer!!.worldLocation) <= 2 && attackNpc == it.name } as ArrayList<NPC>
        log = "attacking ${this.attackMonsters.size} crabs"
    }

    /**
     * Checks if a cannon has spawned and adds it to the list of nearby cannons if applicable.
     *
     * @param it the `GameObjectSpawned` event.
     * @implNote Calls `checkCannon()` with the spawned cannon.
     */
    override fun onGameObjectSpawned(it: GameObjectSpawned) {
        checkCannon(it.gameObject.id)
    }

    /**
     * Determines whether the given NPC is near the home point and has the name 'dormant_npc'.
     * If the NPC satisfies these conditions, it is added to the dormant_monsters list.
     *
     * @param npc the NPC to check for proximity to the home point and name
     */
    fun monster_nearby(npc: NPC) {
        val near = npc.worldLocation.distanceTo(this.homePoint) <= this.attackRadius

        if (npc.name == dormantNpc && near) {
            this.dormantMonsters.add(npc)
        }
    }

    override fun onLoginStateChanged(it: LoginStateChanged) {
        this.dormantMonsters = ArrayList()
        this.attackMonsters = ArrayList()
        this.afkTicker = -1
        this.hopTicker = -1
        this.startTime = System.currentTimeMillis()
    }

    /**
     * Initializes the script by checking if any other players are at home.
     */
    private fun initialize() {
        this.homePoint = config.home().home
        this.deaggroPoint = config.home().deaggro
        this.attackRadius = config.home().distance
        this.dormantNpc = config.home().npc2
        this.attackNpc = config.home().npc1
        this.areaName = config.home().location

        if (initialize_num == -1) {
            println("going to ${this.areaName}")
            when (this.areaName) {
                "Kourend" -> queue.add { kourend_findHome() }
                "Relekka" -> queue.add { go_home() }
            }
            initialize_num = 0
            afkTicker = 15
        }
        checkIfOtherPlayersIsAtHome()
    }

    /**
     * Checks if any other players are within 3 tiles of the `home_point`.
     * If there are other players nearby, increments `hop_ticker`.
     * If `hop_ticker` is greater than 15, calls `hop()` to switch to a different world.
     */
    fun checkIfOtherPlayersIsAtHome() {
        if (client.players.any { it != client.localPlayer!! && it.worldLocation.distanceTo(this.homePoint) <= 3 }) {
            this.log = ("player at home")
            if(this.hopTicker == -1) {
                this.hopTicker = 15
                return
            }
            this.hopTicker

            if (this.hopTicker <= 0) {
                println("hopping for other person is on tile")
                hop()
                this.hopTicker = 15
            }
        }
    }

    /**
     * Switches to a different world that meets specific criteria.
     *
     * @implNote Sets `state_num` to 5 and logs "hop worlds".
     *           Calls `client.hopToWorld()` with the first available world that meets the criteria.
     */
    private fun hop() {
        this.log = ("hop worlds")
        this.spot = null
        this.queue.add {
            this.client.hopToWorld(Worlds.getRandom {
                        it.isMembers && !it.isAllPkWorld && !it.isLeague
                        && !it.isSkillTotal && !it.isTournament && it.id != this.client.world
            })
        }
    }

    /**
     * Sends the player back to their designated `home_point`.
     *
     * @implNote Sets `state_num` to 1 and logs "Going home".
     *           Calls `wait_until()` with a `Runnable` that attempts to walk to `home_point`
     *           and sets `state_num` to 4 once the player has arrived.
     *           Calls `wait4agg()` once the player has arrived.
     */
    private fun go_home() {
        this.log = "Going home"

        val point = this.spot ?: randWorldPoint(this.homePoint, 3)
        println(" heading to: $point")
        if(point.distanceTo(client.localPlayer!!.worldLocation) > 10) {
            loop(
                {
                    walk_to(point)
                },
                "Going home",
            )
        }
    }

    private fun walk_to(point: WorldPoint): Boolean {
        println("Walking to $point")
        Movement.walkTo(point)
        return point.distanceTo(this.client.localPlayer!!.worldLocation) == 0
    }
    /**
     * Checks if the player is interacting with a cannon.
     *
     * @param it the `ObjectID` of the object being interacted with.
     *
     * @implNote If the `ObjectID` is `CANNON_BASE`, calls `hop()` to switch to a different world.
     */
    fun checkCannon(it: Int) {
        if (it == ObjectID.CANNON_BASE) {
            hop()
        }
    }

    /**
     * Handles de-aggroing for the area "Rellekka".
     *
     * @implNote Logs "running -> deagg".
     *           Determines whether the player is inside the tunnel.
     *           If not, interacts with the tunnel to enter it.
     *           If inside, calls `go_home()` to return to the designated `home_point`.
     */
    private fun relekka_deaggro() {
        this.log = ("running -> deagg")
        val tunnel: TileObject? = Objects.getFirst("Tunnel")
        val inside: Boolean = tunnel!!.id == 5014
        if (inside) {
            tunnel.interact("Enter")
            go_home()
        } else if (this.client.localPlayer!!.isIdle) {
            tunnel.interact("Enter")
        }
    }


    /**
     * Starts an AFK timer for the player. If the `callable` argument returns `true`,
     * the `runnable` argument is executed after a specified `wait` time.
     *
     * @param callable a `Callable` object that determines whether the player is idle.
     * @param runnable a `Runnable` object that specifies the action to take when the timer expires.
     * @param wait an integer representing the time to wait before executing the `runnable` action.
     *
     * @see Callable
     * @see Runnable
     * @implNote Tracks the `timeout_ticker` to schedule the next execution of the method.
     *           Uses `immediate` to execute the `runnable` action.
     *           The `wait` argument specifies the duration of the timer.
     */
    private fun loop(
        runnable: Runnable,
        callable: Callable<Boolean>,
        wait: Int,
        name: String,
        state: Int
    ) {
        println("Running::${name}")
        if(callable.call()) {
            println("Ending::${name}")
            this.queue.clear()
            this.queue.add(runnable)
            return
        }
        this.queue.add { loop(runnable, callable, wait, name, state) }
        println("ReRunning::${name}")
    }

    private fun loop(
        callable: Callable<Boolean>,
        name: String,
    ) {
        println("Running::${name}")
        if(callable.call()) {
            println("Ending::${name}")
            return
        }
        this.queue.add { loop(callable, name) }
        println("ReRunning::${name}")
    }
}



/**
TODO:
- do sand crabs in kourend, ammonite crabs on fossil island, rock crabs in rellekka / waterbirth?).
- change attack styles or weapons with level ceilings
- ammonite crab fossil looting and banking
if inventory is full, and fossil drops, eat food and pickup fossil
- break handler
logout and log back in after 5 minutes
whenever there's movement -- add a break before moving
 */
