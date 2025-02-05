package com.artillexstudios.axminions.api.integrations.types

import com.artillexstudios.axminions.api.integrations.Integration
import org.bukkit.inventory.ItemStack
import org.bukkit.Location
import us.lynuxcraft.deadsilenceiv.advancedchests.chest.AdvancedChest

interface StorageIntegration : Integration {

    //Retorna a localização
    fun getStorageLocation(location: Location): AdvancedChest<*, *>

    // Verifica se há um armazenamento na localização
    fun hasStorage(location: Location, advancedChest: AdvancedChest<*, *>): Boolean

    // Verifica se possui espaço no bau
    fun hasSpace(location: Location, item: List<ItemStack>): Boolean

    // Retorna se o alvo é do tipo storage
    fun isStorage(location: Location): Boolean

    // Retorna os itens que estão em um storage
    fun getStorageContents(location: Location): List<ItemStack>

    // Obtém os a quantidade máxima do bau em uma localização
    fun getStorageMax(location: Location): Int

    // Obtém a quantidade de slots restantes
    fun getStorageLeft(location: Location): Int

    // Organiza os itens no armazenamento
    fun organizeStorage(location: Location): List<ItemStack>

    // Obtém todos os itens armazenados
    fun getItems(location: Location): MutableList<out Any>?

    // Transfere os itens de um bau armazenamento para outro
    fun transferItems(fromLocation: Location, toLocation: Location): List<ItemStack>

    // Aiciona itens no bau do local especifico
    fun addItem(location: Location, items: List<ItemStack>): List<ItemStack>

   // Remove itens de um bau do local especifico
   fun removeItem(location: Location, items: List<ItemStack>): List<ItemStack>

}