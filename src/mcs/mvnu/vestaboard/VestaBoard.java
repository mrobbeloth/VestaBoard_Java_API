
package mcs.mvnu.vestaboard;

import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class that serves as an abstraction for a VestaBoard in Java
 * @version 0.1
 * @since 11/15.2022
 * @author Michael Robbeloth
 */
public class VestaBoard {
    /**
     * Return whether VestaBoard object is representing a white on black Vestaboard or a black on white Vestaboard.
     * Default is white on black Vestaboard
     * @return
     */
    public BoardType getVestaBoardType() {
        return bt;
    }

    /**
     * Configure whether Vestaboard object is a white on black Vestaboard or a black on white Vestaboard
     * Default is white on black Vestaboard
     * @param bt
     */
    public void setVestaBoardType(BoardType bt) {
        this.bt = bt;
    }

    /* VestaBoardcii
    *  @see https://docs.vestaboard.com/characters */
    public enum VestaChars{
        Blank(0),A(1), B(2), C(3), D(4), E(5),
        F(6), G(7), H(8), I(9), J(10), K(11), L(12), M(13),
        N(14), O(15), P(16), Q(17), R(18), S(19), T(20), U(21),
        V(22), W(23), X(24), Y(25), Z(26), One(27), Two(28),
        Three(29), Four(30), Five(31), Six(32), Seven(33), Eight(34),
        Nine(35), Zero(36), ExaminationMark(37), AtSign(38), PoundSign(39),
        DollarSign(40), LeftParen(41), RightParen(42), Hyphen(44), PlusSign(45),
        Ampersand(47), EqualsSign(48), Semicolon(49), Colon(50), SingleQuote(52),
        DoubleQuote(53), PercentSign(54), Comma(55), Period(56), Slash(59),
        QuestionMark(60), DegreeSign(62), PoppyRed(63), Orange(64), Yellow(65),
        Green(66), ParisBlue(67), Violet(68), White(69), Black( 70), Filled (71);

        private final int charValue; // Internal Vestaboard encoding for the character

        /**
         * Associate the enumeration value with a variable that can be fetched
         * @param i -- the value to be fetched later on
         */
        VestaChars(int i) {
            charValue = i;
        }

        /**
         * Retrieve the enumeration value for the Vestaboard character. Do not use
         * the ordinal method of the enumeration, the values will not match up.
         * @return the enumerated value for the Vestaboard character
         */
        public int getCharValue() {return charValue;}
    }

    public enum BoardType {
        WHITE_ON_BLACK_VESTABOARD(1), BLACK_ON_WHITE_VESTABOARD(2);

        private final int charValue;

        BoardType(int i) {
            charValue = i;
        }

        public int getCharValue() {return charValue;}
    }

    private final int ROWS=6;                   // number of rows in Vestaboard
    private final int COLS=22;                  // number of cols in Vestaboard
    private final int RETRIES=5;                // number of attempts to send board to VestaBoard

    private boolean truncate = false;
    private boolean wrap = true;
    private final String api_key;               // Key to allow use of Vestaboard Installable APIs
    private final String api_secret;
    private final String api_rw_key;            // Key to use for direct reading/writing of Vestaboard (max 1 per board)

    private final String content_type = "application/json"; // new edition 4-2003

    private final HttpClient httpClient;              // Used to communicate with VestaBoard APIs

    private final VestaChars[][] board;               // Data Structure Representation of VestaBoard

    public static final char UNICODE_UTF16_RED = '\uDFE5';          // {63} in VestaBoard char codes
    public static final char UNICODE_UTF16_BLUE = '\uDFE6';         // {67} in VestaBoard char codes
    public static final char UNICODE_UTF16_ORANGE = '\uDFE7';       // {64} in VestaBoard char codes
    public static final char UNICODE_UTF16_YELLOW = '\uDFE8';       // {65} in VestaBoard char codes
    public static final char UNICODE_UTF16_GREEN = '\uDFE9';        // {66} in VestaBoard char codes
    public static final char UNICODE_UTF16_VIOLET = '\uDFEA';       // {68} in VestaBoard char codes
    public static final char UNICODE_UTF16_WHITE = '\u25A1';        // {69} in VestaBoard char codes
    public static final char UNICODE_UTF16_BLACK = '\u2B24';        // {70} in VestaBoard char codes

    // BoardType
    private BoardType bt;       // Board type is white lettering on black or black lettering on white

    /**
     * Create a Vestaboard object
     * Base constructor, will pass credentials-virtual.ini as config file to constructor taking a string filename
     * @throws ConfigurationException Any exception that occurs while initializing a Configuration object.
     * @throws IOException Signals that an I/O exception has occurred
     */
    public VestaBoard() throws ConfigurationException, IOException {
        this("credentials-virtual.ini");
    }

    /**
     * Create a Vestaboard virtual object
     * @param f the full path and filename of credentials of the file to open
     */
    public VestaBoard(File f) throws ConfigurationException, IOException {
        this(f.getAbsolutePath());
    }

    /**
     * Create a Vestaboard virtual object
     * @param filename credentials filename
     * @throws IOException Signals that an I/O exception has occurred
     * @throws ConfigurationException Any exception that occurs while initializing a Configuration object.
     */
    public VestaBoard(String filename) throws IOException, ConfigurationException {
        // Init virtual rep of Vestaboard
        board = new VestaChars[ROWS][COLS];
        for(int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                board[i][j] = VestaChars.Blank;
            }
        }
        httpClient = HttpClient.newHttpClient();

        // Read in ini file
        INIConfiguration iniConfiguration = new INIConfiguration();
        try (FileReader fileReader = new FileReader(filename)) {
            iniConfiguration.read(fileReader);

            // Read out credentials from ini file
            api_key = iniConfiguration.getString("X-Vestaboard-Api-Key");
            api_secret = iniConfiguration.getString("X-Vestaboard-Api-Secret");
            api_rw_key = iniConfiguration.getString("X-Vestaboard-Read-Write-Key");
        }

        // Set board type, by default use more popular white on black vestaboard
        bt = BoardType.WHITE_ON_BLACK_VESTABOARD;
    }

    /**
     * Convert the Internal VestaBoard representation into a string
     * for the HttpRequest Body Publisher string method
     *
     * For example, with curl:
     * curl -X POST -H "X-Vestaboard-Read-Write-Key: my-key" -d
     * '[[0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
     * [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
     * [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
     * [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
     * [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0],
     * [0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,1]]'
     * https://rw.vestaboard.com/
     *
     * Prints an A in the last location
     *
     * The string values in each cell must be the Vesta character encoding
     * and not the ASCII encoding
     *
     * @see <a href="https://docs.vestaboard.com/read-write">...</a>
     * @see <a href="https://docs.vestaboard.com/characters">...</a>
     * @return curl data compatible string
     */
    private String convertBoardToString() {
        StringBuilder msgToSend = new StringBuilder();
        msgToSend.append("[");

        // Collapse 2D array into one long string
        for (int i = 0; i < ROWS; i++) {
            msgToSend.append("[");
            for (int j = 0; j < COLS; j++) {
                // Need Vesta Character Encoding, not ASCII/Unicode encoding
                msgToSend.append(board[i][j].getCharValue());
                if (j < COLS-1) {
                    msgToSend.append(",");
                }
            }
            if (i < ROWS-1) {
                msgToSend.append("],");
            }
            else {
                msgToSend.append("]");
            }
        }
        msgToSend.append("]");
        return msgToSend.toString();
    }

    /**
     * Wipe the internal virtual vestaboard object
     * NOTE: Does not send to the web to avoid wearing out mechanical tiles.
     * @throws IOException
     * @throws InterruptedException
     */
    public void wipeBoard () throws IOException, InterruptedException {
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                board[i][j] = VestaChars.Blank;
            }
        }
    }

    /**
     * Sends virtual vestaboard to company's app or real board based on configuration
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    private int sendOverTheWeb() throws IOException, InterruptedException {
        /* Prepare Request-Response HTTP Post message to write to the VestaBoard
         *  Be mindful if writing to virtual or real board */
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().header("X-Vestaboard-Read-Write-Key", api_rw_key).
                header("Content-Type", content_type).
                uri(URI.create("https://rw.vestaboard.com")).POST(
                        HttpRequest.BodyPublishers.ofString(convertBoardToString())).build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        // don't try to send the same message again
        if (response.statusCode() == 304) {
            System.err.println("304: Trying to send the same message again");
            return response.statusCode();
        }

        for (int i = 0; ((response.statusCode() != 200) && (i < RETRIES)); i++) {
            Thread.sleep(5000);
            response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());
        }

        System.out.println("Response code:"+response.statusCode());
        System.out.println(response.body());

        return response.statusCode();
    }

    /**
     * Convert String message into VestaBoard message
     * So far, no support for color tiles
     * @param msg Input message to write to board
     * @throws IOException Signals that an I/O exception has occurred
     * @throws InterruptedException Thread is interrupted
     * @return status code of message post to board
     * @see  <a href="https://www.compart.com/en/unicode/">. Unicode Docs..</a>
     */
    public int postMessage(String msg) throws IOException, InterruptedException {
        // Wipe board
        wipeBoard();

        int len = msg.length();
        int row = 0;
        int col = 0;
        for (int i = 0; i < len; i++, col++) {
            char c = msg.charAt(i);
            if (col == (COLS-1)) {
                row++;
                col = 0;
            }
            switch(c) {
                case 'A', 'a': board[row][col] = VestaChars.A; break;
                case 'B', 'b': board[row][col] = VestaChars.B; break;
                case 'C', 'c': board[row][col] = VestaChars.C; break;
                case 'D', 'd': board[row][col] = VestaChars.D; break;
                case 'E', 'e': board[row][col] = VestaChars.E; break;
                case 'F', 'f': board[row][col] = VestaChars.F; break;
                case 'G', 'g': board[row][col] = VestaChars.G; break;
                case 'H', 'h': board[row][col] = VestaChars.H; break;
                case 'I', 'i', '|': board[row][col] = VestaChars.I; break;
                case 'J', 'j': board[row][col] = VestaChars.J; break;
                case 'K', 'k': board[row][col] = VestaChars.K; break;
                case 'L', 'l': board[row][col] = VestaChars.L; break;
                case 'M', 'm': board[row][col] = VestaChars.M; break;
                case 'N', 'n': board[row][col] = VestaChars.N; break;
                case 'O', 'o': board[row][col] = VestaChars.O; break;
                case 'P', 'p': board[row][col] = VestaChars.P; break;
                case 'Q', 'q': board[row][col] = VestaChars.Q; break;
                case 'R', 'r': board[row][col] = VestaChars.R; break;
                case 'S', 's': board[row][col] = VestaChars.S; break;
                case 'T', 't': board[row][col] = VestaChars.T; break;
                case 'U', 'u': board[row][col] = VestaChars.U; break;
                case 'V', 'v': board[row][col] = VestaChars.V; break;
                case 'W', 'w': board[row][col] = VestaChars.W; break;
                case 'X', 'x': board[row][col] = VestaChars.X; break;
                case 'Y', 'y': board[row][col] = VestaChars.Y; break;
                case 'Z', 'z': board[row][col] = VestaChars.Z; break;
                case '1' : board[row][col] = VestaChars.One; break;
                case '2' : board[row][col] = VestaChars.Two; break;
                case '3' : board[row][col] = VestaChars.Three; break;
                case '4' : board[row][col] = VestaChars.Four; break;
                case '5' : board[row][col] = VestaChars.Five; break;
                case '6' : board[row][col] = VestaChars.Six; break;
                case '7' : board[row][col] = VestaChars.Seven; break;
                case '8' : board[row][col] = VestaChars.Eight; break;
                case '9' : board[row][col] = VestaChars.Nine; break;
                case '0' : board[row][col] = VestaChars.Zero; break;
                case '!' : board[row][col] = VestaChars.ExaminationMark; break;
                case '@' : board[row][col] = VestaChars.AtSign; break;
                case '#' : board[row][col] = VestaChars.PoundSign; break;
                case '$' : board[row][col] = VestaChars.DollarSign; break;
                case '(' : board[row][col] = VestaChars.LeftParen; break;
                case ')' : board[row][col] = VestaChars.RightParen; break;
                case '-' : board[row][col] = VestaChars.Hyphen; break;
                case '+' : board[row][col] = VestaChars.PlusSign; break;
                case '&' : board[row][col] = VestaChars.Ampersand; break;
                case '=' : board[row][col] = VestaChars.EqualsSign; break;
                case ';' : board[row][col] = VestaChars.Semicolon; break;
                case ':' : board[row][col] = VestaChars.Colon; break;
                case '\'' : board[row][col] = VestaChars.SingleQuote; break;
                case '\"' : board[row][col] = VestaChars.DoubleQuote; break;
                case '%' : board[row][col] = VestaChars.PercentSign; break;
                case ',' : board[row][col] = VestaChars.Comma; break;
                case '.' : board[row][col] = VestaChars.Period; break;
                case '/' : board[row][col] = VestaChars.Slash; break;
                case '?' : board[row][col] = VestaChars.QuestionMark; break;
                case '°', '*' : board[row][col] = VestaChars.DegreeSign; break;
                case UNICODE_UTF16_RED: board[row][col] = VestaChars.PoppyRed; break;
                case UNICODE_UTF16_BLUE: board[row][col] = VestaChars.ParisBlue; break;
                case UNICODE_UTF16_ORANGE: board[row][col] = VestaChars.Orange; break;
                case UNICODE_UTF16_YELLOW: board[row][col] = VestaChars.Yellow; break;
                case UNICODE_UTF16_GREEN: board[row][col] = VestaChars.Green; break;
                case UNICODE_UTF16_VIOLET: board[row][col] = VestaChars.Violet; break;
                case UNICODE_UTF16_WHITE: board[row][col] = VestaChars.White; break;
                case UNICODE_UTF16_BLACK: board[row][col] = VestaChars.Black; break;
                case '\n': col = 0; row++; break;
                default:
                    board[row][col] = VestaChars.Blank;
            }

        }

        /* Prepare Request-Response HTTP Post message to write to the VestaBoard
        *  Be mindful if writing to virtual or real board */
        return sendOverTheWeb();
    }

    /**
     * Read the last message written to the VestaBoard
     * @return the last message written to the board
     */
    public String readMessage() throws IOException, InterruptedException {
        /* Prepare Request-Response HTTP Post message to write to the VestaBoard
         *  Be mindful if writing to virtual or real board */
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().header("X-Vestaboard-Read-Write-Key", api_rw_key).
                uri(URI.create("https://rw.vestaboard.com")).GET().build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        System.out.println("Response code:"+response.statusCode());
        System.out.println(response.body());
        JSONObject jsonObj = new JSONObject(response.body());
        JSONObject jsonObjCM = jsonObj.getJSONObject("currentMessage");
        String layoutArray = jsonObjCM.getString("layout");

        char[] layoutArrayAsCharArr = layoutArray.toCharArray();
        StringBuilder convertedString = new StringBuilder();
        layoutArray = layoutArray.substring(1,layoutArray.length()-1);
        final Pattern pattern = Pattern.compile("([0-9]+(,[0-9]+)+)");
        final Matcher matcher = pattern.matcher(layoutArray);
        List<String> allMatches = new ArrayList<String>();
        while (matcher.find()) {
            allMatches.add(matcher.group());
         }
        List<String> elements = new ArrayList<String>();
        for(String aMatch : allMatches) {
            String[] elementsInARow = aMatch.split(",");
            for(String e : elementsInARow) {
                elements.add(e);
            }
        }

        for(int i = 0; i < elements.size(); i++) {
            int value = Integer.valueOf(elements.get(i));
            if (value == '[' || value == ']')
                continue;
            else if (value == VestaChars.PoppyRed.getCharValue())
                convertedString.append(UNICODE_UTF16_RED);
            else if (value == VestaChars.Yellow.getCharValue())
                convertedString.append(UNICODE_UTF16_YELLOW);
            else if (value == VestaChars.ParisBlue.getCharValue())
                convertedString.append(UNICODE_UTF16_BLUE);
            else if (value == VestaChars.White.getCharValue())
                convertedString.append(UNICODE_UTF16_WHITE);
            else if (value == VestaChars.Green.getCharValue())
                convertedString.append(UNICODE_UTF16_GREEN);
            else if (value == VestaChars.Orange.getCharValue())
                convertedString.append(UNICODE_UTF16_ORANGE);
            else if (value == VestaChars.Violet.getCharValue())
                convertedString.append(UNICODE_UTF16_VIOLET);
            else if (value == VestaChars.A.getCharValue())
                convertedString.append('A');
            else if (value == VestaChars.B.getCharValue())
                convertedString.append('B');
            else if (value == VestaChars.C.getCharValue())
                convertedString.append('C');
            else if (value == VestaChars.D.getCharValue())
                convertedString.append('D');
            else if (value == VestaChars.E.getCharValue())
                convertedString.append('E');
            else if (value == VestaChars.F.getCharValue())
                convertedString.append('F');
            else if (value == VestaChars.G.getCharValue())
                convertedString.append('G');
            else if (value == VestaChars.H.getCharValue())
                convertedString.append('H');
            else if (value == VestaChars.I.getCharValue())
                convertedString.append('I');
            else if (value == VestaChars.J.getCharValue())
                convertedString.append('J');
            else if (value == VestaChars.K.getCharValue())
                convertedString.append('K');
            else if (value == VestaChars.J.getCharValue())
                convertedString.append('J');
            else if (value == VestaChars.L.getCharValue())
                convertedString.append('L');
            else if (value == VestaChars.M.getCharValue())
                convertedString.append('M');
            else if (value == VestaChars.N.getCharValue())
                convertedString.append('N');
            else if (value == VestaChars.O.getCharValue())
                convertedString.append('O');
            else if (value == VestaChars.P.getCharValue())
                convertedString.append('P');
            else if (value == VestaChars.Q.getCharValue())
                convertedString.append('Q');
            else if (value == VestaChars.R.getCharValue())
                convertedString.append('R');
            else if (value == VestaChars.S.getCharValue())
                convertedString.append('S');
            else if (value == VestaChars.T.getCharValue())
                convertedString.append('T');
            else if (value == VestaChars.U.getCharValue())
                convertedString.append('U');
            else if (value == VestaChars.V.getCharValue())
                convertedString.append('V');
            else if (value == VestaChars.W.getCharValue())
                convertedString.append('W');
            else if (value == VestaChars.X.getCharValue())
                convertedString.append('X');
            else if (value == VestaChars.Y.getCharValue())
                convertedString.append('Y');
            else if (value == VestaChars.Z.getCharValue())
                convertedString.append('Z');
            else if (value == VestaChars.One.getCharValue())
                convertedString.append('1');
            else if (value == VestaChars.Two.getCharValue())
                convertedString.append('2');
            else if (value == VestaChars.Three.getCharValue())
                convertedString.append('3');
            else if (value == VestaChars.Four.getCharValue())
                convertedString.append('4');
            else if (value == VestaChars.Five.getCharValue())
                convertedString.append('5');
            else if (value == VestaChars.Six.getCharValue())
                convertedString.append('6');
            else if (value == VestaChars.Seven.getCharValue())
                convertedString.append('7');
            else if (value == VestaChars.Eight.getCharValue())
                convertedString.append('8');
            else if (value == VestaChars.Nine.getCharValue())
                convertedString.append('9');
            else if (value == VestaChars.Zero.getCharValue())
                convertedString.append('0');
            else if (value == VestaChars.Blank.getCharValue())
                convertedString.append(' ');
            else if (value == VestaChars.Hyphen.getCharValue())
                convertedString.append('-');
            else if (value == VestaChars.ExaminationMark.getCharValue())
                convertedString.append('!');
            else if (value == VestaChars.AtSign.getCharValue())
                convertedString.append('@');
            else if (value == VestaChars.PoundSign.getCharValue())
                convertedString.append('#');
            else if (value == VestaChars.DollarSign.getCharValue())
                convertedString.append('$');
            else if (value == VestaChars.LeftParen.getCharValue())
                convertedString.append('(');
            else if (value == VestaChars.RightParen.getCharValue())
                convertedString.append(')');
            else if (value == VestaChars.PlusSign.getCharValue())
                convertedString.append('+');
            else if (value == VestaChars.DoubleQuote.getCharValue())
                convertedString.append('"');
            else if (value == VestaChars.Colon.getCharValue())
                convertedString.append(':');
            else if (value == VestaChars.SingleQuote.getCharValue())
                convertedString.append('\'');
            else if (value == VestaChars.Ampersand.getCharValue())
                convertedString.append('&');
            else if (value == VestaChars.EqualsSign.getCharValue())
                convertedString.append('=');
            else if (value == VestaChars.PercentSign.getCharValue())
                convertedString.append('%');
            else if (value == VestaChars.DegreeSign.getCharValue())
                convertedString.append('°');
            else if (value == VestaChars.QuestionMark.getCharValue())
                convertedString.append('?');
            else if (value == VestaChars.Slash.getCharValue())
                convertedString.append('\\');
            else if (value == VestaChars.Semicolon.getCharValue())
                convertedString.append(';');
            else if (value == VestaChars.Comma.getCharValue())
                convertedString.append(',');
            else if (value == VestaChars.Period.getCharValue())
                convertedString.append('.');
            else
                convertedString.append("");

            }

        System.out.println(convertedString);
        return convertedString.toString();
    }

    /**
     * Quick test method
     * @param args values from the command line or runtime configuration editor to pass to code in the method
     * @throws ConfigurationException Any exception that occurs while initializing a Configuration object.
     * @throws IOException Signals that an I/O Signals that an I/O exception has occurred
     * @throws InterruptedException Thread is interrupted
     */
    public static void main(String[] args) throws ConfigurationException, IOException, InterruptedException {
        VestaBoard v = new VestaBoard("credentials-virtual.ini");
        System.out.println(v.readMessage());
        v.postMessage("TEST2");
    }
}
