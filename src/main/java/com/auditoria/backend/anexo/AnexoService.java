package com.auditoria.backend.anexo;

import com.auditoria.backend.manifestacao.Manifestacao;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Valida, guarda o arquivo na pasta de uploads e anota o anexo no banco.
 * Isolado aqui para trocar a pasta local por um bucket no futuro sem mexer no resto.
 */
@Service
public class AnexoService {

    // extensao permitida -> tipo gravado em anexo.tipo_anexo
    private static final Map<String, String> TIPOS_PERMITIDOS = Map.ofEntries(
            Map.entry("pdf", "DOCUMENTO"),
            Map.entry("doc", "DOCUMENTO"),
            Map.entry("docx", "DOCUMENTO"),
            Map.entry("txt", "DOCUMENTO"),
            Map.entry("png", "IMAGEM"),
            Map.entry("jpg", "IMAGEM"),
            Map.entry("jpeg", "IMAGEM"),
            Map.entry("webp", "IMAGEM"),
            Map.entry("mp3", "AUDIO"),
            Map.entry("wav", "AUDIO"),
            Map.entry("mp4", "VIDEO")
    );

    private final AnexoRepository anexoRepository;
    private final Path pastaUploads;
    private final long tamanhoMaximoBytes;

    public AnexoService(AnexoRepository anexoRepository,
                        @Value("${odrcon.uploads.dir:uploads}") String pastaUploads,
                        @Value("${odrcon.uploads.max-bytes:10485760}") long tamanhoMaximoBytes) {
        this.anexoRepository = anexoRepository;
        this.pastaUploads = Path.of(pastaUploads).toAbsolutePath().normalize();
        this.tamanhoMaximoBytes = tamanhoMaximoBytes;
    }

    /** Nao ha arquivo para guardar (campo opcional do formulario). */
    public static boolean semArquivo(MultipartFile arquivo) {
        return arquivo == null || arquivo.isEmpty();
    }

    /**
     * Confere o arquivo SEM gravar nada. Chamar antes de registrar a reclamacao,
     * para um arquivo invalido nao "queimar" o texto no detector de duplicidade.
     */
    public void validar(MultipartFile arquivo) {
        if (semArquivo(arquivo)) {
            return;
        }
        if (arquivo.getSize() > tamanhoMaximoBytes) {
            throw new ResponseStatusException(HttpStatus.CONTENT_TOO_LARGE,
                    "Arquivo maior que o limite de " + (tamanhoMaximoBytes / (1024 * 1024)) + " MB.");
        }
        extensaoPermitida(arquivo);
    }

    /** Guarda o arquivo e cria a linha em anexo. Devolve null se nao veio arquivo. */
    public Anexo salvar(Manifestacao manifestacao, MultipartFile arquivo) {
        if (semArquivo(arquivo)) {
            return null;
        }
        validar(arquivo);

        String extensao = extensaoPermitida(arquivo);
        // Nome novo e aleatorio: o nome enviado pelo usuario nunca vira caminho em disco.
        String caminhoRelativo = "manifestacao-" + manifestacao.getIdManifestacao()
                + "/" + UUID.randomUUID() + "." + extensao;
        Path destino = pastaUploads.resolve(caminhoRelativo).normalize();
        if (!destino.startsWith(pastaUploads)) {
            throw new IllegalStateException("Caminho de upload invalido.");
        }

        try {
            Files.createDirectories(destino.getParent());
            try (InputStream entrada = arquivo.getInputStream()) {
                Files.copy(entrada, destino, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Nao foi possivel guardar o anexo.", e);
        }

        // Se algo falhar depois (banco ou rollback da reclamacao), nao deixa arquivo orfao.
        apagarSeNaoConfirmar(destino);

        try {
            return anexoRepository.save(new Anexo(
                    manifestacao.getIdManifestacao(),
                    TIPOS_PERMITIDOS.get(extensao),
                    caminhoRelativo));
        } catch (RuntimeException e) {
            apagarQuieto(destino);
            throw e;
        }
    }

    private String extensaoPermitida(MultipartFile arquivo) {
        String nome = arquivo.getOriginalFilename();
        int ponto = nome == null ? -1 : nome.lastIndexOf('.');
        String extensao = ponto < 0 ? "" : nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
        if (!TIPOS_PERMITIDOS.containsKey(extensao)) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Tipo de arquivo nao permitido. Aceitos: " + String.join(", ", TIPOS_PERMITIDOS.keySet()) + ".");
        }
        return extensao;
    }

    private void apagarSeNaoConfirmar(Path arquivo) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    apagarQuieto(arquivo);
                }
            }
        });
    }

    private void apagarQuieto(Path arquivo) {
        try {
            Files.deleteIfExists(arquivo);
        } catch (IOException ignorado) {
            // melhor esforco: nao esconder o erro original
        }
    }
}
