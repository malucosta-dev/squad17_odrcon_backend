package com.auditoria.backend.ia;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MascaraDadosPessoais {
    private static final String PREFIXO = "ODRCONPII";
    private static final String MARCADOR = "\\[\\[" + PREFIXO + "[a-z]+_[a-z]+\\]\\]";
    private static final Pattern MARCADORES = Pattern.compile(MARCADOR);
    private static final Pattern TEXTO_RESERVADO = Pattern.compile(PREFIXO, Pattern.CASE_INSENSITIVE);
    private static final String EMAIL_LOCAL = "[\\p{L}\\p{N}.!#$%&'*+/=?^_`{|}~-]";
    private static final Pattern IDENTIFICADORES = Pattern.compile(
            "(?<!" + EMAIL_LOCAL + ")" + EMAIL_LOCAL + "++@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+"
            + "|(?<![\\p{L}\\p{N}])(?:[a-z0-9]{2}\\.[a-z0-9]{3}\\.[a-z0-9]{3}/[a-z0-9]{4}-[0-9]{2}"
            + "|[0-9]{3}\\.[0-9]{3}\\.[0-9]{3}-[0-9]{2}|[0-9]{14}|[0-9]{11})(?!\\p{N})"
            + "|(?<=cnpj: )[a-z0-9]{12}[0-9]{2}\\b"
            + "|(?<![\\p{L}\\p{N}])(?:(?:\\+55|0055)[ .-]?)?"
            + "(?:(?:\\([0-9]{2}\\)|[0-9]{2})[ .-]?)?[0-9]{4,5}[ .-][0-9]{4}(?!\\p{N})"
            + "|(?<![\\p{L}\\p{N}])(?:\\+55[ .-]?)?\\([0-9]{2}\\)[ .-]?[0-9]{8,9}(?!\\p{N})"
            + "|(?<!\\p{N})\\+55[ .-]?[0-9]{10,11}(?!\\p{N})"
            + "|(?<!\\p{N})[0-9]{10}(?!\\p{N})"
            + "|(?:(?<=telefone: )|(?<=celular: )|(?<=whatsapp: ))[0-9]{8,9}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern TRECHOS_A_MASCARAR = Pattern.compile(
            MARCADOR + "|" + PREFIXO + "|" + IDENTIFICADORES.pattern(), Pattern.CASE_INSENSITIVE);

    Resultado mascarar(String texto) {
        String prefixo = PREFIXO + UUID.randomUUID().toString().replace("-", "")
                .chars().mapToObj(c -> String.valueOf((char) (c <= '9' ? 'g' + c - '0' : c)))
                .reduce("", String::concat);
        Map<String, String> porValor = new HashMap<>();
        Map<String, String> porMarcador = new HashMap<>();
        List<String> sequencia = new ArrayList<>();

        String mascarado = TRECHOS_A_MASCARAR.matcher(texto).replaceAll(match -> {
            String valor = match.group();
            String marcador = porValor.computeIfAbsent(valor, chave ->
                    "[[" + prefixo + "_" + indiceEmLetras(porValor.size()) + "]]");
            porMarcador.put(marcador, valor);
            sequencia.add(marcador);
            return marcador;
        });
        return new Resultado(mascarado, Map.copyOf(porMarcador), List.copyOf(sequencia));
    }

    private static String indiceEmLetras(int indice) {
        StringBuilder letras = new StringBuilder();
        do {
            letras.append((char) ('a' + indice % 26));
            indice /= 26;
        } while (indice > 0);
        return letras.reverse().toString();
    }

    static boolean contemIdentificadorSemMascara(String texto) {
        return IDENTIFICADORES.matcher(MARCADORES.matcher(texto).replaceAll(" ")).find();
    }

    static final class Resultado {
        private final String texto;
        private final Map<String, String> porMarcador;
        private final List<String> sequencia;

        Resultado(String texto, Map<String, String> porMarcador, List<String> sequencia) {
            this.texto = texto;
            this.porMarcador = porMarcador;
            this.sequencia = sequencia;
        }

        String texto() {
            return texto;
        }

        Optional<String> restaurar(String sugestao) {
            var marcadores = MARCADORES.matcher(sugestao);
            if (!sequencia.equals(marcadores.results().map(MatchResult::group).toList())
                    || TEXTO_RESERVADO.matcher(marcadores.reset().replaceAll(" ")).find()) {
                return Optional.empty();
            }
            return Optional.of(marcadores.reset().replaceAll(
                    match -> Matcher.quoteReplacement(porMarcador.get(match.group()))));
        }
    }
}
