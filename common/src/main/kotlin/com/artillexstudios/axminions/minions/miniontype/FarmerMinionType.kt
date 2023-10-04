package com.artillexstudios.axminions.minions.miniontype

import com.artillexstudios.axminions.AxMinionsPlugin
import com.artillexstudios.axminions.api.minions.Minion
import com.artillexstudios.axminions.api.minions.miniontype.MinionType
import com.artillexstudios.axminions.utils.LocationUtils
import com.artillexstudios.axminions.utils.fastFor
import org.bukkit.Material
import org.bukkit.block.data.Ageable
import org.bukkit.inventory.ItemStack

class FarmerMinionType : MinionType("farmer", AxMinionsPlugin.INSTANCE.getResource("minions/farmer.yml")!!) {

    override fun run(minion: Minion) {
        LocationUtils.getAllBlocksInRadius(minion.getLocation(), minion.getRange(), false).fastFor { location ->
            val block = location.block
            val drops = arrayListOf<ItemStack>()

            when (block.type) {
                Material.CACTUS, Material.SUGAR_CANE, Material.BAMBOO, Material.MELON, Material.PUMPKIN -> {
                    drops.addAll(block.getDrops(minion.getTool()))
                    block.type = Material.AIR
                }
                Material.COCOA_BEANS, Material.COCOA, Material.NETHER_WART, Material.WHEAT, Material.CARROTS, Material.BEETROOTS, Material.POTATOES -> {
                    val ageable = block.blockData as Ageable
                    if (ageable.age != ageable.maximumAge) return@fastFor
                    drops.addAll(block.getDrops(minion.getTool()))
                    ageable.age = 0
                    block.blockData = ageable
                }
                Material.SWEET_BERRY_BUSH -> {
                    val ageable = block.blockData as Ageable
                    if (ageable.age != ageable.maximumAge) return@fastFor
                    drops.addAll(block.getDrops(minion.getTool()))
                    ageable.age = 1
                    block.blockData = ageable
                }
                else -> return@fastFor
            }

            minion.addToContainerOrDrop(drops)
        }
    }
}