package com.auditoria.backend.manifestacao;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProtocoloServiceTest {

    @Test
    void geraSomenteNoveDigitosNoIntervaloEsperado() {
        ProtocoloService service = new ProtocoloService();

        for (int amostra = 0; amostra < 100; amostra++) {
            String protocolo = service.gerar();

            assertThat(protocolo).matches("[0-9]{9}");
            assertThat(Integer.parseInt(protocolo)).isBetween(100_000_000, 999_999_999);
        }
    }
}
