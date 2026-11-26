package br.offgr1d.companion.ui

/**
 * Rosto da 2B, da artista (design/rosto). Gerado por design/rosto/gerar.py: não edite à mão.
 * Cada forma é uma lista de contornos (x, y alternados); contorno dentro de outro vira buraco.
 */
object FaceArt {
    const val WIDTH = 1163.755f
    const val HEIGHT = 942.118f

    /** @param accent os losangos da testa, que podem ter outra cor */
    class Shape(val accent: Boolean, val rings: List<FloatArray>)

    val NEUTRAL: List<Shape> = listOf(
        Shape(false, listOf(floatArrayOf(121.843f, 234.254f, 97.854f, 172.124f, 203.693f, 215f, 327.793f, 185.807f, 492.172f, 350.186f, 532.996f, 567.335f, 165.125f, 638.963f, 194.856f, 596.151f, 0f, 262.915f), floatArrayOf(311.839f, 244.391f, 165.125f, 281.234f, 155.405f, 393.616f, 232.707f, 567.335f, 503.95f, 553.544f, 454.584f, 360.948f))),
        Shape(false, listOf(floatArrayOf(1041.912f, 234.254f, 1065.901f, 172.124f, 960.062f, 215f, 835.962f, 185.807f, 671.583f, 350.186f, 630.759f, 567.335f, 998.631f, 638.963f, 968.899f, 596.151f, 1163.755f, 262.915f), floatArrayOf(851.917f, 244.391f, 998.631f, 281.234f, 1008.35f, 393.616f, 931.048f, 567.335f, 659.805f, 553.544f, 709.171f, 360.948f))),
        Shape(false, listOf(floatArrayOf(485.418f, 406.153f, 451.428f, 529.874f, 383.45f, 529.874f, 349.461f, 406.153f, 383.45f, 282.432f, 469.526f, 348.308f))),
        Shape(false, listOf(floatArrayOf(678.337f, 406.153f, 712.327f, 529.874f, 780.305f, 529.874f, 814.295f, 406.153f, 780.305f, 282.432f, 694.229f, 348.308f))),
        Shape(false, listOf(floatArrayOf(503.95f, 145.388f, 329.677f, 46.796f, 72.271f, 80.552f, 165.125f, 115.748f, 329.677f, 115.748f, 441.829f, 198.491f))),
        Shape(false, listOf(floatArrayOf(659.805f, 145.388f, 834.078f, 46.796f, 1091.485f, 80.552f, 998.631f, 115.748f, 834.078f, 115.748f, 721.927f, 198.491f))),
        Shape(false, listOf(floatArrayOf(492.172f, 808.343f, 581.878f, 733.591f, 581.878f, 762.265f, 492.172f, 826.14f, 411.039f, 750.058f))),
        Shape(false, listOf(floatArrayOf(581.878f, 762.265f, 581.878f, 733.591f, 671.583f, 808.343f, 752.716f, 750.058f, 671.583f, 826.14f))),
        Shape(true, listOf(floatArrayOf(210.817f, 135.328f, 232.215f, 156.726f, 210.817f, 178.124f, 189.419f, 156.726f))),
        Shape(true, listOf(floatArrayOf(155.405f, 0f, 176.803f, 21.398f, 155.405f, 42.796f, 134.007f, 21.398f))),
    )
    val TALK: List<Shape> = listOf(
        Shape(false, listOf(floatArrayOf(121.843f, 234.254f, 97.854f, 172.124f, 203.693f, 215f, 327.793f, 185.807f, 492.172f, 350.186f, 532.996f, 567.335f, 165.125f, 638.963f, 194.856f, 596.151f, 0f, 262.915f), floatArrayOf(311.839f, 244.391f, 165.125f, 281.234f, 155.405f, 393.616f, 232.707f, 567.335f, 503.95f, 553.544f, 454.584f, 360.948f))),
        Shape(false, listOf(floatArrayOf(1041.912f, 234.254f, 1065.901f, 172.124f, 960.062f, 215f, 835.962f, 185.807f, 671.583f, 350.186f, 630.759f, 567.335f, 998.631f, 638.963f, 968.899f, 596.151f, 1163.755f, 262.915f), floatArrayOf(851.917f, 244.391f, 998.631f, 281.234f, 1008.35f, 393.616f, 931.048f, 567.335f, 659.805f, 553.544f, 709.171f, 360.948f))),
        Shape(false, listOf(floatArrayOf(485.418f, 406.153f, 451.428f, 529.874f, 383.45f, 529.874f, 349.461f, 406.153f, 383.45f, 282.432f, 469.526f, 348.308f))),
        Shape(false, listOf(floatArrayOf(678.337f, 406.153f, 712.327f, 529.874f, 780.305f, 529.874f, 814.295f, 406.153f, 780.305f, 282.432f, 694.229f, 348.308f))),
        Shape(false, listOf(floatArrayOf(503.95f, 145.388f, 329.677f, 46.796f, 72.271f, 80.552f, 165.125f, 115.748f, 329.677f, 115.748f, 441.829f, 198.491f))),
        Shape(false, listOf(floatArrayOf(659.805f, 145.388f, 834.078f, 46.796f, 1091.485f, 80.552f, 998.631f, 115.748f, 834.078f, 115.748f, 721.927f, 198.491f))),
        Shape(false, listOf(floatArrayOf(492.172f, 808.343f, 581.878f, 733.591f, 581.878f, 762.265f, 492.172f, 826.14f, 411.039f, 750.058f))),
        Shape(false, listOf(floatArrayOf(581.878f, 762.265f, 581.878f, 733.591f, 671.583f, 808.343f, 752.716f, 750.058f, 671.583f, 826.14f))),
        Shape(true, listOf(floatArrayOf(210.817f, 135.328f, 232.215f, 156.726f, 210.817f, 178.124f, 189.419f, 156.726f))),
        Shape(true, listOf(floatArrayOf(155.405f, 0f, 176.803f, 21.398f, 155.405f, 42.796f, 134.007f, 21.398f))),
        Shape(false, listOf(floatArrayOf(527.154f, 942.118f, 411.039f, 750.058f, 538.837f, 917.598f, 581.878f, 917.598f, 581.878f, 942.118f))),
        Shape(false, listOf(floatArrayOf(624.918f, 917.598f, 752.716f, 750.058f, 636.601f, 942.118f, 581.878f, 942.118f, 581.878f, 917.598f))),
    )
    val BLINK: List<Shape> = listOf(
        Shape(false, listOf(floatArrayOf(1041.912f, 443.254f, 1065.901f, 381.124f, 960.062f, 424f, 835.962f, 424.807f, 671.583f, 517.186f, 630.759f, 567.335f, 998.631f, 638.963f, 968.899f, 596.151f, 1163.755f, 471.915f))),
        Shape(false, listOf(floatArrayOf(121.843f, 443.254f, 97.854f, 381.124f, 203.693f, 424f, 327.793f, 424.807f, 492.172f, 517.186f, 532.996f, 567.335f, 165.125f, 638.963f, 194.856f, 596.151f, 0f, 471.915f))),
        Shape(false, listOf(floatArrayOf(503.95f, 145.388f, 329.677f, 46.796f, 72.271f, 80.552f, 165.125f, 115.748f, 329.677f, 115.748f, 441.829f, 198.491f))),
        Shape(false, listOf(floatArrayOf(659.805f, 145.388f, 834.078f, 46.796f, 1091.485f, 80.552f, 998.631f, 115.748f, 834.078f, 115.748f, 721.927f, 198.491f))),
        Shape(false, listOf(floatArrayOf(492.172f, 808.343f, 581.878f, 733.591f, 581.878f, 762.265f, 492.172f, 826.14f, 411.039f, 750.058f))),
        Shape(false, listOf(floatArrayOf(581.878f, 762.265f, 581.878f, 733.591f, 671.583f, 808.343f, 752.716f, 750.058f, 671.583f, 826.14f))),
        Shape(true, listOf(floatArrayOf(210.817f, 135.328f, 232.215f, 156.726f, 210.817f, 178.124f, 189.419f, 156.726f))),
        Shape(true, listOf(floatArrayOf(155.405f, 0f, 176.803f, 21.398f, 155.405f, 42.796f, 134.007f, 21.398f))),
    )
}
