package br.offgr1d.companion.chat

import br.offgr1d.companion.text.Text

/** Papo do dia a dia: não procura fontes nem usa a web, só conversa. */
object SmallTalk {
    private val OPENERS = Regex(
        "^(oi|ola|opa|eai|e ai|eae|salve|fala|hey|alo|bom dia|boa tarde|boa noite|obrigad\\w+|valeu|vlw|brigad\\w+|tchau|ate (mais|logo|amanha)|falou|" +
            "tudo (bem|bom|certo|joia)|beleza|blz|como (voce )?(esta|ta|vai|foi)|ta (bem|ai)|sim|nao|ok|certo|entendi|legal|show|massa|kk+|haha\\w*|rs+)\\b",
    )
    private val FEELINGS = Regex(
        "\\b(to|tou|estou|ando|fiquei|me sinto|sinto|me sentindo|acordei|tive um dia|meu dia|minha semana|cansad\\w+|trist\\w+|feliz|ansios\\w+|entediad\\w+|sozinh\\w+|" +
            "com (sono|preguica|fome|saudade|raiva)|desabaf\\w+|conversar um pouco|bater papo|vamos conversar|quero conversar)\\b",
    )
    private val FUN = Regex(
        "\\b(piada|conta (uma|algo)|filmes?|series?|musicas?|livros?|jogos?|games?|anime|receita|jantar|almoco|cafe|hobby|" +
            "seu (nome|gosto|favorito)|sua (cor|musica|comida)|voce (gosta|curte|prefere|sente|sonha|dorme)|o que voce (acha|faz|gosta)|quem e voce)\\b",
    )

    /** Assunto sério vence: aí valem fontes, guias e avisos. */
    private val SERIOUS = Regex(
        "\\b(golpe\\w*|pix|senha\\w*|hacke\\w+|invad\\w+|link\\w*|virus|protesto\\w*|manifestac\\w+|ato|atos|policia|preso|detid\\w+|direitos?|lei|leis|" +
            "vaz\\w+|ameac\\w+|persegu\\w+|stalk\\w*|fake|boato\\w*|verdade que|noticia\\w*|checar|clonar\\w*|privacidade|vigilancia|seguranca|" +
            "minha conta|celular|whatsapp|signal|telegram|tor|vpn|antivirus|criptografia|reflexao|reflexoes|dedsec|0ff|grid|w4k3|tarot|gacha|f4c3|bitchat|remedio|dose|sintoma\\w*|medico)\\b",
    )

    fun isCasual(question: String): Boolean {
        val q = Text.normalize(question)
        if (SERIOUS.containsMatchIn(q) || "http" in q) return false
        val words = q.split(' ').size
        return (OPENERS.containsMatchIn(q) && words <= 12) || FEELINGS.containsMatchIn(q) || FUN.containsMatchIn(q)
    }

    /** Sem modelo instalado a 2B não conversa de verdade, mas também não precisa ser fria. */
    fun withoutModel(question: String): String? {
        val q = Text.normalize(question)
        return when {
            SERIOUS.containsMatchIn(q) -> null
            Regex("^(obrigad|valeu|vlw|brigad)").containsMatchIn(q) -> "Tamo junto. Qualquer coisa, me chama."
            Regex("^(tchau|ate (mais|logo|amanha)|falou)\\b").containsMatchIn(q) -> "Até. Se cuida aí fora."
            Regex("\\b(quem e voce|o que voce faz|seu nome)\\b").containsMatchIn(q) ->
                "Sou a 2B, IA do 0FF-GR1D. Trago a notícia do dia, a mensagem do dia, os próximos atos, analiso links e ajudo com segurança digital. Para a gente conversar de verdade, baixe um modelo em Modelos."
            OPENERS.containsMatchIn(q) -> "Oi! Que bom te ver por aqui. Quer a notícia do dia, a mensagem do dia ou os próximos atos? Para conversar livremente, baixe um modelo em Modelos."
            isCasual(question) -> "Te ouvi. Para conversar de verdade eu preciso de um modelo: baixe um em Modelos. Enquanto isso, posso trazer a notícia, a mensagem do dia ou os próximos atos."
            else -> null
        }
    }
}
