package com.artillexstudios.axminions.integrations.storage

import com.artillexstudios.axminions.api.integrations.types.StorageIntegration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.inventory.ItemStack
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChests
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChestsAPI
import us.lynuxcraft.deadsilenceiv.advancedchests.chest.AdvancedChest
import us.lynuxcraft.deadsilenceiv.advancedchests.services.chest.sorter.SortType
import java.util.*

/**
 * Integração com AdvancedChests para manipulação de baús e integração com o sistema de armazenamento.
 */
class AdvancedChestsIntegration : StorageIntegration {
    private lateinit var instance: AdvancedChests

    /**
     * Obtém o bau numa localização específica.
     */
    override fun getLocation(location: Location): AdvancedChest<*, *> {
        return try {
            instance.chestsManager.getAdvancedChest(location)
        } catch (e: Exception) {
            throw IllegalStateException("Erro ao obter a localização do baú para a localização dada.")
        }
    }

    /**
     * Obtém o UUID do baú numa localização específica.
     */
    private fun getUUID(location: Location): UUID {
        return try {
            getLocation(location).uniqueId
                ?: throw IllegalStateException("UUID não encontrado para o baú na localização: $location")
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Verifica se é um AdvancedChest na localização especificada.
     */
    private fun isAdvancedChest(location: Location, advancedChest: AdvancedChest<*, *>): Boolean {
        return try {
            // Obtém o AdvancedChest na localização fornecida
            val chestAtLocation = getLocation(location)

            // Confirma se o baú na localização é o mesmo que o fornecido
            chestAtLocation == advancedChest
        } catch (e: Exception) {
            // Tratamento de erros
            false
        }
    }

    /**
     * Verifica se o baú fornecido é do tipo AdvancedChest e está na localização especificada.
     */
    override fun hasStorage(location: Location, advancedChest: AdvancedChest<*, *>): Boolean {
        return try {
            // Apenas verifica se o baú é um AdvancedChest válido e corresponde ao fornecido
            isAdvancedChest(location, advancedChest)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Retorna a quantidade máxima de slots de um baú.
     */
    override fun getStorageMax(location: Location): Int {
        return try {
            getLocation(location).size ?: 0
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Retorna a quantidade de slots livres num baú.
     */
    override fun getStorageLeft(location: Location): Int {
        return try {
            getLocation(location).slotsLeft
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Adiciona itens ao baú.
     */
    override fun addItem(location: Location, items: List<ItemStack>): List<ItemStack> {
        return try {
            val chest = getLocation(location).takeIf { hasStorage(location, it) }
                ?: return items

            val dispenserService = chest.chestType.dispenserService
                ?: throw IllegalStateException("DispenserService não está disponível para o baú na localização: $location")

            val unaidedItems = items.filter { item ->
                val hasSpace = dispenserService.hasSpaceForItem(chest, item)
                if (!hasSpace) return@filter true
                !dispenserService.dispenseItemToChest(chest, item)
            }

            chest.updatePages()
            unaidedItems
        } catch (e: Exception) {
            items
        }
    }

    /**
     * Organiza o bau.
     */
    override fun organizeStorage(location: Location): List<ItemStack> {
        return try {
            val chest = getLocation(location)
            val sorterService = chest.chestType.sorterService
                ?: throw IllegalStateException("SorterService não está disponível para o baú em $location")

            sorterService.sort(chest, SortType.BYMATERIAL)
            chest.updatePages()
            emptyList()
        } catch (e: Exception) {
            throw IllegalStateException("Erro ao organizar: ${e.message}", e)
        }
    }

    /**
     * Obtém todos os itens do baú.
     */
    override fun getItems(location: Location): MutableList<out Any>? {
        return try {
            getLocation(location).getAllContent()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Verifica se há espaço no baú para os itens fornecidos.
     */
    override fun hasSpace(location: Location, item: List<ItemStack>): Boolean {
        return try {
            item.all { getLocation(location).chestType.dispenserService.hasSpaceForItem(getLocation(location), it) }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Transfere itens de um baú de origem para um baú de destino.
     */
    override fun transferItems(fromLocation: Location, toLocation: Location): List<ItemStack> {
        return try {
            val fromChest = getLocation(fromLocation)
            val toChest = getLocation(toLocation)

            val itemsToTransfer = fromChest.getAllContent().filterIsInstance<ItemStack>()
            val transferredItems = mutableListOf<ItemStack>()

            itemsToTransfer.forEach { item ->
                if (toChest.chestType.dispenserService.hasSpaceForItem(toChest, item)) {
                    val successfullyDispensed = toChest.chestType.dispenserService.dispenseItemToChest(toChest, item)
                    if (successfullyDispensed) {
                        fromChest.chestType.dispenserService.dispenseLastItemFromChest(fromChest, item.amount)
                    } else {
                        transferredItems.add(item)
                    }
                } else {
                    transferredItems.add(item)
                }
            }

            fromChest.updatePages()
            toChest.updatePages()
            transferredItems
        } catch (e: Exception) {
            throw IllegalStateException("Erro ao transferir itens", e)
        }
    }

    /**
     * Registra o plugin AdvancedChests para uso na integração.
     */
    override fun register() {
        // Obtém a instância atual do AdvancedChestsAPI
        var instance = AdvancedChestsAPI.getInstance()

        // Se não houver nenhuma instância configurada no AdvancedChestsAPI
        if (instance == null) {
            // Tenta obter o plugin "AdvancedChests" pelo PluginManager do Bukkit
            val plugin = Bukkit.getServer().pluginManager.getPlugin("AdvancedChests") as? AdvancedChests

            // Verifica se o plugin foi encontrado
            if (plugin != null) {
                // Define a instância do plugin no AdvancedChestsAPI
                AdvancedChestsAPI.setInstance(plugin)
                instance = plugin
            }
        }

        // Atualiza a variável de instância da classe se o AdvancedChestsAPI foi corretamente configurado
        this.instance = instance ?: throw IllegalStateException("[AdvancedChestsIntegration] Falha ao registrar o plugin AdvancedChests no API.")
    }
}