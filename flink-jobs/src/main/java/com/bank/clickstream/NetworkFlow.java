package com.bank.clickstream;

public class NetworkFlow {
    // All 52 features from your CICIDS2017 dataset in exact order
    public int destinationPort; // 0
    public long flowDuration; // 1
    public int totalFwdPackets; // 2
    public long totalLengthOfFwdPackets;  // 3
    public int fwdPacketLengthMax;        // 4
    public int fwdPacketLengthMin; // 5
    public double fwdPacketLengthMean; // 6
    public double fwdPacketLengthStd; // 7
    public int bwdPacketLengthMax; // 8
    public int bwdPacketLengthMin; // 9
    public double bwdPacketLengthMean; // 10
    public double bwdPacketLengthStd; // 11
    public double flowBytesPerS; // 12
    public double flowPacketsPerS; // 13
    public double flowIATMean; // 14
    public double flowIATStd; // 15
    public long flowIATMax; // 16
    public long flowIATMin; // 17
    public long fwdIATTotal; // 18
    public double fwdIATMean; // 19
    public double fwdIATStd; // 20
    public long fwdIATMax; // 21
    public long fwdIATMin; // 22
    public long bwdIATTotal; // 23
    public double bwdIATMean; // 24
    public double bwdIATStd; // 25
    public long bwdIATMax; // 26
    public long bwdIATMin; // 27
    public long fwdHeaderLength; // 28
    public long bwdHeaderLength; // 29
    public double fwdPacketsPerS; // 30
    public double bwdPacketsPerS; // 31
    public int minPacketLength; // 32
    public int maxPacketLength; // 33
    public double packetLengthMean; // 34
    public double packetLengthStd; // 35
    public double packetLengthVariance; // 36
    public int finFlagCount; // 37
    public int pshFlagCount; // 38
    public int ackFlagCount;              // 39
    public double averagePacketSize;      // 40
    public long subflowFwdBytes; // 41
    public long initWinBytesForward; // 42
    public long initWinBytesBackward; // 43
    public int actDataPktFwd; // 44
    public long minSegSizeForward; // 45
    public double activeMean; // 46
    public long activeMax; // 47
    public long activeMin; // 48
    public double idleMean; // 49
    public long idleMax; // 50
    public long idleMin; // 51

    // Additional context fields (not part of model features)
    public String sourceIP;
    public String destinationIP;
}