package com.frequencia;

/**
 * Todo o conteúdo narrativo do jogo. Mantido separado do GameEngine para
 * deixar fácil editar/expandir a história sem mexer na lógica do jogo.
 */
public class Lore {

    /** Nome de cada uma das 8 torres. */
    public static final String[] PHASE_NAMES = {
            "Torre 1 — Posto Norte",
            "Torre 2 — Vale do Silêncio",
            "Torre 3 — Encruzilhada",
            "Torre 4 — Bunker de Retransmissão",
            "Torre 5 — Labirinto de Antenas",
            "Torre 6 — Sala de Máquinas",
            "Torre 7 — Cofre de Sinal",
            "Torre 8 — Núcleo da Frequência",
    };

    /**
     * Dica/aviso mostrado na tela de "fase concluída", sobre a PRÓXIMA torre.
     * Índice 0 nunca é exibido (não existe "próxima fase" antes da fase 1).
     */
    public static final String[] PHASE_HINT = {
            "",
            "A Torre 2 fica mais silenciosa. Silêncio demais.",
            "Na Torre 3 os corredores se repetem. Ou é você que se repete.",
            "A Torre 4 exige mais fusíveis — e ela está mais rápida agora.",
            "A Torre 5 é um labirinto de antenas. Fácil se perder. Fácil ser encontrado.",
            "Na Torre 6 ela já reconhece os seus passos.",
            "A Torre 7 guarda o penúltimo núcleo. Ela sabe que você está perto.",
            "A Torre 8 é o núcleo. Depois dela, não há mais para onde fugir — nem para onde voltar.",
    };

    /**
     * Fragmentos revelados, em ordem, um a um, a cada fusível encontrado
     * (contagem acumulada ao longo do jogo todo — são 36 no total, o mesmo
     * tanto de fusíveis que existem somando as 8 torres). Contam, aos poucos,
     * a história por trás da rede de torres e da Estática.
     */
    public static final String[] LOG_FRAGMENTS = {
            "ECO: 87.0 MHz — a frequência de emergência que nunca deveria ficar em silêncio.",
            "ECO: Oito torres. Uma rede. Um único operador em cada uma, sempre vigiando.",
            "ECO: O Programa Éter prometia: se tudo mais falhar, a voz humana ainda vai passar.",
            "ECO: Torre 1, registro antigo: 'Terminei meu turno. Nada de anormal. — Operador R.'",
            "ECO: Torre 1: 'Ouvi algo no canal às 3 da manhã. Não reportei. Deve ter sido o vento.'",
            "ECO: Manual rasgado: 'Nunca fique sintonizado por mais de trinta minutos seguidos.'",
            "ECO: 'Trinta minutos. Por quê? Ninguém nunca explicou o porquê.'",
            "ECO: Torre 2: 'A antena capta uma segunda portadora. Não identificada. Investigar.'",
            "ECO: 'A segunda portadora repete frases. As minhas frases. De ontem.'",
            "ECO: 'Pedi transferência. Recusada. Disseram que eu era essencial demais para sair.'",
            "ECO: Torre 3: 'Encontrei o diário do operador anterior. As últimas páginas estão em branco.'",
            "ECO: 'Não em branco. Escritas em algo que a luz não revela.'",
            "ECO: 'Ela não fala sozinha. Ela responde com a sua própria voz, um segundo atrasada.'",
            "ECO: Torre 4: 'Perda de contato com 3 operadores nos últimos 2 anos. Causa: desconhecida.'",
            "ECO: 'Desconhecida era mentira. Todos sabiam. Ninguém escrevia o resto.'",
            "ECO: 'Ela cresce a cada operador que escuta demais. Ela lembra o que eles lembravam.'",
            "ECO: Torre 5: 'Hoje entendi: não é uma voz. São todas as vozes, ao mesmo tempo.'",
            "ECO: 'Reconheci a voz do meu antecessor no meio do ruído. Ele me chamou pelo nome.'",
            "ECO: 'Eu nunca disse meu nome pelo rádio. Nunca.'",
            "ECO: Torre 6, última entrada legível: 'Se está lendo isso, desligue a lanterna. Ela sente a luz.'",
            "ECO: 'Não. Ela teme a luz. É diferente. Ela só se move onde não a veem.'",
            "ECO: 'Descobri o esquema: cada torre tem um núcleo. Cortar a energia, corta um pedaço dela.'",
            "ECO: Torre 7: 'Oito núcleos. Oito pedaços. Corte todos e talvez ela silencie de vez.'",
            "ECO: 'Ou talvez cortar todos a liberte por completo. Não tenho certeza. Não há tempo.'",
            "ECO: 'Minha sanidade está em 12%. Escrevo rápido antes que esqueça o que sou.'",
            "ECO: 'Ela não me persegue. Ela espera. Ela sabe que eu vou olhar de novo.'",
            "ECO: Torre 8, entrada final de um operador: 'Cheguei ao núcleo. A voz aqui é... a minha própria.'",
            "ECO: 'De um futuro que talvez ainda não tenha acontecido. Ou já tenha, várias vezes.'",
            "ECO: 'Se você está ouvindo isso agora, operador novo: já aconteceu com você antes.'",
            "ECO: 'Você só não lembra ainda. A estática guarda as lembranças por você.'",
            "ECO: 'Seu nome... eu sei o seu nome. Vi você chegar à Torre 1, há pouco.'",
            "ECO: 'Não desligue a lanterna por muito tempo. Não pelo monstro. Por você mesmo.'",
            "ECO: 'Cada fusível que você encontra é um fragmento meu que ainda resiste.'",
            "ECO: 'Estou quase lá. Quase esquecida por completo dentro dela.'",
            "ECO: 'Termine o que eu não consegui. Chegue ao núcleo. Desligue tudo.'",
            "ECO: 'E quando o silêncio finalmente vier... não escute com saudade.'",
    };

    public static final String[] WIN_LINES = {
            "Você desliga o núcleo. Por um instante, todas as vozes gritam ao mesmo tempo — e depois, nada.",
            "O silêncio que sobra não é alívio. É o mesmo silêncio de antes do apagão.",
            "Em algum lugar, uma torre nova começa a piscar. Alguém, em breve, vai atender à chamada.",
    };

    public static final String[] LOSE_CAUGHT_LINES = {
            "A estática te alcança e, por um segundo, você reconhece a própria voz nela.",
            "Mais um fragmento se junta ao coro. Mais uma torre espera por outro operador.",
    };

    public static final String[] LOSE_SANITY_LINES = {
            "Você para de conseguir dizer o que é real e o que é eco.",
            "A escuridão não precisa mais te alcançar — você já foi até ela.",
    };

    public static final String[] WIN_LINES_HIGH_SANITY = {
            "Você desliga o núcleo com as mãos firmes. A estática grita — e some.",
            "Pela primeira vez, o silêncio parece seu. Não dela.",
            "Os ecos se calam. Em alguma torre distante, a frequência 87 fica mud.",
    };

    public static final String[] WIN_LINES_ALL_ECHOS = {
            "Você ouviu todos os fragmentos. Cada voz. Cada aviso.",
            "Ao cortar o núcleo, reconhece o próprio nome no último eco — e escolhe esquecer.",
            "A rede Éter se apaga. Desta vez, talvez, de verdade.",
    };

    public static final String[] WIN_LINES_LOW_SANITY = {
            "Você desliga o núcleo, mas a mão que aperta o disjuntor... parece não ser só sua.",
            "O silêncio vem. Dentro dele, ainda há chiado.",
            "Em algum lugar, uma torre nova começa a piscar. A chamada já tem o seu tom de voz.",
    };
}
