//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
import java.util.*;
import java.io.*;
import java.math.*;

/**
 * Rush to the opponent's row
 **/
class Player {
    private static final Map<Long, Double> whiteHistory = new HashMap<>();
    private static final Map<Long, Double> blackHistory = new HashMap<>();
    public static void main(String args[]) {
        Scanner in = new Scanner(System.in);



        boolean white = false;
        boolean firstMove = true;
        // game loop
        Field field = new Field();
        field.printField();
        Random random = new Random();
        Node root = null;
        while (true) {
            String opponentMove = in.nextLine(); // last move played or "None"
            long turnStart = System.nanoTime();
            if (opponentMove.equals("None")) {
                white = true;
            }
            else {
                Position fromPos = Position.FROM(opponentMove.substring(0, 2));
                Position toPos = Position.FROM(opponentMove.substring(2, 4));
                field.move(fromPos, toPos, !white);
            }
            //System.err.println("field");
            //  field.printField();
            int legalMoves = in.nextInt(); // number of legal moves
            for (int i = 0; i < legalMoves; i++) {
                String moveString = in.next(); // a legal move
            }
            in.nextLine();
            long durationLimit = firstMove
                    ? 850_000_000L
                    : 85_000_000L;

            MiniMax miniMax = new MiniMax(turnStart + durationLimit);
            long[] action = miniMax.search(white, field);
            if (firstMove){
                firstMove = false;
            }
            field.move(action [0], action [1], white);
            int pawnIndex = Game.getIndexes(action [0]).get(0);
            int moveIndex = Game.getIndexes(action [1]).get(0);
            Position moveFromPos = new Position(pawnIndex);
            Position moveToPos = new Position(moveIndex);
            System.out.println(moveFromPos.getChessPos() + moveToPos.getChessPos());
            /*
            // Write an action using System.out.println()
            // To debug: System.err.println("Debug messages...");
            Node reusedRoot = null;
            if (root != null){
                search:
                for (Node child : root.children){
                    for (Node nextChild : child.children){
                        if (nextChild.field.blackPawnMask == field.blackPawnMask &&
                                nextChild.field.whitePawnMask == field.whitePawnMask
                        ){
                            reusedRoot = nextChild;
                            break search;
                        }
                    }
                }
            }
            if (reusedRoot != null) {
                root = reusedRoot;
                root.parent = null;
            }
            else {
                root = new Node(null, white, field.copy(), null);
            }
            */
        }
    }
    static class TransitionTable{
        static final int TT_SIZE = 1 << 18; // 262.144 Einträge
        static final int TT_MASK = TT_SIZE - 1;

        static final byte EXACT = 1;
        static final byte LOWER_BOUND = 2;
        static final byte UPPER_BOUND = 3;

        // bleiben trotz new MiniMax() pro Spielzug erhalten.
        static final long[] ttWhite = new long[TT_SIZE];
        static final long[] ttBlack = new long[TT_SIZE];
        static final boolean[] ttWhiteToMove = new boolean[TT_SIZE];

        static final int[] ttDepth = new int[TT_SIZE];
        static final double[] ttScore = new double[TT_SIZE];
        static final byte[] ttType = new byte[TT_SIZE]; // 0 = leer, byte wegen weniger speicher
        static final int[] ttMove = new int[TT_SIZE];


        static final long[][] ZOBRIST = new long[2][64];
        static final long BLACK_TO_MOVE;

        static {
            Random random = new Random(123456789L);//123456789L ist der Seed --> reproduzierbar

            for (int color = 0; color < 2; color++) {
                for (int square = 0; square < 64; square++) {
                    ZOBRIST[color][square] = random.nextLong();
                }
            }

            BLACK_TO_MOVE = random.nextLong();
        }
        static long computeHash(long wp, long bp, boolean whiteToMove) {
            long hash = whiteToMove ? 0L : BLACK_TO_MOVE;

            while (wp != 0) {
                int square = Long.numberOfTrailingZeros(wp);
                hash ^= ZOBRIST[0][square];
                wp &= wp - 1;
            }

            while (bp != 0) {
                int square = Long.numberOfTrailingZeros(bp);
                hash ^= ZOBRIST[1][square];
                bp &= bp - 1;
            }
            return hash;
        }
        private static void storeTT(
                int index, Field field, boolean white,
                int depth, double score, byte type, int move) {
            //Positionen mit mehr tiefe bevorzugen
            boolean samePosition = ttType[index] != 0
                    && ttWhite[index] == field.whitePawnMask
                    && ttBlack[index] == field.blackPawnMask
                    && ttWhiteToMove[index] == white;

            if (samePosition) {
                if (ttDepth[index] > depth) {
                    return;
                }

                if (ttDepth[index] == depth
                        && ttType[index] == EXACT
                        && type != EXACT) {
                    return;
                }
            }

            ttWhite[index] = field.whitePawnMask;
            ttBlack[index] = field.blackPawnMask;
            ttWhiteToMove[index] = white;

            ttDepth[index] = depth;
            ttScore[index] = score;
            ttMove[index] = move;
            ttType[index] = type;
        }
    }
    static class MiniMax{
        public MiniMax (long deadline){
            this.deadline = deadline;
        }
        public static final int MAX_DEPTH = 5;
        long bestPawn;
        long bestTarget;

        long deadline;
        long nodes;



        private static final SearchTimeout TIMEOUT = new SearchTimeout();

        private static final class SearchTimeout extends RuntimeException {
            SearchTimeout() {
                super(null, null, false, false);
            }
        }

        private void checkTime() {
            if (System.nanoTime() - deadline >= 0) {
                throw TIMEOUT;
            }
        }
        public long[] search(boolean white, Field field) {
            if (field.isOver()) {
                throw new IllegalStateException("Spiel bereits beendet");
            }

            nodes = 0;

            long own = field.getPawns(white);
            long enemy = field.getPawns(!white);

            // Legal vorhandenen Zug als Rückfalloption bestimmen.
            long targets = getPossibleMoves(own, enemy, white);

            if (targets == 0) {
                throw new IllegalStateException("Keine legalen Züge");
            }

            long completedTarget = Long.lowestOneBit(targets);
            long sources = getPossiblePawns(
                    own, enemy, completedTarget, white
            );

            if (sources == 0) {
                throw new IllegalStateException("Zielfeld ohne Startbauern");
            }

            long completedPawn = Long.lowestOneBit(sources);
            int completedDepth = 0;

            bestPawn = completedPawn;
            bestTarget = completedTarget;
            long hash = TransitionTable.computeHash(
                    field.whitePawnMask,
                    field.blackPawnMask,
                    white
            );
            for (int depth = 1; depth <= 210; depth++) {
                try {
                    checkTime();

                    double score = miniMax(
                            white, depth, field, depth,
                            Double.NEGATIVE_INFINITY,
                            Double.POSITIVE_INFINITY,
                            hash
                    );

                    completedPawn = bestPawn;
                    completedTarget = bestTarget;
                    completedDepth = depth;

                    // Passend zu deinen aktuellen Endstellungswerten.
                    if (Math.abs(score) >= 1_000_000.0) {
                        break;
                    }
                } catch (SearchTimeout timeout) {
                    break;
                }
            }

            bestPawn = completedPawn;
            bestTarget = completedTarget;

            System.err.println(
                    "depth=" + completedDepth + " nodes=" + nodes
            );

            return new long[]{completedPawn, completedTarget};
        }
        public double miniMax(boolean white, int depth, Field field, int maxDepth, double alpha, double beta, long hash){
            // Am Wurzelknoten und danach alle 256 Knoten prüfen.
            if ((nodes++ & 255L) == 0 || depth == maxDepth) {
                checkTime();
            }
            if (depth == 0 || field.isOver()){
                return EVAL(field, white);
            }
            int index = (int) hash & TransitionTable.TT_MASK;

            boolean hit = TransitionTable.ttType[index] != 0
                    && TransitionTable.ttWhite[index] == field.whitePawnMask
                    && TransitionTable.ttBlack[index] == field.blackPawnMask
                    && TransitionTable.ttWhiteToMove[index] == white;

            int hashMove = hit ? TransitionTable.ttMove[index] : -1;

            // Am Wurzelknoten zunächst keinen Tabellen-Cutoff verwenden.
            // So werden bestPawn und bestTarget weiterhin regulär gesetzt.
            if (hit && depth != maxDepth && TransitionTable.ttDepth[index] >= depth) {
                double cached = TransitionTable.ttScore[index];
                byte type = TransitionTable.ttType[index];

                if (type == TransitionTable.EXACT
                        || (type == TransitionTable.LOWER_BOUND && cached >= beta)
                        || (type == TransitionTable.UPPER_BOUND && cached <= alpha)) {
                    return cached;
                }
            }
            double originalAlpha = alpha;
            int localBestMove = -1;

            double bestValue = Double.NEGATIVE_INFINITY;
            long ownPawns = field.getPawns(white);
            long enemyPawns = field.getPawns(!white);
            long allMoves = getPossibleMoves(ownPawns, enemyPawns, white);

            long hashPawn = 0L;
            long hashTarget = 0L;

            if (hashMove >= 0 && hashMove < 4096) {
                int from = hashMove >>> 6;
                int to = hashMove & 63;

                long pawn = 1L << from;
                long target = 1L << to;

                // Prüfen, ob genau dieser Zug legal ist.
                if ((allMoves & target) != 0
                        && (getPossiblePawns(
                        ownPawns, enemyPawns, target, white
                ) & pawn) != 0) {

                    hashPawn = pawn;
                    hashTarget = target;
                }
            }

            long winningMoves = allMoves & (white ? Field.MASKE_OBERE_REIHE : Field.MASKE_UNTERE_REIHE);
            if (Long.bitCount(enemyPawns) == 1) {
                winningMoves |= allMoves & enemyPawns;
            }

            long remaining = allMoves & ~winningMoves;

            long captureMoves = remaining & enemyPawns;
            long quietMoves = remaining & ~enemyPawns;
            long behindFrontMask;

            if (white) {
                int frontRank = Long.numberOfTrailingZeros(enemyPawns) >>> 3;

                behindFrontMask = frontRank == 7 ? 0L : (-1L << ((frontRank + 1) * 8));
            } else {
                int frontRank = (63 - Long.numberOfLeadingZeros(enemyPawns)) >>> 3;

                behindFrontMask = (1L << (frontRank * 8)) - 1;
            }

            long advancedMoves = quietMoves & behindFrontMask;
            quietMoves &= ~advancedMoves;


            long oldWhite = field.whitePawnMask;
            long oldBlack = field.blackPawnMask;
            boolean firstMove = true;
            for (int phase = -1; phase < 4; phase++) {
                long targets = 0L;
                if (phase == -1) {
                    targets = hashTarget;
                }
                else if (phase == 0){
                    targets = winningMoves;
                }
                else if (phase == 1){
                    targets = captureMoves;
                }
                else if (phase ==2){
                    targets = advancedMoves;
                }
                else if (phase == 3) {
                    targets = quietMoves;
                }
                while (targets != 0) {
                    long move = Long.lowestOneBit(targets);
                    targets &= targets - 1;

                    long possiblePawns;

                    if (phase == -1) {
                        possiblePawns = hashPawn;
                    } else {
                        possiblePawns =
                                getPossiblePawns(ownPawns, enemyPawns, move, white);

                        if (move == hashTarget) {
                            possiblePawns &= ~hashPawn;
                        }
                    }

                    while (possiblePawns != 0) {
                        long pawn = Long.lowestOneBit(possiblePawns);
                        possiblePawns &= possiblePawns - 1;

                        double result;
                        int color = white ? 0 : 1;
                        int from = Long.numberOfTrailingZeros(pawn);
                        int to = Long.numberOfTrailingZeros(move);

                        long nextHash = hash
                                ^ TransitionTable.ZOBRIST[color][from]
                                ^ TransitionTable.ZOBRIST[color][to]
                                ^ TransitionTable.BLACK_TO_MOVE;

                        if ((enemyPawns & move) != 0) {
                            nextHash ^= TransitionTable.ZOBRIST[color ^ 1][to];
                        }

                        try {
                            field.move(pawn, move, white);

                            if (firstMove) {
                                // Erster tatsächlich untersuchter Zug: volles Fenster.
                                result = -miniMax(
                                        !white, depth - 1, field, maxDepth,
                                        -beta, -alpha, nextHash
                                );
                            } else {
                                // Weitere Züge: zunächst enges Testfenster.
                                result = -miniMax(
                                        !white, depth - 1, field, maxDepth,
                                        -alpha - 1, -alpha, nextHash
                                );

                                if (result > alpha && result < beta) {
                                    // Könnte der neue beste Zug sein:
                                    // erneut mit vollem Fenster untersuchen.
                                    result = -miniMax(
                                            !white, depth - 1, field, maxDepth,
                                            -beta, -alpha, nextHash
                                    );
                                }
                            }
                        } finally {
                            field.whitePawnMask = oldWhite;
                            field.blackPawnMask = oldBlack;
                        }

                        firstMove = false;

                        if (result > bestValue) {
                            bestValue = result;

                            localBestMove = (from << 6) | to;

                            if (depth == maxDepth) {
                                bestPawn = pawn;
                                bestTarget = move;
                            }
                        }

                        alpha = Math.max(alpha, result);

                        if (alpha >= beta) {
                            TransitionTable.storeTT(
                                    index, field, white, depth,
                                    bestValue, TransitionTable.LOWER_BOUND, localBestMove
                            );
                            return bestValue;
                        }
                    }
                }
            }

            if (localBestMove == -1) {
                throw new IllegalStateException(
                        "Keine Züge in einer nicht beendeten Stellung"
                );
            }

            byte type = bestValue <= originalAlpha
                    ? TransitionTable.UPPER_BOUND
                    : TransitionTable.EXACT;

            TransitionTable.storeTT(
                    index, field, white, depth,
                    bestValue, type, localBestMove
            );

            return bestValue;
        }
        public static long getPossibleMoves(
                long pawns, long otherPawns, boolean white) {

            long diagonal;
            long forward;

            if (white) {
                diagonal = ((pawns << 7) & Field.NOT_H_FILE)
                        | ((pawns << 9) & Field.NOT_A_FILE);
                forward = pawns << 8;
            } else {
                diagonal = ((pawns >>> 7) & Field.NOT_A_FILE)
                        | ((pawns >>> 9) & Field.NOT_H_FILE);
                forward = pawns >>> 8;
            }

            diagonal &= ~pawns;
            forward &= ~(pawns | otherPawns);

            return diagonal | forward;
        }
        public static long getPossiblePawns(long pawns, long field, long move, boolean white){
            if (white){
                return getPossibleWhitePawns(pawns, field, move);
            }
            else{
                return getPossibleBlackPawns(pawns, field, move);
            }
        }
        public static long getPossibleWhitePawns(long pawns, long field, long move){
            long possiblePawnMask = move >>> (7) & Field.NOT_A_FILE | move >>> (9) & Field.NOT_H_FILE;
            if ((move & field) == 0) {
                possiblePawnMask |= move >>> 8;
            }
            possiblePawnMask &=pawns;
            return possiblePawnMask;
        }
        public static long getPossibleBlackPawns(long pawns, long field, long move){
            long possiblePawnMask = move << (7) & Field.NOT_H_FILE | move << (9) & Field.NOT_A_FILE;
            if ((move & field) == 0) {
                possiblePawnMask |= move << 8;
            }
            possiblePawnMask &=pawns;
            return possiblePawnMask;
        }
    public static double EVAL(Field field, boolean white) {
        long wp = field.whitePawnMask;
        long bp = field.blackPawnMask;

        // Endstellungen haben Vorrang vor Positionsmerkmalen.
        if (field.isOver()) {
            return field.getWinnner() == white
                    ? 1_000_000.0
                    : -1_000_000.0;
        }

        long controlledWhite = ((wp << 7) & Game.NOT_H_FILE) | ((wp << 9) & Game.NOT_A_FILE);

        long controlledBlack = ((bp >>> 7) & Game.NOT_A_FILE) | ((bp >>> 9) & Game.NOT_H_FILE);

        long guardedWhite = controlledWhite & wp;
        long guardedBlack = controlledBlack & bp;

        long guardedChainWhite =  ((guardedWhite << 7) & Game.NOT_H_FILE) | ((guardedWhite << 9) & Game.NOT_A_FILE);
        long guardedChainBlack =  ((guardedBlack >>> 7) & Game.NOT_A_FILE) | ((guardedBlack >>> 9) & Game.NOT_H_FILE);

        guardedChainWhite &= wp;
        guardedChainBlack &= bp;

        long pawnsOnWhiteSquaresWhite = Field.MASKE_WHITE_SQUARES & wp;
        long pawnsOnBlackSquaresWhite = Field.MASKE_BLACK_SQUARES & wp;
        int pawnSquareRatioWhite = Long.bitCount(pawnsOnWhiteSquaresWhite) - Long.bitCount(pawnsOnBlackSquaresWhite);

        long pawnsOnWhiteSquaresBlack = Field.MASKE_WHITE_SQUARES & bp;
        long pawnsOnBlackSquaresBlack = Field.MASKE_BLACK_SQUARES & bp;
        int pawnSquareRatioBlack = Long.bitCount(pawnsOnWhiteSquaresBlack) - Long.bitCount(pawnsOnBlackSquaresBlack);



        //long saveWhite = guardedWhite | (wp & ~controlledBlack);
        //long saveBlack = guardedBlack | (bp & ~controlledWhite);

        //long guardedFinalWhite =
        //   guardedWhite & Field.MASKE_DEFAULT_BLACK_PAWNS;

        //long guardedFinalBlack =
        //    guardedBlack & Field.MASKE_DEFAULT_WHITE_PAWNS;

        long attackedUnguardedBlack = bp & ~guardedBlack & controlledWhite;

        long attackedUnguardedWhite = wp & ~guardedWhite & controlledBlack;

        //long advancedPawnsWhite = Field.MASKE_OBERE_HÄLFTE & wp;
        //long advancedPawnsBlack = Field.MASKE_UNTERE_HÄLFTE & bp;
        long dangerousPawnsWhite = 0L;
        if (pawnSquareRatioBlack > 0) {
            dangerousPawnsWhite = pawnsOnBlackSquaresWhite;
        } else if (pawnSquareRatioBlack < 0) {
            dangerousPawnsWhite = pawnsOnWhiteSquaresWhite;
        }

        long dangerousPawnsBlack = 0L;
        if (pawnSquareRatioWhite > 0) {
            dangerousPawnsBlack = pawnsOnBlackSquaresBlack;
        } else if (pawnSquareRatioWhite < 0) {
            dangerousPawnsBlack = pawnsOnWhiteSquaresBlack;
        }

        dangerousPawnsWhite &= Field.MASKE_OBERE_HÄLFTE;
        dangerousPawnsBlack &= Field.MASKE_UNTERE_HÄLFTE;

        //long doublePawnsWhite = wp & (wp << 8);
        //long doublePawnsBlack = bp & (bp >>> 8);

        long centerPawnsWhite = wp & Field.MASKE_MITTE_WHITE;
        long centerPawnsBlack = bp & Field.MASKE_MITTE_BLACK;

        //long backrowWhite = wp & Field.MASKE_UNTERE_REIHE;
        //long backrowBlack = bp & Field.MASKE_OBERE_REIHE;
        // Zunächst alles aus weißer Perspektive bewerten.
        double score =
                6* (Long.bitCount(wp) - Long.bitCount(bp))
                        //+ 0*(Long.bitCount(backrowWhite) - Long.bitCount(backrowBlack))
                        + 2.0 * (Long.bitCount(dangerousPawnsWhite)- Long.bitCount(dangerousPawnsBlack))

                        - 0.5*(Math.abs(pawnSquareRatioWhite) - Math.abs(pawnSquareRatioBlack))

                        + 0.3 * (Long.bitCount(guardedChainWhite) -Long.bitCount(guardedChainBlack))

                        + 1* (
                        Long.bitCount(attackedUnguardedBlack)
                                - Long.bitCount(attackedUnguardedWhite)
                )

                        + 1 * (
                        Long.bitCount(guardedWhite)
                                - Long.bitCount(guardedBlack))

                        // Mitte nur leicht belohnen, hier nur bei gedeckten Figuren.
                        + 0.5 * (
                        Long.bitCount(centerPawnsWhite & guardedWhite)
                                - Long.bitCount(centerPawnsBlack & guardedBlack));



        return white ? score : -score;
    }
    }

















































    public static double getHistoryScore(long move, boolean white) {
        if (white) {
            return whiteHistory.getOrDefault(move, 0.0);
        } else {
            return blackHistory.getOrDefault(move, 0.0);
        }
    }

    public static void addHistoryScore(long move, double score, boolean white) {
        double oldScore = getHistoryScore(move, white);

        double newScore = oldScore * 0.95 + score * 0.05;

        if (white) {
            whiteHistory.put(move, newScore);
        } else {
            blackHistory.put(move, newScore);
        }
    }
}

class Field{
    public static final long NOT_A_FILE = 0xFEFEFEFEFEFEFEFEL;
    public static final long NOT_H_FILE = 0x7F7F7F7F7F7F7F7FL;

    public static final long MASKE_MITTE_WHITE = 0x18181818L;
    public static final long MASKE_MITTE_BLACK = 0x1818181800000000L;

    public static final long MASKE_WHITE_SQUARES = 0x55aa55aa55aa55aaL;
    public static final long MASKE_BLACK_SQUARES = ~MASKE_WHITE_SQUARES;

    public static final long MASKE_UNTERE_HÄLFTE = 0xffffffffL;
    public static final long MASKE_OBERE_HÄLFTE = 0xffffffff00000000L;
    public static final long MASKE_OBERE_REIHE = 0xFF00000000000000L;
    public static final long MASKE_UNTERE_REIHE = 0xFFL;
    public static final long MASKE_DEFAULT_WHITE_PAWNS = 0xffffL;
    public static final long MASKE_DEFAULT_BLACK_PAWNS  = 0xffff000000000000L;

    public long whitePawnMask;
    public long blackPawnMask;

    public Field (long whitePawnMask, long blackPawnMask){
        this.whitePawnMask =whitePawnMask;
        this.blackPawnMask = blackPawnMask;
    }
    public Field (){
        whitePawnMask = MASKE_DEFAULT_WHITE_PAWNS;
        blackPawnMask = MASKE_DEFAULT_BLACK_PAWNS;
    }
    public Field copy(){
        return new Field(whitePawnMask, blackPawnMask);
    }
    public void printField(){
        long combined = whitePawnMask | blackPawnMask;
        printMask(combined);
    }
    public static void printMask(long mask){
        String binaryStr = String.format("%64s", Long.toBinaryString(mask)).replace(' ', '0');
        for (int i = 0; i < 64; i += 8) {
            String row = binaryStr.substring(i, i + 8);

            // Die 8 Zeichen der Reihe spiegeln (umdrehen)
            String mirroredRow = new StringBuilder(row).reverse().toString();

            System.err.println(mirroredRow);
        }
        System.err.println(" ");
    }
    public long getPawns(boolean white){
        if (white){
            return whitePawnMask;
        }
        return blackPawnMask;
    }

    public void move(long pawnToMove, long moveMask, boolean white){
        if (white){
            whitePawnMask &= ~pawnToMove;
            whitePawnMask |= moveMask;

            blackPawnMask &= ~moveMask;
        }
        else{
            blackPawnMask &= ~pawnToMove;
            blackPawnMask |= moveMask;
            whitePawnMask &= ~moveMask;
        }
    }
    public void move(Position fromPos, Position toPos, boolean white){
        long pawnToMove = fromPos.getPosMask();
        long moveMask = toPos.getPosMask();
        if (white){
            whitePawnMask &= ~pawnToMove;
            whitePawnMask |= moveMask;

            blackPawnMask &= ~moveMask;
        }
        else{
            blackPawnMask &= ~pawnToMove;
            blackPawnMask |= moveMask;
            whitePawnMask &= ~moveMask;
        }

    }
    public boolean isOver() {
        return (whitePawnMask & MASKE_OBERE_REIHE) != 0
                || (blackPawnMask & MASKE_UNTERE_REIHE) != 0
                || whitePawnMask == 0
                || blackPawnMask == 0;
    }
    //nur nach einem isOver() check verwenden
    public boolean getWinnner() {
        return (whitePawnMask & MASKE_OBERE_REIHE) != 0
                || blackPawnMask == 0;
    }
}
class Position{
    static final String alphabet = "abcdefgh";
    int pos;
    int y;
    public Position(int pos){
        this.pos = pos;
    }
    public String getChessPos(){
        return ""+alphabet.charAt(pos %8) + (pos/8 +1);
    }
    public static Position FROM (String move){
        int fromX = alphabet.indexOf(move.charAt(0));
        int fromY = move.charAt(1) - '0';;
        int finalPos = fromX + 8*(fromY -1);
        return new Position(finalPos);
    }
    public long getPosMask(){
        return 1L << pos;
    }
}
class Game {
    public static final long NOT_A_FILE = 0xFEFEFEFEFEFEFEFEL;
    public static final long NOT_H_FILE = 0x7F7F7F7F7F7F7F7FL;
    public static long getPossibleMoves(long pawns, long otherPawns, boolean white){
        long mask;
        long pawnMaskFront;
        if (white){
            mask = (pawns << (7) & NOT_H_FILE) | (pawns << (9) & NOT_A_FILE);
            pawnMaskFront = pawns << (8);
        }
        else{
            mask = (pawns >>> (7) & NOT_A_FILE) | (pawns >>> (9) & NOT_H_FILE);
            pawnMaskFront = pawns >>> (8);
        }
        if ((pawnMaskFront & otherPawns) == 0){
            mask |= pawnMaskFront;
        }
        mask &= ~pawns;
        return mask;
    }

    public static List<Integer> getIndexes(long maske){
        List<Integer> positions = new ArrayList<>();

        long temporaereMaske = maske;
        while (temporaereMaske != 0) {
            int index = Long.numberOfTrailingZeros(temporaereMaske);

            positions.add(index);

            temporaereMaske &= (temporaereMaske - 1);
        }
        return positions;
    }
    public static List<Long> getPossiblePawns(long pawns, long field, long move, boolean white){
        if (white){
            return getPossibleWhitePawns(pawns, field, move);
        }
        else{
            return getPossibleBlackPawns(pawns, field, move);
        }
    }
    public static List<Long> getPossibleWhitePawns(long pawns, long field, long move){
        List<Long> possiblePawnMask = new ArrayList<>();
        long moveMask = move;
        //System.err.println("moveMask:");
        //Field.printMask(moveMask);
        long pawnMask1 = moveMask >>> (7) & NOT_A_FILE;
        //Field.printMask(pawnMask1);
        pawnMask1 &= pawns;
        //Field.printMask(pawnMask1);

        long pawnMask2 = moveMask >>> (9) & NOT_H_FILE;
        //Field.printMask(pawnMask2);
        pawnMask2 &= pawns;
        //Field.printMask(pawnMask2);

        long pawnMask3 = moveMask >>> (8);
        //Field.printMask(pawnMask3);
        pawnMask3 &= pawns;

        if (pawnMask1 != 0){
            possiblePawnMask.add(pawnMask1);
        }
        if (pawnMask2 != 0){
            possiblePawnMask.add(pawnMask2);
        }
        if (pawnMask3 != 0 && (moveMask & field) == 0){
            possiblePawnMask.add(pawnMask3);
        }
        return possiblePawnMask;
    }
    public static List<Long> getPossibleBlackPawns(long pawns, long field, long move){
        List<Long> possiblePawnMask = new ArrayList<>();
        long moveMask = move;
        //System.err.println("moveMask:");
        long pawnMask1 = moveMask << (7) & NOT_H_FILE;
        //Field.printMask(pawnMask1);
        pawnMask1 &= pawns;
        //Field.printMask(pawnMask1);


        long pawnMask2 = moveMask << (9) & NOT_A_FILE;
        //Field.printMask(pawnMask2);
        pawnMask2 &= pawns;
        //Field.printMask(pawnMask2);


        long pawnMask3 = moveMask << (8);
        //Field.printMask(pawnMask3);
        //Field.printMask(pawnMask3);
        pawnMask3 &= pawns;

        if (pawnMask1 != 0){
            possiblePawnMask.add(pawnMask1);
        }
        if (pawnMask2 != 0){
            possiblePawnMask.add(pawnMask2);
        }
        if (pawnMask3 != 0 && (moveMask & field) == 0){
            possiblePawnMask.add(pawnMask3);
        }
        return possiblePawnMask;
    }
    public static double progressScore(long pawns, boolean white) {
        double score = 0;

        while (pawns != 0) {
            int square = Long.numberOfTrailingZeros(pawns);
            int rank = square / 8;

            int progress = white
                    ? Math.max(0, rank - 1)
                    : Math.max(0, 6 - rank);

            score += progress * progress;

            pawns &= pawns - 1;
        }

        return score;
    }
    public static List<Long> getPossibleMoveList(
            long pawns, long otherPawns, boolean white) {

        long diagonal;
        long forward;

        if (white) {
            diagonal = ((pawns << 7) & NOT_H_FILE) | ((pawns << 9) & NOT_A_FILE);
            forward = pawns << 8;
        }
        else {
            diagonal = ((pawns >>> 7) & NOT_A_FILE) | ((pawns >>> 9) & NOT_H_FILE);
            forward = pawns >>> 8;
        }

        // Diagonal: keine eigenen Figuren auf dem Zielfeld.
        diagonal &= ~pawns;

        // Geradeaus: Zielfeld muss vollständig leer sein.
        forward &= ~(pawns | otherPawns);

        long targets = diagonal | forward;

        List<Long> possibleMoves = new ArrayList<>();

        while (targets != 0) {
            long singleTarget = Long.lowestOneBit(targets);
            possibleMoves.add(singleTarget);
            targets &= targets - 1;
        }
        return possibleMoves;
    }
    public static double EVAL(Field field, boolean white) {
        long wp = field.whitePawnMask;
        long bp = field.blackPawnMask;

        // Endstellungen haben Vorrang vor Positionsmerkmalen.
        if (field.isOver()) {
            return field.getWinnner() == white
                    ? 1_000_000.0
                    : -1_000_000.0;
        }

        long controlledWhite = ((wp << 7) & Game.NOT_H_FILE) | ((wp << 9) & Game.NOT_A_FILE);

        long controlledBlack = ((bp >>> 7) & Game.NOT_A_FILE) | ((bp >>> 9) & Game.NOT_H_FILE);

        long guardedWhite = controlledWhite & wp;
        long guardedBlack = controlledBlack & bp;

        long guardedChainWhite =  ((guardedWhite << 7) & Game.NOT_H_FILE) | ((guardedWhite << 9) & Game.NOT_A_FILE);
        long guardedChainBlack =  ((guardedBlack >>> 7) & Game.NOT_A_FILE) | ((guardedBlack >>> 9) & Game.NOT_H_FILE);

        guardedChainWhite &= wp;
        guardedChainBlack &= bp;

        long controlledWhiteSquaresWhite = Field.MASKE_WHITE_SQUARES & controlledWhite;
        long controlledWhiteSquaresBlack = Field.MASKE_WHITE_SQUARES & controlledBlack;

        long controlledBlackSquaresWhite = Field.MASKE_BLACK_SQUARES & controlledWhite;
        long controlledBlackSquaresBlack = Field.MASKE_BLACK_SQUARES & controlledBlack;




        //long saveWhite = guardedWhite | (wp & ~controlledBlack);
        //long saveBlack = guardedBlack | (bp & ~controlledWhite);

        //long guardedFinalWhite =
        //   guardedWhite & Field.MASKE_DEFAULT_BLACK_PAWNS;

        //long guardedFinalBlack =
        //    guardedBlack & Field.MASKE_DEFAULT_WHITE_PAWNS;

        long attackedUnguardedBlack = bp & ~guardedBlack & controlledWhite;

        long attackedUnguardedWhite = wp & ~guardedWhite & controlledBlack;

        //long advancedPawnsWhite = Field.MASKE_OBERE_HÄLFTE & wp;
        //long advancedPawnsBlack = Field.MASKE_UNTERE_HÄLFTE & bp;

        //long doublePawnsWhite = wp & (wp << 8);
        //long doublePawnsBlack = bp & (bp >>> 8);

        long centerPawnsWhite = wp & Field.MASKE_MITTE_WHITE;
        long centerPawnsBlack = bp & Field.MASKE_MITTE_BLACK;

        //long backrowWhite = wp & Field.MASKE_UNTERE_REIHE;
        //long backrowBlack = bp & Field.MASKE_OBERE_REIHE;
        // Zunächst alles aus weißer Perspektive bewerten.
        double score =
                10* (Long.bitCount(wp) - Long.bitCount(bp))
                        //+ 0*(Long.bitCount(backrowWhite) - Long.bitCount(backrowBlack))

                        //+ 0.5 * Math.abs(Long.bitCount(controlledWhiteSquaresWhite) - Long.bitCount(controlledBlackSquaresWhite))
                        //- 0.5 * Math.abs(Long.bitCount(controlledWhiteSquaresBlack) - Long.bitCount(controlledBlackSquaresBlack))

                        + 1 * (Long.bitCount(guardedChainWhite) -Long.bitCount(guardedChainBlack))

                        + 2* (
                        Long.bitCount(attackedUnguardedBlack)
                                - Long.bitCount(attackedUnguardedWhite)
                )

                        + 1 * (
                        Long.bitCount(guardedWhite)
                                - Long.bitCount(guardedBlack)
                )

                        // Mitte nur leicht belohnen, hier nur bei gedeckten Figuren.
                        + 0.5 * (
                        Long.bitCount(centerPawnsWhite & guardedWhite)
                                - Long.bitCount(centerPawnsBlack & guardedBlack))
                        + 0.6 * (
                        progressScore(wp, true)
                                - progressScore(bp, false)

                );



        return white ? score : -score;
    }

}
class Node{
    public static final Random random = new Random();
    public int visits;
    double eval;
    Node parent;
    boolean white;
    List<Node> children = new ArrayList<>();
    Field field;
    long [] prevMove;
    public Node(Node parent, boolean white, Field field, long [] prevMove){
        this.parent = parent;
        this.white = white;
        this.field = field;
        this.prevMove = prevMove;
    }
    public void expand(){
        List <Long> possibleMoves = Game.getPossibleMoveList(field.getPawns(white), field.getPawns(!white), white);
        List <long []> possibleActions = new ArrayList<>();
        for (int i = 0; i < possibleMoves.size(); i++){
            long move = possibleMoves.get(i);
            List<Long> possiblePawns = Game.getPossiblePawns(field.getPawns(white), field.getPawns(!white), move, white);
            for (long pawn : possiblePawns){
                long [] action = new long [2];
                action [0] = pawn;
                action [1] = move;
                possibleActions.add(action);
            }
        }
        for (long [] action : possibleActions){
            Field newField = field.copy();
            newField.move(action[0], action[1], white);
            long [] simAction = {action[0], action[1]};
            children.add(new Node(this, !white, newField, simAction));
        }
    }
    public Node select(){
        if (children.isEmpty()){
            return this;
        }
        Node bestChild = getBestChild();
        return bestChild.select();
    }
    public Node getBestChild(){
        double bestUct = Double.NEGATIVE_INFINITY;;
        Node bestChild = null;
        for (Node child : children){
            double uct = child.getUct();
            if (uct > bestUct){
                bestUct = uct;
                bestChild = child;
            }
        }
        return bestChild;
    }
    public double rollout() {
        Field simField = field.copy();
        boolean simWhite = white;

        int depth = 25;

        for (int i = 0; i < depth && !simField.isOver(); i++) {
            if (hasImmediateWin(simField, simWhite)) {
                return simWhite ? 1.0 : -1.0;
            }
            long[] action = getRandomMove(simWhite, simField);

            if (action == null) {
                throw new IllegalStateException(
                        "Keine legalen Züge trotz laufendem Spiel"
                );
            }

            simField.move(action[0], action[1], simWhite);
            simWhite = !simWhite;
        }

        // Immer aus weißer Sicht!
        if (simField.isOver()) {
            return simField.getWinnner() ? 1.0 : -1.0;
        }
        if (hasImmediateWin(simField, simWhite)) {
            return simWhite ? 1.0 : -1.0;
        }

        // 10.0 ist ein abstimmbarer Skalierungswert.
        return Math.tanh(Game.EVAL(simField, true) / 50);
    }
    public void backpropagateResult(double whiteScore) {
        // Bewertung aus Sicht des Spielers, der zuletzt gezogen hat
        double score = white ? -whiteScore : whiteScore;

        // Der vorherige Zug stammt von der anderen Farbe
        if (parent != null) {
            Player.addHistoryScore(
                    prevMove[0] | prevMove[1],
                    score,
                    !white
            );
        }

        this.eval += score;
        visits++;

        if (parent != null) {
            parent.backpropagateResult(whiteScore);
        }
    }
    public double getUct() {
        double hValue = Player.getHistoryScore(prevMove[0] | prevMove[1], !white);

        double historyBonus = 0.2 * hValue / (visits + 1);
        if (visits == 0) {
            return Double.POSITIVE_INFINITY;
        }

        return (double) eval / visits + 0.5 * Math.sqrt(2 * Math.log(parent.visits) / visits) + historyBonus;
    }
    public long[] getRandomMove(boolean white, Field simField) {
        long own = simField.getPawns(white);
        long enemy = simField.getPawns(!white);

        List<Long> moves = Game.getPossibleMoveList(
                own, enemy, white
        );

        if (moves.isEmpty()) {
            return null;
        }

        long goalRow = white
                ? Field.MASKE_OBERE_REIHE
                : Field.MASKE_UNTERE_REIHE;

        long nearGoalRow = white
                ? 0x00FF000000000000L
                : 0x000000000000FF00L;

        // Vom Gegner kontrollierte Felder
        long enemyAttacks = white
                ? ((enemy >>> 7) & Game.NOT_A_FILE)
                | ((enemy >>> 9) & Game.NOT_H_FILE)
                : ((enemy << 7) & Game.NOT_H_FILE)
                | ((enemy << 9) & Game.NOT_A_FILE);

        List<Long> winningMoves = new ArrayList<>();
        List<Long> threats = new ArrayList<>();
        List<Long> captures = new ArrayList<>();

        boolean lastEnemy = Long.bitCount(enemy) == 1;

        for (long target : moves) {

            boolean capture = (target & enemy) != 0;

            // Sofortiger Gewinn
            if ((target & goalRow) != 0 ||
                    (lastEnemy && capture)) {
                winningMoves.add(target);
                continue;
            }

            // Sicherer Vorstoß auf die vorletzte Reihe
            if ((target & nearGoalRow) != 0 &&
                    (target & enemyAttacks) == 0) {
                threats.add(target);
            }

            // Schlagzug
            if (capture) {
                captures.add(target);
            }
        }

        List<Long> selectedMoves;

        if (!winningMoves.isEmpty()) {
            selectedMoves = winningMoves;
        }
        else if (!threats.isEmpty() &&
                random.nextDouble() < 0.8) {
            selectedMoves = threats;
        }
        else if (!captures.isEmpty() &&
                random.nextDouble() < 0.8) {
            selectedMoves = captures;
        }
        else {
            selectedMoves = moves;
        }

        long target = selectedMoves.get(
                random.nextInt(selectedMoves.size())
        );

        List<Long> pawns = Game.getPossiblePawns(
                own, enemy, target, white
        );

        long pawn = pawns.get(
                random.nextInt(pawns.size())
        );

        return new long[]{pawn, target};
    }
    public boolean hasImmediateWin(Field position, boolean white) {
        if (position.isOver()) {
            return position.getWinnner() == white;
        }

        long goalSide = white ? Field.MASKE_OBERE_REIHE : Field.MASKE_UNTERE_REIHE;

        long pawns = position.getPawns(white);
        long enemy = position.getPawns(!white);

        long controlled = white ? ((pawns << 7) & Game.NOT_H_FILE) | ((pawns << 9) & Game.NOT_A_FILE)
                : ((pawns >>> 7) & Game.NOT_A_FILE) | ((pawns >>> 9) & Game.NOT_H_FILE);

        return (controlled & goalSide) != 0 || (Long.bitCount(enemy) == 1 && (controlled & enemy) != 0);
    }
    public long [] chooseMove(boolean firstMove){
        // 1. Startzeit in Nanosekunden erfassen
        long startTime = System.nanoTime();

        // 2. Limit festlegen (100 Millisekunden * 1.000.000 = 100.000.000 ns)
        long durationLimit = 80_000_000L;
        if (firstMove){
            durationLimit = 900_000_000L;
        }

        if (children.isEmpty()) {
            expand();
        }

        // Sofortigen eigenen Sieg zuerst nehmen.
        for (Node child : children) {
            if (child.field.isOver() && child.field.getWinnner() == white) {
                return child.prevMove;
            }
        }
        List<Node> safeChildren = new ArrayList<>();

        for (Node child : children) {
            // Nach meinem Zug ist der Gegner dran.
            if (!hasImmediateWin(child.field, !white)) {
                safeChildren.add(child);
            }
        }

        // Nur filtern, wenn es überhaupt eine sichere Alternative gibt.
        if (!safeChildren.isEmpty()) {
            children = safeChildren;
        }
        // 3. Schleife läuft, solange die vergangene Zeit kleiner als das Limit ist
        while (System.nanoTime() - startTime < durationLimit) {
            Node selectedNode = select();

            if (!selectedNode.field.isOver()) {
                selectedNode.expand();

                if (!selectedNode.children.isEmpty()) {
                    selectedNode = selectedNode.children.get( random.nextInt(selectedNode.children.size()));
                }
            }
            double result = selectedNode.rollout();
            selectedNode.backpropagateResult(result);
        }
        System.err.println(visits);
        Node bestChild = null;

        for (Node child : children) {
            if (bestChild == null || child.visits > bestChild.visits) {
                bestChild = child;
            }
        }
        return bestChild.prevMove;
    }
}