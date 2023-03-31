package meteor


import dev.hoot.api.entities.TileObjects
import dev.hoot.api.movement.Movement
import dev.hoot.api.scene.Tiles
import eventbus.events.*
import meteor.api.NPCs
import meteor.api.Objects
import meteor.plugins.Plugin
import meteor.plugins.PluginDescriptor
import net.runelite.api.*
import net.runelite.api.coords.WorldPoint
import net.runelite.api.queries.GameObjectQuery
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue

@PluginDescriptor(
    name = "jTempoRoss",
    description = "Automatically does tempoross",
    enabledByDefault = false,
    external = true)
class jTempoRossPlugin : Plugin() {
    val config = configuration<jTempoRossConfig>()
    private var overlay = overlay(jTempoRossOverlay(this, config))
    private var necessary_items: ArrayList<Item> = ArrayList() //TODO get necessary items

    var hop_ticker = -1
    var timeout_ticker = -1
    var afk_ticker = 30

    private val executor = Executors.newWorkStealingPool()
    private val scheduledTaskExecutor = Executors.newScheduledThreadPool(3)
    val queue: LinkedBlockingQueue<Runnable> = LinkedBlockingQueue()
    var start_time = System.currentTimeMillis()
    var log = ""

    var num = -1

    var in_game = false

    val fishing_spots: ArrayList<NPC> = ArrayList()
    private var fish_point: WorldPoint? = null
    var home_point: WorldPoint? = null
    private var north: Boolean = false

    var fishing = false
    private var cooking: Boolean = false




    /**
     * Initializes the bot and runs any pending tasks.
     *
     * @implNote Calls `initialize()` and `immediate?.run()`.
     */
    override fun onGameTick(it: GameTick) {
        if(queue.size > 0) {
            println("queue size: ${queue.size}")
        }


        if(num == -1) {
            log = "INITIALIZING"
            create_event({ go_home() }, "Initializing, going home.")
            num = 0
        }

        if(FIRE.invoke()!!.filter { it.distanceTo(client.localPlayer!!.worldLocation) < 5}.isNotEmpty() ) {
            println("FIRE")
            if(water_buckets!!.isNotEmpty()) {
                INTERACT_FIRE.invoke()
            }
        }

        if(queue.isEmpty()) {
            if(client.localPlayer!!.isIdle) {
                fish()
            }
        }
        queue.poll()?.run()
    }

    private fun fire_too_close(it: NPC): Boolean {
        val fire_spots = FIRE.invoke()

        if (fire_spots != null) {
            for(spot in fire_spots) {
                if (spot.distanceTo(it.worldLocation) < 3) {
                    return false
                }
            }
        }

        return true
    }

    private fun fish(): Boolean {
        log = "GOING TO FISH"
        val fish_spot = client.npcs.filter { it.id == 10565 || it.id == 10569 && fire_too_close(it) }.maxByOrNull { it.id }

        if(fish_spot == null) {
            Movement.walkTo(fish_point)
            return false
        } else {
            fish_spot.interact("Harpoon")
            return true
        }
    }

    private fun check_items() {
        if(rope_count?.size == 0 || buckets_count?.size == 0 || hammer_count?.size == 0 || harpoon_count?.size == 0) {
            create_event(
                {
                    walkTo_home_point()
                },
                { create_event({ collect_items() }, "COLLECTING ITEMS", 0) },
                0,
                "Going to ship to collect items",
                0
            )
        }
    }

//    private fun get_rope(): Boolean {
//
//    }

    private fun walkTo_home_point(): Boolean {
        return if(client.localPlayer!!.worldLocation.distanceTo(home_point) < 3) {
            true
        } else {
            Movement.walkTo(home_point)
            false
        }
    }

    private val inventory get() = client.getItemContainer(InventoryID.INVENTORY)
    private val rope_count get() = inventory?.items?.filter { it.name == "Rope" }
    private val buckets_count get() = inventory?.items?.filter {  it.name == "Bucket"}
    private val water_buckets get() = inventory?.items?.filter {  it.name == "Bucket of water"}
    private val hammer_count get() = inventory?.items?.filter { it.name == "Hammer" }
    private val harpoon_count get() = inventory?.items?.filter { it.name == "Harpoon" }

    private fun collect_items(): Boolean {
        log = "COLLECTION ITEMS"
        println("ROPE: " + rope_count)
        println("HAMMER: " + hammer_count)
        println("BUCKET: " + buckets_count)
        val bucket_sum = buckets_count?.size?.let { water_buckets?.size?.plus(it) }
        if(rope_count?.size == 0) {
            log = "COLLECTING ROPE"
            println("COLLECTING ROPE")
            queue.add { INTERACT_ROPE_BUCKET.invoke() }
            repeat(3, ) { queue.add {  } }
            return false
        }
        if (bucket_sum == 0) {
            queue.add{ INTERACT_BUCKETS_BUCKET.invoke() }
            repeat(3, ) { queue.add {  } }
            queue.add{ INTERACT_BUCKETS_BUCKET.invoke() }
            repeat(3, ) { queue.add {  } }
            queue.add{ INTERACT_BUCKETS_BUCKET.invoke() }
            return false
        } else if (bucket_sum == 1) {
            queue.add{ INTERACT_BUCKETS_BUCKET.invoke() }
            repeat(3, ) { queue.add {  } }
            queue.add{ INTERACT_BUCKETS_BUCKET.invoke() }
            repeat(3, ) { queue.add {  } }
            return false
        } else if (bucket_sum == 2) {
            queue.add{ INTERACT_BUCKETS_BUCKET.invoke() }
            repeat(3, ) { queue.add {  } }
            return false
        }

        if(water_buckets!!.size < 2) {
            queue.add{ INTERACT_WATER_PUMP.invoke() }
            repeat(3, ) { queue.add {  } }
            return false
        }

        if(hammer_count?.size == 0) {
            log = "COLLECTING HAMMER"
            println("COLLECTING HAMMER")
            queue.add { INTERACT_HAMMER_BUCKET.invoke() }
            repeat(3, ) { queue.add {  } }
            return false
        }
        if(harpoon_count?.size == 0) {
            log = "COLLECTING HARPOON"
            println("COLLECTING HARPOON")
            queue.add { INTERACT_HARPOON_BUCKET.invoke() }
            repeat(3, ) { queue.add {  } }
            return false
        }
        log = "FINISHED COLLECTING"
        println("FINISHED COLLECTING")
        create_event({ fish() }, "Fishing", 0)
        repeat(3, ) { queue.add {  } }
        return true
    }

    private fun go_home(): Boolean {
//        if(LADDER().worldLocation.distanceTo(client.localPlayer!!.worldLocation) <= 250) {
            create_event( INTERACT_LADDER, "going to ladder")
            create_event({ onShip() },"Waiting for Ship to Land", 0)
            return true
//        } else {
//            Movement.walkTo(WorldPoint(3156, 2835, 0))
//            create_event({ go_home() }, "recalling go_home")
//            return false
//        }
    }

    private fun onShip(): Boolean {
        var npc: NPC? = null
        if (client.npcs.any {npc = it
                it.name == "Cannoneer"
        }) {
            log = "ON THE SHIP"
            home_point = client.localPlayer!!.worldLocation
            north = false
            if(client.localPlayer!!.localLocation.x > 50) {
                north = true
                fish_point = WorldPoint(client.localPlayer!!.worldLocation.x + 8, client.localPlayer!!.worldLocation.y + 13, 0)
            } else {
                fish_point = WorldPoint(client.localPlayer!!.worldLocation.x - 8, client.localPlayer!!.worldLocation.y - 13, 0)
            }
            check_items()
            return true
        } else {
            return false
        }
    }

    override fun onChatMessage(it: ChatMessage) {
        if(home_point != null) {
            check_items()
        }
        fishing = false

        if(it.message.contains("Spirit Anglers weigh")) {
            log = "GAME STARTED"
            println("$it")
        }

        if(it.message.contains("vulnerable")) {
            log = "BOSS VULNERABLE"
            println("$it")
            return
        }

        if(it.message.contains("A colossal wave")) {
            log = "WATCH OUT FOR THE WAVE"
            create_event(TETHER_POLL, "Tethering to Totem Poll")
            repeat(3, ) { queue.add {  } }

            println("$it")
            return
        }

        if(it.message.contains("untether")) {
            log = "WAVE IS FINISHED"
            create_event({ fish() }, "Going to fish")
            println("$it")
        }

        if(it.message.contains("skies")) {
            log = "GAME ENDED"
            println("$it")
            return
        }

        if(it.message.contains("harpooning")) {
//            log = "STARTED FISHING"
            fishing = true
            println("$it")
            return
        }

        if(it.message.contains("successfully cook")) {
            println("${it.message}")
        }

        if(it.message.contains("earned enough")) {
            println("${it.message}")
        }

        if(it.message.contains("successfully cook")) {
//            log = "COOKING"
//            println("${it.message}")
        }
    }

    override fun onGameObjectSpawned(it: GameObjectSpawned) {
        if(it.gameObject.name != "null" || it.gameObject.id == 41006) {
            println("Object spawned: ${it.gameObject.name} ${it.gameObject.worldArea}")

            if(it.gameObject.name == "Damaged totem pole")
            return
        }

        if(it.gameObject.id == 37582) {
            if(water_buckets!!.isNotEmpty()) {
                Objects.getAll(37582)?.filter { it.distanceTo(home_point) < 5 || it.distanceTo(fish_point) < 5 }?.forEach {
//                    create_event({ it.interact("Douse") }, "Dousing fire")
                }
            } else {
                create_event({ fill_buckets() }, "Filling Buckets", 0)
            }
        }
    }

    fun fill_buckets(): Boolean {
        if(water_buckets!!.size < 3) {
            INTERACT_WATER_PUMP.invoke()
            return false
        } else {
            return true
        }
    }

    override fun onItemDespawned(it: ItemDespawned) {
        println("Item despawned ${it.item.getName()}")
    }

    override fun onWidgetTextChanged(it: WidgetTextChanged) {
        if(it.text!!.contains("Energy: ")) {
            println("${it.text}")
            return
        }

        if(it.text!!.contains("Energy: ")) {
            println("${it.text}")
            return
        }

        if(it.text!!.contains("Essence: ")) {
            println("${it.text}")
            return
        }
    }

    override fun onNpcChanged(it: NpcChanged) {
        if(it.npc.name!!.contains("Fishing spot")) {
            println("$it")
        }
    }

    override fun onWidgetLoaded(it: WidgetLoaded) {
        println("Widget loaded: ${it.groupId}")
        super.onWidgetLoaded(it)


        // TODO get tempoross widgets and check how they update
    }

    override fun onItemQuantityChanged(it: ItemQuantityChanged) {
        if(it.item.getName()?.contains("raw") == true) {
            fishing = true
            if(inventory!!.items.size > 15) {
                create_event({ cook() }, "Cooking")
                cook()
            }
            return
        } else {
            fishing = false
        }

        if(it.item.getName()?.contains("harpoonfish") == true) {
            cooking = true
            return
        } else {
            cooking = false
        }

        if(inventory!!.items.size > 25) {
            create_event(
                { walkTo_home_point() },
                { load_ammo() },
                0,
                "",
                0
            )
        }
    }

    private fun load_ammo() {
        queue.add { INTERACT_FISH_BUCKET.invoke() }
        if(inventory!!.items.filter { it.name!!.contains("harpoonfish") }.isNotEmpty()) {
            queue.add { load_ammo() }
        }
    }

    private fun cook(): Boolean {
        if(cooking) {
            return false
        } else {
            if(client.localPlayer!!.isIdle) {
                create_event(
                    { walkTo_home_point() },
                    { load_ammo() },
                    0,
                    "",
                    0
                )
                return true
            } else {
                return false
            }
        }
    }

    fun create_event(callable: Callable<Boolean>, runnable: Runnable, wait: Int, name: String, state: Int) {
        println("Event created::${name}")
        queue.add { loop(runnable, callable, wait, name, state) }
    }
    private fun create_event(callable: Callable<Boolean>, name: String, state: Int) {
        println("Event created::${name}")
        queue.add { loop(callable, name) }
    }

    private fun create_event(runnable: Runnable, name: String,) {
        println("Event created::${name}")
        queue.add(runnable)
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
            queue.clear()
            queue.add(runnable)
            return
        }
        queue.add { loop(runnable, callable, wait, name, state) }
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
        queue.add { loop(callable, name) }
        println("ReRunning::${name}")
    }

    /************************/
    /******GAME OBJEcTS******/
    /************************/
    /************************/
    /************************/
    val LADDER = { TileObjects.getNearest { it.name == "Rope ladder" } }
    val INTERACT_LADDER = {
        log = "interacting with ladder"
        TileObjects.getNearest { it.name == "Rope ladder" }.interact("Climb")  }

    /************************/
    /******ON THE SHIP ******/
    /************************/
    val INTERACT_WATER_PUMP = { TileObjects.getAll { it.id == 41000 }.first { it.distanceTo(home_point) < 15 || it.distanceTo(client.localPlayer!!.worldLocation) < 10 }.interact("Use") }
    private val INTERACT_BUCKETS_BUCKET = { TileObjects.getAll { it.id == 40966 }.first { it.distanceTo(home_point) < 15 }.interact("Take") }
    private val INTERACT_ROPE_BUCKET = { TileObjects.getAll { it.id == 40965 }.first { it.distanceTo(home_point) < 15 }.interact("Take") }
    private val INTERACT_HAMMER_BUCKET = { TileObjects.getAll { it.id == 40964 }.first { it.distanceTo(home_point) < 15 || it.distanceTo(client.localPlayer!!.worldLocation) < 5 }.interact("Take")}
    private val INTERACT_HARPOON_BUCKET = { TileObjects.getAll { it.id == 40967 }.first { it.distanceTo(home_point) < 15 }.interact("Take") }
    val INTERACT_FISH_BUCKET = { TileObjects.getAll { it.id == 40968 }.first { it.distanceTo(home_point) < 15 }.interact("Take") }

    val WATER_PUMP = { TileObjects.getNearest { it.name.contains("<col=ffff>Water pump") } }
    val BUCKETS_BUCKET = { TileObjects.getNearest { it.name.contains("<col=ffff>Buckets") } }
    val ROPE_BUCKET = { TileObjects.getNearest { it.name.contains("<col=ffff>Ropes") } }
    val HAMMER_BUCKET = { TileObjects.getNearest { it.name.contains("<col=ffff>Hammers") } }
    val HARPOON_BUCKET = { TileObjects.getNearest { it.name.contains("Harpoons") } }
    val FISH_BUCKET = { TileObjects.getNearest { it.name.contains("<col=ffff00><col=00ffff>Ammunition crate</col>") } }



    val FISHING_SPOT = { NPCs.getFirst(10568) } //10569 is special
    val INTERACT_FISHING_SPOT = { NPCs.getFirst(10568)!!.interact("Harpoon") }
    val SHRINE = { TileObjects.getNearest { it.name.contains("Shrine") } }
    val SPIRIT_POOL = { client.npcs.first { it.name!!.contains("Spirit pool") } } // 10571
    val INTERACT_SPIRIT_POOL = { client.npcs.first { it.name!!.contains("<col=ffff00><col=00ffff>Spirit pool</col>") }.interact("Harpoon") }
    private val TETHER_POLL: Runnable = Runnable { Objects.getAll(41353, 41354, 41355, 41352)
        ?.first { it.distanceTo(fish_point) < 15 || it.distanceTo(client.localPlayer!!.worldLocation) < 7}.let { it?.interact(it.id, 3)} }
    private val INTERACT_FIRE = {
        Objects.getAll(37582)?.first { it.distanceTo(client.localPlayer!!.worldLocation) < 5 }
            ?.let { it.interact(it.id, 9)}
    }

    private val FIRE = {
        Objects.getAll(37582)
    }
    val INTERACT_TOTEM_POLL = { TileObjects.getNearest { it.id == 41354}.interact("Tether") }


    override fun onGameObjectChanged(it: GameObjectChanged) {
        println("GAME OBJECT CHANGED: " + it.oldObject.name + " -> " + it.newObject.name)
    }
    fun check_tether_poll() {
        GameObjectQuery().idEquals(41352, 41353, 41354, 41355, 41352, 41010).result(client).filter { it.name.contains("Repair Totem") }

    }


    //broken 40997
    //fire 37582

    val INTERACT_BANK = { TileObjects.getNearest { it.id == 41315 }.interact("Use") }



//41006 game object id for cloud spot location





    val BANK = { Tiles.getAt(client.localPlayer!!.worldLocation).gameObjects.filter { it?.id == 41315 } }

}
