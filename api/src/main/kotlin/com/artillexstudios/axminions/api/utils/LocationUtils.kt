package com.artillexstudios.axminions.api.utils

import kotlin.math.roundToInt
import org.bukkit.Location
import org.bukkit.block.BlockFace

object LocationUtils {

    @JvmStatic

    fun getAllBlocksInSquare(location: Location, radius: Double, filterEmpty: Boolean): ArrayList<Location> {
        val blocks = ArrayList<Location>() // Lista para armazenar os blocos

        // Coordenadas centrais
        val centerX = location.blockX
        val centerZ = location.blockZ

        // Itera por todas as coordenadas dentro do quadrado (interior incluído)
        for (x in (centerX - radius).toInt()..(centerX + radius).toInt()) {
            for (z in (centerZ - radius).toInt()..(centerZ + radius).toInt()) {
                val blockLocation = Location(location.world, x.toDouble(), location.blockY.toDouble(), z.toDouble())

                // Verifica o filtro (blocos vazios ou sólidos)
                if (!filterEmpty || blockLocation.block.type.isSolid) {
                    blocks.add(blockLocation)
                }
            }
        }

        return blocks
    }


    fun getAllBlocksFacing(location: Location, range: Double, face: BlockFace): ArrayList<Location> {
        val blocks = ArrayList<Location>() // Lista para armazenar as localizações dos blocos

        // Coordenadas centrais do farmer
        val centerX = location.blockX
        val centerZ = location.blockZ
        val centerY = location.blockY

        // Direção principal do farmer (onde ele está olhando)
        val modX = face.modX
        val modZ = face.modZ

        // Vetores perpendiculares para deslocamento lateral (define a largura)
        val perpendicularX = if (modZ != 0) 1 else 0 // Mudança lateral no eixo X
        val perpendicularZ = if (modX != 0) 1 else 0 // Mudança lateral no eixo Z

        val intRange = range.toInt() // Converte o range para um inteiro

        // Loop frontal para capturar os blocos na direção especificada
        for (i in 1..intRange) { // Começa na linha imediatamente à frente do farmer
            for (j in -(intRange / 2)..(intRange / 2)) { // Deslocamento lateral, centrado no farmer
                // Calcula os blocos à frente e nas laterais
                val x = centerX + modX * i + j * perpendicularX
                val z = centerZ + modZ * i + j * perpendicularZ

                // Adiciona cada bloco da faixa à lista de blocos
                val blockLocation = Location(location.world, x.toDouble(), centerY.toDouble(), z.toDouble())
                blocks.add(blockLocation)
            }
        }

        return blocks
    }



    fun getAllBlocksInRadius(location: Location, radius: Double, filterEmpty: Boolean): ArrayList<Location> {
        // Approximate the volume of the sphere
        val blocks = ArrayList<Location>((2 * radius * radius * radius).toInt())

        val blockX = location.blockX
        val blockY = location.blockY
        val blockZ = location.blockZ

        val rangeX = (blockX - radius).rangeTo((blockX + radius)).step(1.0)
        val rangeY = (blockY - radius).rangeTo((blockY + radius)).step(1.0)
        val rangeZ = (blockZ - radius).rangeTo((blockZ + radius)).step(1.0)

        val radiusSquared = radius * radius
        val smallRadiusSquared = (radius - 1) * (radius - 1)

        for (x in rangeX) {
            for (y in rangeY) {
                for (z in rangeZ) {
                    val distance =
                        ((blockX - x) * (blockX - x) + ((blockZ - z) * (blockZ - z)) + ((blockY - y) * (blockY - y)))

                    if (distance < radiusSquared && !(filterEmpty && distance < smallRadiusSquared)) {
                        blocks.add(Location(location.world, x, y, z))
                    }
                }
            }
        }

        return blocks
    }

    fun getAllBlocksInCube(location: Location, radius: Double, filterEmpty: Boolean): ArrayList<Location> {
        // Approximate the volume of the sphere
        val blocks = ArrayList<Location>((2 * radius * radius * radius).toInt())

        // Coordenadas centrais do cubo
        val blockX = location.blockX
        val blockY = location.blockY
        val blockZ = location.blockZ

        // Faixas para cada eixo (X, Y, Z)
        val rangeX = (blockX - radius).toInt()..(blockX + radius).toInt()
        val rangeY = (blockY - radius).toInt()..(blockY + radius).toInt()
        val rangeZ = (blockZ - radius).toInt()..(blockZ + radius).toInt()

        // Itera sobre todas as coordenadas dentro do cubo
        for (x in rangeX) {
            for (y in rangeY) {
                for (z in rangeZ) {
                    val block = Location(location.world, x.toDouble(), y.toDouble(), z.toDouble())
                    /// filtro para evitar blocos vazios
                    if (!filterEmpty || block.block.type.isSolid) {
                        blocks.add(block)
                    }
                }
            }
        }

        return blocks
    }

    fun getAllBlocksInChunk(location: Location, filterEmpty: Boolean): ArrayList<Location> {
        val blocks = ArrayList<Location>()

        // Obtemos o chunk da localização
        val chunk = location.chunk

        // Iteramos pelas coordenadas do chunk
        for (x in 0..15) {
            for (z in 0..15) {
                for (y in (location.world?.minHeight ?: 0)..(location.world?.maxHeight ?: 0)) {
                    val blockLocation = Location(location.world, (chunk.x * 16 + x).toDouble(), y.toDouble(), (chunk.z * 16 + z).toDouble())

                    // Filtro Adicionar apenas blocos sólidos
                    if (!filterEmpty || blockLocation.block.type.isSolid) {
                        blocks.add(blockLocation)
                    }
                }
            }
        }

        return blocks
    }

}