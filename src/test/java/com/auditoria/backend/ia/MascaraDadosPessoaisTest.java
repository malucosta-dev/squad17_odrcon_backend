package com.auditoria.backend.ia;

import org.junit.jupiter.api.Test;

import java.util.regex.MatchResult;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class MascaraDadosPessoaisTest {
    private static final Pattern MARCADOR = Pattern.compile("\\[\\[ODRCONPII[a-z]+_[a-z]+\\]\\]");
    private final MascaraDadosPessoais mascara = new MascaraDadosPessoais();

    @Test
    void deveDiferenciarRequisicoesEUsarMesmoMarcadorParaValorRepetido() {
        String original = "Contato teste@example.com ou teste@example.com.";
        var primeira = mascara.mascarar(original);
        var segunda = mascara.mascarar(original);
        var marcadores = MARCADOR.matcher(primeira.texto()).results().map(MatchResult::group).toList();
        assertEquals(2, marcadores.size());
        assertEquals(marcadores.get(0), marcadores.get(1));
        assertNotEquals(primeira.texto(), segunda.texto());
        assertTrue(segunda.restaurar(primeira.texto()).isEmpty());
        assertEquals(original, primeira.restaurar(primeira.texto()).orElseThrow());
    }

    @Test
    void deveRestaurarMarcadorDigitadoComoTextoLiteralSemColisao() {
        String digitado = MARCADOR.matcher(mascara.mascarar("000.000.000-00").texto())
                .results().findFirst().orElseThrow().group();
        String original = "A tela mostrou " + digitado + ". Meu e-mail é teste@example.com.";
        var resultado = mascara.mascarar(original);
        assertFalse(resultado.texto().contains(digitado));
        assertEquals(original, resultado.restaurar(resultado.texto()).orElseThrow());
        assertTrue(resultado.restaurar(resultado.texto() + digitado).isEmpty());
    }

    @Test
    void deveRejeitarPerdaAlteracaoDuplicacaoETrocaDeMarcadores() {
        var resultado = mascara.mascarar("Meu e-mail é teste@example.com e meu telefone é +55 79 99912-3456.");
        var marcadores = MARCADOR.matcher(resultado.texto()).results().map(MatchResult::group).toList();
        assertEquals(2, marcadores.size());
        assertTrue(resultado.restaurar(resultado.texto().replace("ODRCONPII", "ODRCONPii")).isEmpty());
        assertTrue(resultado.restaurar(resultado.texto().replace(marcadores.get(0), "")).isEmpty());
        assertTrue(resultado.restaurar(resultado.texto() + marcadores.get(0)).isEmpty());
        assertTrue(resultado.restaurar(marcadores.get(1) + " e " + marcadores.get(0)).isEmpty());
    }
}
