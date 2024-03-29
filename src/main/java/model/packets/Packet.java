package model.packets;

public abstract class Packet {
    protected byte type;

    public Packet(byte type) {
        this.type = type;
    }


    public byte getType() {
        return type;
    }

    protected int convertSeqNumToUnsigned(byte seq){
        return seq & 0xff;
    }
}
