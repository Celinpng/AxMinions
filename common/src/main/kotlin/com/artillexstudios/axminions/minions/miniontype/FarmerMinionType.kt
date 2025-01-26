package com.artillexstudios.axminions.minions.miniontype

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.events.PreFarmerMinionHarvestEvent
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.api.utils.LocationUtils
import com.artillexstudios.axminions.api.utils.MinionUtils
import com.artillexstudios.axminions.api.utils.fastFor
import com.artillexstudios.axminions.api.warnings.Warnings
import com.artillexstudios.axminions.minions.MinionTicker
import dev.lone.itemsadder.api.CustomBlock
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Ageable
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.DoubleChestInventory
import org.bukkit.inventory.ItemStack
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChests
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChestsAPI
import java.util.logging.Level
import kotlin.math.roundToInt

@Suppress("DUPLICATE_LABEL_IN_WHEN", "DuplicatedCode")
class FarmerMinionType : MinionType("farmer", AxMinionsPlugin.INSTANCE.getResource("minions/farmer.yml")!!, true) {

    override fun shouldRun(minion: Minion): Boolean {
        return MinionTicker.getTick() % minion.getNextAction() == 0L
    }

    override fun onToolDirty(minion: Minion) {
        val minionImpl = minion as com.artillexstudios.axminions.minions.Minion
        minionImpl.setRange(getDouble("range", minion.getLevel()))
        val tool = minion.getTool()?.getEnchantmentLevel(Enchantment.DIG_SPEED)?.div(10.0) ?: 0.1
        val efficiency = 1.0 - if (tool > 0.9) 0.9 else tool
        minionImpl.setNextAction((getLong("speed", minion.getLevel()) * efficiency).roundToInt())
    }

    override fun run(minion: Minion) {
        if (!canWork(minion)) return

        val drops = arrayListOf<ItemStack>()
        val blocks = getBlocksToProcess(minion)

        blocks.fastFor { location ->
            val block = location.block

            // Integração com ItemsAdder
            if (AxMinionsPlugin.integrations.itemsAdderIntegration) {
                val customBlock = CustomBlock.byAlreadyPlaced(block)
                if (customBlock !== null) {
                    handleItemsAdderBlock(minion, block, customBlock, drops)
                    return@fastFor
                }
            }

            // Verificação e colheita dos tipos de blocos
            when (block.type) {
                Material.CACTUS, Material.SUGAR_CANE, Material.BAMBOO -> handleStackableBlock(minion, block, drops)
                Material.MELON, Material.PUMPKIN -> handleHarvestableBlock(minion, block, drops)
                Material.COCOA_BEANS, Material.COCOA, Material.NETHER_WART,
                Material.WHEAT, Material.CARROTS, Material.BEETROOTS,
                Material.POTATOES, Material.PITCHER_CROP, Material.CAVE_VINES -> handleAgeableBlock(minion, block, drops)
                Material.SWEET_BERRY_BUSH -> handleBerryBush(minion, block, drops)
                Material.TORCHFLOWER -> handleTorchflowerBlock(minion, block, drops)
                else -> return@fastFor
            }
        }

        // Processa os drops com a integração do AdvancedChests
        handleDrops(minion, drops)
    }

    private fun canWork(minion: Minion): Boolean {
        // Verifica inventário e status do minion
        if (minion.getLinkedInventory()?.firstEmpty() != -1) {
            Warnings.remove(minion, Warnings.CONTAINER_FULL)
        } else {
            Warnings.CONTAINER_FULL.display(minion)
            return false
        }

        if (minion.getLinkedChest() != null) {
            verifyLinkedChest(minion)
        }

        if (minion.getLinkedInventory() == null) {
            minion.setLinkedChest(null)
        }

        if (!minion.canUseTool()) {
            Warnings.NO_TOOL.display(minion)
            return false
        }

        Warnings.remove(minion, Warnings.NO_TOOL)
        return true
    }

    private fun verifyLinkedChest(minion: Minion) {
        val type = minion.getLinkedChest()!!.block.type
        if (type !in listOf(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL)) {
            minion.setLinkedChest(null)
            return
        }

        if (type == Material.CHEST && minion.getLinkedInventory() !is DoubleChestInventory &&
            hasChestOnSide(minion.getLinkedChest()!!.block)) {
            minion.setLinkedChest(minion.getLinkedChest())
        }

        if (type == Material.CHEST && minion.getLinkedInventory() is DoubleChestInventory &&
            !hasChestOnSide(minion.getLinkedChest()!!.block)) {
            minion.setLinkedChest(minion.getLinkedChest())
        }
    }

    private fun getBlocksToProcess(minion: Minion) = when (getConfig().getString("mode")) {
        "face" -> LocationUtils.getAllBlocksFacing(minion.getLocation(), minion.getRange(), minion.getDirection().facing)
        "sphere" -> LocationUtils.getAllBlocksInRadius(minion.getLocation(), minion.getRange(), false)
        "cube" -> LocationUtils.getAllBlocksInCube(minion.getLocation(), minion.getRange(), false)
        else -> LocationUtils.getAllBlocksInSquare(minion.getLocation(), minion.getRange(), false)
    }

    private fun handleItemsAdderBlock(minion: Minion, block: org.bukkit.block.Block, customBlock: CustomBlock, drops: ArrayList<ItemStack>) {
        val preHarvestEvent = PreFarmerMinionHarvestEvent(minion, block)
        Bukkit.getPluginManager().callEvent(preHarvestEvent)
        if (preHarvestEvent.isCancelled) return
        val blockDrops = customBlock.getLoot(minion.getTool(), false)
        drops.addAll(blockDrops)
        customBlock.remove()
    }

    private fun handleStackableBlock(minion: Minion, block: org.bukkit.block.Block, drops: ArrayList<ItemStack>) {
        val aboveBlock = block.getRelative(BlockFace.UP)
        if (aboveBlock.type != block.type) return
        MinionUtils.getPlant(block).fastFor { targetBlock ->
            if (targetBlock.type == block.type) {
                drops.addAll(targetBlock.getDrops(minion.getTool()))
                if(targetBlock.type == Material.BAMBOO) {
                    targetBlock.type = Material.BAMBOO_SAPLING
                } else{
                    targetBlock.type = Material.AIR
                }
            }
        }
    }

    private fun handleHarvestableBlock(minion: Minion, block: org.bukkit.block.Block, drops: ArrayList<ItemStack>) {
        drops.addAll(block.getDrops(minion.getTool()))
        block.type = Material.AIR
    }

    private fun handleAgeableBlock(minion: Minion, block: org.bukkit.block.Block, drops: ArrayList<ItemStack>) {
        val ageable = block.blockData as Ageable
        if (ageable.age != ageable.maximumAge) return
        drops.addAll(block.getDrops(minion.getTool()))
        ageable.age = 0
        block.blockData = ageable
    }

    private fun handleBerryBush(minion: Minion, block: org.bukkit.block.Block, drops: ArrayList<ItemStack>) {
        val ageable = block.blockData as Ageable
        if (ageable.age != ageable.maximumAge) return
        drops.addAll(block.getDrops(minion.getTool()))
        ageable.age = 1
        block.blockData = ageable
    }

    private fun handleTorchflowerBlock(minion: Minion, block: org.bukkit.block.Block, drops: ArrayList<ItemStack>) {
        drops.addAll(block.getDrops(minion.getTool()))
        block.type = Material.TORCHFLOWER_CROP
    }

    private fun handleDrops(minion: Minion, drops: List<ItemStack>) {
        // Retorna imediatamente se a lista de drops estiver vazia
        if (drops.isEmpty()) return

        val linkedChestLocation = minion.getLinkedChest() // Obtém a localização do baú vinculado ao minion
        val storageIntegration = AxMinionsPlugin.integrations.getStorageIntegration() // Obtém a integração de armazenamento
        val instance = AdvancedChestsAPI.getInstance()

        // Tenta armazenar os itens no baú vinculado, se possível
        if (linkedChestLocation != null && storageIntegration != null) {
            try {
                // Obtém o baú (AdvancedChest) na localização vinculada
                val advancedChest = instance.chestsManager.getAdvancedChest(linkedChestLocation)

                // Verifica se o baú recuperado é do tipo correto e válido para armazenamento
                if (advancedChest != null && storageIntegration.hasStorage(linkedChestLocation, advancedChest)) {
                    // Armazena os itens no baú e captura os itens restantes, se houver
                    val remainingDrops = storageIntegration.addItem(advancedChest.location, drops)

                    // Caso existam itens restantes, adiciona ao contêiner ou solta no chão
                    if (remainingDrops.isNotEmpty()) {
                        minion.addToContainerOrDrop(remainingDrops)
                    }
                    return // Finaliza o método após armazenar os itens
                } else if (advancedChest == null) {
                    // Loga se nenhum AdvancedChest foi encontrado na localização
                    Bukkit.getLogger().warning("[Minion] Nenhum AdvancedChest encontrado na localização: $linkedChestLocation")
                }
            } catch (e: Exception) {
                // Registra o erro caso ocorra falha ao processar os itens
                Bukkit.getLogger().log(Level.SEVERE, "[Minion] Erro ao interagir com o AdvancedChest na localização $linkedChestLocation: ${e.message}", e)
            }
        }

        // Se não houver baú ou integração, adiciona os drops ao contêiner ou solta no chão
        minion.addToContainerOrDrop(drops)
    }
}