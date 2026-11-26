-- Estrutura de armazenamento dos termos de acordo.
-- Script manual: já aplicado ao banco Neon compartilhado.
-- Não executar novamente nesse banco.
-- Pré-requisitos: tabelas proposta_solucao e anexo existentes.
-- Testados: criação de rascunho, emissão com data automática
-- e bloqueio de alteração do texto após emissão.

BEGIN;

-- Chaves compostas para validar os vínculos do termo.
ALTER TABLE public.proposta_solucao
    ADD CONSTRAINT uq_proposta_manifestacao_termo
    UNIQUE (id_proposta, id_manifestacao);

ALTER TABLE public.anexo
    ADD CONSTRAINT uq_anexo_manifestacao_termo
    UNIQUE (id_anexo, id_manifestacao);

CREATE TABLE public.termo_acordo (
    id_termo INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    id_proposta INTEGER NOT NULL,
    id_manifestacao INTEGER NOT NULL,

    -- Opcional: referência ao PDF armazenado como anexo.
    id_anexo_pdf INTEGER,

    versao INTEGER NOT NULL DEFAULT 1,
    conteudo TEXT NOT NULL,
    status_termo VARCHAR(20) NOT NULL DEFAULT 'RASCUNHO',

    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    emitido_em TIMESTAMPTZ,
    cancelado_em TIMESTAMPTZ,

    CONSTRAINT uq_termo_proposta_versao
        UNIQUE (id_proposta, versao),

    CONSTRAINT ck_termo_versao
        CHECK (versao > 0),

    CONSTRAINT ck_termo_conteudo
        CHECK (length(btrim(conteudo)) > 0),

    CONSTRAINT ck_termo_status
        CHECK (
            status_termo IN ('RASCUNHO', 'EMITIDO', 'CANCELADO')
        ),

    CONSTRAINT ck_termo_datas_status
        CHECK (
            (
                status_termo = 'RASCUNHO'
                AND emitido_em IS NULL
                AND cancelado_em IS NULL
            )
            OR (
                status_termo = 'EMITIDO'
                AND emitido_em IS NOT NULL
                AND cancelado_em IS NULL
            )
            OR (
                status_termo = 'CANCELADO'
                AND cancelado_em IS NOT NULL
            )
        ),

    CONSTRAINT fk_termo_proposta_manifestacao
        FOREIGN KEY (id_proposta, id_manifestacao)
        REFERENCES public.proposta_solucao (
            id_proposta,
            id_manifestacao
        )
        ON DELETE RESTRICT
        ON UPDATE RESTRICT,

    CONSTRAINT fk_termo_anexo_manifestacao
        FOREIGN KEY (id_anexo_pdf, id_manifestacao)
        REFERENCES public.anexo (
            id_anexo,
            id_manifestacao
        )
        ON DELETE RESTRICT
        ON UPDATE RESTRICT
);

CREATE INDEX idx_termo_manifestacao
    ON public.termo_acordo (id_manifestacao);

CREATE INDEX idx_termo_anexo_pdf
    ON public.termo_acordo (id_anexo_pdf)
    WHERE id_anexo_pdf IS NOT NULL;

-- Preserva o texto de termos emitidos e o histórico.
CREATE FUNCTION public.controlar_termo_acordo()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status_termo <> 'RASCUNHO' THEN
            RAISE EXCEPTION
                'Termos emitidos ou cancelados não podem ser excluídos.';
        END IF;

        RETURN OLD;
    END IF;

    IF TG_OP = 'INSERT' THEN
        IF NEW.status_termo <> 'RASCUNHO' THEN
            RAISE EXCEPTION
                'Um novo termo deve começar como RASCUNHO.';
        END IF;

        RETURN NEW;
    END IF;

    -- O vínculo e a versão não mudam depois da criação.
    IF NEW.id_termo IS DISTINCT FROM OLD.id_termo
       OR NEW.id_proposta IS DISTINCT FROM OLD.id_proposta
       OR NEW.id_manifestacao IS DISTINCT FROM OLD.id_manifestacao
       OR NEW.versao IS DISTINCT FROM OLD.versao
       OR NEW.criado_em IS DISTINCT FROM OLD.criado_em THEN
        RAISE EXCEPTION
            'Não é permitido alterar a identificação ou o vínculo do termo.';
    END IF;

    IF OLD.status_termo = 'CANCELADO' THEN
        RAISE EXCEPTION
            'Um termo cancelado não pode ser alterado.';
    END IF;

    IF OLD.status_termo = 'EMITIDO' THEN
        IF NEW.conteudo IS DISTINCT FROM OLD.conteudo THEN
            RAISE EXCEPTION
                'O texto de um termo emitido não pode mudar. Crie outra versão.';
        END IF;

        IF NEW.status_termo NOT IN ('EMITIDO', 'CANCELADO') THEN
            RAISE EXCEPTION
                'Um termo emitido não pode voltar a ser rascunho.';
        END IF;

        -- Permite anexar o PDF posteriormente, mas não substituí-lo.
        IF OLD.id_anexo_pdf IS NOT NULL
           AND NEW.id_anexo_pdf IS DISTINCT FROM OLD.id_anexo_pdf THEN
            RAISE EXCEPTION
                'O PDF de um termo emitido não pode ser substituído.';
        END IF;
    END IF;

    -- Datas controladas pelo banco.
    NEW.emitido_em := OLD.emitido_em;
    NEW.cancelado_em := OLD.cancelado_em;

    IF OLD.status_termo = 'RASCUNHO'
       AND NEW.status_termo = 'EMITIDO' THEN
        NEW.emitido_em := CURRENT_TIMESTAMP;
    END IF;

    IF NEW.status_termo = 'CANCELADO' THEN
        NEW.cancelado_em := CURRENT_TIMESTAMP;
    END IF;

    NEW.atualizado_em := CURRENT_TIMESTAMP;

    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_controlar_termo_acordo
BEFORE INSERT OR UPDATE OR DELETE
ON public.termo_acordo
FOR EACH ROW
EXECUTE FUNCTION public.controlar_termo_acordo();

COMMIT;