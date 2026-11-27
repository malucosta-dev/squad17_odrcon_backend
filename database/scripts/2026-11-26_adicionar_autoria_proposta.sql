-- Registra a autoria das propostas de solução.
-- Script manual já aplicado ao Neon compartilhado.

BEGIN;

ALTER TABLE public.proposta_solucao
    ADD COLUMN id_autor INTEGER,
    ADD COLUMN origem_proposta VARCHAR(20),
    ADD CONSTRAINT fk_proposta_autor
        FOREIGN KEY (id_autor) REFERENCES public.usuario (id_usuario),
    ADD CONSTRAINT ck_proposta_origem
        CHECK (origem_proposta IN ('SOLICITANTE', 'EQUIPE'));

COMMIT;