package model;

import model.packets.RREQ;
import model.route.ForwardRoute;
import model.route.ReverseRoute;
import model.route.Route;
import utils.MyLogger;
import utils.Parser;
import utils.Utils;

import java.util.*;

import static java.lang.Math.max;

public class Node {
    public static final int ACTIVE_ROUTE_TIMEOUT = 3000;
    public static final int MY_ROUTE_TIMEOUT = 2 * ACTIVE_ROUTE_TIMEOUT;
    public static final int NODE_TRAVERSAL_TIME = 40;
    public static final int RREQ_RETRIES = 2;
    public static final int NET_DIAMETER = 35;
    public static final int NET_TRAVERSAL_TIME = 2 * NODE_TRAVERSAL_TIME * NET_DIAMETER;
    public static final int PATH_DISCOVERY_TIME = 2 * NET_TRAVERSAL_TIME;

    public static final int DELETE_PERIOD = 5 * ACTIVE_ROUTE_TIMEOUT;


    private Node() {
    }

    public static class ProcessedRREQInfo {
        private final byte requestID;
        private final byte[] oriAddr;

        private long lifetime;

        public ProcessedRREQInfo(byte requestID, byte[] oriAddr) {
            this.requestID = requestID;
            this.oriAddr = oriAddr;
            lifetime = System.currentTimeMillis();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ProcessedRREQInfo that = (ProcessedRREQInfo) o;
            return requestID == that.requestID && Arrays.equals(oriAddr, that.oriAddr);
        }

        @Override
        public int hashCode() {
            int result = Objects.hash(requestID);
            result = 31 * result + Arrays.hashCode(oriAddr);
            return result;
        }

        @Override
        public String toString() {
            return "ProcessedRREQ:\n" +
                    requestID + "\t" + Arrays.toString(oriAddr);
        }
    }

    private static final Set<ProcessedRREQInfo> processedRREQ = new HashSet<>();
    private static  Map<Integer, ForwardRoute> ROUTE_TABLE = new HashMap<>();
    private static Map<Integer, ReverseRoute> REVERSE_ROUTE_TABLE = new HashMap<>();
    private static byte[] ADDR;
    private static byte REQUEST_ID = 0;
    private static int SEQ_NUM = 0;

    public static void setADDR(byte[] addr) {
        ADDR = addr;
    }

    public static byte[] getADDR() {
        return ADDR;
    }

    public static int getSeqNum() {
        return SEQ_NUM++;
    }

    public static byte useREQid() {
        var tmp = REQUEST_ID;
        REQUEST_ID++;
        return tmp;
    }

    public static void incrementSeqNum() {
        SEQ_NUM = ((SEQ_NUM + 1) % 0x100);
    }

    public static boolean updateRouteEntry(ForwardRoute control) {
        Map<Integer, ForwardRoute> tmp = new HashMap<>();
       ROUTE_TABLE.keySet().stream().filter(x-> ROUTE_TABLE.get(x).active()).forEach(x-> {
           tmp.put(x, ROUTE_TABLE.get(x));
       });
       ROUTE_TABLE = tmp;
        if (Arrays.equals(control.getDestAddr(), Node.getADDR()))
            return false;
        var entry = ROUTE_TABLE.putIfAbsent(Arrays.hashCode(control.getDestAddr()), control);
        if (entry != null) {
            if (!entry.isValidSeqNum()
                    || Utils.compareSeqNums(control.getSeq(), entry.getSeq()) > 0
                    || ((Utils.compareSeqNums(control.getSeq(), entry.getSeq()) == 0) && (control.getHopCount() < entry.getHopCount()))) {
                ROUTE_TABLE.put(Arrays.hashCode(control.getDestAddr()), control);
                return true;
            }else return false;
        }
        return true;
    }

    public static boolean updateReverseRouteEntry(ReverseRoute control) {
        Map<Integer, ReverseRoute> tmp = new HashMap<>();
        REVERSE_ROUTE_TABLE.keySet().stream().filter(x-> REVERSE_ROUTE_TABLE.get(x).active()).forEach(x-> {
            tmp.put(x, REVERSE_ROUTE_TABLE.get(x));
        });
        REVERSE_ROUTE_TABLE = tmp;

        var entry = REVERSE_ROUTE_TABLE.putIfAbsent(Arrays.hashCode(control.getSourceAddr()), control);
        if (entry != null) {
            if (!entry.isValidSeqNum()
                    || Utils.compareSeqNums(control.getSeq(), entry.getSeq()) > 0
                    || ((Utils.compareSeqNums(control.getSeq(), entry.getSeq()) == 0) && control.getHopCount() < entry.getHopCount())) {
                REVERSE_ROUTE_TABLE.put(Arrays.hashCode(control.getSourceAddr()), control);
                return true;
            } else return false;
        }
        return true;
    }

    public static void updateRouteLifetimeRREQ(byte[] destAddr) {
        ROUTE_TABLE.get(Arrays.hashCode(destAddr)).updateLifetimeRREQ();
    }

    public static void updateReverseRouteLifetimeRREQ(byte[] oriAddr) {
        REVERSE_ROUTE_TABLE.get(Arrays.hashCode(oriAddr)).updateLifetimeRREQ();
    }

    public static void updateRouteLifetimeRREP(byte[] destAddr, long lifetime) {
        ROUTE_TABLE.get(Arrays.hashCode(destAddr)).updateLifetimeRREP(lifetime);
    }

    public static void updateReverseRouteLifetimeRREP(byte[] oriAddr, long lifetime) {
        REVERSE_ROUTE_TABLE.get(Arrays.hashCode(oriAddr)).updateLifetimeRREP(lifetime);
    }

    public static boolean RREQWasProcessed(RREQ rreq) {
        var pr = new ProcessedRREQInfo(rreq.getReqId(), rreq.getOriAddr());
        var check = processedRREQ.contains(pr);
        if (!check) {
            processedRREQ.add(new ProcessedRREQInfo(rreq.getReqId(), rreq.getOriAddr()));
        }
        var pr2 = processedRREQ.stream().filter(x -> x.equals(pr)).toList().get(0);
        if (pr.lifetime - pr2.lifetime > Node.DELETE_PERIOD) {
            processedRREQ.remove(pr2);
            check = false;
        }
        return check;
    }

    public static ForwardRoute findRoute(byte[] destAddr) {
        return ROUTE_TABLE.get(Arrays.hashCode(destAddr));
    }


    public static ReverseRoute findReverseRoute(byte[] oriAddr) {
        return REVERSE_ROUTE_TABLE.get(Arrays.hashCode(oriAddr));
    }

    public static ForwardRoute validRouteExists(byte[] destAddr) {
        var route = findRoute(destAddr);
        if (route == null)
            return null;
        return route.isValid() ? route : null;
    }

    public static List<byte[]> getValidDestAddrs() {
        return ROUTE_TABLE.values().stream().filter(Route::isValid).map(Route::getDestAddr).toList();
    }

    public static void logInfo() {
        var procRREQ = "Processed" + MyLogger.createTable(processedRREQ.stream().map(x -> new String[]{String.valueOf(x.requestID), Parser.parseBytesToAddr(x.oriAddr)}).toList(),
                "RREQ_ID", "Originator");
        var fRouteT = "Forward" + MyLogger.createTable(ROUTE_TABLE.values().stream()
                        .map(x -> new String[]{Parser.parseBytesToAddr(x.getDestAddr()), Parser.parseBytesToAddr(x.getNextHop()), String.valueOf(x.getHopCount()), String.valueOf(x.isValid()), String.valueOf(x.getSeq())}).toList(),
                "Dest", "Hop", "HopCount", "Valid", "Seq");
        var rRouteT = "Reverse" + MyLogger.createTable(REVERSE_ROUTE_TABLE.values().stream()
                        .map(x -> new String[]{Parser.parseBytesToAddr(x.getDestAddr()), Parser.parseBytesToAddr(x.getPrevHop()), Parser.parseBytesToAddr(x.getSourceAddr()), String.valueOf(x.getHopCount()), String.valueOf(x.getSeq())}).toList(),
                "Dest", "Prev", "Source", "HopCount", "Seq");

        MyLogger.info("\n\nADDR= " + Parser.parseBytesToAddr(ADDR) + "\n" + procRREQ + fRouteT + rRouteT+"\n");
    }


    public static void clearForTest() {
        processedRREQ.clear();
        ROUTE_TABLE.clear();
        REVERSE_ROUTE_TABLE.clear();
        ADDR = null;
        SEQ_NUM = 0;
        REQUEST_ID = 0;
    }
}
