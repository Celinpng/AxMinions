package com.artillexstudios.axminions.minions.miniontype

import com.artillexstudios.axapi.scheduler.Scheduler
import com.artillexstudios.axapi.scheduler.impl.FoliaScheduler
import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.AxMinionsAPI
import com.artillexstudios.axminions.api.config.Config
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.api.utils.LocationUtils
import com.artillexstudios.axminions.api.utils.MinionUtils
import com.artillexstudios.axminions.api.utils.fastFor
import com.artillexstudios.axminions.api.warnings.Warnings
import com.artillexstudios.axminions.minions.MinionTicker
import com.artillexstudios.axminions.nms.NMSHandler
import dev.lone.itemsadder.api.CustomBlock
import me.kryniowesegryderiusz.kgenerators.Main
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.DoubleChestInventory
import org.bukkit.inventory.FurnaceRecipe
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MinerMinionType : MinionType("miner", AxMinionsPlugin.INSTANCE.getResource("minions/miner.yml")!!, true) {
    companion object {
        private var asyncExecutor: ExecutorService? = null
        private val smeltingRecipes = ArrayList<FurnaceRecipe>()
        private val faces = arrayOf(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)

        init {
            Bukkit.recipeIterator().forEachRemaining {
                if (it is FurnaceRecipe) {
                    smeltingRecipes.add(it)
                }
            }
        }
    }

    private var generatorMode = false
    private val whitelist = arrayListOf<Material>()

    override fun shouldRun(minion: Minion): Boolean {
        return MinionTicker.getTick() % minion.getNextAction() == 0L
    }

    override fun onToolDirty(minion: Minion) {
        val minionImpl = minion as com.artillexstudios.axminions.minions.Minion
        minionImpl.setRange(getDouble("range", minion.getLevel()))
        val tool = minion.getTool()?.getEnchantmentLevel(Enchantment.DIG_SPEED)?.div(10.0) ?: 0.1
        val efficiency = 1.0 - if (tool > 0.9) 0.9 else tool
        minionImpl.setNextAction((getLong("speed", minion.getLevel()) * efficiency).roundToInt())

        generatorMode = getConfig().getString("break", "generator").equals("generator", true)
        whitelist.clear()
        getConfig().getStringList("whitelist").fastFor {
            whitelist.add(Material.matchMaterial(it.uppercase(Locale.ENGLISH)) ?: return@fastFor)
        }
    }

    override fun run(minion: Minion) {
        if (minion.getLinkedInventory() != null && minion.getLinkedInventory()?.firstEmpty() != -1) {
            Warnings.remove(minion, Warnings.CONTAINER_FULL)
        }

        if (minion.getLinkedChest() != null && minion.getLinkedInventory() != null) {
            val type = minion.getLinkedChest()!!.block.type
            if (type == Material.CHEST && minion.getLinkedInventory() !is DoubleChestInventory && hasChestOnSide(minion.getLinkedChest()!!.block)) {
                minion.setLinkedChest(minion.getLinkedChest())
            }

            if (type == Material.CHEST && minion.getLinkedInventory() is DoubleChestInventory && !hasChestOnSide(minion.getLinkedChest()!!.block)) {
                minion.setLinkedChest(minion.getLinkedChest())
            }

            if (type != Material.CHEST && type != Material.TRAPPED_CHEST && type != Material.BARREL) {
                minion.setLinkedChest(null)
            }
        }

        if (minion.getLinkedInventory() == null) {
            minion.setLinkedChest(null)
        }

        if (!minion.canUseTool()) {
            Warnings.NO_TOOL.display(minion)
            return
        }

        if (minion.getLinkedInventory()?.firstEmpty() == -1) {
            Warnings.CONTAINER_FULL.display(minion)
            return
        }

        Warnings.remove(minion, Warnings.NO_TOOL)

        var amount = 0
        var xp = 0
        val mode = getConfig().getString("mode").lowercase(Locale.ENGLISH)

        getBlocksFromMode(minion, mode).fastFor { location ->
            if (AxMinionsPlugin.integrations.kGeneratorsIntegration) {
                val gen = Main.getPlacedGenerators().getLoaded(location)
                if (gen != null) {
                    val possible = gen.isBlockPossibleToMine(location)
                    if (possible) {
                        minion.addToContainerOrDrop(gen.lastGeneratedObject.customDrops?.item?.clone() ?: return@fastFor)
                        gen.scheduleGeneratorRegeneration()
                        return@fastFor
                    } else {
                        return@fastFor
                    }
                }
            }

            val block = location.block
            val canBreak = if (generatorMode) {
                MinionUtils.isStoneGenerator(location)
            } else {
                whitelist.contains(block.type)
            }

            if (canBreak) {
                val drops = block.getDrops(minion.getTool())
                xp += NMSHandler.get().getExp(block, minion.getTool() ?: return@fastFor)
                drops.forEach { amount += it.amount }
                minion.addToContainerOrDrop(drops)
                block.type = Material.AIR
            }
        }

        val coerced = (minion.getStorage() + xp).coerceIn(0.0, minion.getType().getLong("storage", minion.getLevel()).toDouble())
        minion.setStorage(coerced)
        minion.setActions(minion.getActionAmount() + amount)
        for (i in 0 until amount) {
            minion.damageTool()
        }
    }

    private fun getBlocksFromMode(minion: Minion, mode: String): List<org.bukkit.Location> {
        return when (mode) {
            "sphere" -> LocationUtils.getAllBlocksInRadius(minion.getLocation(), minion.getRange(), false)
            "cube" -> LocationUtils.getAllBlocksInCube(minion.getLocation(), minion.getRange(), false)
            "square" -> LocationUtils.getAllBlocksInSquare(minion.getLocation(), minion.getRange(), false)
            "face" -> LocationUtils.getAllBlocksFacing(minion.getLocation(), minion.getRange(), minion.getDirection().facing)
            "tunnel" -> getTunnelBlocks(minion)
            "deep" -> getDeepBlocks(minion)
            else -> emptyList()
        }
    }

    private fun getTunnelBlocks(minion: Minion): List<org.bukkit.Location> {
        val blocks = mutableListOf<org.bukkit.Location>()
        val face = minion.getDirection().facing
        val locCopy = minion.getLocation().clone()

        for (i in 0 until minion.getRange().toInt()) {
            locCopy.add(face.direction)

            for (yOffset in 0..1) {
                blocks.add(locCopy.clone().add(0.0, yOffset.toDouble(), 0.0))
            }
        }
        return blocks
    }
    private fun getDeepBlocks(minion: Minion): List<org.bukkit.Location> {
        val blocks = mutableListOf<org.bukkit.Location>()
        val locCopy = minion.getLocation().clone()
        val range = minion.getRange().toInt() // Transforma o range em Int para realizar iterações em blocos

        // Obtém o limite inferior do mundo dinamicamente
        val minY = locCopy.world?.minHeight

        // Loop para minerar os blocos no eixo Y diretamente para baixo (até minY)
        for (y in locCopy.blockY downTo minY!!) { // Move do Y atual até o limite inferior do mundo
            for (dx in -range..range) {
                for (dz in -range..range) {
                    val targetLocation = locCopy.clone().add(dx.toDouble(), (y - locCopy.blockY).toDouble(), dz.toDouble())
                    blocks.add(targetLocation)
                }
            }
        }
        return blocks
    }

}