# Frequência 87

Jogo de terror/tensão em Java + JavaFX.

## História
Você é o único operador de uma estação de retransmissão de rádio isolada,
durante um apagão. Uma voz responde na frequência de emergência 87.0 MHz —
e não deveria haver ninguém do outro lado.

Há **8 fases** (torres diferentes). Em cada uma, encontre os fusíveis
espalhados, leve-os até o **quadro de energia** para restaurar a luz, e
fuja pela porta antes que **a Estática** te alcance.

A Estática só se move enquanto **não** está diretamente sob o foco da sua
lanterna — mas cada segundo com a lanterna ligada gasta bateria, e no
escuro sua sanidade despenca. A cada fase ela fica mais rápida e o número
de fusíveis necessários aumenta.

Se você morrer, **reinicia na mesma fase** (checkpoint).

## História (novo)
Por trás da Estática há um mistério mais antigo: o **Programa Éter**, uma
rede de 8 torres que devia manter a comunicação humana viva mesmo se tudo
mais falhasse. A cada fusível encontrado, um **eco** (fragmento de história)
aparece no topo da tela por alguns segundos, revelando aos poucos o que
aconteceu com os operadores que vieram antes de você — em ordem, do
primeiro ao último dos 36 fusíveis do jogo todo. Cada torre também tem
nome próprio, mostrado no HUD e nas telas de transição entre fases.

## Controles
- **WASD** ou **Setas**: mover
- **Mouse**: mirar a lanterna
- **F**: ligar/desligar a lanterna
- **ESPAÇO**: interagir (quadro de energia / porta de saída)
- **R**: radar (desbloqueado a partir da fase 4; consome células)
- **ESC** ou **P**: pausar / continuar
- **ENTER**: começar / avançar de fase / reiniciar no checkpoint após derrota (ou do zero após vitória final)

## Atmosfera (polish)
- Lanterna **pisca** com bateria baixa
- **Sanidade baixa**: vinheta, ruído de estática na tela, drone sonoro
- **Energia restaurada**: flash visual + luz residual na torre + som de relés
- **Estática perto**: screen shake, borda vermelha, avisos
- Ecos de lore ficam mais tempo na tela (e quebram em duas linhas se longos)
- Áudio ambiente de chiado de rádio que reage à sanidade, fase e proximidade

## Como rodar
Pré-requisitos: JDK 17+ e Maven.

```bash
cd frequencia-87
mvn clean javafx:run
```

## Estrutura
- `Main.java` — inicialização da aplicação JavaFX
- `GameMap.java` — 8 layouts da torre (paredes, itens, portas)
- `Player.java` — jogador (posição, lanterna, bateria, sanidade, fusíveis)
- `Enemy.java` — a Estática (só se move fora do cone de luz; velocidade sobe por fase)
- `Pickup.java` — itens colecionáveis (fusível / bateria)
- `Lore.java` — nomes das torres, dicas de fase e os 36 fragmentos de história
- `GameEngine.java` — loop do jogo, input, iluminação, HUD, estados e progressão de fases


## Novidades (conteúdo)
- **Estática evolui**: teleporte (fase 4+), investida ao sair da luz (fase 5+), perseguição da última posição (fase 6+)
- **Zonas de sanidade** em fases intermediárias (manchas roxas no chão)
- **Fases 7–8**: após religar a energia, sintonize o rádio (segure ESPAÇO no console) antes de sair
- **Finais alternativos** conforme sanidade mínima e ecos ouvidos + log de transmissão
- **Save local**: ao concluir uma fase, progresso salvo; no menu, **C** continua
- **Tutorial** na fase 1 com dicas contextuais
- **M** muta o áudio; tiles, cabos, placas e props visuais
