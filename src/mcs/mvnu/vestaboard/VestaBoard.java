
package mcs.mvnu.vestaboard;

import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.FileReader;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * A class that serves as an abstraction for a VestaBoard in Java
 * @version 0.1
 * @since 11/15.2022
 * @author Michael Robbeloth
 */
public class VestaBoard {

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
        Green(66), ParisBlue(67), Violet(68), White(69);

        VestaChars(int i) {
        }
    }

    private final int ROWS=6;                   // number of rows in Vestaboard
    private final int COLS=22;                  // number of cols in Vestaboard

    private boolean truncate = false;
    private boolean wrap = true;
    private final String api_key;
    private final String api_secret;
    private final String api_rw_key;

    private HttpClient httpClient;

    private VestaChars[][] board;

    VestaBoard() throws IOException, ConfigurationException {
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
        String filename = "credentials-virtual.ini";
        try (FileReader fileReader = new FileReader(filename)) {
            iniConfiguration.read(fileReader);

            // Read out credentials from ini file
            api_key = iniConfiguration.getString("X-Vestaboard-Api-Key");
            api_secret = iniConfiguration.getString("X-Vestaboard-Api-Secret");
            api_rw_key = iniConfiguration.getString("X-Vestaboard-Read-Write-Key");
        }
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
                msgToSend.append(board[i][j].ordinal());
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
     * Convert String message into VestaBoard message
     * So far, no support for color tiles
     * @param msg Input message to write to board
     * @throws IOException
     * @throws InterruptedException
     */
    public void postMessage(String msg) throws IOException, InterruptedException {
        int len = msg.length();
        int row = 0;
        int col = 0;
        for (int i = 0; i < len; i++, col++) {
            char c = msg.charAt(i);
            if (i % (COLS-1)==0) {
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
                case 'I', 'i': board[row][col] = VestaChars.I; break;
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
                case '°' : board[row][col] = VestaChars.DegreeSign; break;
                default:
                    board[row][col] = VestaChars.Blank;
            }

        }

        /* Prepare Request-Response HTTP Post message to write to the VestaBoard
        *  Be mindful if writing to virtual or real board */
        HttpClient client = HttpClient.newHttpClient();
         HttpRequest request = HttpRequest.newBuilder().header("X-Vestaboard-Read-Write-Key", api_rw_key).
                 uri(URI.create("https://rw.vestaboard.com")).POST(
                         HttpRequest.BodyPublishers.ofString(convertBoardToString())).build();
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString());

        System.out.println("Response code:"+response.statusCode());
        System.out.println(response.body());
    }

    /**
     * Quick test method
     * @param args
     * @throws ConfigurationException
     * @throws IOException
     * @throws InterruptedException
     */
    public static void main(String args[]) throws ConfigurationException, IOException, InterruptedException {
        VestaBoard v = new VestaBoard();
        v.postMessage("Hello Octavia");
    }
}
