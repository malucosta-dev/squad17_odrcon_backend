Projeto Java spring - ODRcon - Squad17

## Configuração local

Copie `.env.example` para `.env` na raiz do projeto e preencha `DB_HOST`,
`DB_USERNAME` e `DB_PASSWORD` com os dados do PostgreSQL no Neon.
Use apenas o hostname em `DB_HOST`, sem `https://`, porta ou caminho.
Preencha os valores sem aspas: o arquivo é carregado no formato `.properties`,
que mantém aspas como parte do valor.
A variável `GEMINI_API_KEY` configura a integração com Gemini.

Inicie o servidor a partir da raiz do projeto:

```powershell
mvn spring-boot:run
```

O Spring carrega o `.env` dessa pasta automaticamente. Variáveis de ambiente
definidas no sistema têm prioridade sobre os valores do arquivo.
O banco precisa estar acessível e com o esquema criado, pois o Hibernate
está configurado com `ddl-auto=validate`.
