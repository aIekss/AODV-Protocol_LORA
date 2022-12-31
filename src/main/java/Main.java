import java.util.*;

import model.ForwardRoute;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.uncommons.maths.binary.BitString;
import packets.RREP;
import packets.RREQ;
import utils.Converter;
import utils.MyArrayUtils;
import utils.Timer;

import static java.lang.System.*;
import static java.lang.System.out;
import static java.lang.Thread.sleep;

public class Main {
    static Scanner scanner = new Scanner(in);

    public static void main(String[] args) {

        var decoded = Base64.getDecoder().decode("BBACAAEAAAMI");
        out.println(Arrays.toString(decoded));
        byte[] converted = Converter.convertDecoded(decoded);
        out.println(Arrays.toString(converted));
        var rreq = new RREQ(converted);
        out.println(Arrays.toString(rreq.getBytes()));

        out.println(Arrays.toString(ArrayUtils.removeAll(converted, converted.length-1, converted.length-2, converted.length-3, converted.length-4)));

//        Connection connection = null;
//        while (true) {
//            if (connection == null) {
//                connection = setConnection();
//            } else {
//                var m = scanner.nextLine();
//                try {
//                    if (m.length() > 0 && !connection.send(m)) {
//                        connection = null;
//                    }
//                } catch (IOException e) {
//                    err.println("Failed");
//                    connection = null;
//                }
//            }
//        }

    }


    private static byte[] convertThree(byte a, byte b, byte c) {

        byte value1 = (byte) ((a & 0xff) >> 2);
        byte value2 = (byte) (((a & 0x3) << 4) | ((b & 0xff) >> 4));
        byte value3 = (byte) (((b & 0xf) << 2) | ((c & 0xff) >> 6));
        byte value4 = (byte) (c & 0x3f);

        return new byte[]{value1, value2, value3, value4};
    }

    static byte[] convertFour(byte a, byte b, byte c, byte d) {
        byte p = (byte) (((a & 0xff) << 2) | (b & 0xf0) >>> 4);
        byte q = (byte) (((b & 0x0f) << 4) | (c & 0xff) >>> 2);
        byte r = (byte) (((c & 0x0f) << 6) | (d & 0xff));
//        int s = ((d & 0x03) << 8) | (e & 0xff) >>> 0
        return new byte[]{p, q, r};
    }

    private static String getBits(BitSet set) {
        var bits = new BitString(set.length());
        for (int i = set.length() - 1; i >= 0; i--) {
            bits.setBit(i, set.get(i));
        }
        return bits.toString();
    }

//        private static Connection setConnection() {
//
//            out.print("enter port path: ");
//            var path = "/dev/ttys001"; //scanner.nextLine();
//            var port = SerialPort.getCommPort(path);
//            Connection connection = new Connection(port);
//
//            if (connection.connect()) {
//                out.println("Opened port: " + connection.port().getDescriptivePortName());
//                return connection;
//            } else {
//                return null;
//            }
//        }

    private static Connection setConnection() {
        Connection connection;
//        var port = SerialPort.getCommPort("/dev/ttys002");

        var ports = Connection.getPorts();
        for (int i = 0; i < ports.length; i++) {
            out.println(i + ". " + ports[i].getDescriptivePortName());
        }
        out.print("Choose port: ");
        var num = scanner.nextInt();
        if (num < 0 || num >= ports.length) {
            return null;
        }
        connection = new Connection(ports[num]);
//        connection = new Connection(port);
        if (connection.connect()) {
            out.println("Opened port: " + connection.port().getDescriptivePortName());
            return connection;
        } else {
            return null;
        }
    }
}
