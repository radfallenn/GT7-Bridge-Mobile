# GT7 v1.4.6 - 04-06-26 as 11-22

Base usada: `GT7 v1.4.5 - 03-06-26 as 20-57.json`

APK recebido como referência/base compilada: `GT7-Bridge-Mobile-v1.5.4.apk`

## Objetivo desta versão

Preparar a atualização incremental do projeto GT7 Bridge Mobile / gt7.online para tornar o dashboard funcional com dados reais da telemetria, preservando o visual travado da referência.

## Regras principais

- Não recriar o dashboard do zero.
- Não alterar fundo, proporção, espaçamentos, bordas, estilo neon, tipografia, cards, ícones ou posição dos elementos.
- Manter formato vertical 9:32.
- Manter conta-giros circular grande no topo.
- Manter cards abaixo em 2 colunas por linha.
- Cada card deve manter o check no canto superior direito.
- O check ativa/desativa apenas a função do card, sem parar a telemetria geral.
- Campos ausentes devem mostrar `--` sem quebrar o layout.
- Sessões salvas devem ser preservadas.

## Status

Diretório criado para controle da versão e preparação do build do APK.

Para gerar um APK novo funcional, ainda é necessário que o repositório contenha o projeto-fonte Android/Flutter/React Native/WebView usado para gerar o APK anterior.
