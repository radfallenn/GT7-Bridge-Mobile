# GT7 Bridge Mobile - Dashboard Racing v1.60

Este design substitui o layout anterior por um painel vertical inspirado no dashboard futurista enviado como referência.

## Estrutura visual
- Tela vertical com fundo preto/azul muito escuro.
- Topo com botão de menu à esquerda, logotipo central e botão de configuração à direita.
- Painel principal com conta-giros/speedometer circular grande.
- Arco segmentado com zonas verde, amarela e vermelha.
- Velocidade grande no centro, unidade KMH, marcha em destaque e modo TRACK.
- Informações auxiliares nas laterais: intake, horário, coolant e combustível.
- Grade inferior com 8 cards em 2 colunas:
  - Telemetry
  - Lap Timer
  - Tire Status
  - Engine Temp
  - Fuel Level
  - G-Force
  - Boost Pressure
  - Track Map

## Funcionalidade mantida
- O app continua consultando o bridge em `/api/fields`.
- Os dados principais continuam funcionais: velocidade, RPM, marcha, combustível, melhor volta, voltas, turbo/boost e estado online/offline.
- O botão de menu permite alterar o IP do PS5.
- O botão de configuração abre informações do app e bridge.

## Paleta
- Fundo: preto azulado.
- Cards: preto com bordas metálicas.
- Destaques: verde neon.
- Zona intermediária: amarelo.
- Alta rotação: vermelho.
- Texto principal: branco.
