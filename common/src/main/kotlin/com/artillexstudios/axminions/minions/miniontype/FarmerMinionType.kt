package com.artillexstudios.axminions.minions.miniontype

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.events.PreFarmerMinionHarvestEvent
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.api.utils.LocationUtils
import com.artillexstudios.axminions.api.warnings.Warnings
import com.artillexstudios.axminions.minions.MinionTicker
import dev.lone.itemsadder.api.CustomBlock
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Ageable
import org.bukkit.block.Block
import org.bukkit.block.Container
import org.bukkit.Location
import org.bukkit.enchantments.Enchantment
import org.bukkit.inventory.DoubleChestInventory
import org.bukkit.inventory.ItemStack
import us.lynuxcraft.deadsilenceiv.advancedchests.chest.AdvancedChest


@Suppress("DUPLICATE_LABEL_IN_WHEN", "DuplicatedCode")
class FarmerMinionType : MinionType("farmer", AxMinionsPlugin.INSTANCE.getResource("minions/farmer.yml")!!, true) {

    override fun shouldRun(minion: Minion): Boolean {
        return MinionTicker.getTick() % minion.getNextAction() == 0L
    }

    override fun onToolDirty(minion: Minion) {
        val minionImpl = minion as com.artillexstudios.axminions.minions.Minion
        minionImpl.setRange(getDouble("range", minion.getLevel()))
        val tool = minion.getTool()?.getEnchantmentLevel(Enchantment.DIG_SPEED)?.div(10.0) ?: 0.1
        // val efficiency = 1.0 - if (tool > 0.9) 0.9 else tool
        minionImpl.setNextAction((getLong("speed", minion.getLevel())).toInt())
    }

    override fun run(minion: Minion) {
        if (!canWork(minion)) return

        val drops = arrayListOf<ItemStack>()
        val blocks = getBlocksToProcess(minion)
        var blocksBroken = 0 // Contador de blocos realmente modificados

        verifyLinkedChest(minion)

        blocks.forEach { location ->
            val block = location.block ?: return@forEach
            blocksBroken += processBlock(minion, block, drops) // Soma os blocos processados
        }

        handleDrops(minion, drops, blocksBroken) // Usa "blocksBroken"
    }

    private fun canWork(minion: Minion): Boolean {
        // Verifica inventário e status do minion
        if (minion.getLinkedInventory()?.firstEmpty() != -1) {
            Warnings.remove(minion, Warnings.CONTAINER_FULL)
        } else {
            Warnings.CONTAINER_FULL.display(minion)
            return false
        }

        if (!minion.canUseTool()) {
            Warnings.NO_TOOL.display(minion)
            return false
        }
        Warnings.remove(minion, Warnings.NO_TOOL)
        return true
    }

    private fun verifyLinkedChest(minion: Minion) {
        val chestLocation = minion.getLinkedChest() ?: return // Sem baú

        val block = chestLocation.block
        val type = block.type

        if (type !in listOf(Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL)) {
            minion.setLinkedChest(null)
            return
        }

        // Verifica se o estado do contêiner ainda é válido
        val chestInventory = (block.state as? Container)?.inventory ?: run {
            //Bukkit.getLogger().warning("[AxMinions] Linked chest at ${chestLocation} is no longer valid.")
            minion.setLinkedChest(null) // Remove o baú inválido
            return
        }

        // Verifica se é DoubleChest e ajusta estado
        if (type == Material.CHEST) {
            val hasDoubleChest = hasChestOnSide(block)
            if (chestInventory is DoubleChestInventory && !hasDoubleChest) minion.setLinkedChest(null)
            if (chestInventory !is DoubleChestInventory && hasDoubleChest) minion.setLinkedChest(chestLocation)
        }
    }

    private fun getBlocksToProcess(minion: Minion) = when (getConfig().getString("mode")) {
        "face" -> LocationUtils.getAllBlocksFacing(
            minion.getLocation(),
            minion.getRange(),
            minion.getDirection().facing
        )

        "sphere" -> LocationUtils.getAllBlocksInRadius(minion.getLocation(), minion.getRange(), false)
        "cube" -> LocationUtils.getAllBlocksInCube(minion.getLocation(), minion.getRange(), false)
        else -> LocationUtils.getAllBlocksInSquare(minion.getLocation(), minion.getRange(), false)
    }


    private fun handleItemsAdderBlock(
        minion: Minion,
        block: Block,
        customBlock: CustomBlock,
        drops: ArrayList<ItemStack>
    ): Int {
        // Dispara evento de pré-colheita para interação com outros plugins
        val preHarvestEvent = PreFarmerMinionHarvestEvent(minion, block)
        Bukkit.getPluginManager().callEvent(preHarvestEvent)

        // Cancela a operação se o evento foi cancelado
        if (preHarvestEvent.isCancelled) {
            Bukkit.getLogger().info("Harvest event for custom block ${customBlock.id} at ${block.location} was cancelled.")
            return 0 // Nenhum bloco foi processado
        }

        // Obtém os drops do bloco customizado
        val blockDrops = customBlock.getLoot(minion.getTool(), false)

        // Adiciona os drops à lista fornecida
        drops.addAll(blockDrops)

        // Remove o bloco customizado
        customBlock.remove()

        // Retorna 1, pois processamos exatamente 1 bloco
        return 1
    }

    private fun handleStackableBlock(minion: Minion, block: Block, drops: ArrayList<ItemStack>): Int {
        val blockType = block.type
        val maxHeight = block.world.maxHeight
        var currentBlock = block.getRelative(BlockFace.UP)
        var blocksProcessed = 0

        // Contabiliza os blocos acima do bloco base
        while (currentBlock.type == blockType && currentBlock.y < maxHeight) {
            drops.addAll(currentBlock.getDrops(minion.getTool()))
            currentBlock.type = Material.AIR
            blocksProcessed++
            currentBlock = currentBlock.getRelative(BlockFace.UP)
        }

        // Tratamento especial para o bloco base
        when (blockType) {
            Material.BAMBOO -> {
                // Remove o bloco base e transforma em muda
                block.type = Material.BAMBOO_SAPLING
                // Retorna a contagem de blocos processados, pois não contamos o base
                return blocksProcessed
            }
            Material.KELP -> {
                // Remove o bloco base e transforma em planta de kelp
                block.type = Material.KELP_PLANT
                return blocksProcessed
            }
            else -> {
                // Para Cacto e Cana de Açúcar, não fazemos nada especial
                block.type = Material.AIR
                return blocksProcessed
            }
        }
    }

    private fun handleHarvestableBlock(minion: Minion, block: Block, drops: ArrayList<ItemStack>): Int {
        drops.addAll(block.getDrops(minion.getTool()))
        block.type = Material.AIR // Remove o bloco
        return 1 // Apenas 1 bloco foi colhido
    }

    private fun handleAgeableBlock(minion: Minion, block: Block, drops: ArrayList<ItemStack>): Int {
        val ageable = block.blockData as? Ageable ?: return 0 // Se não for Ageable, retorna 0
        if (ageable.age != ageable.maximumAge) return 0 // Se não estiver no estágio máximo, retorna 0

        drops.addAll(block.getDrops(minion.getTool()))

        // Lógica específica para Pitcher Plant
        if (block.type == Material.PITCHER_PLANT) {
            block.type = Material.PITCHER_CROP
            val aboveBlock = block.getRelative(BlockFace.UP)
            if (aboveBlock.type == Material.PITCHER_PLANT) {
                aboveBlock.type = Material.AIR // Remove o bloco superior
                aboveBlock.state.update(true, false)
            }
        } else {
            ageable.age = 0 // Restaura o estágio inicial do agricultável
            block.blockData = ageable
        }


        return 1 // Apenas 1 bloco foi processado
    }

    private fun handleBerryBush(minion: Minion, block: Block, drops: ArrayList<ItemStack>): Int {
        val ageable = block.blockData as Ageable
        if (ageable.age != ageable.maximumAge) return 0 // Se não estiver maduro, retorna 0
        drops.addAll(block.getDrops(minion.getTool()))
        ageable.age = 1 // Reseta a idade da planta
        block.blockData = ageable
        return 1 // Apenas 1 bloco processado
    }

    private fun handleTorchflowerBlock(minion: Minion, block: Block, drops: ArrayList<ItemStack>): Int {
        // Adiciona os drops ao inventário
        drops.addAll(block.getDrops(minion.getTool()))

        // Troca o bloco para TORCHFLOWER_CROP (estado inicial)
        block.type = Material.TORCHFLOWER_CROP

        // Sempre processa apenas 1 bloco
        return 1
    }

    private fun handleDrops(minion: Minion, drops: List<ItemStack>, blocksProcessed: Int) {
        if (drops.isEmpty() || blocksProcessed <= 0) return

        val chestLocation = minion.getLinkedChest() ?: run {
            // Sem um baú vinculado, dropar no mundo
            minion.addToContainerOrDrop(drops)
            applyMinionProgress(minion, blocksProcessed)
            return
        }

        try {
            // Procura a integração mais adequada para o tipo de baú do local
            val storageIntegration = AxMinionsPlugin.integrations.getSuitableStorageIntegration(chestLocation)

            // Caso exista uma integração adequada
            if (storageIntegration != null && storageIntegration.isStorage(chestLocation)) {
                // Tenta adicionar itens ao armazenamento integration
                val remainingDrops = storageIntegration.addItem(chestLocation, drops)
                if (!remainingDrops.isNullOrEmpty()) {
                    // Se sobrar, faz o fallback para dropar os itens restantes
                    minion.addToContainerOrDrop(remainingDrops)
                }
            } else {
                // Se não há integração ou o armazenamento não é suportado, tenta o inventário vanilla
                handleFallbackInventory(chestLocation, drops, minion)
            }

            // Aplica o progresso do minion após processar os blocos
            applyMinionProgress(minion, blocksProcessed)
        } catch (e: Exception) {
            // Lida com erros ao processar drops e registra no log
            Bukkit.getLogger().severe("[AxMinions] Erro ao processar drops no baú em ${chestLocation.block.type} na localização $chestLocation.")
            e.printStackTrace()

            // Garantia de fallback: dropar os itens no mundo
            minion.addToContainerOrDrop(drops)
        }
    }

    private fun handleFallbackInventory(chestLocation: Location, drops: List<ItemStack>, minion: Minion) {
        val chestInventory = (chestLocation.block.state as? Container)?.inventory
        if (chestInventory != null) {
            val remainingDrops = inventoryAddItems(chestInventory, drops)
            if (remainingDrops.isNotEmpty()) minion.addToContainerOrDrop(remainingDrops)
        } else {
            minion.addToContainerOrDrop(drops) // Fallback: soltar os itens no mundo
        }
    }


    private fun inventoryAddItems(inventory: org.bukkit.inventory.Inventory?, drops: List<ItemStack>): List<ItemStack> {
        if (inventory == null) return drops

        val remainingItems = mutableListOf<ItemStack>()
        for (drop in drops) {
            val remaining = inventory.addItem(drop).values
            remainingItems.addAll(remaining)
        }
        return remainingItems
    }

    private fun applyMinionProgress(minion: Minion, blocksProcessed: Int) {
        if (blocksProcessed <= 0) return // Evita aplicar mudanças caso nenhum bloco tenha sido processado
        minion.damageTool(blocksProcessed) // Aplica dano proporcional ao número de blocos processados
        minion.setActions(minion.getActionAmount() + blocksProcessed) // Incrementa ações com base nos blocos processados
    }


    private fun processBlock(minion: Minion, block: Block, drops: ArrayList<ItemStack>): Int {
        // Verifica blocos customizados (como ItemsAdder)
        if (AxMinionsPlugin.integrations.itemsAdderIntegration) {
            val customBlock = CustomBlock.byAlreadyPlaced(block)
            if (customBlock !== null) {
                handleItemsAdderBlock(minion, block, customBlock, drops)
                return 1 // Custom blocks processam apenas 1 bloco por vez
            }
        }

        // Identifica e processa o tipo do bloco
        return when (block.type) {
            Material.CACTUS, Material.SUGAR_CANE, Material.BAMBOO -> {
                handleStackableBlock(minion, block, drops)
            }
            Material.MELON, Material.PUMPKIN -> {
                handleHarvestableBlock(minion, block, drops)
            }
            Material.SWEET_BERRY_BUSH -> {
                val ageable = block.blockData as? Ageable
                if (ageable != null && ageable.age == ageable.maximumAge) {
                    handleBerryBush(minion, block, drops)
                } else 0 // Não maduro, nenhum bloco processado
            }
            Material.WHEAT, Material.CARROTS, Material.BEETROOTS, Material.POTATOES,
            Material.NETHER_WART, Material.PITCHER_CROP, Material.CAVE_VINES -> {
                val ageable = block.blockData as? Ageable
                if (ageable != null && ageable.age == ageable.maximumAge) {
                    handleAgeableBlock(minion, block, drops)
                } else 0 // Não maduro, nenhum bloco processado
            }
            Material.TORCHFLOWER -> {
                handleTorchflowerBlock(minion, block, drops)
                1
            }
            else -> 0 // Bloco desconhecido, nenhum processado
        }
    }

}