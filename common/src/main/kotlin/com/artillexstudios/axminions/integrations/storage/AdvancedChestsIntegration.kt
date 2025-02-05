package com.artillexstudios.axminions.integrations.storage

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.integrations.types.StorageIntegration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChests
import us.lynuxcraft.deadsilenceiv.advancedchests.AdvancedChestsAPI
import us.lynuxcraft.deadsilenceiv.advancedchests.chest.AdvancedChest
import us.lynuxcraft.deadsilenceiv.advancedchests.services.chest.dispenser.DispenserService
import us.lynuxcraft.deadsilenceiv.advancedchests.services.chest.sorter.SortType
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue


//Integração com AdvancedChests para manipulação de baús e integração com o sistema de armazenamento.
class AdvancedChestsIntegration : StorageIntegration {
    private lateinit var instance: AdvancedChests
    private val chestQueues = ConcurrentHashMap<Location, ConcurrentLinkedQueue<List<ItemStack>>>() // Consistente e segura para acesso concorrente
    private val lockedChests = ConcurrentHashMap.newKeySet<Location>() // Utiliza um conjunto thread-safe para gerenciar os chests bloqueados


    //Envia mensagens de debug para o console apenas se o modo debug estiver habilitado.
    private fun debug(message: String) {
        if (AxMinionsPlugin.config.get<Boolean>("debug")) {
            Bukkit.getLogger().info("[DEBUG] $message")
        }
    }

    //Obtém o baú numa localização específica.
    override fun getStorageLocation(location: Location): AdvancedChest<*, *> {
        debug("[AdvancedChestsIntegration] Tentando obter o baú na localização $location")
        return try {
            val chest = instance.chestsManager.getAdvancedChest(location)
                ?: throw IllegalStateException("Nenhum baú encontrado na localização: $location")
            chest
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Erro ao obter baú: ${e.message}")
            throw IllegalStateException("Erro ao obter a localização do baú para a localização dada.", e)
        }
    }

    //Obtém o UUID do baú numa localização específica.
    private fun getUUID(location: Location): UUID {
        debug("[AdvancedChestsIntegration] Obtendo UUID do baú em $location")
        return try {
            val uuid = getStorageLocation(location).uniqueId
                ?: throw IllegalStateException("UUID não encontrado para o baú na localização: $location")
            debug("[AdvancedChestsIntegration] UUID obtido: $uuid")
            uuid
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Erro ao obter UUID: ${e.message}")
            throw e
        }
    }

    //Verifica se é um AdvancedChest na localização especificada.
    private fun isAdvancedChest(location: Location, advancedChest: AdvancedChest<*, *>): Boolean {
        debug("[AdvancedChestsIntegration] Verificando se a localização $location contém o AdvancedChest fornecido")
        return try {
            val chestAtLocation = getStorageLocation(location)
            val result = chestAtLocation == advancedChest
            debug("[AdvancedChestsIntegration] Comparação de baús: $result")
            result
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Não é um AdvancedChest válido: ${e.message}")
            false
        }
    }

    //Verifica se o baú fornecido é do tipo AdvancedChest e está na localização especificada.
    override fun hasStorage(location: Location, advancedChest: AdvancedChest<*, *>): Boolean {
        debug("[AdvancedChestsIntegration] Verificando se a localização $location tem armazenamento")
        return try {
            isAdvancedChest(location, advancedChest)
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Erro ao verificar armazenamento: ${e.message}")
            false
        }
    }

    //Retorna a quantidade máxima de slots de um baú.
    override fun getStorageMax(location: Location): Int {
        debug("[AdvancedChestsIntegration] Obtendo número máximo de slots para o baú em $location")
        return try {
            getStorageLocation(location).size ?: 0
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Erro ao obter slots máximos: ${e.message}")
            0
        }
    }

    //Retorna a quantidade de slots livres num baú.
    override fun getStorageLeft(location: Location): Int {
        debug("[AdvancedChestsIntegration] Obtendo número de slots livres para o baú em $location")
        return try {
            getStorageLocation(location).slotsLeft
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Erro ao obter slots livres: ${e.message}")
            0
        }
    }

    private fun processChest(location: Location, initialItems: List<ItemStack>): List<ItemStack> {
        debug("[AdvancedChestsIntegration] Iniciando processamento no baú em $location")

        var items = initialItems

        return try {
            while (true) {
                // Obtém o baú e valida se é um armazenamento válido
                val chest = getStorageLocation(location).takeIf { hasStorage(location, it) }
                    ?: return logAndReturnItems(location, items)

                // Obtém o serviço de dispenser associado ao baú
                val dispenserService = chest.chestType.dispenserService
                    ?: throw IllegalStateException("DispenserService indisponível para o baú em $location")

                // Processa os itens e retorna os não armazenados
                val unaidedItems = processItemsForChest(chest, dispenserService, items)

                // Atualiza inventários dos jogadores conectados ao baú
                updateConnectedPlayers(chest)

                // Verifica a fila para continuar o processamento
                val queue = chestQueues[location]
                if (queue.isNullOrEmpty()) {
                    unlockChest(location)
                    debug("[AdvancedChestsIntegration] Todas as requisições no baú $location foram processadas.")
                    return unaidedItems
                }

                // Atualiza os itens com o próximo conjunto na fila
                items = queue.poll() ?: break
            }
            items
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Erro ao processar itens no baú em $location: ${e.message}")
            items
        } finally {
            // Libera qualquer bloqueio remanescente
            unlockChest(location)
        }
    }

    // Método auxiliar para retornar os itens e registrar o log
    private fun logAndReturnItems(location: Location, items: List<ItemStack>): List<ItemStack> {
        debug("[AdvancedChestsIntegration] Nenhum baú ativo encontrado em $location.")
        return items
    }

    // Processa os itens e retorna os que não puderam ser armazenados
    private fun processItemsForChest(
        chest: AdvancedChest<*, *>,
        dispenserService: DispenserService,
        items: List<ItemStack>
    ): List<ItemStack> {
        // Agrupa os itens por tipo e soma suas quantidades
        val groupedItems = items.groupBy { it.type }
            .map { (type, itemGroup) ->
                val totalAmount = itemGroup.sumOf { it.amount }
                ItemStack(type, totalAmount)
            }

        // Filtra os itens que não puderam ser armazenados
        return groupedItems.filterNot { item ->
            val itemStored = dispenserService.dispenseItemToChest(chest, item)
            if (itemStored) {
                debug("[AdvancedChestsIntegration] Item armazenado com sucesso no baú: $item.")
            }
            itemStored
        }
    }

    // Atualiza os inventários dos jogadores conectados ao baú
    private fun updateConnectedPlayers(chest: AdvancedChest<*,*>,) {
        Bukkit.getOnlinePlayers()
            .filter { player ->
                val playerPage = chest.getPlayerPage(player)
                playerPage != null && player.openInventory.topInventory == playerPage
            }
            .forEach { player ->
                player.updateInventory()
                debug("[AdvancedChestsIntegration] Inventário do jogador ${player.name} atualizado.")
            }
    }

    // Libera o bloqueio e remove da fila
    private fun unlockChest(location: Location) {
        lockedChests.remove(location)
        chestQueues.remove(location)
        debug("[AdvancedChestsIntegration] Baú em $location desbloqueado.")
    }



    override fun addItem(location: Location, items: List<ItemStack>): List<ItemStack> {
        debug("[AdvancedChestsIntegration] Tentando adicionar itens ao baú em $location")

        if (lockedChests.contains(location)) {
            debug("[AdvancedChestsIntegration] Baú bloqueado na localização $location. Adicionando à fila.")

            // Adiciona os itens à fila sem bloquear completamente a thread
            chestQueues.computeIfAbsent(location) { ConcurrentLinkedQueue() }.add(items)
            return emptyList() // Retorna vazio porque os itens foram enfileirados
        }

        // Se o baú não estiver bloqueado, bloqueie-o e prossiga
        lockedChests.add(location)
        return processChest(location, items)
    }

    override fun removeItem(location: Location, items: List<ItemStack>): List<ItemStack> {
        debug("[AdvancedChestsIntegration] Tentando remover itens do baú em $location")

        // Verifica se o baú está bloqueado
        if (lockedChests.contains(location)) {
            debug("[AdvancedChestsIntegration] Baú bloqueado na localização $location. Adicionando à fila.")
            chestQueues.computeIfAbsent(location) { ConcurrentLinkedQueue() }.add(items)
            return items // Retorna os itens sem removê-los
        }

        // Tenta obter a localização do baú
        val chest = try {
            getStorageLocation(location)
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Falha ao localizar o baú em $location: ${e.message}")
            return items
        }

        // Lista que armazenará os itens que não puderam ser removidos
        val remainingItems = mutableListOf<ItemStack>()

        // Itera sobre as páginas do baú
        chest.getPages().forEach { (pageIndex, page) ->
            // Obtém os itens da página e filtra apenas os ItemStacks
            val inventory = page.getItems().filterIsInstance<ItemStack>()
            val inputSlots = page.getInputSlots().size

            // Se o inventário estiver vazio, pula para a próxima página
            if (inventory.isEmpty()) return@forEach

            // Cria uma cópia mutável do inventário para manipulação
            val newInventory = inventory.toMutableList()

            // Itera sobre os itens que queremos remover
            items.forEach { itemToRemove ->
                var totalRemoved = 0

                // Itera sobre os itens no inventário
                for (index in newInventory.indices) {
                    val itemStack = newInventory[index]
                    if (itemStack.isSimilar(itemToRemove)) { // Verifica se os itens são iguais
                        val removeAmount = minOf(itemStack.amount, itemToRemove.amount - totalRemoved)
                        itemStack.amount -= removeAmount // Reduz a quantidade do ItemStack
                        totalRemoved += removeAmount

                        // Se o ItemStack estiver vazio, substitui por um ItemStack vazio
                        if (itemStack.amount <= 0) {
                            debug("[AdvancedChestsIntegration] Removido com sucesso: $itemToRemove") // Mensagem antes de substituir por AIR
                            newInventory[index] = ItemStack(Material.AIR)
                        }

                        // Se a quantidade total removida for suficiente, sai do loop
                        if (totalRemoved >= itemToRemove.amount) {
                            break
                        }
                    }
                }

                // Se a quantidade removida for insuficiente, adiciona à lista de itens restantes
                if (totalRemoved < itemToRemove.amount) {
                    remainingItems.add(itemToRemove.clone().apply { amount = itemToRemove.amount - totalRemoved })
                }
            }

            // Converte a lista de ItemStack para List<Nothing?> (compatível com a API)
            val updatedInventory: List<Nothing?> = newInventory.take(inputSlots).map { it as? Nothing }

            // Atualiza o conteúdo da página com os itens removidos
            if (updatedInventory.isNotEmpty()) {
                page.setContent(updatedInventory)
                debug("[AdvancedChestsIntegration] Página $pageIndex atualizada.")
            } else {
                debug("[AdvancedChestsIntegration] Inventário vazio ou inputSlots inválido para a página $pageIndex.")
            }
        }

        // Atualiza as páginas do baú após as modificações
        //chest.updatePages()

        // Log dos itens que não puderam ser removidos
        if (remainingItems.isNotEmpty()) {
            debug("[AdvancedChestsIntegration] Itens restantes que não puderam ser removidos: ${remainingItems.joinToString(", ") { it.toString() }}")
        }

        // Retorna os itens que não puderam ser removidos
        return remainingItems
    }

    override fun isStorage(location: Location): Boolean {
        val chest = AdvancedChestsAPI.getInstance().chestsManager.getAdvancedChest(location)
        return chest != null
    }

    override fun getStorageContents(location: Location): List<ItemStack> {
        debug("[AdvancedChestsIntegration] Obtendo conteúdo do armazenamento em $location")

        val chest: AdvancedChest<*, *> = try {
            getStorageLocation(location) // Obtém o AdvancedChest na localização fornecida
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Erro ao localizar baú em $location: ${e.message}")
            return emptyList() // Retorna uma lista vazia caso o baú não seja localizado
        }

        return getChestContents(chest, location)
    }

    private fun getChestContents(chest: AdvancedChest<*, *>, location: Location): List<ItemStack> {
        return try {
            val contents = chest.getAllContent()
                ?.filterIsInstance<ItemStack>() // Filtra apenas objetos do tipo ItemStack
                ?.filter { it.type != Material.AIR } // Remove itens do tipo AIR (vazios)
                ?: emptyList() // Retorna lista vazia se o resultado for nulo ou nenhum item for válido

            debug("[AdvancedChestsIntegration] Itens obtidos de $location: ${contents.size} encontrados")
            contents
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Falha ao obter conteúdo do baú em $location: ${e.message}")
            emptyList()
        }
    }


    // Organiza o baú.
    override fun organizeStorage(location: Location): List<ItemStack> {
        debug("[AdvancedChestsIntegration] Organizando o baú na localização $location")
        return try {
            val chest = getStorageLocation(location)
            val sorterService = chest.chestType.sorterService
                ?: throw IllegalStateException("SorterService não está disponível para o baú em $location")

            sorterService.sort(chest, SortType.BYMATERIAL)
            chest.updatePages()
            debug("[AdvancedChestsIntegration] Baú organizado com sucesso")
            emptyList()
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Erro ao organizar baú: ${e.message}")
            throw IllegalStateException("Erro ao organizar: ${e.message}", e)
        }
    }

    //Obtém todos os itens do baú.
    override fun getItems(location: Location): MutableList<out Any>? {
        debug("[AdvancedChestsIntegration] Obtendo todos os itens do baú em $location")
        return try {
            getStorageLocation(location).getAllContent()
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Erro ao obter itens: ${e.message}")
            null
        }
    }

    // Verifica se há espaço no baú para os itens fornecidos.
    override fun hasSpace(location: Location, item: List<ItemStack>): Boolean {
        debug("[AdvancedChestsIntegration] Verificando se há espaço para itens no baú em $location")
        return try {
            val chest = getStorageLocation(location)
            item.all { chest.chestType.dispenserService.hasSpaceForItem(chest, it) }
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Erro ao verificar espaço: ${e.message}")
            false
        }
    }

    //Transfere itens de um baú de origem para um baú de destino.
    override fun transferItems(fromLocation: Location, toLocation: Location): List<ItemStack> {
        debug("[AdvancedChestsIntegration] Transferindo itens de $fromLocation para $toLocation")
        return try {
            val fromChest = getStorageLocation(fromLocation)
            val toChest = getStorageLocation(toLocation)

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
            debug("[AdvancedChestsIntegration] Transferência concluída")
            transferredItems
        } catch (e: Exception) {
            debug("[AdvancedChestsIntegration] Erro ao transferir itens: ${e.message}")
            throw IllegalStateException("Erro ao transferir itens", e)
        }
    }

    //Registra o plugin AdvancedChests para uso na integração.
    override fun register() {
        debug("[AdvancedChestsIntegration] Registrando AdvancedChests")

        val apiInstance = AdvancedChestsAPI.getInstance()
        if (apiInstance != null) {
            this.instance = apiInstance
            debug("[AdvancedChestsIntegration] Plugin AdvancedChests registrado com sucesso.")
            return
        }

        val plugin = Bukkit.getServer().pluginManager.getPlugin("AdvancedChests")
        if (plugin == null || plugin !is AdvancedChests) {
            throw IllegalStateException("[AdvancedChestsIntegration] Plugin AdvancedChests não encontrado ou inválido.")
        }

        if (!plugin.isEnabled) {
            throw IllegalStateException("[AdvancedChestsIntegration] Plugin AdvancedChests está desabilitado.")
        }

        AdvancedChestsAPI.setInstance(plugin)
        this.instance = plugin
        debug("[AdvancedChestsIntegration] Plugin AdvancedChests registrado com sucesso.")
    }
}