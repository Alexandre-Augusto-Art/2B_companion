package br.offgr1d.companion.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import br.offgr1d.companion.ui.FaceArt
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertTrue
import org.jetbrains.skia.EncodedImageFormat

/**
 * Ícone do Windows, a opção 7 (rosto preto no fundo verde), desenhado com o mesmo FaceArt do app.
 * Gera design/icones/opcao-07.ico (usado pelo instalador) e o PNG da janela. Rode de novo se a arte mudar.
 */
class IconTest {
    private fun png(size: Int): ByteArray {
        val scene = ImageComposeScene(size, size, Density(1f)) {
            Canvas(Modifier.fillMaxSize()) {
                val u = size / 1024f
                drawRoundRect(Color(0xFF00FF00), cornerRadius = CornerRadius(230 * u))
                // Mesmo enquadramento de design/rosto/gerar_icones.py: 62% da largura, centrado pelo rosto neutro (826 de altura).
                val k = size * 0.62f / FaceArt.WIDTH
                translate((size - FaceArt.WIDTH * k) / 2, (size - 826.14f * k) / 2) {
                    scale(k, k, pivot = Offset.Zero) {
                        FaceArt.NEUTRAL.forEach { shape ->
                            drawPath(Path().apply {
                                fillType = PathFillType.EvenOdd
                                shape.rings.forEach { r ->
                                    moveTo(r[0], r[1])
                                    for (i in 2 until r.size step 2) lineTo(r[i], r[i + 1])
                                    close()
                                }
                            }, Color(0xFF0B0B0B))
                        }
                    }
                }
            }
        }
        return try {
            scene.render(0).encodeToData(EncodedImageFormat.PNG)!!.bytes
        } finally {
            scene.close()
        }
    }

    @Test fun `gera o ícone do Windows`() {
        val sizes = listOf(256, 64, 48, 32, 16)
        val images = sizes.map(::png)
        // ICO com PNG dentro: aceito pelo Windows desde o Vista.
        val out = ByteArrayOutputStream()
        val header = ByteBuffer.allocate(6 + 16 * sizes.size).order(ByteOrder.LITTLE_ENDIAN)
        header.putShort(0).putShort(1).putShort(sizes.size.toShort())
        var offset = 6 + 16 * sizes.size
        sizes.zip(images).forEach { (s, img) ->
            header.put((if (s >= 256) 0 else s).toByte()).put((if (s >= 256) 0 else s).toByte()).put(0).put(0)
            header.putShort(1).putShort(32).putInt(img.size).putInt(offset)
            offset += img.size
        }
        out.write(header.array())
        images.forEach(out::write)
        File("../design/icones/opcao-07.ico").writeBytes(out.toByteArray())
        File("src/main/resources").mkdirs()
        File("src/main/resources/icone.png").writeBytes(images.first())
        assertTrue(File("../design/icones/opcao-07.ico").length() > 1000)
    }
}
